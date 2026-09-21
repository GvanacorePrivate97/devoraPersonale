import SwiftUI

struct StepOperator: View {
    @Bindable var viewModel: BookingViewModel

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(alignment: .bottom) {
                        Text(L("wizard_operator_title"))
                            .font(Typo.cormorant(28))
                            .foregroundStyle(Color.ink)
                        Spacer()
                        Text(L("wizard_step_counter", 1, 4))
                            .font(Typo.jost(12))
                            .foregroundStyle(Color.ink)
                    }
                    anyOperatorCard
                    BrandSectionLabel(text: L("wizard_team_count", viewModel.operatorOptions.count))
                    ForEach(viewModel.operatorOptions) { option in
                        operatorRow(option)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 16)
                .readableWidth()
            }
            BottomBarReveal(visible: viewModel.operatorChosen) {
                DarkContinueBar(
                    label: L("wizard_continue_services"),
                    action: viewModel.continueFromOperator
                )
            }
        }
    }

    private var anyOperatorCard: some View {
        let selected = viewModel.anyOperator
        return Button {
            viewModel.selectOperator(nil)
        } label: {
            HStack(spacing: 13) {
                Circle()
                    .fill(Color.ink)
                    .frame(width: 44, height: 44)
                    .overlay(
                        Image(systemName: "plus")
                            .font(.system(size: 18))
                            .foregroundStyle(Color.oliveLight)
                    )
                VStack(alignment: .leading, spacing: 0) {
                    Text(L("wizard_any_operator"))
                        .font(Typo.jost(16, weight: .medium))
                        .foregroundStyle(Color.ink)
                    Text(L("wizard_any_operator_hint"))
                        .font(Typo.jost(12, weight: .medium))
                        .foregroundStyle(Color.textMuted)
                }
                Spacer()
                if selected {
                    Image(systemName: "checkmark")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.oliveWood)
                }
            }
            .padding(15)
            // Scelta = OliveTint col filo d'accento, non un blocco d'oliva pieno.
            .background(RoundedRectangle(cornerRadius: Radii.md).fill(selected ? Color.oliveTint : Color.bone))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md)
                    .strokeBorder(selected ? Color.oliveWood : Color.stoneBorder, lineWidth: 1.5)
            )
        }
        .buttonStyle(.plain)
    }

    private func operatorRow(_ option: OperatorOption) -> some View {
        let selected = viewModel.selectedOperatorId == option.op.id
        let contentAlpha = option.availableSoon ? 1.0 : 0.45
        let textColor = Color.ink.opacity(contentAlpha)
        return Button {
            viewModel.selectOperator(option.op.id)
        } label: {
            // Solo nome e mansione: niente tag di specialità né prossimo orario.
            HStack(spacing: 12) {
                hatchedAvatar(option.op.initials)
                VStack(alignment: .leading, spacing: 3) {
                    Text(option.op.name)
                        .font(Typo.jost(15, weight: .medium))
                        .foregroundStyle(textColor)
                    Text(option.op.title)
                        .font(Typo.jost(12))
                        .foregroundStyle(Color.textMuted.opacity(contentAlpha))
                }
                Spacer()
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: Radii.md).fill(selected ? Color.oliveTint : Color.bone))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md)
                    .strokeBorder(selected ? Color.oliveWood : Color.stoneBorder, lineWidth: 1.5)
            )
        }
        .buttonStyle(.plain)
    }

    /// Tondo scuro con l'iniziale in oro: lo stesso avatar della scheda cliente.
    private func hatchedAvatar(_ initials: String) -> some View {
        Circle()
            .fill(Color.ink)
            .frame(width: 44, height: 44)
            .overlay(
                Text(initials)
                    .font(Typo.cormorant(16, weight: .regular))
                    .foregroundStyle(Color.oliveLight)
            )
    }
}
