import SwiftUI
import Observation

@MainActor
@Observable
final class ManageViewModel {

    let catalogRepository: CatalogRepository
    private let timeBlocks: TimeBlockRepository
    /// Serve solo a costruire la scheda "Notifiche", che ha il suo view model.
    let admin: AdminRepository

    let state = LoadState()
    private(set) var services: [Service] = []
    private(set) var operators: [Operator] = []
    /// Ferie e corsi in arrivo, per operatore. L'API ragiona per settimane:
    /// se ne guardano sei, quanto basta a coprire un periodo di ferie.
    private(set) var blocksByOperator: [String: [TimeBlock]] = [:]

    init(catalog: CatalogRepository, timeBlocks: TimeBlockRepository, admin: AdminRepository) {
        self.catalogRepository = catalog
        self.timeBlocks = timeBlocks
        self.admin = admin
    }

    func load() async {
        await state.run {
            let snapshot = try await catalogRepository.catalog()
            services = snapshot.services
            operators = snapshot.operators
            blocksByOperator = Dictionary(
                grouping: try await timeBlocks.upcomingBlocks(from: .today(), weeks: 6), by: \.operatorId
            )
        }
    }
}

struct ManageScreen: View {
    @State var viewModel: ManageViewModel
    let onEditService: (String?) -> Void
    let onNewOperator: () -> Void

    @State private var tab = 0

    var body: some View {
        let titles = [
            L("manage_tab_services"), L("manage_tab_operators"),
            L("manage_tab_notifications"), L("manage_tab_business"),
        ]
        VStack(spacing: 0) {
            DarkHeader(contentPadding: EdgeInsets(top: 10, leading: 20, bottom: 16, trailing: 20)) {
                Text(L("manage_title").uppercased())
                    .font(Typo.jost(10, weight: .medium))
                    .kerning(0.8)
                    .foregroundStyle(Color.textMuted)
                Text(titles[tab])
                    .font(Typo.cormorant(30))
                    .foregroundStyle(Color.bone)
                    .padding(.top, 2)
                HStack(spacing: 8) {
                    ForEach(Array(titles.enumerated()), id: \.offset) { index, title in
                        BrandChip(text: title, selected: tab == index, action: { tab = index }, onDark: true, fill: true)
                    }
                }
                .padding(.top, 14)
            }

            switch tab {
            case 0:
                Loadable(state: viewModel.state, retry: { Task { await viewModel.load() } }) {
                    ServicesTab(viewModel: viewModel, onEditService: onEditService)
                }
            case 1:
                Loadable(state: viewModel.state, retry: { Task { await viewModel.load() } }) {
                    OperatorsTab(viewModel: viewModel, onNewOperator: onNewOperator)
                }
            case 2:
                NotificationSettingsTab(viewModel: NotificationSettingsViewModel(admin: viewModel.admin))
            default:
                BusinessSettingsTab(viewModel: BusinessSettingsViewModel(catalog: viewModel.catalogRepository))
            }
        }
        .background(Color.bone)
        .task { await viewModel.load() }
    }
}

private struct ServicesTab: View {
    let viewModel: ManageViewModel
    let onEditService: (String?) -> Void

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 10) {
                        BrandSectionLabel(text: L("manage_services_all", viewModel.services.count))
                        Rectangle().fill(Color.stone).frame(height: 1)
                    }
                    .padding(.bottom, 10)
                    ForEach(viewModel.services) { service in
                        serviceRow(service)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 16)
                .readableWidth()
            }
            AccentButton(
                text: L("manage_new_service"),
                action: { onEditService(nil) },
                height: 54,
                corner: 16,
                leadingSystemImage: "plus"
            )
            .padding(.horizontal, 20)
            .padding(.bottom, 14)
            .readableWidth()
        }
    }

    private func serviceRow(_ service: Service) -> some View {
        let eligible = viewModel.operators.filter { $0.serviceIds.contains(service.id) }.count
        return Button {
            onEditService(service.id)
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(service.name)
                        .font(Typo.jost(15, weight: .medium))
                        .foregroundStyle(Color.ink)
                        .lineLimit(1)
                    Text(L("manage_service_meta", service.durationMinutes, eligible))
                        .font(Typo.jost(12))
                        .foregroundStyle(Color.textMuted)
                }
                Spacer()
                Text(formatPriceCompact(service.priceCents))
                    .font(Typo.jost(15, weight: .medium))
                    .foregroundStyle(Color.ink)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13))
                    .foregroundStyle(Color.textMuted)
                    .padding(.leading, 8)
            }
            .padding(.horizontal, 15)
            .padding(.vertical, 13)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color.stone))
        }
        .buttonStyle(.plain)
        .padding(.bottom, 9)
    }
}

private struct OperatorsTab: View {
    let viewModel: ManageViewModel
    let onNewOperator: () -> Void

