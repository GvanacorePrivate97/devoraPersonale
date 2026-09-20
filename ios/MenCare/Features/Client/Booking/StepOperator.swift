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
                RoundedRectangle(cornerRadius: 16)
                    .fill(selected ? Color.bone.opacity(0.18) : Color.bone)
                    .frame(width: 46, height: 46)
                    .overlay(
                        Image(systemName: "plus")
                            .font(.system(size: 17))
                            .foregroundStyle(selected ? Color.bone : Color.oliveWood)
                    )
                VStack(alignment: .leading, spacing: 0) {
                    Text(L("wizard_any_operator"))
                        .font(Typo.jost(15, weight: .medium))
                    Text(L("wizard_any_operator_hint"))
                        .font(Typo.jost(12, weight: .medium))
                }
                .foregroundStyle(selected ? Color.bone : Color.ink)
                Spacer()
                if selected {
                    Circle()
                        .fill(Color.bone)
                        .frame(width: 24, height: 24)
                        .overlay(
                            Image(systemName: "checkmark")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(Color.oliveWood)
                        )
                }
            }
            .padding(15)
            .background(RoundedRectangle(cornerRadius: 16).fill(selected ? Color.oliveWood : Color.stone))
        }
        .buttonStyle(.plain)
    }

    private func operatorRow(_ option: OperatorOption) -> some View {
        let selected = viewModel.selectedOperatorId == option.op.id
        let contentAlpha = option.availableSoon ? 1.0 : 0.45
        let textColor = (selected ? Color.bone : Color.ink).opacity(contentAlpha)
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
                        .foregroundStyle(textColor)
                }
                Spacer()
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 16).fill(selected ? Color.oliveWood : Color.stone))
        }
        .buttonStyle(.plain)
    }

    /// Hatched square stands in for the operator photo, as in the mockup.
    private func hatchedAvatar(_ initials: String) -> some View {
        ZStack {
            Canvas { context, size in
                var x = -size.height
                while x < size.width {
                    var path = Path()
                    path.move(to: CGPoint(x: x, y: size.height))
                    path.addLine(to: CGPoint(x: x + size.height, y: 0))
                    context.stroke(path, with: .color(.ink.opacity(0.06)), lineWidth: 1)
                    x += 8
                }
            }
            Text(initials)
                .font(Typo.cormorant(17, weight: .regular))
                .foregroundStyle(Color.ink)
        }
        .frame(width: 54, height: 54)
        .background(Color.bone)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}
