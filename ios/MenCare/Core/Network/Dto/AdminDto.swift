import Foundation

// Area titolare — docs/API.md §Gestione (solo titolare).

struct DashboardDto: Decodable {
    struct Occupancy: Decodable {
        let operatorId: String
        let operatorName: String
        let percent: Int
    }

    struct UpcomingDayDto: Decodable {
        let date: LocalDate
        let occupancyPercent: Int
        let waitlistCount: Int
        let closed: Bool
    }

    let period: String
    let from: LocalDate
    let to: LocalDate
    let revenueCents: Int64
    let revenueTrendPercent: Int
    let appointmentCount: Int
    let noShowPercent: Double
    let averageTicketCents: Int64
    let operatorOccupancy: [Occupancy]
    let upcomingDays: [UpcomingDayDto]
    let inactiveClients: [ClientDto]

    func toDomain() -> DashboardStats {
        DashboardStats(
            period: WireEnum.dashboardPeriod(period),
            revenueCents: revenueCents,
            revenueTrendPercent: revenueTrendPercent,
            appointmentCount: appointmentCount,
            noShowPercent: noShowPercent,
            averageTicketCents: averageTicketCents,
            operatorOccupancy: operatorOccupancy.map {
                OperatorOccupancy(operatorId: $0.operatorId, operatorName: $0.operatorName, percent: $0.percent)
            },
            upcomingDays: upcomingDays.map {
                UpcomingDay(date: $0.date, occupancyPercent: $0.occupancyPercent, waitlistCount: $0.waitlistCount, closed: $0.closed)
            },
            inactiveClients: inactiveClients.map { $0.toDomain() }
        )
    }
}

/// I quattro interruttori del salone; i promemoria stanno su una rotta a parte.
struct SalonNotificationSettingsDto: Codable {
    let bookingConfirmation: Bool
    let cancellationAlert: Bool
    let lateOperatorAlert: Bool
    let emptyDayPromos: Bool
}

struct ReminderRuleDto: Decodable {
    let id: String
    let hoursBefore: Int

    func toDomain() -> ReminderRule {
        ReminderRule(id: id, hoursBefore: hoursBefore)
    }
}

struct ReminderRulesResponseDto: Decodable {
    let rules: [ReminderRuleDto]
}

struct ReminderRuleWriteDto: Encodable {
    let hoursBefore: Int
}

struct CampaignDto: Decodable {
    let id: String
    let name: String
    let segment: String
    let title: String
    let body: String
    let scheduledAt: String?
    let scheduledDate: LocalDate?
    let scheduledTime: LocalTime?
    let repeatWeekly: Bool
    let sendCap: Int?
    let reachableCount: Int
    let segmentSize: Int
    let sentCount: Int
    let status: String
    let lastSentAt: String?

    func toDomain() -> PushCampaign {
        PushCampaign(
            id: id,
            name: name,
            segment: WireEnum.campaignSegment(segment),
            title: title,
            body: body,
            // Il server manda sia l'istante sia l'ora da muro: al titolare
            // interessa quella che ha scelto lui sul calendario.
            scheduledAt: scheduledDate.flatMap { date in scheduledTime.map { date.atTime($0) } },
            repeatWeekly: repeatWeekly,
            sendCap: sendCap,
            reachableCount: reachableCount,
            segmentSize: segmentSize,
            status: WireEnum.campaignStatus(status)
        )
    }
}

struct CampaignsResponseDto: Decodable {
    let campaigns: [CampaignDto]
}

struct CampaignWriteDto: Encodable {
    let name: String
    let segment: String
    let title: String
    let body: String
    /// Orario da muro del salone, `2026-10-02T09:00`: il server lo porta a
    /// istante con le stesse regole dell'agenda.
    let scheduledAt: String?
    let repeatWeekly: Bool
    let sendCap: Int?
    let status: String
}

struct CampaignReachDto: Decodable {
    let reachable: Int
    let segmentSize: Int
}

struct CampaignSendResultDto: Decodable {
    let queued: Int?
}
