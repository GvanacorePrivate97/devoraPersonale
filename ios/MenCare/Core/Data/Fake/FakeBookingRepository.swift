import Foundation

@MainActor
final class FakeBookingRepository: BookingRepository {

    private let store: InMemoryStore

    init(store: InMemoryStore) {
        self.store = store
    }

    /// La scheda cliente dell'account con la sessione aperta: il fake fa quello
    /// che il server fa dai claims del token.
    private var myClientId: String? { store.currentUser?.clientRecordId }

    func myAppointments() async throws -> [Appointment] {
        guard let clientId = myClientId else { return [] }
        return store.appointments.filter { $0.clientId == clientId }.sorted { $0.start < $1.start }
    }

    func appointmentsForOperator(_ operatorId: String?, date: LocalDate) async throws -> [Appointment] {
        let scoped = operatorId ?? store.currentUser?.operatorId
        return store.appointments
            .filter { $0.operatorId == scoped && $0.date == date }
            .sorted { $0.start < $1.start }
    }

    func appointmentsForWeek(_ weekStart: LocalDate) async throws -> [Appointment] {
        let weekEnd = weekStart.plusDays(7)
        return store.appointments
            .filter { $0.date >= weekStart && $0.date < weekEnd }
            .sorted { $0.start < $1.start }
    }

    func appointment(_ id: String) async throws -> Appointment? {
        store.appointments.first { $0.id == id }
    }

    private func eligibleOperators(_ operatorId: String?, _ serviceIds: [String]) -> [Operator] {
        store.operators.filter { op in
            (operatorId == nil || op.id == operatorId) && serviceIds.allSatisfy { op.serviceIds.contains($0) }
        }
    }

    private func totalDuration(_ serviceIds: [String]) -> Int {
        store.services.filter { serviceIds.contains($0.id) }.reduce(0) { $0 + $1.durationMinutes }
    }

    /// `appointments` empty = the day's capacity: what would be free with no booking at all.
    private func computeSlots(
        _ operatorId: String?, _ serviceIds: [String], _ date: LocalDate, appointments: [Appointment]? = nil
    ) -> [LocalTime] {
        SlotEngine.unionSlots(
            date: date,
            operators: eligibleOperators(operatorId, serviceIds),
            salon: store.salon,
            totalDurationMinutes: totalDuration(serviceIds),
            appointments: appointments ?? store.appointments,
            blocks: store.timeBlocks,
            holidays: store.holidays,
            now: store.now()
        )
    }

    func availability(
        operatorId: String?, serviceIds: [String], date: LocalDate, ignoreAppointmentId: String?
    ) async throws -> DayAvailability {
        let others = store.appointments.filter { $0.id != ignoreAppointmentId }
        return DayAvailability(date: date, slots: computeSlots(operatorId, serviceIds, date, appointments: others))
    }

    func days(
        operatorId: String?, serviceIds: [String], from: LocalDate, to: LocalDate, ignoreAppointmentId: String?
    ) async throws -> DayRangeAvailability {
        let others = store.appointments.filter { $0.id != ignoreAppointmentId }
        var available = Set<LocalDate>()
        var full = Set<LocalDate>()
        var d = from
        while d <= to {
            let slots = computeSlots(operatorId, serviceIds, d, appointments: others)
            if slots.isEmpty {
                // Giorno aperto ma senza piu' niente di libero: e' quello che
                // offre la lista d'attesa, e va distinto da una chiusura.
                if !computeSlots(operatorId, serviceIds, d, appointments: []).isEmpty { full.insert(d) }
            } else {
                available.insert(d)
            }
            d = d.plusDays(1)
        }
        return DayRangeAvailability(available: available, fullyBooked: full)
    }

