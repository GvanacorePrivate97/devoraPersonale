import SwiftUI

struct StepSummary: View {
    @Bindable var viewModel: BookingViewModel

    /// Un errore di conferma puo' essere "l'orario e' andato" oppure qualunque
    /// altra cosa il server (o la rete) abbia da dire.
    private func errorText(_ error: BookingError) -> String {
        switch error {
        case .slotTaken: L("wizard_error_slot_taken")
        case .message(let message): message
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    recapCard
                    servicesBlock
                    noteBlock
                    if let error = viewModel.error {
                        Text(errorText(error))
                            .font(Typo.bodySmall)
                            .foregroundStyle(Color.errorRed)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 16)
                .readableWidth()
            }
            AccentButton(
                text: L("wizard_confirm_cta"),
                action: viewModel.confirm,
                loading: viewModel.submitting,
                height: 56,
                corner: 16
            )
            .padding(.horizontal, 20)
            .padding(.bottom, 14)
            .readableWidth()
        }
    }

    private var recapCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text((viewModel.selectedDate.map(formatDateLong) ?? "").uppercased())
                .font(Typo.jost(10, weight: .medium))
                .kerning(1.6)
                .foregroundStyle(Color.oliveWood)
            HStack {
                Text(viewModel.selectedSlot.map(formatTime) ?? "")
                    .font(Typo.cormorant(38, weight: .regular))
                    .foregroundStyle(Color.bone)
                Spacer()
                Text(formatDuration(viewModel.totalDurationMinutes))
                    .font(Typo.jost(12, weight: .medium))
                    .foregroundStyle(Color.bone)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.bone.opacity(0.12)))
            }
            .padding(.top, 6)
            Rectangle().fill(Color.bone.opacity(0.14)).frame(height: 1).padding(.vertical, 12)
            HStack(spacing: 10) {
                Circle()
                    .fill(Color.bone.opacity(0.14))
                    .frame(width: 32, height: 32)
                    .overlay(
                        Text(viewModel.selectedOperator?.initials ?? "·")
                            .font(Typo.cormorant(12, weight: .regular))
                            .foregroundStyle(Color.bone)
                    )
                Text(viewModel.selectedOperator?.name ?? L("wizard_any_operator"))
                    .font(Typo.titleSmall)
                    .foregroundStyle(Color.bone)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 22).fill(Color.ink))
    }

    private var servicesBlock: some View {
        VStack(alignment: .leading, spacing: 8) {
            BrandSectionLabel(text: L("wizard_summary_services"))
            ForEach(viewModel.selectedServices) { service in
                HStack {
                    Text("\(service.name) · \(formatDuration(service.durationMinutes))")
                        .font(Typo.jost(14))
                        .foregroundStyle(Color.ink)
                    Spacer()
                    Text(formatPriceCompact(service.priceCents))
                        .font(Typo.titleSmall)
                        .foregroundStyle(Color.ink)
                }
                .padding(.horizontal, 15)
                .padding(.vertical, 14)
                .background(RoundedRectangle(cornerRadius: 16).fill(Color.stone))
            }
            HStack {
                Text(L("wizard_summary_total"))
                    .font(Typo.titleMedium)
                    .foregroundStyle(Color.ink)
                Spacer()
                Text(formatPrice(viewModel.totalPriceCents))
                    .font(Typo.titleMedium)
                    .foregroundStyle(Color.ink)
            }
            .padding(.horizontal, 4)
            .padding(.vertical, 4)
        }
    }

    private var noteBlock: some View {
        CounterTextField(
            label: L("wizard_notes_label"),
            text: $viewModel.note,
            placeholder: L("wizard_notes_hint"),
            limit: maxNoteLength,
            error: validateNote(viewModel.note).message
        )
        .onChange(of: viewModel.note) { viewModel.noteChanged() }
    }

}
