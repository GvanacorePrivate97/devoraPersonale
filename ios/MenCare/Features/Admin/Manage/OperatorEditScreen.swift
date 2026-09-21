import SwiftUI
import Observation

private let defaultDays: Set<DayOfWeek> = [.monday, .tuesday, .wednesday, .thursday, .friday, .saturday]

/// New hire form. Saving creates the operator profile *and* the staff account
/// that lets them sign in — both live behind CatalogRepository.createOperator.
@MainActor
@Observable
final class OperatorEditViewModel {

    private let catalog: CatalogRepository

    var name = ""
    var title = ""
    var email = ""
    var phone = ""
    var selectedServiceIds: Set<String> = []
    var workingDays: Set<DayOfWeek> = defaultDays
    var from = LocalTime(9, 0)
    var to = LocalTime(19, 0)
    /// Chiave del campo → messaggio già localizzato: le regole sono quelle di
    /// `Core/Common/Validation.swift`, non più una seconda copia della regex.
    var fieldErrors: [String: String] = [:]
    var saveError: String?
    var saving = false
    var saved = false

    @ObservationIgnored private var submitted = false

    let state = LoadState()
    private(set) var services: [Service] = []

    init(catalog: CatalogRepository) {
        self.catalog = catalog
    }

    func load() async {
        await state.run { services = try await catalog.services() }
    }

    var canSave: Bool {
        validate().isEmpty && !selectedServiceIds.isEmpty && !workingDays.isEmpty && from < to
    }

    func fieldChanged() {
        saveError = nil
        if submitted { fieldErrors = validate() }
    }

    private func validate() -> [String: String] {
        var errors: [String: String] = [:]
        errors["name"] = validateName(name).message
        errors["email"] = validateEmail(email).message
        // Il telefono è obbligatorio come su POST /catalog/operators: prima non
        // veniva controllato mai e finiva grezzo nel database.
        errors["phone"] = validatePhone(phone).message
        return errors.compactMapValues { $0 }
    }

    func toggleService(_ serviceId: String) {
        if selectedServiceIds.contains(serviceId) {
            selectedServiceIds.remove(serviceId)
        } else {
            selectedServiceIds.insert(serviceId)
        }
    }

    func toggleDay(_ day: DayOfWeek) {
        if workingDays.contains(day) {
            workingDays.remove(day)
        } else {
            workingDays.insert(day)
        }
    }

    func setFrom(_ time: LocalTime) {
        from = time
        if time >= to { to = time.plusMinutes(60) }
    }

    func setTo(_ time: LocalTime) {
        to = time <= from ? from.plusMinutes(60) : time
    }

    func save() {
        guard !saving else { return }
        submitted = true
        let errors = validate()
        if !errors.isEmpty || selectedServiceIds.isEmpty || workingDays.isEmpty || from >= to {
            fieldErrors = errors
            saveError = L("validation_form_invalid")
            return
        }
        fieldErrors = [:]
        saveError = nil
        saving = true
        let phoneToSave = normalizePhone(phone) ?? phone
        Task {
            let shift = [TimeRange(from, to)]
            let result = await catalog.createOperator(
                NewOperator(
                    name: name.trimmingCharacters(in: .whitespaces),
                    title: title.trimmingCharacters(in: .whitespaces),
                    email: normalizeEmail(email),
                    phone: phoneToSave,
                    serviceIds: selectedServiceIds,
                    weeklyHours: Dictionary(uniqueKeysWithValues: workingDays.map { ($0, shift) })
                )
            )
            saving = false
            switch result {
            case .success:
                saved = true
            case .failure(let error):
                if error == .emailAlreadyRegistered {
                    fieldErrors = ["email": L("ops_error_email_taken")]
                } else {
                    saveError = L("validation_save_failed")
                }
            }
        }
    }
}

struct OperatorEditScreen: View {
    @Bindable var viewModel: OperatorEditViewModel
    let onBack: () -> Void

    /// true = opening time, false = closing time.
    @State private var editingFrom: Bool?

