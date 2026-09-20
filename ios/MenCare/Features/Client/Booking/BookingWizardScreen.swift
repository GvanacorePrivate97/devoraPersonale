import SwiftUI

struct BookingWizardScreen: View {
    @Bindable var viewModel: BookingViewModel
    let onCancel: () -> Void
    let onConfirmed: () -> Void

    var body: some View {
        Group {
            if viewModel.step == .confirmed {
                ConfirmationScreen(viewModel: viewModel, onDone: onConfirmed)
            } else {
                wizard
            }
        }
        .task { await viewModel.load() }
    }

    private var wizard: some View {
        VStack(spacing: 0) {
            DarkHeader(contentPadding: EdgeInsets(top: 0, leading: 0, bottom: 22, trailing: 0)) {
                BrandTopBar(
                    title: L(titleKey),
                    onBack: backAction
                ) {
                    if viewModel.step != .summary {
                        BarAction(text: L("wizard_cancel"), action: onCancel)
                    }
                }
                WizardSteps(
                    labels: [
                        L("wizard_step_operator"),
                        L("wizard_step_services"),
                        L("wizard_step_datetime"),
                        L("wizard_step_summary"),
                    ],
                    currentIndex: viewModel.step.rawValue
                )
                .padding(.horizontal, 22)
                .padding(.top, 8)
                if viewModel.step == .services {
                    chosenOperatorRow
                        .padding(.horizontal, 22)
                        .padding(.top, 14)
                }
            }

            // Listino, squadra e prime disponibilita' arrivano dalla rete: il
            // wizard non puo' partire senza, quindi mostra il caricamento e, se
            // va male, un riprova al posto del primo passo.
            Loadable(state: viewModel.state, retry: { Task { await viewModel.load() } }) {
                switch viewModel.step {
                case .operatorStep: StepOperator(viewModel: viewModel)
                case .services: StepServices(viewModel: viewModel)
                case .datetime: StepDatetime(viewModel: viewModel)
                case .summary: StepSummary(viewModel: viewModel)
                case .confirmed: EmptyView()
                }
            }
        }
        .background(Color.bone)
    }

    private var titleKey: String {
        if viewModel.step == .summary { return "wizard_summary_title" }
        return viewModel.isEditing ? "wizard_edit_title" : "wizard_title"
    }

    private var backAction: (() -> Void)? {
        switch viewModel.step {
        case .services: { viewModel.goToStep(.operatorStep) }
        case .datetime: { viewModel.goToStep(.services) }
        case .summary: { viewModel.goToStep(.datetime) }
        default: nil
        }
    }

    /// Recap of step 1 shown while picking services, with a shortcut back.
    private var chosenOperatorRow: some View {
        HStack(spacing: 10) {
            RoundedRectangle(cornerRadius: 11)
                .fill(Color.bone.opacity(0.12))
                .frame(width: 34, height: 34)
                .overlay(
                    Text(viewModel.selectedOperator?.initials ?? "·")
                        .font(Typo.cormorant(12, weight: .regular))
                        .foregroundStyle(Color.bone)
                )
            Text(viewModel.selectedOperator?.name ?? L("wizard_any_operator"))
                .font(Typo.jost(13, weight: .medium))
                .foregroundStyle(Color.bone)
            Spacer()
            Button {
                viewModel.goToStep(.operatorStep)
            } label: {
                Text(L("wizard_change"))
                    .font(Typo.jost(12, weight: .medium))
                    .foregroundStyle(Color.oliveWood)
            }
            .buttonStyle(.plain)
        }
    }
}
