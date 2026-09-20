import Foundation

// Forme JSON condivise fra piu' aree dell'API, copiate una a una da
// `backend/src/db/mappers.ts`. I DTO non sono i modelli di dominio: restano
// piatti e fedeli al contratto, e `toDomain()` fa la traduzione in un punto solo.

struct TimeRangeDto: Codable {
    let start: LocalTime
    let end: LocalTime

    /// `TimeRange` ha una precondizione su start < end che farebbe cadere l'app:
    /// una fascia malformata che arriva dal server viene scartata, non fatta
    /// esplodere in faccia all'utente.
    func toDomain() -> TimeRange? {
        start < end ? TimeRange(start, end) : nil
    }
}

/// Orari settimanali: chiavi "1".."7", ISO, 1 = lunedi. Un giorno assente e' chiuso.
enum WeeklyHoursDto {

    static func toDomain(_ raw: [String: [TimeRangeDto]]) -> [DayOfWeek: [TimeRange]] {
        var result: [DayOfWeek: [TimeRange]] = [:]
        for (key, ranges) in raw {
            guard let value = Int(key), let day = DayOfWeek(rawValue: value) else { continue }
            let mapped = ranges.compactMap { $0.toDomain() }.sorted()
            if !mapped.isEmpty { result[day] = mapped }
        }
        return result
    }

    static func toWire(_ hours: [DayOfWeek: [TimeRange]]) -> [String: [TimeRangeDto]] {
        var result: [String: [TimeRangeDto]] = [:]
        for (day, ranges) in hours where !ranges.isEmpty {
            result["\(day.rawValue)"] = ranges.map { TimeRangeDto(start: $0.start, end: $0.end) }
        }
        return result
    }
}

// MARK: - Enumerazioni sul filo

// Sul filo gli enum sono in MAIUSCOLO con l'underscore (`IN_PROGRESS`), come su
// Android e nel database; nel dominio Swift sono camelCase. La traduzione sta
// qui, non sparsa nei repository.

enum WireEnum {

    static func role(_ raw: String) -> UserRole {
        switch raw {
        case "STAFF": .staff
        case "OWNER": .owner
        default: .client
        }
    }

    static func appointmentStatus(_ raw: String) -> AppointmentStatus {
        switch raw {
        case "IN_PROGRESS": .inProgress
        case "COMPLETED": .completed
        case "CANCELLED": .cancelled
        case "NO_SHOW": .noShow
        default: .confirmed
        }
    }

    static func channel(_ raw: String) -> BookingChannel {
        switch raw {
        case "PHONE": .phone
        case "WALK_IN": .walkIn
        default: .app
        }
    }

    static func channelWire(_ channel: BookingChannel) -> String {
        switch channel {
        case .app: "APP"
        case .phone: "PHONE"
        case .walkIn: "WALK_IN"
        }
    }

    static func cancellationActor(_ raw: String?) -> CancellationActor? {
        switch raw {
        case "CLIENT": .client
        case "SALON": .salon
        default: nil
        }
    }

    static func waitlistStatus(_ raw: String) -> WaitlistStatus {
        switch raw {
        case "NOTIFIED": .notified
        case "EXPIRED": .expired
        default: .waiting
        }
    }

    static func blockReason(_ raw: String) -> BlockReason {
        switch raw {
        case "PAUSA": .pausa
        case "FERIE": .ferie
        case "CORSO": .corso
        default: .permesso
        }
    }

    static func blockReasonWire(_ reason: BlockReason) -> String {
        switch reason {
        case .permesso: "PERMESSO"
        case .pausa: "PAUSA"
        case .ferie: "FERIE"
        case .corso: "CORSO"
        }
    }

    static func clientSegmentWire(_ segment: ClientSegment) -> String {
        switch segment {
        case .tutti: "TUTTI"
        case .fedeli: "FEDELI"
        case .inattivi60: "INATTIVI_60"
        case .noShow: "NO_SHOW"
        case .topSpesa: "TOP_SPESA"
        }
    }

    static func campaignSegment(_ raw: String) -> CampaignSegment {
        switch raw {
        case "TUTTI": .tutti
        case "TOP_SPESA": .topSpesa
        default: .inattivi60
        }
    }

    static func campaignSegmentWire(_ segment: CampaignSegment) -> String {
        switch segment {
        case .tutti: "TUTTI"
        case .inattivi60: "INATTIVI_60"
        case .topSpesa: "TOP_SPESA"
        }
    }

    static func campaignStatus(_ raw: String) -> CampaignStatus {
        switch raw {
        case "SCHEDULED": .scheduled
        case "SENT": .sent
        default: .draft
        }
    }

    static func dashboardPeriodWire(_ period: DashboardPeriod) -> String {
        switch period {
        case .day: "DAY"
        case .week: "WEEK"
        case .month: "MONTH"
        }
    }

    static func dashboardPeriod(_ raw: String) -> DashboardPeriod {
        switch raw {
        case "DAY": .day
        case "MONTH": .month
        default: .week
        }
    }
}
