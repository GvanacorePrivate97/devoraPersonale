import Foundation

enum AppointmentStatus: Hashable {
    case confirmed, inProgress, completed, cancelled, noShow
}

enum BookingChannel: Hashable {
    case app, phone, walkIn
}

enum CancellationActor: Hashable {
    case client, salon
}

struct Appointment: Identifiable, Hashable {
    let id: String
    let clientId: String
    var operatorId: String
    var serviceIds: [String]
    var start: LocalDateTime
    var durationMinutes: Int
    var totalPriceCents: Int64
    var status: AppointmentStatus
    var channel: BookingChannel = .app
    var noteForOperator: String?
    var cancelledBy: CancellationActor?

    var end: LocalDateTime { start.plusMinutes(durationMinutes) }

    var date: LocalDate { start.date }
    var time: LocalTime { start.time }

    var isActive: Bool {
        status == .confirmed || status == .inProgress
    }

    /// In corso e completato arrivano da soli (lo decide il server a orario);
    /// a mano resta il no-show: da orario d'inizio passato, anche su un
    /// appuntamento già chiuso come completato.
    func canMarkNoShow(now: LocalDateTime = .now()) -> Bool {
        start <= now && (isActive || status == .completed)
    }

    /// Un no-show segnato per sbaglio si corregge riportandolo a completato.
    var canRevertNoShow: Bool { status == .noShow }
}

enum WaitlistStatus: Hashable {
    case waiting, notified, expired
}

struct WaitlistEntry: Identifiable, Hashable {
    let id: String
    let clientId: String
    var date: LocalDate
    var time: LocalTime?
    var operatorId: String?
    var serviceIds: [String]
    var durationMinutes: Int
    var totalPriceCents: Int64
    var position: Int
    var status: WaitlistStatus = .waiting
}