    func book(_ request: BookingRequest) async -> AppResult<Appointment> {
        try? await Task.sleep(for: .milliseconds(500))
        let services = store.services.filter { request.serviceIds.contains($0.id) }
        if services.isEmpty { return .failure(.validation(field: "services")) }

        // Re-verify inside the "transaction": the slot may have been taken
        // meanwhile. In modifica il vecchio appuntamento non conta: lo sostituisce.
        let others = store.appointments.filter { $0.id != request.replacesAppointmentId }
        let slots = computeSlots(request.operatorId, request.serviceIds, request.start.date, appointments: others)
        if !slots.contains(request.start.time) {
            return .failure(.slotNoLongerAvailable)
        }
        if let replaced = request.replacesAppointmentId {
            store.appointments = store.appointments.map {
                guard $0.id == replaced else { return $0 }
                var old = $0
                old.status = .cancelled
                old.cancelledBy = request.clientId == nil ? .client : .salon
                return old
            }
        }

        // "Qualsiasi operatore": pick the eligible operator free at that time.
        let operatorId: String
        if let requested = request.operatorId {
            operatorId = requested
        } else {
            let free = eligibleOperators(nil, request.serviceIds).first { op in
                SlotEngine.slotsFor(
                    date: request.start.date, operator: op, salon: store.salon,
                    totalDurationMinutes: totalDuration(request.serviceIds),
                    appointments: store.appointments, blocks: store.timeBlocks,
                    holidays: store.holidays, now: store.now()
                ).contains(request.start.time)
            }
            guard let free else { return .failure(.slotNoLongerAvailable) }
            operatorId = free.id
        }

        func instance(_ start: LocalDateTime) -> Appointment {
            Appointment(
                id: store.newId("apt"),
                clientId: request.clientId ?? myClientId ?? "",
                operatorId: operatorId,
                serviceIds: request.serviceIds,
                start: start,
                durationMinutes: services.reduce(0) { $0 + $1.durationMinutes },
                totalPriceCents: services.reduce(0) { $0 + $1.priceCents },
                status: .confirmed,
                channel: request.channel,
                noteForOperator: request.noteForOperator.flatMap { $0.isEmpty ? nil : $0 }
            )
        }

        let first = instance(request.start)
        store.appointments.append(first)
        return .success(first)
    }

    func reschedule(_ appointmentId: String, newStart: LocalDateTime, newOperatorId: String?) async -> AppResult<Appointment> {
        guard let current = store.appointments.first(where: { $0.id == appointmentId }) else {
            return .failure(.notFound)
        }
        let operatorId = newOperatorId ?? current.operatorId

        // A manual move is free-form: it only has to fit the operator's real
        // availability, not the client-facing slot grid or its lead time.
        guard let op = store.operators.first(where: { $0.id == operatorId }) else {
            return .failure(.notFound)
        }
        let fits = SlotEngine.canPlace(
            date: newStart.date,
            operator: op,
            salon: store.salon,
            start: newStart.time,
            totalDurationMinutes: current.durationMinutes,
            appointments: store.appointments,
            blocks: store.timeBlocks,
            holidays: store.holidays,
            ignoreAppointmentId: appointmentId
        )
        if !fits { return .failure(.slotNoLongerAvailable) }

        var updated = current
        updated.start = newStart
        updated.operatorId = operatorId
        store.appointments = store.appointments.map { $0.id == appointmentId ? updated : $0 }
        notifyWaitlist(current.date)
        return .success(updated)
    }

    func cancel(_ appointmentId: String, by: CancellationActor) async -> AppResult<Void> {
        guard let found = store.appointments.first(where: { $0.id == appointmentId }) else {
            return .failure(.notFound)
        }
        store.appointments = store.appointments.map {
            guard $0.id == appointmentId else { return $0 }
            var apt = $0
            apt.status = .cancelled
            apt.cancelledBy = by
            return apt
        }
        notifyWaitlist(found.date)
        return .success(())
    }

    private func setStatus(_ appointmentId: String, _ status: AppointmentStatus) -> AppResult<Void> {
        guard store.appointments.contains(where: { $0.id == appointmentId }) else {
            return .failure(.notFound)
        }
        store.appointments = store.appointments.map {
            guard $0.id == appointmentId else { return $0 }
            var apt = $0
            apt.status = status
            return apt
        }
        return .success(())
    }

    func markInProgress(_ appointmentId: String) async -> AppResult<Void> {
        setStatus(appointmentId, .inProgress)
    }

    func markNoShow(_ appointmentId: String) async -> AppResult<Void> {
        guard let apt = store.appointments.first(where: { $0.id == appointmentId }) else {
            return .failure(.notFound)
        }
        guard apt.start <= store.now() else { return .failure(.validation(field: "status")) }
        let wasCompleted = apt.status == .completed
        let result = setStatus(appointmentId, .noShow)
        store.clients = store.clients.map {
            guard $0.id == apt.clientId else { return $0 }
            var client = $0
            client.noShowCount += 1
            if wasCompleted {
                client.visitCount -= 1
                client.lifetimeSpendCents -= apt.totalPriceCents
            }
            return client
        }
        return result
    }

