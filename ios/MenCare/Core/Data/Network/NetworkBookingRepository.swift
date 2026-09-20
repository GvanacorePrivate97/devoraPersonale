import Foundation

@MainActor
final class NetworkBookingRepository: BookingRepository {

    private let client: ApiClient

    init(client: ApiClient) {
        self.client = client
    }

    // MARK: - Letture

    func myAppointments() async throws -> [Appointment] {
        try await apiThrowing {
            try await client.send(BookingEndpoint.myAppointments, as: AppointmentsResponseDto.self)
                .appointments.map { $0.toDomain() }
                .sorted { $0.start < $1.start }
        }
    }

    func appointmentsForOperator(_ operatorId: String?, date: LocalDate) async throws -> [Appointment] {
        try await apiThrowing {
            try await client.send(
                BookingEndpoint.appointments(operatorId: operatorId, date: date), as: AppointmentsResponseDto.self
            )
            .appointments.map { $0.toDomain() }
            .sorted { $0.start < $1.start }
        }
    }

    func appointmentsForWeek(_ weekStart: LocalDate) async throws -> [Appointment] {
        try await apiThrowing {
            try await client.send(BookingEndpoint.week(weekStart), as: AppointmentsResponseDto.self)
                .appointments.map { $0.toDomain() }
                .sorted { $0.start < $1.start }
        }
    }

    func appointment(_ id: String) async throws -> Appointment? {
        do {
            return try await client.send(BookingEndpoint.appointment(id), as: AppointmentDto.self).toDomain()
        } catch AppError.notFound {
            // "Non c'e'" non e' un guasto: la schermata mostra il suo vuoto.
            return nil
        } catch {
            throw ApiError.appError(from: error)
        }
    }

    func availability(
        operatorId: String?, serviceIds: [String], date: LocalDate, ignoreAppointmentId: String?
    ) async throws -> DayAvailability {
        guard !serviceIds.isEmpty else { return DayAvailability(date: date, slots: []) }
        return try await apiThrowing {
            let dto = try await client.send(
                BookingEndpoint.availability(
                    operatorId: operatorId, serviceIds: serviceIds, date: date, ignoreAppointmentId: ignoreAppointmentId
                ),
                as: AvailabilityDto.self
            )
            return DayAvailability(date: dto.date, slots: dto.slots)
        }
    }

    func days(
        operatorId: String?, serviceIds: [String], from: LocalDate, to: LocalDate, ignoreAppointmentId: String?
    ) async throws -> DayRangeAvailability {
        guard !serviceIds.isEmpty, from <= to else { return .empty }
        return try await apiThrowing {
            let dto = try await client.send(
                BookingEndpoint.days(
                    operatorId: operatorId, serviceIds: serviceIds, from: from, to: to,
                    ignoreAppointmentId: ignoreAppointmentId
                ),
                as: AvailabilityRangeDto.self
            )
            return DayRangeAvailability(available: Set(dto.available), fullyBooked: Set(dto.fullyBooked))
        }
    }

    func myWaitlist() async throws -> [WaitlistEntry] {
        try await apiThrowing {
            try await client.send(BookingEndpoint.myWaitlist, as: WaitlistResponseDto.self)
                .entries.map { $0.toDomain() }
        }
    }

    // MARK: - Scritture

    func book(_ request: BookingRequest) async -> AppResult<Appointment> {
        await apiResult {
            let note = request.noteForOperator?.trimmingCharacters(in: .whitespacesAndNewlines)
            let dto = try await client.send(
                BookingEndpoint.book(
                    BookRequestDto(
                        clientId: request.clientId,
                        operatorId: request.operatorId,
                        serviceIds: request.serviceIds,
                        date: request.start.date,
                        time: request.start.time,
                        noteForOperator: (note?.isEmpty ?? true) ? nil : note,
                        channel: WireEnum.channelWire(request.channel),
                        replacesAppointmentId: request.replacesAppointmentId
                    )
                ),
                as: AppointmentDto.self
            )
            return dto.toDomain()
        }
    }

    func reschedule(
        _ appointmentId: String, newStart: LocalDateTime, newOperatorId: String?
    ) async -> AppResult<Appointment> {
        await apiResult {
            try await client.send(
                BookingEndpoint.reschedule(
                    appointmentId,
                    RescheduleRequestDto(date: newStart.date, time: newStart.time, operatorId: newOperatorId)
                ),
                as: AppointmentDto.self
            ).toDomain()
        }
    }

    func cancel(_ appointmentId: String, by: CancellationActor) async -> AppResult<Void> {
        // Chi annulla lo decide il server dal ruolo del token: il cliente puo'
        // solo annullare per se', e fino a due ore prima.
        await apiResult { _ = try await client.send(BookingEndpoint.cancel(appointmentId)) }
    }

    func markInProgress(_ appointmentId: String) async -> AppResult<Void> {
        await setStatus(appointmentId, "IN_PROGRESS")
    }

    func markCompleted(_ appointmentId: String) async -> AppResult<Void> {
        await setStatus(appointmentId, "COMPLETED")
    }

    func markNoShow(_ appointmentId: String) async -> AppResult<Void> {
        await setStatus(appointmentId, "NO_SHOW")
    }

    private func setStatus(_ appointmentId: String, _ status: String) async -> AppResult<Void> {
        await apiResult {
            _ = try await client.send(
                BookingEndpoint.setStatus(appointmentId, StatusRequestDto(status: status)), as: AppointmentDto.self
            )
        }
    }

    func joinWaitlist(
        date: LocalDate, time: LocalTime?, operatorId: String?, serviceIds: [String]
    ) async -> AppResult<WaitlistEntry> {
        await apiResult {
            try await client.send(
                BookingEndpoint.joinWaitlist(
                    JoinWaitlistRequestDto(date: date, time: time, operatorId: operatorId, serviceIds: serviceIds)
                ),
                as: WaitlistEntryDto.self
            ).toDomain()
        }
    }

    func leaveWaitlist(_ entryId: String) async -> AppResult<Void> {
        await apiResult { _ = try await client.send(BookingEndpoint.leaveWaitlist(entryId)) }
    }
}
