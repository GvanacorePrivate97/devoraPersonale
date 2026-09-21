import SwiftUI
import Observation

/// Il cruscotto leggeva il repository dentro `body`: con una chiamata di rete
/// non si puo' piu', e i numeri li calcola il server. Qui c'e' il view model che
/// mancava.
@MainActor
@Observable
final class DashboardViewModel {

    private let admin: AdminRepository

    let state = LoadState()
    var period: DashboardPeriod = .day
    private(set) var stats: DashboardStats?

    init(admin: AdminRepository) {
        self.admin = admin
    }

    func load() async {
        await state.run { stats = try await admin.dashboard(period) }
    }

    func select(_ newPeriod: DashboardPeriod) {
        guard newPeriod != period else { return }
        period = newPeriod
        Task { await load() }
    }
}

struct DashboardScreen: View {
    @State var viewModel: DashboardViewModel
    let onSendCampaign: () -> Void

    private var period: DashboardPeriod { viewModel.period }

    var body: some View {
        Group {
            if let stats = viewModel.stats {
                content(stats)
            } else if let error = viewModel.state.error {
                VStack(spacing: 0) {
                    periodHeader
                    BrandErrorRetry(error: error, retry: { Task { await viewModel.load() } })
                    Spacer()
                }
            } else {
                VStack(spacing: 0) {
                    periodHeader
                    BrandLoading()
                    Spacer()
                }
            }
        }
        .background(Color.bone)
        .inkStatusBarBackdrop()
        .task { await viewModel.load() }
    }

    /// La banda con i tre periodi resta anche mentre si carica: cambiare
    /// periodo e' l'unica cosa da fare se la lettura sta fallendo.
    private var periodHeader: some View {
        DarkHeader(
            roundedBottom: true,
            contentPadding: EdgeInsets(top: 8, leading: 20, bottom: 18, trailing: 20)
        ) {
            // La banda porta il suo titolo, come le altre schermate del
            // titolare: prima si sa dove si è, poi si sceglie il periodo.
            Text(L("dash_overline").uppercased())
                .font(Typo.overline)
                .foregroundStyle(Color.oliveLight)
            Text(L("dash_title"))
                .font(Typo.displaySmall)
                .foregroundStyle(Color.bone)
                .padding(.top, 4)
                .padding(.bottom, 16)
            SegmentedTabs(
                options: [L("dash_period_day"), L("dash_period_week"), L("dash_period_month")],
                selectedIndex: DashboardPeriod.allCases.firstIndex(of: period) ?? 0,
                onSelect: { viewModel.select(DashboardPeriod.allCases[$0]) }
            )
        }
    }

    private func content(_ stats: DashboardStats) -> some View {
        ScrollView {
            VStack(spacing: 0) {
                if let error = viewModel.state.error {
                    InlineErrorBar(message: error.displayMessage, retry: { Task { await viewModel.load() } })
                }
                DarkHeader(
                    roundedBottom: true,
                    contentPadding: EdgeInsets(top: 8, leading: 20, bottom: 18, trailing: 20)
                ) {
                    // La banda porta il suo titolo, come le altre schermate del
                    // titolare: prima si sa dove si è, poi si sceglie il periodo.
                    Text(L("dash_overline").uppercased())
                        .font(Typo.overline)
                        .foregroundStyle(Color.oliveLight)
                    Text(L("dash_title"))
                        .font(Typo.displaySmall)
                        .foregroundStyle(Color.bone)
                        .padding(.top, 4)
                        .padding(.bottom, 16)
                    SegmentedTabs(
                        options: [L("dash_period_day"), L("dash_period_week"), L("dash_period_month")],
                        selectedIndex: DashboardPeriod.allCases.firstIndex(of: period) ?? 0,
                        onSelect: { viewModel.select(DashboardPeriod.allCases[$0]) }
                    )
                    Text(revenueLabel.uppercased())
                        .font(Typo.jost(11, weight: .medium))
                        .kerning(1.6)
                        .foregroundStyle(Color.oliveWood)
                        .padding(.top, 16)
                    HStack {
                        Text(formatPriceCompact(stats.revenueCents))
                            .font(Typo.cormorant(44))
                            .foregroundStyle(Color.bone)
                        Spacer()
                        Text(L("dash_trend", stats.revenueTrendPercent))
                            .font(Typo.titleSmall)
                            .foregroundStyle(Color.bone)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Color.oliveWood))
                    }
                    revenueBars
                        .padding(.top, 10)
                }

                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 10) {
                        // I tre KPI hanno lo stesso aspetto: fondo chiaro e filo, niente grigio.
                        StatTile(
                            label: L("dash_appointments"),
                            value: "\(stats.appointmentCount)",
                            container: .bone,
                            borderColor: .stoneBorder
                        )
                        StatTile(
                            label: L("dash_no_show"),
                            value: String(format: "%.1f%%", stats.noShowPercent).replacingOccurrences(of: ".", with: ","),
                            container: .bone,
                            borderColor: .stoneBorder
                        )
                        StatTile(
                            label: L("dash_avg_ticket"),
                            value: formatPriceCompact(stats.averageTicketCents),
                            container: .bone,
                            borderColor: .stoneBorder
                        )
                    }
                    .fixedSize(horizontal: false, vertical: true)

