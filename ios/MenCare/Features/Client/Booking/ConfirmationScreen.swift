import SwiftUI

struct ConfirmationScreen: View {
    @Bindable var viewModel: BookingViewModel
    let onDone: () -> Void

    var body: some View {
        GeometryReader { proxy in
            ScrollView {
                content
                    .padding(.horizontal, 24)
                    .readableWidth()
                    // At least as tall as the viewport, so short content sits
                    // centered instead of stuck to the top; taller content scrolls.
                    .frame(minHeight: proxy.size.height)
            }
        }
        .background(Color.ink.ignoresSafeArea())
    }

    private var content: some View {
        VStack(spacing: 0) {
                Circle()
                    .fill(Color.oliveWood)
                    .frame(width: 96, height: 96)
                    .overlay(
                        Image(systemName: "checkmark")
                            .font(.system(size: 40, weight: .medium))
                            .foregroundStyle(Color.bone)
                    )
                    .padding(.top, 24)
                Text(L("confirm_title"))
                    .font(Typo.cormorant(34))
                    .foregroundStyle(Color.bone)
                    .multilineTextAlignment(.center)
                    .padding(.top, 28)
                Text(
                    L(
                        "confirm_when",
                        viewModel.selectedDate.map(formatDateLong) ?? "",
                        viewModel.selectedSlot.map(formatTime) ?? ""
                    )
                )
                .font(Typo.jost(16, weight: .medium))
                .foregroundStyle(Color.bone)
                .multilineTextAlignment(.center)
                .padding(.top, 10)

                recapPanel
                    .padding(.top, 26)

                AccentButton(
                    // Oro con testo scuro: la schermata è tutta nera, l'oliva
                    // ci sparisce.
                    text: L("client_confirm_add_calendar"),
                    action: {},
                    height: 56,
                    corner: Radii.md,
                    leadingSystemImage: "calendar",
                    container: .oliveLight,
                    contentColor: .ink
                )
                .padding(.top, 28)
                Button {
                    viewModel.reset()
                    onDone()
                } label: {
                    Text(L("confirm_back_home"))
                        .font(Typo.titleMedium)
                        .foregroundStyle(Color.bone)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .strokeBorder(Color.inkBorder, lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .padding(.top, 10)
                .padding(.bottom, 24)
        }
    }

    private var recapPanel: some View {
        VStack(spacing: 0) {
            // Con "Qualsiasi operatore" la prenotazione ne ha già assegnato uno: si mostra quello.
            darkRecapRow(
                L("wizard_summary_operator"),
                viewModel.confirmed
                    .flatMap { apt in viewModel.operatorOptions.first { $0.op.id == apt.operatorId } }?.op.name
                    ?? viewModel.selectedOperator?.name
                    ?? L("wizard_any_operator")
            )
            divider
            darkRecapRow(L("wizard_summary_services"), viewModel.selectedServices.map(\.name).joined(separator: " + "))
            divider
            darkRecapRow(L("wizard_summary_duration"), formatDuration(viewModel.totalDurationMinutes))
            divider
            darkRecapRow(L("wizard_summary_total"), formatPrice(viewModel.totalPriceCents))
            // Le note compaiono solo se il cliente le ha scritte.
            let note = viewModel.note.trimmingCharacters(in: .whitespacesAndNewlines)
            if !note.isEmpty {
                divider
                darkRecapRow(L("confirm_note"), note)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 4)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.bone.opacity(0.06)))
    }

    private var divider: some View {
        Rectangle().fill(Color.bone.opacity(0.1)).frame(height: 1)
    }

    private func darkRecapRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(Typo.bodyMedium).foregroundStyle(Color.bone.opacity(0.65))
            Spacer()
            Text(value)
                .font(Typo.titleSmall)
                .foregroundStyle(Color.bone)
                .multilineTextAlignment(.trailing)
                .padding(.leading, 16)
        }
        .padding(.vertical, 13)
    }
}
