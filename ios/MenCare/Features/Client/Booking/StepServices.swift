import SwiftUI

struct StepServices: View {
    @Bindable var viewModel: BookingViewModel

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 11) {
                    Text(L("wizard_services_title"))
                        .font(Typo.cormorant(27))
                        .foregroundStyle(Color.ink)
                    ForEach(viewModel.eligibleServices) { service in
                        serviceRow(service)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 16)
                .readableWidth()
            }
            BottomBarReveal(visible: !viewModel.selectedServiceIds.isEmpty) {
                DarkTotalBar(
                    caption: viewModel.selectedServiceIds.count == 1
                        ? L("wizard_cart_caption_one", formatDuration(viewModel.totalDurationMinutes))
                        : L("wizard_cart_caption", viewModel.selectedServiceIds.count, formatDuration(viewModel.totalDurationMinutes)),
                    value: formatPrice(viewModel.totalPriceCents),
                    ctaLabel: L("wizard_continue"),
                    action: viewModel.continueFromServices
                )
            }
        }
    }

    private func serviceRow(_ service: Service) -> some View {
        let selected = viewModel.selectedServiceIds.contains(service.id)
        return Button {
            viewModel.toggleService(service.id)
        } label: {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.bone)
                    .frame(width: 24, height: 24)
                    .overlay(
                        selected
                            ? Image(systemName: "checkmark")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(Color.oliveWood)
                            : nil
                    )
                VStack(alignment: .leading, spacing: 0) {
                    Text(service.name)
                        .font(Typo.jost(15, weight: .medium))
                    Text(formatDuration(service.durationMinutes))
                        .font(Typo.jost(12))
                }
                .foregroundStyle(selected ? Color.bone : Color.ink)
                Spacer()
                Text(formatPriceCompact(service.priceCents))
                    .font(Typo.jost(15, weight: .medium))
                    .foregroundStyle(selected ? Color.bone : Color.ink)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 11)
            .background(RoundedRectangle(cornerRadius: 16).fill(selected ? Color.oliveWood : Color.stone))
        }
        .buttonStyle(.plain)
    }
}