                    VStack(alignment: .leading, spacing: 9) {
                        BrandSectionLabel(text: L("dash_operator_occupancy"))
                        ForEach(stats.operatorOccupancy) { row in
                            HStack(spacing: 10) {
                                Text(String(row.operatorName.split(separator: " ").first ?? ""))
                                    .font(Typo.bodyMedium)
                                    .foregroundStyle(Color.ink)
                                    .frame(width: 64, alignment: .leading)
                                GeometryReader { geo in
                                    ZStack(alignment: .leading) {
                                        Capsule().fill(Color.stone)
                                        Capsule()
                                            .fill(row.percent >= 80 ? Color.oliveWood : Color.ink)
                                            .frame(width: geo.size.width * CGFloat(row.percent) / 100)
                                    }
                                }
                                .frame(height: 7)
                                Text("\(row.percent)%")
                                    .font(Typo.titleSmall)
                                    .foregroundStyle(Color.ink)
                                    .frame(width: 44, alignment: .trailing)
                            }
                        }
                    }

                    upcomingSection(stats.upcomingDays)
                }
                .padding(.horizontal, 20)
                .padding(.top, 14)
                .padding(.bottom, 20)
                .readableWidth()
            }
        }
    }

    private var revenueLabel: String {
        switch period {
        case .day: L("dash_revenue_label_day")
        case .week: L("dash_revenue_label_week")
        case .month: L("dash_revenue_label")
        }
    }

    /// Period-over-period revenue sketch; the demo layer ships no per-day series yet.
    private var revenueBars: some View {
        let heights: [CGFloat] = [0.42, 0.55, 0.48, 0.72, 0.5, 1, 0.6, 0.38]
        return HStack(alignment: .bottom, spacing: 7) {
            ForEach(Array(heights.enumerated()), id: \.offset) { index, fraction in
                RoundedRectangle(cornerRadius: 6)
                    .fill(index == 5 ? Color.oliveWood : Color.bone.opacity(0.12))
                    .frame(maxWidth: .infinity)
                    .frame(height: 44 * fraction)
            }
        }
        .frame(height: 44, alignment: .bottom)
    }

/// Sotto questa soglia un giorno aperto è "vuoto": è lì che una campagna serve.
    private static let emptyDayThreshold = 50

    /// I prossimi 7 giorni: una colonna per giorno con l'occupazione, i giorni
    /// chiusi segnati, chi aspetta in lista ("Avvisami") e, se c'è un giorno
    /// vuoto, la scorciatoia per riempirlo con una campagna.
    private func upcomingSection(_ days: [UpcomingDay]) -> some View {
        let waiting = days.reduce(0) { $0 + $1.waitlistCount }
        let hasEmptyDay = days.contains { !$0.closed && $0.occupancyPercent < Self.emptyDayThreshold }
        return VStack(alignment: .leading, spacing: 10) {
            BrandSectionLabel(text: L("dash_upcoming"))
            HStack(alignment: .bottom, spacing: 6) {
                ForEach(days) { day in
                    upcomingColumn(day)
                }
            }
            if waiting > 0 {
                Text(L("dash_upcoming_waitlist", waiting))
                    .font(Typo.jost(12))
                    .foregroundStyle(Color.textMuted)
            }
            // L'unico ingresso alla campagna push: sempre presente, e dice "riempi"
            // quando c'è davvero un giorno vuoto.
            AccentButton(
                text: L(hasEmptyDay ? "dash_upcoming_fill" : "dash_send_campaign"),
                action: onSendCampaign,
                height: 52,
                corner: 16,
                leadingSystemImage: "bell"
            )
            .padding(.top, 4)
        }
    }

    private func upcomingColumn(_ day: UpcomingDay) -> some View {
        let parts = formatDateShort(day.date).split(separator: " ")
        let barHeight: CGFloat = 56
        return VStack(spacing: 5) {
            // Chi aspetta un posto quel giorno: la domanda che l'agenda non ha servito.
            Text(day.waitlistCount > 0 ? "\(day.waitlistCount)" : " ")
                .font(Typo.jost(11, weight: .medium))
                .foregroundStyle(Color.bone)
                .frame(minWidth: 18, minHeight: 18)
                .background(Circle().fill(day.waitlistCount > 0 ? Color.oliveWood : Color.clear))
            ZStack(alignment: .bottom) {
                RoundedRectangle(cornerRadius: 6).fill(day.closed ? Color.stoneSoft : Color.stone)
                if !day.closed {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(day.occupancyPercent >= 80 ? Color.oliveWood : Color.ink)
                        .frame(height: barHeight * CGFloat(day.occupancyPercent) / 100)
                }
            }
            .frame(height: barHeight)
            Text(day.closed ? L("dash_upcoming_closed") : "\(day.occupancyPercent)%")
                .font(Typo.jost(11, weight: .medium))
                .foregroundStyle(day.closed ? Color.textMuted : Color.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            VStack(spacing: 0) {
                Text(parts.first.map { String($0).uppercased() } ?? "")
                    .font(Typo.jost(11, weight: .medium))
                    .kerning(0.8)
                    .foregroundStyle(Color.textMuted)
                Text(parts.count > 1 ? String(parts[1]) : "")
                    .font(Typo.cormorant(16, weight: .regular))
                    .foregroundStyle(Color.ink)
            }
        }
        .frame(maxWidth: .infinity)
    }

}
