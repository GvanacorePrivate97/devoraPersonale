import Foundation
import Observation


/// Wizard steps 1–4 plus the final confirmation screen.
enum WizardStep: Int {
    case operatorStep, services, datetime, summary, confirmed
}

struct OperatorOption: Identifiable {
    let op: Operator
    let nextAvailability: LocalDateTime?

    var id: String { op.id }
    var availableSoon: Bool { nextAvailability != nil }
}

enum BookingError: Equatable {
    case slotTaken
    /// Messaggio gia' pronto: rete assente, conflitto, guasto del server.
    case message(String)
}

/// Year+month pair, the `YearMonth` of the calendar step.
struct YearMonth: Hashable {
    let year: Int
    let month: Int

    static func of(_ date: LocalDate) -> YearMonth { YearMonth(year: date.year, month: date.month) }
    static func current() -> YearMonth { of(.today()) }

    var firstDay: LocalDate { LocalDate(year: year, month: month, day: 1) }
    var lastDay: LocalDate { firstDay.endOfMonth }

    func plusMonths(_ n: Int) -> YearMonth { .of(firstDay.plusMonths(n)) }

    static func > (lhs: YearMonth, rhs: YearMonth) -> Bool {
        (lhs.year, lhs.month) > (rhs.year, rhs.month)
    }
}

/// Prefill coming from outside the wizard: a home quick slot, a rebook or an edit.
enum BookingEntry {
    case blank
    case rebook(appointmentId: String)
    case edit(appointmentId: String)
    case quickSlot(QuickSlot)
}

@MainActor
@Observable
final class BookingViewModel {

    private let auth: AuthRepository
    private let booking: BookingRepository
    private let catalog: CatalogRepository

    var step: WizardStep = .operatorStep
    var salon: Salon?
    // Step 1
    var operatorOptions: [OperatorOption] = []
    var selectedOperatorId: String?
    var anyOperator = false
    // Step 2
    var services: [Service] = []
    var selectedServiceIds: [String] = []
    // Step 3
    var month: YearMonth = .current()
    var availableDays: Set<LocalDate> = []
    /// Open days whose every slot is taken: selectable, they offer the waitlist.
    var fullyBookedDays: Set<LocalDate> = []
    var selectedDate: LocalDate?
    var slots: [LocalTime] = []
    var slotsLoading = false
    var selectedSlot: LocalTime?
    // Step 4
    var note = ""
    var submitting = false
    var error: BookingError?
    var confirmed: Appointment?
    // Waitlist: set when the chosen slot turns out taken at confirm time.
    var takenSlot: LocalTime?
    var waitlistJoinedPosition: Int?
    var joiningWaitlist = false
    /// Set when the wizard edits an upcoming appointment instead of creating one.
    var editingAppointmentId: String?

    /// Primo caricamento del wizard: listino, squadra e prime disponibilita'.
    let state = LoadState()
    /// La coda del giorno scelto, letta dal server: la posizione non la calcola
    /// piu' l'app.
    private(set) var dayWaitlistEntry: WaitlistEntry?

    var isEditing: Bool { editingAppointmentId != nil }

    private let entry: BookingEntry

    init(auth: AuthRepository, booking: BookingRepository, catalog: CatalogRepository, entry: BookingEntry = .blank) {
        self.auth = auth
        self.booking = booking
        self.catalog = catalog
        self.entry = entry
    }

    var operatorChosen: Bool { anyOperator || selectedOperatorId != nil }

    var selectedServices: [Service] { services.filter { selectedServiceIds.contains($0.id) } }

    var totalDurationMinutes: Int { selectedServices.reduce(0) { $0 + $1.durationMinutes } }

    /// Duration used for slot generation.
    var totalSlotMinutes: Int { totalDurationMinutes }

    var totalPriceCents: Int64 { selectedServices.reduce(0) { $0 + $1.priceCents } }

