import SwiftUI
import Observation

@MainActor
@Observable
final class ServiceEditViewModel {

    private let catalog: CatalogRepository
    private let serviceId: String?

    var isNew: Bool { serviceId == nil }
    var name = ""
    var durationMinutes = 30
    var priceEuros = ""
    var enabledOperatorIds: Set<String> = []
    var saved = false
    var saving = false
    /// Chiave del campo → messaggio: prima il tasto "Salva" con il nome vuoto
    /// semplicemente non faceva nulla, senza dire perché.
    var fieldErrors: [String: String] = [:]
    var saveError: String?

    @ObservationIgnored private var submitted = false

    let state = LoadState()
    private(set) var operators: [Operator] = []
    private var editing: Service?

    init(catalog: CatalogRepository, serviceId: String?) {
        self.catalog = catalog
        self.serviceId = serviceId
    }

    func load() async {
        await state.run {
            let snapshot = try await catalog.catalog()
            operators = snapshot.operators
            let service = serviceId.flatMap { id in snapshot.services.first { $0.id == id } }
            editing = service
            if let service {
                name = service.name
                durationMinutes = service.durationMinutes
                priceEuros = formatCentsAsInput(service.priceCents)
                enabledOperatorIds = Set(snapshot.operators.filter { $0.serviceIds.contains(service.id) }.map(\.id))
            }
        }
    }

    func fieldChanged() {
        saveError = nil
        saved = false
        if submitted { fieldErrors = validate() }
    }

    private func validate() -> [String: String] {
        var errors: [String: String] = [:]
        // Il nome di un servizio non è un nome proprio: "Shampoo + taglio" deve passare.
        errors["name"] = validateRequiredText(name).message
        errors["duration"] = validateDuration(durationMinutes).message
        errors["price"] = validatePriceInput(priceEuros).message
        return errors.compactMapValues { $0 }
    }

    func toggleOperator(_ operatorId: String) {
        if enabledOperatorIds.contains(operatorId) {
            enabledOperatorIds.remove(operatorId)
        } else {
            enabledOperatorIds.insert(operatorId)
        }
    }

    func save() {
        guard !saving else { return }
        submitted = true
        let errors = validate()
        if !errors.isEmpty {
            fieldErrors = errors
            saveError = L("validation_form_invalid")
            return
        }
        // `validatePrice` ha già accettato il testo, quindi la conversione non
        // può fallire; si arrotonda ai centesimi, non si tronca.
        guard let cents = parsePriceToCents(priceEuros) else {
            fieldErrors = ["price": L("validation_price_invalid")]
            return
        }
        fieldErrors = [:]
        saveError = nil
        saving = true
        Task {
            // Servizio e abilitazioni in un colpo solo: il server ha una rotta
            // apposta, cosi' non resta un listino a meta' se una scrittura salta.
            let result = await catalog.saveService(
                Service(
                    id: serviceId ?? "",
                    name: name.trimmingCharacters(in: .whitespaces),
                    durationMinutes: durationMinutes,
                    priceCents: cents,
                    description: editing?.description,
                    featured: editing?.featured ?? false
                ),
                operatorIds: enabledOperatorIds
            )
            saving = false
            switch result {
            case .success:
                saved = true
            case .failure(let error):
                saveError = error.displayMessage
            }
        }
    }
}

struct ServiceEditScreen: View {
    @Bindable var viewModel: ServiceEditViewModel
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            DarkHeader(contentPadding: EdgeInsets(top: 0, leading: 0, bottom: 8, trailing: 0)) {
                BrandTopBar(
                    title: L(viewModel.isNew ? "svc_new_title" : "svc_edit_title"),
                    onBack: onBack,
                    backLabel: L("manage_tab_services")
                ) {
                    BarAction(text: L("svc_save_short"), action: viewModel.save, color: .oliveLight)
                }
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    FilledTextField(
                        label: L("svc_name"), text: $viewModel.name,
                        autocapitalization: .sentences,
                        error: viewModel.fieldErrors["name"], outlined: true
                    )
                    .onChange(of: viewModel.name) { viewModel.fieldChanged() }

                    HStack(alignment: .top, spacing: 10) {
                        DurationField(
                            label: L("svc_duration"),
                            minutes: $viewModel.durationMinutes,
                            error: viewModel.fieldErrors["duration"]
                        )
                        .onChange(of: viewModel.durationMinutes) { viewModel.fieldChanged() }
                        VStack(alignment: .leading, spacing: 8) {
                            BrandSectionLabel(text: L("svc_price"))
                            PriceField(
                                label: "", text: $viewModel.priceEuros,
                                error: viewModel.fieldErrors["price"]
                            )
                            .onChange(of: viewModel.priceEuros) { viewModel.fieldChanged() }
                        }
                    }

                    FormErrorBanner(message: viewModel.saveError)

                    VStack(alignment: .leading, spacing: 8) {
                        BrandSectionLabel(text: L("svc_operators"))
                        VStack(spacing: 0) {
                            ForEach(Array(viewModel.operators.enumerated()), id: \.element.id) { index, op in
                                if index > 0 {
                                    Rectangle().fill(Color.stoneBorder).frame(height: 1)
                                }
                                Button {
                                    viewModel.toggleOperator(op.id)
                                } label: {
                                    HStack(spacing: 11) {
                                        Circle()
                                            .fill(Color.bone)
                                            .frame(width: 32, height: 32)
                                            .overlay(
                                                Text(op.initials)
                                                    .font(Typo.cormorant(12, weight: .regular))
                                                    .foregroundStyle(Color.ink)
                                            )
                                        Text(op.name)
                                            .font(Typo.bodyLarge)
                                            .foregroundStyle(Color.ink)
                                        Spacer()
                                        let enabled = viewModel.enabledOperatorIds.contains(op.id)
                                        RoundedRectangle(cornerRadius: 6)
                                            .fill(enabled ? Color.oliveWood : Color.bone)
                                            .frame(width: 24, height: 24)
                                            .overlay(
                                                enabled
                                                    ? Image(systemName: "checkmark")
                                                        .font(.system(size: 12, weight: .semibold))
                                                        .foregroundStyle(Color.bone)
                                                    : nil
                                            )
                                    }
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 12)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color.stone))
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 16)
                .readableWidth()
            }

            BottomActionBar {
                AccentButton(
                    text: L(viewModel.isNew ? "svc_save" : "svc_save_changes"),
                    action: viewModel.save,
                    loading: viewModel.saving,
                    height: 56,
                    corner: 16
                )
                .padding(.bottom, 10)
                .readableWidth()
            }
        }
        .background(Color.bone)
        .task { await viewModel.load() }
        .onChange(of: viewModel.saved) {
            if viewModel.saved { onBack() }
        }
    }

}
