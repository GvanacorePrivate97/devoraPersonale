import SwiftUI
import Observation

/// Same flow as the admin manual booking, but scoped to the signed-in staff
/// member's own chair: no operator picker, and the service list is limited to
/// what that operator actually performs (so slot generation never comes back
/// empty because of an eligibility mismatch the staff member can't see).
@MainActor
@Observable
final class StaffBookingViewModel {

    private let auth: AuthRepository
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
    var date: LocalDate = .today()
    var selectedServiceIds: [String] = []
    var slots: [LocalTime] = []
    var selectedSlot: LocalTime?
    /// Time the sheet was opened on (a tap on the agenda) or last picked:
    /// selected again as soon as the chosen services fit there.
    var preferredSlot: LocalTime?
    var sendSms = true
    var done = false

    @ObservationIgnored private var searchTask: Task<Void, Never>?
    /// "Modifica" dal dettaglio appuntamento: l'appuntamento che questo modulo sostituisce.
    @ObservationIgnored private let editingId: String?

    var isEditing: Bool { editingId != nil }

    init(
        auth: AuthRepository, booking: BookingRepository, catalog: CatalogRepository, crm: CrmRepository,
        date: LocalDate = .today(), time: LocalTime? = nil,
        editAppointment: Appointment? = nil, editClient: ClientRecord? = nil
    ) {
        self.auth = auth
        self.booking = booking
        self.catalog = catalog
        self.crm = crm
        if let editAppointment {
            editingId = editAppointment.id
            self.date = editAppointment.date
            selectedServiceIds = editAppointment.serviceIds
            preferredSlot = editAppointment.time
            selectedClient = editClient
            query = editClient?.fullName ?? ""
        } else {
            editingId = nil
            self.date = date
            preferredSlot = time
        }
    }

    let state = LoadState()
    private(set) var op: Operator?
    /// Solo i servizi che l'operatore esegue davvero: altrimenti la griglia
    /// tornerebbe vuota per un'incompatibilita' che lui non puo' vedere.
    private(set) var services: [Service] = []
    var saveError: String?

    func load() async {
        await state.run {
            let operatorId = try await auth.currentUser()?.operatorId
            let snapshot = try await catalog.catalog()
            op = snapshot.operators.first { $0.id == operatorId }
            services = snapshot.services.filter { op?.serviceIds.contains($0.id) ?? false }
        }
        refreshSlots()
    }

    var totalMinutes: Int {
        services.filter { selectedServiceIds.contains($0.id) }.reduce(0) { $0 + $1.durationMinutes }
    }

    var canSave: Bool { selectedClient != nil && !selectedServiceIds.isEmpty && selectedSlot != nil }

    /// The preferred time is taken for the chosen services.
    var preferredUnavailable: Bool {
        guard let preferredSlot, !selectedServiceIds.isEmpty else { return false }
        return selectedSlot == nil && !slots.contains(preferredSlot)
    }

    func search(_ text: String) {
        query = text
        searchTask?.cancel()
        guard !text.trimmingCharacters(in: .whitespaces).isEmpty else {
            results = []
            return
        }
        // La ricerca la fa il server: si aspetta la pausa fra un tasto e l'altro.
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
            guard let opId = op?.id, !selectedServiceIds.isEmpty else {
                slots = []
                return
            }
            // In modifica l'appuntamento non occupa il proprio posto: il suo orario resta sceglibile.
            slots = (try? await booking.availability(
                operatorId: opId, serviceIds: selectedServiceIds, date: date,
                ignoreAppointmentId: editingId
            ).slots) ?? []
            if selectedSlot == nil, let preferredSlot, slots.contains(preferredSlot) {
                selectedSlot = preferredSlot
            }
        }
    }

    func save() {
        guard let client = selectedClient, let slot = selectedSlot, let opId = op?.id else { return }
        Task {
            saveError = nil
            let result = await booking.book(
                BookingRequest(
                    clientId: client.id, operatorId: opId, serviceIds: selectedServiceIds,
                    start: date.atTime(slot), channel: .phone,
                    // Modifica: il server sostituisce l'originale nella stessa transazione.
                    replacesAppointmentId: editingId
                )
            )
            switch result {
            case .success:
                done = true
            case .failure(let error):
                saveError = error.displayMessage
                // Se l'orario e' stato preso nel frattempo la griglia va rifatta.
                refreshSlots()
            }
        }
    }
}

/// Staff self-service booking sheet: same flow as the admin manual booking,
/// but the operator is always "me" — lets staff take a phone/walk-in booking
/// without the titolare.
struct StaffBookingScreen: View {
    @Bindable var viewModel: StaffBookingViewModel
    let onDismiss: () -> Void