    /// The chosen day has no free slot at all: the "Avvisami" page replaces the grid.
    var selectedDayFull: Bool {
        guard let selectedDate else { return false }
        return fullyBookedDays.contains(selectedDate) && !slotsLoading && slots.isEmpty
    }

    var selectedOperator: Operator? { operatorOptions.first { $0.op.id == selectedOperatorId }?.op }

    /// Services the chosen operator can perform (all when "Qualsiasi").
    var eligibleServices: [Service] {
        if anyOperator { return services }
        guard let op = selectedOperator else { return [] }
        return services.filter { op.serviceIds.contains($0.id) }
    }

    func load() async {
        // Un solo giro: listino e squadra insieme, poi le prime disponibilita'
        // che il server calcola per tutti gli operatori in una chiamata sola.
        await state.run {
            let snapshot = try await catalog.catalog()
            salon = snapshot.salon
            services = snapshot.services

            let next = await nextAvailabilityByOperator(snapshot.operators)
            operatorOptions = snapshot.operators.map {
                OperatorOption(op: $0, nextAvailability: next[$0.id])
            }

            switch entry {
            case .quickSlot(let slot):
                // Slot scelto dalla home: si atterra sullo step data, gia' compilato.
                try await applyQuickSlot(slot)
            case .rebook(let id):
                // Riprenota con un tocco: operatore e servizi della visita passata.
                if let appointment = try await booking.appointment(id) {
                    prefillFrom(appointment)
                }
            case .edit(let id):
                if let appointment = try await booking.appointment(id) {
                    prefillForEdit(appointment)
                }
            case .blank:
                break
            }
        }
    }

    /// "Prima disponibilita'" di ogni operatore, composta da `/booking/days` piu'
    /// `/booking/availability` sul primo giorno utile. C'e' anche una rotta che
    /// la calcola in un colpo solo, ma userebbe un metodo che il repository
    /// Android non ha: due contratti diversi per la stessa app, no. Le richieste
    /// partono insieme, una per operatore, non un giorno alla volta.
    private func nextAvailabilityByOperator(_ team: [Operator]) async -> [String: LocalDateTime] {
        let probes: [(id: String, serviceIds: [String])] = team.compactMap { op in
            let serviceIds = selectedServiceIds.isEmpty
                ? Array(op.serviceIds.prefix(1))
                : selectedServiceIds.filter { op.serviceIds.contains($0) }
            return serviceIds.isEmpty ? nil : (op.id, serviceIds)
        }
        let today = LocalDate.today()
        let horizon = today.plusDays(14)

        return await withTaskGroup(of: (String, LocalDateTime?).self) { group in
            for probe in probes {
                group.addTask { @MainActor [booking] in
                    guard let range = try? await booking.days(
                        operatorId: probe.id, serviceIds: probe.serviceIds, from: today, to: horizon
                    ), let firstDay = range.available.min() else {
                        return (probe.id, nil)
                    }
                    let slots = try? await booking.availability(
                        operatorId: probe.id, serviceIds: probe.serviceIds, date: firstDay
                    ).slots
                    return (probe.id, slots?.first.map { firstDay.atTime($0) })
                }
            }
            var result: [String: LocalDateTime] = [:]
            for await (operatorId, when) in group {
                result[operatorId] = when
            }
            return result
        }
    }

    /// Apre il wizard sullo slot scelto in home. Gli slot vengono ricalcolati
    /// qui: se nel frattempo qualcuno ha preso quell'ora, il giorno resta
    /// selezionato ma l'ora no, e il cliente ne sceglie un'altra.
    private func applyQuickSlot(_ quick: QuickSlot) async throws {
        let month = YearMonth.of(quick.date)
        let from = max(month.firstDay, .today())
        let range = try await booking.days(
            operatorId: quick.operatorId, serviceIds: quick.serviceIds, from: from, to: month.lastDay
        )
        let daySlots = try await booking.availability(
            operatorId: quick.operatorId, serviceIds: quick.serviceIds, date: quick.date
        ).slots
        step = .datetime
        selectedOperatorId = quick.operatorId
        anyOperator = quick.operatorId == nil
        selectedServiceIds = quick.serviceIds
        self.month = month
        availableDays = range.available
        fullyBookedDays = range.fullyBooked
        selectedDate = quick.date
        slots = daySlots
        selectedSlot = daySlots.contains(quick.time) ? quick.time : nil
        slotsLoading = false
    }