    func markCompleted(_ appointmentId: String) async -> AppResult<Void> {
        guard let apt = store.appointments.first(where: { $0.id == appointmentId }) else {
            return .failure(.notFound)
        }
        if apt.status == .completed { return .success(()) }
        if apt.status == .noShow {
            store.clients = store.clients.map {
                guard $0.id == apt.clientId else { return $0 }
                var client = $0
                client.noShowCount -= 1
                return client
            }
        }
        let result = setStatus(appointmentId, .completed)
        // Completion drives the visit counter and spend stats.
        store.clients = store.clients.map {
            guard $0.id == apt.clientId else { return $0 }
            var client = $0
            client.visitCount += 1
            client.lifetimeSpendCents += apt.totalPriceCents
            client.lastVisit = apt.date
            return client
        }
        return result
    }

    /// Queue position, derived rather than stored: entries for the same day and
    /// the same operator choice, in the order they joined. Leaving moves the
    /// others up.
    private func positionOf(_ entry: WaitlistEntry, in all: [WaitlistEntry]) -> Int {
        let queue = all.filter {
            $0.status == .waiting && $0.date == entry.date && $0.operatorId == entry.operatorId
        }
        return (queue.firstIndex { $0.id == entry.id } ?? -1) + 1
    }

    func myWaitlist() async throws -> [WaitlistEntry] {
        guard let clientId = myClientId else { return [] }
        let all = store.waitlist
        let today = store.now().date
        return all
            .filter { $0.clientId == clientId && $0.status == .waiting && $0.date >= today }
            .map { entry -> WaitlistEntry in
                var positioned = entry
                positioned.position = positionOf(entry, in: all)
                return positioned
            }
            .sorted { ($0.date, $0.time?.minutesOfDay ?? -1) < ($1.date, $1.time?.minutesOfDay ?? -1) }
    }

    func joinWaitlist(date: LocalDate, time: LocalTime?, operatorId: String?, serviceIds: [String]) async -> AppResult<WaitlistEntry> {
        guard let clientId = myClientId else { return .failure(.unauthorized) }
        if var existing = store.waitlist.first(where: {
            $0.clientId == clientId && $0.date == date && $0.time == time &&
                $0.operatorId == operatorId && $0.status == .waiting
        }) {
            existing.position = positionOf(existing, in: store.waitlist)
            return .success(existing)
        }

        let services = store.services.filter { serviceIds.contains($0.id) }
        var entry = WaitlistEntry(
            id: store.newId("wl"),
            clientId: clientId,
            date: date,
            time: time,
            operatorId: operatorId,
            serviceIds: serviceIds,
            durationMinutes: services.reduce(0) { $0 + $1.durationMinutes },
            totalPriceCents: services.reduce(0) { $0 + $1.priceCents },
            position: 0
        )
        store.waitlist.append(entry)
        entry.position = positionOf(entry, in: store.waitlist)
        return .success(entry)
    }

    func leaveWaitlist(_ entryId: String) async -> AppResult<Void> {
        store.waitlist.removeAll { $0.id == entryId }
        return .success(())
    }

    /// Something freed up on `date`: the first entry in line whose services fit
    /// again gets a notification and leaves the queue — the next freed slot
    /// goes to the next one. The slot isn't held: whoever books first gets it.
    private func notifyWaitlist(_ date: LocalDate) {
        var freed: LocalTime?
        let first = store.waitlist.first { entry in
            guard entry.status == .waiting, entry.date == date else { return false }
            let slots = computeSlots(entry.operatorId, entry.serviceIds, date)
            if let wanted = entry.time {
                freed = slots.contains(wanted) ? wanted : nil
            } else {
                freed = slots.first
            }
            return freed != nil
        }
        guard let first, let time = freed else { return }
        store.waitlist = store.waitlist.map {
            guard $0.id == first.id else { return $0 }
            var notified = $0
            notified.status = .notified
            return notified
        }
        guard let userId = store.users.first(where: { $0.clientRecordId == first.clientId })?.id else { return }
        let withOperator = first.operatorId
            .flatMap { id in store.operators.first { $0.id == id } }
            .map { " con \($0.name.split(separator: " ").first.map(String.init) ?? $0.name)" } ?? ""
        store.notifications.append(
            AppNotification(
                id: store.newId("ntf"),
                userId: userId,
                title: "Lista d'attesa",
                body: "Si è liberato un posto \(formatDateLong(date)) alle \(formatTime(time))\(withOperator): "
                    + "prenota prima che lo prenda qualcun altro.",
                at: store.now()
            )
        )
    }
}
