import SwiftUI
import Observation

@MainActor
@Observable
final class ManualBookingViewModel {

    private let booking: BookingRepository
    private let crm: CrmRepository
    private let catalog: CatalogRepository

    var query = ""
    var results: [ClientRecord] = []
    var selectedClient: ClientRecord?
    var creatingClient = false
    var newFirst = ""
    var newLast = ""
    var newPhone = ""
    var newClientErrors: [String: String] = [:]
    var newClientError: String?
    var creatingClientInFlight = false
    var selectedOperatorId: String?
    var date: LocalDate = .today()
    var selectedServiceIds: [String] = []
    var slots: [LocalTime] = []
    var selectedSlot: LocalTime?
    /// Time the sheet was opened on (a tap on the agenda) or last picked:
    /// selected again as soon as the chosen services fit there.
    var preferredSlot: LocalTime?
    var sendSms = true
    var done = false

    let state = LoadState()
    private(set) var operators: [Operator] = []
    private(set) var services: [Service] = []
    var saveError: String?

    @ObservationIgnored private var searchTask: Task<Void, Never>?
    /// Operatore chiesto da chi apre la tendina (un tap sulla colonna): si
    /// applica appena la squadra e' arrivata.
    @ObservationIgnored private let requestedOperatorId: String?
    /// "Modifica" dall'agenda: l'appuntamento che questo modulo sostituisce.
    @ObservationIgnored private let editingId: String?

    var isEditing: Bool { editingId != nil }

    init(
        booking: BookingRepository, catalog: CatalogRepository, crm: CrmRepository,
        operatorId: String? = nil, date: LocalDate = .today(), time: LocalTime? = nil,
        editAppointment: Appointment? = nil, editClient: ClientRecord? = nil
    ) {
        self.booking = booking
        self.catalog = catalog
        self.crm = crm
        if let editAppointment {
            editingId = editAppointment.id
            requestedOperatorId = editAppointment.operatorId
            selectedOperatorId = editAppointment.operatorId
            self.date = editAppointment.date
            selectedServiceIds = editAppointment.serviceIds
            preferredSlot = editAppointment.time
            selectedClient = editClient
            query = editClient?.fullName ?? ""
        } else {
            editingId = nil
            requestedOperatorId = operatorId
            selectedOperatorId = operatorId
            self.date = date
            preferredSlot = time
        }
    }

    func load() async {
        await state.run {
            let snapshot = try await catalog.catalog()
            operators = snapshot.operators
            services = snapshot.services
            selectedOperatorId = requestedOperatorId ?? snapshot.operators.first?.id
        }
        refreshSlots()
    }

    var totalMinutes: Int {
        services.filter { selectedServiceIds.contains($0.id) }.reduce(0) { $0 + $1.durationMinutes }
    }

    var canSave: Bool {
        selectedClient != nil && selectedOperatorId != nil && !selectedServiceIds.isEmpty && selectedSlot != nil
    }

    /// The preferred time is taken for the chosen services.
    var preferredUnavailable: Bool {
        guard let preferredSlot, !selectedServiceIds.isEmpty else { return false }
        return selectedSlot == nil && !slots.contains(preferredSlot)
    }

    private var selectedOperator: Operator? {
        operators.first { $0.id == selectedOperatorId }
    }

    /// The chosen operator does not perform every selected service.
    var operatorIneligible: Bool {
        guard let op = selectedOperator else { return false }
        return selectedServiceIds.contains { !op.serviceIds.contains($0) }
    }

    /// The chosen operator has no working hours on the chosen day.
    var operatorOffDuty: Bool {
        guard let op = selectedOperator else { return false }
        return (op.weeklyHours[date.dayOfWeek] ?? []).isEmpty
    }

    func search(_ text: String) {
        query = text
        searchTask?.cancel()
        guard !text.trimmingCharacters(in: .whitespaces).isEmpty else {
            results = []
            return
        }
        // Ricerca sul server, con la pausa fra un tasto e l'altro.
        searchTask = Task {
            try? await Task.sleep(for: .milliseconds(300))
            guard !Task.isCancelled else { return }
            results = Array(((try? await crm.clients(query: text, segment: .tutti)) ?? []).prefix(5))
        }
    }

    func selectClient(_ client: ClientRecord) {
        selectedClient = client
        results = []
        query = client.fullName
        creatingClient = false
    }