    // MARK: - Step 1

    func selectOperator(_ operatorId: String?) {
        selectedOperatorId = operatorId
        anyOperator = operatorId == nil
        // Selection changes filtering: drop services no longer eligible.
        if let operatorId {
            let allowed = operatorOptions.first { $0.op.id == operatorId }?.op.serviceIds ?? []
            selectedServiceIds = selectedServiceIds.filter { allowed.contains($0) }
        }
        selectedDate = nil
        selectedSlot = nil
        slots = []
    }

    func goToStep(_ target: WizardStep) {
        step = target
        error = nil
    }

    func continueFromOperator() {
        if operatorChosen { goToStep(.services) }
    }

    // MARK: - Step 2

    func toggleService(_ serviceId: String) {
        if let index = selectedServiceIds.firstIndex(of: serviceId) {
            selectedServiceIds.remove(at: index)
        } else {
            selectedServiceIds.append(serviceId)
        }
        selectedDate = nil
        selectedSlot = nil
        slots = []
    }

    func continueFromServices() {
        guard !selectedServiceIds.isEmpty else { return }
        step = .datetime
        loadMonth(month)
    }

    // MARK: - Step 3

    func loadMonth(_ target: YearMonth) {
        month = target
        slotsLoading = true
        Task {
            let today = LocalDate.today()
            let from = max(target.firstDay, today)
            let to = target.lastDay
            let operatorId = anyOperator ? nil : selectedOperatorId
            if to < today {
                availableDays = []
                fullyBookedDays = []
            } else {
                // Giorni liberi e giorni pieni in una chiamata sola: e' il server
                // a sapere quali giorni il salone e' aperto e gia' esaurito.
                // In modifica l'appuntamento originale non occupa il suo posto.
                let range = (try? await booking.days(
                    operatorId: operatorId, serviceIds: selectedServiceIds, from: from, to: to,
                    ignoreAppointmentId: editingAppointmentId
                )) ?? .empty
                availableDays = range.available
                fullyBookedDays = range.fullyBooked
            }
            slotsLoading = false
        }
    }

    func selectDate(_ date: LocalDate) {
        selectedDate = date
        selectedSlot = nil
        slotsLoading = true
        Task {
            let availability = try? await booking.availability(
                operatorId: anyOperator ? nil : selectedOperatorId,
                serviceIds: selectedServiceIds, date: date,
                ignoreAppointmentId: editingAppointmentId
            )
            slots = availability?.slots ?? []
            slotsLoading = false
            await refreshDayWaitlist()
        }
    }

    /// Rilegge la propria coda per il giorno scelto: serve a sapere se la
    /// pagina "Avvisami" deve mostrare la posizione invece del pulsante.
    private func refreshDayWaitlist() async {
        guard let selectedDate else {
            dayWaitlistEntry = nil
            return
        }
        let operatorId = anyOperator ? nil : selectedOperatorId
        let queue = (try? await booking.myWaitlist()) ?? []
        dayWaitlistEntry = queue.first {
            $0.date == selectedDate && $0.time == nil && $0.operatorId == operatorId
        }
    }

    func selectSlot(_ slot: LocalTime) {
        // Picking a new time closes the lost-slot waitlist prompt.
        selectedSlot = slot
        takenSlot = nil
        error = nil
        waitlistJoinedPosition = nil
    }

    func continueFromDatetime() {
        if selectedDate != nil && selectedSlot != nil { goToStep(.summary) }
    }

    // MARK: - Step 4

    /// Il taglio lo fa il campo condiviso; qui resta il gancio per lo stato.
    func noteChanged() {
        note = String(note.prefix(maxNoteLength))
    }