    var body: some View {
        VStack(spacing: 0) {
            DarkHeader(contentPadding: EdgeInsets(top: 0, leading: 0, bottom: 8, trailing: 0)) {
                BrandTopBar(
                    title: L("ops_new"),
                    onBack: onBack,
                    backLabel: L("manage_tab_operators")
                ) {
                    BarAction(text: L("svc_save_short"), action: viewModel.save, color: .oliveLight)
                }
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    NameField(
                        label: L("ops_field_name"), text: $viewModel.name,
                        error: viewModel.fieldErrors["name"], outlined: true
                    )
                    .onChange(of: viewModel.name) { viewModel.fieldChanged() }
                    FilledTextField(
                        label: L("ops_field_role"), text: $viewModel.title,
                        placeholder: L("ops_field_role_hint"), autocapitalization: .sentences
                    )
                    EmailField(
                        label: L("ops_field_email"), text: $viewModel.email,
                        error: viewModel.fieldErrors["email"]
                    )
                    .onChange(of: viewModel.email) { viewModel.fieldChanged() }
                    PhoneField(
                        label: L("ops_field_phone"), text: $viewModel.phone,
                        error: viewModel.fieldErrors["phone"]
                    )
                    .onChange(of: viewModel.phone) { viewModel.fieldChanged() }

                    BrandSectionLabel(text: L("ops_working_days"))
                    HStack(spacing: 6) {
                        ForEach(DayOfWeek.allCases, id: \.self) { day in
                            BrandChip(
                                text: formatDayGroup([day]).prefix(3).capitalizedFirst,
                                selected: viewModel.workingDays.contains(day),
                                action: { viewModel.toggleDay(day) },
                                fill: true
                            )
                        }
                    }
                    HStack(spacing: 9) {
                        PickerTile(label: L("business_from"), value: formatTime(viewModel.from)) {
                            editingFrom = true
                        }
                        PickerTile(label: L("business_to"), value: formatTime(viewModel.to)) {
                            editingFrom = false
                        }
                    }

                    BrandSectionLabel(text: L("svc_operators_services"))
                    servicesGrid
                    if viewModel.selectedServiceIds.isEmpty {
                        Text(L("ops_error_services"))
                            .font(Typo.bodySmall)
                            .foregroundStyle(Color.errorRed)
                    }

                    FormErrorBanner(message: viewModel.saveError)

                    Text(L("ops_account_note"))
                        .font(Typo.jost(12))
                        // Su fondo nero il grigio chiaro, non quello da chiaro.
                        .foregroundStyle(Color.onDarkMuted)
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color.ink))
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 16)
                .readableWidth()
            }

            BottomActionBar {
                AccentButton(
                    text: L("ops_create"),
                    action: viewModel.save,
                    enabled: viewModel.canSave,
                    loading: viewModel.saving,
                    height: 56,
                    corner: 16
                )
                .readableWidth()
            }
        }
        .background(Color.bone)
        .task { await viewModel.load() }
        .onChange(of: viewModel.saved) {
            if viewModel.saved { onBack() }
        }
        .sheet(isPresented: Binding(
            get: { editingFrom != nil },
            set: { if !$0 { editingFrom = nil } }
        )) {
            let isStart = editingFrom ?? true
            BrandTimePickerDialog(
                initial: isStart ? viewModel.from : viewModel.to,
                onDismiss: { editingFrom = nil },
                onConfirm: { time in
                    if isStart { viewModel.setFrom(time) } else { viewModel.setTo(time) }
                    editingFrom = nil
                }
            )
        }
    }

    private var servicesGrid: some View {
        let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 2)
        return LazyVGrid(columns: columns, spacing: 8) {
            ForEach(viewModel.services) { service in
                BrandChip(
                    text: service.name,
                    selected: viewModel.selectedServiceIds.contains(service.id),
                    action: { viewModel.toggleService(service.id) },
                    fill: true,
                    maxLines: 2
                )
            }
        }
    }
}

private extension Substring {
    var capitalizedFirst: String {
        prefix(1).uppercased() + dropFirst()
    }
}
