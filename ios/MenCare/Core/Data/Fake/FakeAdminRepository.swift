import Foundation

/// Dashboard KPIs computed live from the store, on a base offset per period so
/// the demo matches the mockup's order of magnitude ("€ 9.840 · +12%") without
/// seeding hundreds of historical appointments.
private struct PeriodBaseline {
    let revenueCents: Int64
    let appointments: Int
    let noShows: Int
    let trendPercent: Int
    /// Divides the monthly synthetic "top services" volumes.
}

private func baselineFor(_ period: DashboardPeriod) -> PeriodBaseline {
    switch period {
    case .day: PeriodBaseline(revenueCents: 32_000, appointments: 17, noShows: 0, trendPercent: 8)
    case .week: PeriodBaseline(revenueCents: 210_000, appointments: 115, noShows: 3, trendPercent: 5)
    case .month: PeriodBaseline(revenueCents: 900_000, appointments: 490, noShows: 14, trendPercent: 12)
    }
}

@MainActor
final class FakeAdminRepository: AdminRepository {

    private let store: InMemoryStore

    init(store: InMemoryStore) {
        self.store = store
    }

    func dashboard(_ period: DashboardPeriod) async throws -> DashboardStats {
        let today = LocalDate.today()
        let start: LocalDate
        let end: LocalDate
        switch period {
        case .day:
            start = today
            end = today
        case .week:
            start = today.minusDays(today.dayOfWeek.rawValue - 1)
            end = start.plusDays(6)
        case .month:
            start = today.startOfMonth
            end = today.endOfMonth
        }
        let baseline = baselineFor(period)
        let inPeriod = store.appointments.filter { $0.date >= start && $0.date <= end }
        let live = inPeriod.filter { $0.status != .cancelled }

        let completed = inPeriod.filter { $0.status == .completed }
        let noShows = inPeriod.filter { $0.status == .noShow }.count + baseline.noShows
        let revenue = baseline.revenueCents + completed.reduce(0) { $0 + $1.totalPriceCents }
        let ticketCount = baseline.appointments + completed.count

        let days = (0...start.daysBetween(end)).map { start.plusDays($0) }
        let perOperator = store.operators.map { op in
            let bookedMinutes = live.filter { $0.operatorId == op.id }.reduce(0) { $0 + $1.durationMinutes }
            let workMinutes = days.reduce(0) { total, day in
                total + (op.weeklyHours[day.dayOfWeek] ?? [])
                    .reduce(0) { $0 + ($1.end.minutesOfDay - $1.start.minutesOfDay) }
            }
            return OperatorOccupancy(
                operatorId: op.id,
                operatorName: op.name,
                percent: workMinutes == 0 ? 0 : min(bookedMinutes * 100 / workMinutes, 100)
            )
        }

        // I prossimi 7 giorni, come li calcola il server: minuti prenotati sui
        // minuti lavorabili di tutti gli operatori, e chi aspetta in lista.
        let upcoming = (0..<7).map { offset -> UpcomingDay in
            let day = today.plusDays(offset)
            let salonOpen = !(store.salon.weeklyHours[day.dayOfWeek] ?? []).isEmpty
            let workable = salonOpen ? store.operators.reduce(0) { total, op in
                total + (op.weeklyHours[day.dayOfWeek] ?? []).reduce(0) { $0 + ($1.end.minutesOfDay - $1.start.minutesOfDay) }
            } : 0
            let booked = store.appointments
                .filter { $0.date == day && $0.status != .cancelled && $0.status != .noShow }
                .reduce(0) { $0 + $1.durationMinutes }
            return UpcomingDay(
                date: day,
                occupancyPercent: workable == 0 ? 0 : min(booked * 100 / workable, 100),
                waitlistCount: store.waitlist.filter { $0.date == day && $0.status == .waiting }.count,
                closed: workable == 0
            )
        }

        return DashboardStats(
            period: period,
            revenueCents: revenue,
            revenueTrendPercent: baseline.trendPercent,
            appointmentCount: live.count,
            noShowPercent: ticketCount == 0 ? 0 : Double(noShows) * 100.0 / Double(ticketCount),
            averageTicketCents: ticketCount == 0 ? 0 : revenue / Int64(ticketCount),
            operatorOccupancy: perOperator.sorted { $0.percent > $1.percent },
            upcomingDays: upcoming,
            inactiveClients: store.clients.filter { $0.isInactiveSince(today) }
        )
    }

    func notificationSettings() async throws -> NotificationSettings { store.notificationSettings }

    func updateNotificationSettings(_ settings: NotificationSettings) async -> AppResult<NotificationSettings> {
        store.notificationSettings = settings
        return .success(settings)
    }

    func campaigns() async throws -> [PushCampaign] { store.campaigns }

    func saveCampaign(_ campaign: PushCampaign) async -> AppResult<PushCampaign> {
        if campaign.name.trimmingCharacters(in: .whitespaces).isEmpty {
            return .failure(.validation(field: "name"))
        }
        if campaign.body.count > 140 { return .failure(.validation(field: "body")) }
        var saved = campaign
        if saved.id.isEmpty {
            saved = PushCampaign(
                id: store.newId("camp"), name: campaign.name, segment: campaign.segment,
                title: campaign.title, body: campaign.body, scheduledAt: campaign.scheduledAt,
                repeatWeekly: campaign.repeatWeekly, sendCap: campaign.sendCap,
                reachableCount: campaign.reachableCount, segmentSize: campaign.segmentSize,
                status: campaign.status
            )
        }
        if store.campaigns.contains(where: { $0.id == saved.id }) {
            let id = saved.id
            let replacement = saved
            store.campaigns = store.campaigns.map { $0.id == id ? replacement : $0 }
        } else {
            store.campaigns.append(saved)
        }
        return .success(saved)
    }

    func reachFor(_ segment: CampaignSegment) async throws -> (reachable: Int, size: Int) {
        let today = LocalDate.today()
        let matching: [ClientRecord]
        switch segment {
        case .tutti: matching = store.clients
        case .inattivi60: matching = store.clients.filter { $0.isInactiveSince(today) }
        case .topSpesa: matching = store.clients.filter { $0.lifetimeSpendCents >= 40_000 }
        }
        // Mockup scale: the fake CRM only holds a handful of records, so pad
        // to the storefront numbers ("198 di 214").
        let sizePad: Int
        let reachablePad: Int
        switch segment {
        case .tutti: sizePad = 1_272; reachablePad = 1_154
        case .inattivi60: sizePad = 210; reachablePad = 195
        case .topSpesa: sizePad = 80; reachablePad = 74
        }
        let reachable = matching.filter(\.marketingOptIn).count + reachablePad
        return (reachable, matching.count + sizePad)
    }
}