    @State private var showDatePicker = false
    @State private var headerHeight: CGFloat = 0
    @State private var formHeight: CGFloat = 0
    @State private var footerHeight: CGFloat = 0

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                BarAction(text: L("staff_booking_cancel"), action: onDismiss, color: .ink)
                    .frame(width: 72, alignment: .leading)
                Text(L(viewModel.isEditing ? "staff_booking_title_edit" : "staff_booking_title"))
                    .font(Typo.cormorant(21, weight: .regular))
                    .foregroundStyle(Color.ink)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity)
                BarAction(text: L("staff_booking_save"), action: viewModel.save, color: .oliveLight)
                    .frame(width: 72, alignment: .trailing)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 6)
            .measureHeight($headerHeight)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    BrandSectionLabel(text: L("staff_booking_client"))
                    // In modifica il cliente è quello dell'appuntamento: si mostra e basta.
                    if viewModel.isEditing {
                        FixedClientRow(client: viewModel.selectedClient)
                    } else {
                        FilledTextField(
                            label: "",
                            text: Binding(get: { viewModel.query }, set: { viewModel.search($0) }),
                            placeholder: L("staff_booking_search_hint"),
                            leadingSystemImage: "magnifyingglass"
                        )
                        clientList
                    }

                    if viewModel.creatingClient {
                        NewClientForm(
                            firstLabel: L("staff_booking_new_client_first"),
                            lastLabel: L("staff_booking_new_client_last"),
                            phoneLabel: L("staff_booking_new_client_phone"),
                            ctaLabel: L("staff_booking_new_client"),
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

                    BrandSectionLabel(text: L("staff_booking_date"))
                    Button {
                        showDatePicker = true
                    } label: {
                        HStack {
                            Text(formatDateShort(viewModel.date))
                                .font(Typo.bodyLarge)
                                .foregroundStyle(Color.ink)
                            Spacer()
                            Image(systemName: "chevron.down")
                                .font(.system(size: 13))
                                .foregroundStyle(Color.textMuted)
                        }
                        .padding(.horizontal, 14)
                        .frame(height: 56)
                        .background(RoundedRectangle(cornerRadius: Radii.md).fill(Color.bone))
                        .overlay(
                            RoundedRectangle(cornerRadius: Radii.md)
                                .strokeBorder(Color.stoneBorder, lineWidth: 1.5)
                        )
                    }
                    .buttonStyle(.plain)

                    BrandSectionLabel(text: L("staff_booking_services"))
                    // Stessa tendina multi-selezione della prenotazione del titolare.
                    MultiSelectDropdown(
                        options: viewModel.services.map { DropdownOption(id: $0.id, label: $0.name) },
                        selectedIds: viewModel.selectedServiceIds,
                        onToggle: viewModel.toggleService,
                        placeholder: L("manual_services_placeholder_staff")
                    )

                    BrandSectionLabel(text: L("staff_booking_slots", formatDuration(viewModel.totalMinutes)))
                    SlotChipRow(
                        slots: viewModel.slots,
                        selected: viewModel.selectedSlot,
                        onSelect: viewModel.selectSlot,
                        label: formatTime,
                        emptyLabel: viewModel.preferredSlot.map { L("staff_booking_slots_empty_preferred", formatTime($0)) }
                            ?? L("staff_booking_slots_empty")
                    )
                    if viewModel.preferredUnavailable, let preferred = viewModel.preferredSlot {
                        Text(L("staff_booking_slot_unavailable", formatTime(preferred)))
                            .font(Typo.jost(12))
                            .foregroundStyle(Color.textMuted)
                    }

                    HStack {
                        VStack(alignment: .leading, spacing: 0) {
                            Text(L("staff_booking_sms"))
                                .font(Typo.jost(15, weight: .medium))
                                .foregroundStyle(Color.ink)
                            Text(L("staff_booking_sms_hint"))
                                .font(Typo.jost(12))
                                .foregroundStyle(Color.textMuted)
                        }
                        Spacer()
                        BrandSwitch(isOn: $viewModel.sendSms)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(RoundedRectangle(cornerRadius: Radii.md).fill(Color.bone))
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.md)
                            .strokeBorder(Color.stoneBorder, lineWidth: 1.5)
                    )

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
                    text: L(viewModel.isEditing ? "staff_booking_cta_edit" : "staff_booking_cta"),
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
                            Text("\(client.phone) · \(L("staff_booking_visits", client.visitCount))")
                                .font(Typo.jost(12))
                                .foregroundStyle(Color.textMuted)
                                .lineLimit(1)
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(selected ? Color.oliveTint : Color.bone)
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
                    Text(L("staff_booking_new_client"))
                        .font(Typo.bodyMedium)
                        .foregroundStyle(Color.textMuted)
                    Spacer()
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
            }
            .buttonStyle(.plain)
        }
        .background(RoundedRectangle(cornerRadius: Radii.md).fill(Color.bone))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md)
                .strokeBorder(Color.stoneBorder, lineWidth: 1.5)
        )
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

}