    func startCreateClient() {
        creatingClient = true
        selectedClient = nil
    }

    func newClientFieldChanged() {
        newClientError = nil
        if !newClientErrors.isEmpty {
            newClientErrors = validateNewClient(firstName: newFirst, lastName: newLast, phone: newPhone)
        }
    }

    func createClient() {
        guard !creatingClientInFlight else { return }
        let errors = validateNewClient(firstName: newFirst, lastName: newLast, phone: newPhone)
        if !errors.isEmpty {
            // Prima il fallimento veniva ingoiato e il tasto sembrava rotto.
            newClientErrors = errors
            newClientError = L("validation_form_invalid")
            return
        }
        newClientErrors = [:]
        newClientError = nil
        creatingClientInFlight = true
        let phoneToSave = normalizePhone(newPhone) ?? newPhone
        Task {
            let result = await crm.createClient(
                firstName: newFirst.trimmingCharacters(in: .whitespaces),
                lastName: newLast.trimmingCharacters(in: .whitespaces),
                phone: phoneToSave
            )
            creatingClientInFlight = false
            switch result {
            case .success(let record):
                selectClient(record)
            case .failure:
                newClientError = L("validation_save_failed")
            }
        }
    }

    func selectOperator(_ operatorId: String) {
        selectedOperatorId = operatorId
        selectedSlot = nil
        refreshSlots()
    }

    func selectDate(_ newDate: LocalDate) {
        date = newDate
        selectedSlot = nil
        refreshSlots()
    }

    func toggleService(_ serviceId: String) {
        if let index = selectedServiceIds.firstIndex(of: serviceId) {
            selectedServiceIds.remove(at: index)
        } else {
            selectedServiceIds.append(serviceId)
        }
        selectedSlot = nil
        refreshSlots()
    }

    func selectSlot(_ slot: LocalTime) {
        selectedSlot = slot
        preferredSlot = slot
    }

    func refreshSlots() {
        Task {
            guard let selectedOperatorId, !selectedServiceIds.isEmpty else {
                slots = []
                return
            }
            // In modifica l'appuntamento non occupa il proprio posto: il suo orario resta sceglibile.
            slots = (try? await booking.availability(
                operatorId: selectedOperatorId, serviceIds: selectedServiceIds, date: date,
                ignoreAppointmentId: editingId
            ).slots) ?? []
            if selectedSlot == nil, let preferredSlot, slots.contains(preferredSlot) {
                selectedSlot = preferredSlot
            }
        }
    }

    func save() {
        guard let client = selectedClient, let slot = selectedSlot else { return }
        Task {
            saveError = nil
            // In modifica il server sostituisce il vecchio con il nuovo in un colpo
            // solo: se il nuovo non entra, il vecchio resta com'era.
            let result = await booking.book(
                BookingRequest(
                    clientId: client.id, operatorId: selectedOperatorId,
                    serviceIds: selectedServiceIds, start: date.atTime(slot), channel: .phone,
                    replacesAppointmentId: editingId
                )
            )
            switch result {
            case .success:
                done = true
            case .failure(let error):
                saveError = error.displayMessage
                refreshSlots()
            }
        }
    }
}

struct ManualBookingScreen: View {
    @Bindable var viewModel: ManualBookingViewModel
    let onDismiss: () -> Void

