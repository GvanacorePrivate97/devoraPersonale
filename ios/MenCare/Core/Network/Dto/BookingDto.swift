import Foundation

// Agenda e lista d'attesa — docs/API.md §Agenda e §Lista d'attesa.

struct AppointmentDto: Decodable {
    let id: String
    let clientId: String
    let operatorId: String
    let serviceIds: [String]
    let date: LocalDate
    let time: LocalTime
    let startsAt: String
    let durationMinutes: Int
    let totalPriceCents: Int64
    let status: String
    let channel: String
    let noteForOperator: String?
    let cancelledBy: String?

    func toDomain() -> Appointment {
        Appointment(
            id: id,
            clientId: clientId,
            operatorId: operatorId,
            serviceIds: serviceIds,
            // `date` + `time` sono l'ora da muro del salone: e' quella che le
            // schermate mostrano. `startsAt` serve solo al server.
            start: date.atTime(time),
            durationMinutes: durationMinutes,
            totalPriceCents: totalPriceCents,
            status: WireEnum.appointmentStatus(status),
            channel: WireEnum.channel(channel),
            noteForOperator: noteForOperator,
            cancelledBy: WireEnum.cancellationActor(cancelledBy)
        )
    }
}

struct AppointmentsResponseDto: Decodable {
    let appointments: [AppointmentDto]
}

/// `GET /booking/availability` — gli slot li calcola il server, preavviso di
/// 30 minuti e unione "qualsiasi operatore" compresi.
struct AvailabilityDto: Decodable {
    let date: LocalDate
    let slots: [LocalTime]
}

/// `GET /booking/days` — giorni con almeno uno slot e giorni pieni (aperti ma
/// senza piu' niente di libero: quelli offrono la lista d'attesa).
struct AvailabilityRangeDto: Decodable {
    let available: [LocalDate]
    let fullyBooked: [LocalDate]
}

/// `GET /booking/next-availability` — prima disponibilita' di ogni operatore.
/// Mappata ma non usata: vedi la nota su `BookingEndpoint.nextAvailability`.
struct NextAvailabilityDto: Decodable {
    struct Entry: Decodable {
        let operatorId: String
        let date: LocalDate
        let time: LocalTime
    }

    let operators: [Entry]
}

struct WaitlistEntryDto: Decodable {
    let id: String
    let clientId: String
    let date: LocalDate
    let time: LocalTime?
    let operatorId: String?
    let serviceIds: [String]
    let durationMinutes: Int
    let totalPriceCents: Int64
    let position: Int
    let status: String

    func toDomain() -> WaitlistEntry {
        WaitlistEntry(
            id: id,
            clientId: clientId,
            date: date,
            time: time,
            operatorId: operatorId,
            serviceIds: serviceIds,
            durationMinutes: durationMinutes,
            totalPriceCents: totalPriceCents,
            // La posizione in coda la calcola il server: era una delle cose che
            // le due app facevano da sole, ognuna a modo suo.
            position: position,
            status: WireEnum.waitlistStatus(status)
        )
    }
}

struct WaitlistResponseDto: Decodable {
    let entries: [WaitlistEntryDto]
}

// MARK: - Corpi delle scritture

struct BookRequestDto: Encodable {
    let clientId: String?
    let operatorId: String?
    let serviceIds: [String]
    let date: LocalDate
    let time: LocalTime
    let noteForOperator: String?
    let channel: String?
    /// "Modifica": l'appuntamento che questo sostituisce (vedi `BookingRequest`).
    let replacesAppointmentId: String?
}

struct RescheduleRequestDto: Encodable {
    let date: LocalDate
    let time: LocalTime
    let operatorId: String?
}

struct StatusRequestDto: Encodable {
    let status: String
}

struct JoinWaitlistRequestDto: Encodable {
    let date: LocalDate
    let time: LocalTime?
    let operatorId: String?
    let serviceIds: [String]
}