    @State private var expandedId: String?

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 10) {
                    ForEach(viewModel.operators) { op in
                        operatorCard(op)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 16)
                .readableWidth()
            }
            // Stesso pulsante in fondo della tab Servizi.
            AccentButton(
                text: L("ops_new"),
                action: onNewOperator,
                height: 54,
                corner: 16,
                leadingSystemImage: "plus"
            )
            .padding(.horizontal, 20)
            .padding(.bottom, 14)
            .readableWidth()
        }
    }

    private func operatorCard(_ op: Operator) -> some View {
        let expanded = expandedId == op.id
        return VStack(alignment: .leading, spacing: 0) {
            Button {
                expandedId = expanded ? nil : op.id
            } label: {
                HStack(spacing: 11) {
                    Circle()
                        .fill(Color.bone)
                        .frame(width: 38, height: 38)
                        .overlay(
                            Text(op.initials)
                                .font(Typo.cormorant(13, weight: .regular))
                                .foregroundStyle(Color.ink)
                        )
                    VStack(alignment: .leading, spacing: 0) {
                        Text(op.name)
                            .font(Typo.jost(15, weight: .medium))
                            .foregroundStyle(Color.ink)
                        Text("\(op.title) · \(L("ops_services_count", op.serviceIds.count))")
                            .font(Typo.jost(12))
                            .foregroundStyle(Color.textMuted)
                            .lineLimit(1)
                    }
                    Spacer()
                    if op.isOwner {
                        Text(L("ops_owner_badge").uppercased())
                            .font(Typo.jost(9, weight: .medium))
                            .foregroundStyle(Color.bone)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 6)
                            .background(RoundedRectangle(cornerRadius: 8).fill(Color.oliveWood))
                    } else if expanded {
                        Text(L("ops_detail"))
                            .font(Typo.jost(12, weight: .medium))
                            .foregroundStyle(Color.oliveWood)
                    } else {
                        Image(systemName: "chevron.right")
                            .font(.system(size: 13))
                            .foregroundStyle(Color.textMuted)
                    }
                }
            }
            .buttonStyle(.plain)
            if expanded {
                expandedContent(op)
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 18).fill(expanded ? Color.bone : Color.stone))
        .overlay(
            expanded
                ? RoundedRectangle(cornerRadius: 18).strokeBorder(Color.oliveWood, lineWidth: 1.5)
                : nil
        )
    }

    @ViewBuilder
    private func expandedContent(_ op: Operator) -> some View {
        BrandSectionLabel(text: L("ops_hours"))
            .padding(.top, 14)
        VStack(spacing: 8) {
            ForEach(Array(op.weeklyHours.groupConsecutiveDays().enumerated()), id: \.offset) { _, group in
                let closed = group.range == nil
                StoneKeyValueRow(
                    label: formatDayGroup(group.days),
                    value: group.range.map { "\(formatTime($0.start)) — \(formatTime($0.end))" }
                        ?? L("ops_closed").lowercased(),
                    valueColor: closed ? .textMuted : .ink
                )
                .background(
                    closed
                        ? AnyView(RoundedRectangle(cornerRadius: 13).strokeBorder(Color.stoneBorder, lineWidth: 1))
                        : AnyView(RoundedRectangle(cornerRadius: 13).fill(Color.stone))
                )
            }
        }
        .padding(.top, 8)
        BrandSectionLabel(text: L("ops_holidays"))
            .padding(.top, 14)
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(blockPeriods(viewModel.blocksByOperator[op.id] ?? []).enumerated()), id: \.offset) { _, period in
                    Text(periodLabel(period))
                        .font(Typo.jost(12))
                        .foregroundStyle(Color.bone)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 9)
                        .background(RoundedRectangle(cornerRadius: 10).fill(Color.ink))
                }
                Text(L("ops_add_holiday"))
                    .font(Typo.jost(12))
                    .foregroundStyle(Color.textMuted)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 9)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.stone))
            }
        }
        .padding(.top, 8)
        BrandSectionLabel(text: "\(L("ops_services")) · \(op.serviceIds.count)")
            .padding(.top, 14)
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(viewModel.services.filter { op.serviceIds.contains($0.id) }) { service in
                    Text(service.name)
                        .font(Typo.jost(12))
                        .foregroundStyle(Color.bone)
                        .padding(.horizontal, 11)
                        .padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: 10).fill(Color.oliveWood))
                }
            }
        }
        .padding(.top, 8)
    }
}

/// Consecutive days blocked for the same reason read as one period.
private func blockPeriods(_ blocks: [TimeBlock]) -> [(range: ClosedRange<LocalDate>, reason: BlockReason)] {
    let sorted = blocks.sorted { $0.date < $1.date }
    var periods: [(days: [LocalDate], reason: BlockReason)] = []
    for block in sorted {
        if let lastIndex = periods.indices.last, periods[lastIndex].reason == block.reason {
            let lastDay = periods[lastIndex].days.last!
            if lastDay.plusDays(1) == block.date {
                periods[lastIndex].days.append(block.date)
                continue
            }
            if lastDay == block.date {
                // same day, another slot: nothing to add
                continue
            }
        }
        periods.append((days: [block.date], reason: block.reason))
    }
    return periods.map { (range: $0.days.first!...$0.days.last!, reason: $0.reason) }
}

private func periodLabel(_ period: (range: ClosedRange<LocalDate>, reason: BlockReason)) -> String {
    let label = blockReasonLabel(period.reason).lowercased()
    let end = period.range.upperBound
    let endLabel = "\(end.day) \(formatDateShort(end).split(separator: " ").last.map(String.init) ?? "")"
    if period.range.lowerBound == end {
        return "\(endLabel) · \(label)"
    }
    return "\(period.range.lowerBound.day)–\(endLabel) · \(label)"
}