    @State private var showDatePicker = false
    @State private var headerHeight: CGFloat = 0
    @State private var formHeight: CGFloat = 0
    @State private var footerHeight: CGFloat = 0

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                BarAction(text: L("manual_cancel"), action: onDismiss, color: .ink)
                    .frame(width: 72, alignment: .leading)
                Text(L(viewModel.isEditing ? "manual_title_edit" : "manual_title_short"))
                    .font(Typo.cormorant(21, weight: .regular))
                    .foregroundStyle(Color.ink)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity)
                BarAction(text: L("manual_save"), action: viewModel.save, color: .oliveWood)
                    .frame(width: 72, alignment: .trailing)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 6)
            .measureHeight($headerHeight)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    BrandSectionLabel(text: L("manual_client"))
                    // In modifica il cliente è quello dell'appuntamento: si mostra e basta.
                    if viewModel.isEditing {
                        FixedClientRow(client: viewModel.selectedClient)
                    } else {
                        FilledTextField(
                            label: "",
                            text: Binding(get: { viewModel.query }, set: { viewModel.search($0) }),
                            placeholder: L("manual_search_hint"),
                            leadingSystemImage: "magnifyingglass"
                        )
                        clientList
                    }

                    if viewModel.creatingClient {
                        NewClientForm(
                            firstLabel: L("manual_new_client_first"),
                            lastLabel: L("manual_new_client_last"),
                            phoneLabel: L("manual_new_client_phone"),
                            ctaLabel: L("manual_new_client"),
                            firstName: $viewModel.newFirst,
                            lastName: $viewModel.newLast,
                            phone: $viewModel.newPhone,
                            errors: viewModel.newClientErrors,
                            formError: viewModel.newClientError,
                            creating: viewModel.creatingClientInFlight,
                            onCreate: viewModel.createClient
                        )
                        .onChange(of: viewModel.newFirst) { viewModel.newClientFieldChanged() }
                        .onChange(of: viewModel.newLast) { viewModel.newClientFieldChanged() }
                        .onChange(of: viewModel.newPhone) { viewModel.newClientFieldChanged() }
                    }

                    HStack(alignment: .top, spacing: 10) {
                        VStack(alignment: .leading, spacing: 8) {
                            BrandSectionLabel(text: L("manual_operator"))
                            // Menu di sistema: tutti gli operatori, spunta su quello scelto.
                            Menu {
                                ForEach(viewModel.operators) { op in
                                    Button {
                                        viewModel.selectOperator(op.id)
                                    } label: {
                                        if op.id == viewModel.selectedOperatorId {
                                            Label(op.name, systemImage: "checkmark")
                                        } else {
                                            Text(op.name)
                                        }
                                    }
                                }
                            } label: {
                                dropdownLabel(
                                    viewModel.operators.first { $0.id == viewModel.selectedOperatorId }
                                        .map { shortOperatorName($0.name) } ?? "—"
                                )
                            }
                            .buttonStyle(.plain)
                        }
                        VStack(alignment: .leading, spacing: 8) {
                            BrandSectionLabel(text: L("manual_date"))
                            dropdownField(formatDateShort(viewModel.date)) {
                                showDatePicker = true
                            }
                        }
                    }

                    BrandSectionLabel(text: L("manual_services"))
                    // Tendina multi-selezione, gemella di quella dell'operatore.
                    MultiSelectDropdown(
                        options: viewModel.services.map { DropdownOption(id: $0.id, label: $0.name) },
                        selectedIds: viewModel.selectedServiceIds,
                        onToggle: viewModel.toggleService,
                        placeholder: L("manual_services_placeholder")
                    )

                    BrandSectionLabel(text: L("manual_slots", formatDuration(viewModel.totalMinutes)))
                    // Colonna vuota non vuol dire libera: se l'operatore non è in
                    // turno o non esegue i servizi scelti, lo si dice.
                    let emptyReason: String? = {
                        guard viewModel.selectedOperatorId != nil, !viewModel.selectedServiceIds.isEmpty else { return nil }
                        if viewModel.operatorIneligible { return L("manual_slots_ineligible") }
                        if viewModel.operatorOffDuty { return L("manual_slots_off_duty") }
                        return nil
                    }()
                    SlotChipRow(
                        slots: viewModel.slots,
                        selected: viewModel.selectedSlot,
                        onSelect: viewModel.selectSlot,
                        label: formatTime,
                        emptyLabel: emptyReason
                            ?? viewModel.preferredSlot.map { L("manual_slots_empty_preferred", formatTime($0)) }
                            ?? L("manual_slots_empty")
                    )
                    if viewModel.preferredUnavailable, let preferred = viewModel.preferredSlot {
                        Text(L("manual_slot_unavailable", formatTime(preferred)))
                            .font(Typo.jost(12))
                            .foregroundStyle(Color.textMuted)
                    }

                    HStack {
                        VStack(alignment: .leading, spacing: 0) {
                            Text(L("manual_sms"))
                                .font(Typo.jost(15, weight: .medium))
                                .foregroundStyle(Color.ink)
                            Text(L("manual_sms_hint"))
                                .font(Typo.jost(12))
                                .foregroundStyle(Color.textMuted)
                        }
                        Spacer()
                        BrandSwitch(isOn: $viewModel.sendSms)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(RoundedRectangle(cornerRadius: 16).fill(Color.stone))

                }
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 12)
                .measureHeight($formHeight)
            }
            .scrollBounceBehavior(.basedOnSize)
            // Fuori dallo scroll: il pulsante resta sempre a vista, subito sotto il modulo.
            VStack(spacing: 8) {
                FormErrorBanner(message: viewModel.saveError)
                AccentButton(
                    text: L(viewModel.isEditing ? "manual_cta_edit" : "manual_cta"),
                    action: viewModel.save,
                    enabled: viewModel.canSave,
                    height: 56,
                    corner: 16
                )
            }
            .padding(.horizontal, 20)
            .padding(.top, 4)
            .padding(.bottom, 12)
            .measureHeight($footerHeight)
        }
        .background(Color.bone)
        .task { await viewModel.load() }
        // Alta quanto il contenuto, come "Ferie e permessi": niente buco bianco
        // fra il modulo e il pulsante. Se il modulo cresce (risultati della
        // ricerca, nuovo cliente, orari) la tendina lo segue fin sotto il notch,
        // poi scorre; trascinando non cambia altezza.
        .fittedBrandSheet(height: headerHeight + formHeight + footerHeight)
        .presentationCornerRadius(26)
        .onChange(of: viewModel.done) {
            if viewModel.done { onDismiss() }
        }
        .sheet(isPresented: $showDatePicker) {
            BrandDatePickerDialog(
                initial: viewModel.date,
                onDismiss: { showDatePicker = false },
                onConfirm: { date in
                    viewModel.selectDate(date)
                    showDatePicker = false
                }
            )
        }
    }

    private var clientList: some View {
        VStack(spacing: 0) {
            ForEach(viewModel.results.prefix(3)) { client in
                let selected = viewModel.selectedClient?.id == client.id
                Button {
                    viewModel.selectClient(client)
                } label: {
                    HStack(spacing: 11) {
                        Circle()
                            .fill(Color.bone)
                            .frame(width: 34, height: 34)
                            .overlay(
                                Text(client.initials)
                                    .font(Typo.cormorant(12, weight: .regular))
                                    .foregroundStyle(Color.ink)
                            )
                        VStack(alignment: .leading, spacing: 0) {
                            Text(client.fullName)
                                .font(Typo.titleSmall)
                                .foregroundStyle(Color.ink)
                                .lineLimit(1)
                            Text("\(client.phone) · \(L("manual_visits", client.visitCount))")
                                .font(Typo.jost(12))
                                .foregroundStyle(Color.textMuted)
                                .lineLimit(1)
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(selected ? Color.oliveWood.opacity(0.18) : Color.stone)
                }
                .buttonStyle(.plain)
            }
            Button(action: viewModel.startCreateClient) {
                HStack(spacing: 11) {
                    Circle()
                        .fill(Color.bone)
                        .frame(width: 34, height: 34)
                        .overlay(
                            Image(systemName: "plus")
                                .font(.system(size: 13))
                                .foregroundStyle(Color.oliveWood)
                        )
                    Text(L("manual_new_client"))
                        .font(Typo.bodyMedium)
                        .foregroundStyle(Color.textMuted)
                    Spacer()
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
            }
            .buttonStyle(.plain)
        }
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.stone))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }


    /// Stone field that reads as a picker: value plus a chevron, as in the mockup.
    private func dropdownField(_ value: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            dropdownLabel(value)
        }
        .buttonStyle(.plain)
    }

    private func dropdownLabel(_ value: String) -> some View {
        HStack {
            Text(value)
                .font(Typo.bodyLarge)
                .foregroundStyle(Color.ink)
                .lineLimit(1)
            Spacer()
            Image(systemName: "chevron.down")
                .font(.system(size: 13))
                .foregroundStyle(Color.textMuted)
        }
        .padding(.horizontal, 14)
        .frame(height: 56)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.stone))
        .contentShape(Rectangle())
    }
}

/// "Luca Ferrante" -> "Luca F."
func shortOperatorName(_ name: String) -> String {
    let parts = name.split(separator: " ").map(String.init)
    guard parts.count > 1, let initial = parts[1].first else { return name }
    return "\(parts[0]) \(initial)."
}