    func confirm() {
        guard let date = selectedDate, let slot = selectedSlot, !submitting else { return }
        submitting = true
        error = nil
        Task {
            // Il cliente prenota per se': la scheda la ricava il server dal
            // token, l'app non la manda piu'.
            let result = await booking.book(
                BookingRequest(
                    clientId: nil,
                    operatorId: anyOperator ? nil : selectedOperatorId,
                    serviceIds: selectedServiceIds,
                    start: date.atTime(slot),
                    noteForOperator: note,
                    // Modifica: il server sostituisce l'originale nella stessa transazione.
                    replacesAppointmentId: editingAppointmentId
                )
            )
            submitting = false
            switch result {
            case .success(let appointment):
                confirmed = appointment
                step = .confirmed
            case .failure(let err):
                if err == .slotNoLongerAvailable {
                    // The mockup's flagged race: bounce back to slot choice with fresh
                    // data, remembering the lost slot so the waitlist can target it.
                    error = .slotTaken
                    step = .datetime
                    takenSlot = slot
                    // The lost slot may have been the last one: the day can turn full.
                    loadMonth(month)
                    selectDate(date)
                } else {
                    error = .message(err.displayMessage)
                }
            }
        }
    }

    // MARK: - Waitlist

    /// Joins the queue for the slot the client just lost: a push arrives if it
    /// frees up again. Only reachable after a slot-taken bounce.
    func joinWaitlist() {
        guard let date = selectedDate, let slot = takenSlot else { return }
        Task {
            let result = await booking.joinWaitlist(
                date: date, time: slot,
                operatorId: anyOperator ? nil : selectedOperatorId,
                serviceIds: selectedServiceIds
            )
            switch result {
            case .success(let entry): waitlistJoinedPosition = entry.position
            case .failure(let failure): error = .message(failure.displayMessage)
            }
        }
    }

    /// "Avvisami" on a fully booked day: queues the client for any time of
    /// that day, with the chosen operator (or any), sized on the chosen services.
    func joinDayWaitlist() {
        guard let date = selectedDate, !joiningWaitlist, dayWaitlistEntry == nil else { return }
        joiningWaitlist = true
        Task {
            let result = await booking.joinWaitlist(
                date: date, time: nil,
                operatorId: anyOperator ? nil : selectedOperatorId,
                serviceIds: selectedServiceIds
            )
            switch result {
            case .success(let entry): dayWaitlistEntry = entry
            case .failure(let failure): error = .message(failure.displayMessage)
            }
            joiningWaitlist = false
        }
    }

    /// One-tap rebook: preselect operator + services of a past appointment.
    func prefillFrom(_ appointment: Appointment) {
        selectedOperatorId = appointment.operatorId
        anyOperator = false
        selectedServiceIds = appointment.serviceIds
        step = .datetime
        loadMonth(.current())
    }

    /// Modifica: operatore, servizi e note dell'appuntamento sono già scelti,
    /// ma il wizard riparte dal primo step così il cliente può rivedere tutto.
    func prefillForEdit(_ appointment: Appointment) {
        editingAppointmentId = appointment.id
        selectedOperatorId = appointment.operatorId
        anyOperator = false
        selectedServiceIds = appointment.serviceIds
        note = appointment.noteForOperator ?? ""
        month = .of(appointment.date)
        step = .operatorStep
    }

    func reset() {
        step = .operatorStep
        selectedOperatorId = nil
        anyOperator = false
        selectedServiceIds = []
        month = .current()
        availableDays = []
        fullyBookedDays = []
        selectedDate = nil
        slots = []
        slotsLoading = false
        selectedSlot = nil
        note = ""
        submitting = false
        error = nil
        confirmed = nil
        takenSlot = nil
        waitlistJoinedPosition = nil
        joiningWaitlist = false
        editingAppointmentId = nil
        dayWaitlistEntry = nil
    }
}
