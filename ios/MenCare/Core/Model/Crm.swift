import Foundation

enum ClientSegment: CaseIterable, Hashable {
    case tutti, fedeli, inattivi60, noShow, topSpesa
}

struct ClientRecord: Identifiable, Hashable {
    let id: String
    var firstName: String
    var lastName: String
    var phone: String
    var email: String
    let customerSince: LocalDate
    var visitCount: Int
    var lifetimeSpendCents: Int64
    var noShowCount: Int
    var lastVisit: LocalDate?
    var preferredServiceIds: [String] = []
    var preferredOperatorId: String?
    var marketingOptIn: Bool = true
    /// Habits from the client sheet (`GET /crm/clients/:id`): nil in lists.
    var favoriteOperatorId: String?
    var averageDaysBetweenVisits: Int?

    var fullName: String { "\(firstName) \(lastName)" }
    var initials: String {
        let f = firstName.first.map(String.init) ?? ""
        let l = lastName.first.map(String.init) ?? ""
        return (f + l).trimmingCharacters(in: .whitespaces)
    }

    func isInactiveSince(_ today: LocalDate, days: Int = 60) -> Bool {
        guard let lastVisit else { return true }
        return lastVisit.plusDays(days) < today
    }

    func isLoyal(_ today: LocalDate) -> Bool {
        visitCount >= 10 && !isInactiveSince(today)
    }
}

/// Time window the owner dashboard aggregates over.
enum DashboardPeriod: CaseIterable, Hashable {
    case day, week, month
}

struct DashboardStats: Hashable {
    var period: DashboardPeriod
    var revenueCents: Int64
    var revenueTrendPercent: Int
    /// Non-cancelled appointments of every operator in the period.
    var appointmentCount: Int
    var noShowPercent: Double
    var averageTicketCents: Int64
    var operatorOccupancy: [OperatorOccupancy]
    /// The next 7 days from today, whatever the period: where there is room to fill.
    var upcomingDays: [UpcomingDay]
    var inactiveClients: [ClientRecord]
}

struct OperatorOccupancy: Hashable, Identifiable {
    let operatorId: String
    let operatorName: String
    let percent: Int

    var id: String { operatorId }
}

/// One of the next 7 days on the dashboard: how full the salon is, how many
/// clients asked "Avvisami" for it, and whether it is closed.
struct UpcomingDay: Hashable, Identifiable {
    let date: LocalDate
    let occupancyPercent: Int
    let waitlistCount: Int
    let closed: Bool

    var id: LocalDate { date }
}
