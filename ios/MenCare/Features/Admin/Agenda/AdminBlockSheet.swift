import SwiftUI
import Observation

/// Owner-side twin of the staff block screen: same rules, but the operator is
/// chosen instead of being the signed-in one.
@MainActor
@Observable
final class AdminBlockViewModel {

    private let blockRepo: TimeBlockRepository
    private let booking: BookingRepository
    private let catalog: CatalogRepository

    var selectedOperatorId: String? {
        didSet { scheduleConflictCheck() }
    }

    var reason: BlockReason = .permesso {
        didSet { scheduleConflictCheck() }
    }

    var date: LocalDate = .today() {
        didSet { scheduleConflictCheck() }
    }

    private(set) var from = LocalTime(15, 30)
    private(set) var to = LocalTime(18, 0)
    var saving = false
    var saved = false

    let state = LoadState()
    private(set) var operators: [Operator] = []
    /// Chi verrebbe travolto: lo dice `GET /blocks/conflicts`.
    private(set) var conflicts: [Appointment] = []
    private(set) var checkingConflicts = false
    var saveError: String?

    @ObservationIgnored private var conflictTask: Task<Void, Never>?

    init(blocks: TimeBlockRepository, booking: BookingRepository, catalog: CatalogRepository) {
        self.blockRepo = blocks
        self.booking = booking
        self.catalog = catalog
    }

    func load() async {
        await state.run {
            operators = try await catalog.operators()
            if selectedOperatorId == nil { selectedOperatorId = operators.first?.id }
        }
    }

    var totalMinutes: Int { to.minutesOfDay - from.minutesOfDay }

    /// Il server rifiuta un blocco sopra appuntamenti vivi: qui si evita di
    /// arrivarci, mostrando prima quali sono.
    var canSave: Bool {
        selectedOperatorId != nil && totalMinutes > 0 && conflicts.isEmpty && !saving && !checkingConflicts
    }

    private func scheduleConflictCheck() {
        conflictTask?.cancel()
        conflictTask = Task {
            try? await Task.sleep(for: .milliseconds(250))
            guard !Task.isCancelled else { return }
            await checkConflicts()
        }
    }

    private func checkConflicts() async {
        guard let selectedOperatorId, totalMinutes > 0 else {
            conflicts = []
            return
        }
        checkingConflicts = true
        conflicts = (try? await blockRepo.conflictsFor(
            TimeBlock(
                id: "candidate", operatorId: selectedOperatorId, reason: reason,
                date: date, range: TimeRange(from, to)
            )
        )) ?? []
        checkingConflicts = false
    }

    func setFrom(_ newFrom: LocalTime) {
        from = newFrom
        if newFrom >= to { to = newFrom.plusMinutes(60) }
        scheduleConflictCheck()
    }

    func setTo(_ newTo: LocalTime) {
        to = newTo <= from ? from.plusMinutes(60) : newTo
        scheduleConflictCheck()
    }

    func halfDay() {
        from = LocalTime(9, 0)
        to = LocalTime(13, 0)
        scheduleConflictCheck()
    }

    func fullDay() {
        from = LocalTime(9, 0)
        to = LocalTime(19, 0)
        scheduleConflictCheck()
    }

    func save() {
        guard let selectedOperatorId, canSave else { return }
        saving = true
        saveError = nil
        Task {
            let result = await blockRepo.createBlock(
                TimeBlock(
                    id: "", operatorId: selectedOperatorId, reason: reason, date: date,
                    range: TimeRange(from, to)
                )
            )
            saving = false
            switch result {
            case .success: saved = true
            case .failure(let error): saveError = error.displayMessage
            }
        }
    }
}

private enum Editing: Identifiable {
    case date, from, to
    var id: Self { self }
}

/// Owner-side personal block: pick the operator, the day and the window.
struct AdminBlockSheet: View {
    @Bindable var viewModel: AdminBlockViewModel
    let onDismiss: () -> Void

    @State private var editing: Editing?
    @State private var headerHeight: CGFloat = 0
    @State private var formHeight: CGFloat = 0
    @State private var footerHeight: CGFloat = 0

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                BarAction(text: L("manual_cancel"), action: onDismiss, color: .ink)
                    .frame(width: 72, alignment: .leading)
                Text(L("week_new_block"))
                    .font(Typo.cormorant(21, weight: .regular))
                    .foregroundStyle(Color.ink)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity)
                Color.clear.frame(width: 72, height: 1)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 6)
            .measureHeight($headerHeight)

            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    BrandSectionLabel(text: L("manual_operator"))
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
                        ForEach(viewModel.operators) { op in
                            BrandChip(
                                text: String(op.name.split(separator: " ").first ?? ""),
                                selected: op.id == viewModel.selectedOperatorId,
                                action: { viewModel.selectedOperatorId = op.id },
                                fill: true
                            )
                        }
                    }

                    BrandSectionLabel(text: L("block_reason_label"))
                    HStack(spacing: 8) {
                        ForEach(BlockReason.allCases, id: \.self) { reason in
                            BrandChip(
                                text: blockReasonLabel(reason),
                                selected: viewModel.reason == reason,
                                action: { viewModel.reason = reason },
                                fill: true
                            )
                        }
                    }

                    HStack(spacing: 9) {
                        PickerTile(label: L("manual_date"), value: formatDateShort(viewModel.date)) { editing = .date }
                        PickerTile(label: L("business_from"), value: formatTime(viewModel.from)) { editing = .from }
                        PickerTile(label: L("business_to"), value: formatTime(viewModel.to)) { editing = .to }
                    }
                    HStack(spacing: 9) {
                        BrandChip(text: L("block_half_day_label"), selected: false, action: viewModel.halfDay, fill: true)
                        BrandChip(text: L("block_full_day_label"), selected: false, action: viewModel.fullDay, fill: true)
                    }

                    FormErrorBanner(message: viewModel.saveError)
                    if !viewModel.conflicts.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(L("block_conflicts_admin", viewModel.conflicts.count))
                                .font(Typo.jost(15, weight: .medium))
                                .foregroundStyle(Color.errorRed)
                            ForEach(viewModel.conflicts) { conflict in
                                Text("\(formatTime(conflict.time)) · \(formatDurationLong(conflict.durationMinutes))")
                                    .font(Typo.jost(12))
                                    .foregroundStyle(Color.textMuted)
                            }
                            Text(L("block_conflicts_admin_hint"))
                                .font(Typo.jost(12))
                                .foregroundStyle(Color.textMuted)
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color.bone))
                        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.errorRed, lineWidth: 1.5))
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 12)
                .measureHeight($formHeight)
            }
            .scrollBounceBehavior(.basedOnSize)

            // Fuori dallo scroll: il bottone resta sempre a vista in fondo.
            AccentButton(
                text: L("block_cta_admin", formatTime(viewModel.from), formatTime(viewModel.to)),
                action: viewModel.save,
                enabled: viewModel.canSave,
                loading: viewModel.saving,
                height: 56,
                corner: 16
            )
            .padding(.horizontal, 20)
            .padding(.bottom, 12)
            .measureHeight($footerHeight)
        }
        .background(Color.bone)
        .task { await viewModel.load() }
        // Alta quanto il contenuto: il bottone "Blocca" sta subito sotto.
        .fittedBrandSheet(height: headerHeight + formHeight + footerHeight)
        .onChange(of: viewModel.saved) {
            if viewModel.saved { onDismiss() }
        }
        .sheet(item: $editing) { mode in
            switch mode {
            case .date:
                BrandDatePickerDialog(
                    initial: viewModel.date,
                    onDismiss: { editing = nil },
                    onConfirm: { date in
                        viewModel.date = date
                        editing = nil
                    }
                )
            case .from:
                BrandTimePickerDialog(
                    initial: viewModel.from,
                    onDismiss: { editing = nil },
                    onConfirm: { time in
                        viewModel.setFrom(time)
                        editing = nil
                    }
                )
            case .to:
                BrandTimePickerDialog(
                    initial: viewModel.to,
                    onDismiss: { editing = nil },
                    onConfirm: { time in
                        viewModel.setTo(time)
                        editing = nil
                    }
                )
            }
        }
    }
}

/// Tapping a card in the agenda opens this: the appointment at a glance plus
/// the one destructive action the owner needs. Moving is done by dragging.
struct AppointmentActionsSheet: View {
    let viewModel: WeeklyAgendaViewModel
    let appointmentId: String
    let onEditAppointment: () -> Void
    let onCancelAppointment: () -> Void
    let onMarkNoShow: () -> Void
    let onMarkCompleted: () -> Void
    let onDismiss: () -> Void

    @State private var confirming = false
    @State private var confirmingNoShow = false
    @Environment(\.openURL) private var openURL

    var body: some View {
        if let appointment = viewModel.appointments.first(where: { $0.id == appointmentId }) {
            let client = viewModel.clients[appointment.clientId]
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 12) {
                    Circle()
                        .fill(Color.ink)
                        .frame(width: 44, height: 44)
                        .overlay(
                            Text(client?.initials ?? "")
                                .font(Typo.cormorant(15, weight: .regular))
                                .foregroundStyle(Color.oliveWood)
                        )
                    VStack(alignment: .leading, spacing: 0) {
                        Text(client?.fullName ?? "")
                            .font(Typo.headlineSmall)
                            .foregroundStyle(Color.ink)
                            .lineLimit(1)
                        Text(
                            [
                                "\(formatTime(appointment.time)) · \(formatDuration(appointment.durationMinutes))",
                                viewModel.operators.first { $0.id == appointment.operatorId }
                                    .map { L("week_with_operator", $0.name) },
                            ].compactMap { $0 }.joined(separator: " · ")
                        )
                        .font(Typo.jost(12))
                        .foregroundStyle(Color.textMuted)
                    }
                    Spacer()
                    Text(formatPrice(appointment.totalPriceCents))
                        .font(Typo.titleMedium)
                        .foregroundStyle(Color.ink)
                }
                Text(statusLabel(appointment.status).uppercased())
                    .font(Typo.jost(9, weight: .medium))
                    .kerning(1.2)
                    .foregroundStyle(appointment.status == .noShow ? Color.errorRed : Color.oliveWood)
                    .padding(.top, 12)
                BrandSectionLabel(text: L("manual_services"))
                    .padding(.top, 12)
                HStack(spacing: 8) {
                    ForEach(appointment.serviceIds.compactMap { viewModel.services[$0] }) { service in
                        Text("\(service.name) · \(service.durationMinutes)′")
                            .font(Typo.jost(12))
                            .foregroundStyle(Color.bone)
                            .lineLimit(1)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 9)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Color.ink))
                    }
                }
                .padding(.top, 8)
                Text(L("week_move_hint"))
                    .font(Typo.jost(12))
                    .foregroundStyle(Color.textMuted)
                    .padding(.top, 20)
                // "Chiama" sempre, in oro; un appuntamento concluso invece non si
                // modifica né si annulla.
                HStack(spacing: 10) {
                    Button {
                        if let url = phoneDialURL(client?.phone) { openURL(url) }
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "phone")
                                .font(.system(size: 15))
                            Text(L("week_call"))
                                .font(Typo.titleMedium)
                                .lineLimit(1)
                                .fixedSize()
                        }
                        .foregroundStyle(Color.bone)
                        .frame(maxWidth: appointment.isActive ? nil : .infinity)
                        .frame(width: appointment.isActive ? 124 : nil, height: 54)
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color.oliveWood))
                    }
                    .buttonStyle(.plain)
                    if appointment.isActive {
                        Button(action: onEditAppointment) {
                            HStack(spacing: 10) {
                                Image(systemName: "pencil")
                                    .font(.system(size: 15))
                                Text(L("week_edit"))
                                    .font(Typo.titleMedium)
                            }
                            .foregroundStyle(Color.bone)
                            .frame(maxWidth: .infinity)
                            .frame(height: 54)
                            .background(RoundedRectangle(cornerRadius: 16).fill(Color.ink))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.top, 28)
                // Stato a mano: il no-show (da orario d'inizio passato) e la sua correzione.
                if appointment.canMarkNoShow() {
                    Button {
                        if confirmingNoShow {
                            confirmingNoShow = false
                            onMarkNoShow()
                        } else {
                            confirmingNoShow = true
                        }
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "person.crop.circle.badge.xmark")
                                .font(.system(size: 15))
                            Text(L(confirmingNoShow ? "week_no_show_confirm" : "week_mark_no_show"))
                                .font(Typo.titleMedium)
                        }
                        .foregroundStyle(confirmingNoShow ? Color.bone : Color.ink)
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(confirmingNoShow ? Color.ink : Color.bone)
                        )
                        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.stoneBorder, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 10)
                }
                if appointment.canRevertNoShow {
                    Button(action: onMarkCompleted) {
                        HStack(spacing: 10) {
                            Image(systemName: "checkmark")
                                .font(.system(size: 15))
                            Text(L("week_mark_completed"))
                                .font(Typo.titleMedium)
                        }
                        .foregroundStyle(Color.bone)
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color.oliveWood))
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 10)
                }
                if appointment.isActive {
                    Button {
                        if confirming { onCancelAppointment() } else { confirming = true }
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "trash")
                                .font(.system(size: 15))
                            Text(L(confirming ? "week_cancel_confirm" : "week_cancel"))
                                .font(Typo.titleMedium)
                        }
                        .foregroundStyle(confirming ? Color.bone : Color.errorRed)
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(confirming ? Color.errorRed : Color.stone)
                        )
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 10)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 12)
        }
    }

    private func statusLabel(_ status: AppointmentStatus) -> String {
        switch status {
        case .confirmed: L("week_status_confirmed")
        case .inProgress: L("week_status_in_progress")
        case .completed: L("week_status_completed")
        case .noShow: L("week_status_no_show")
        case .cancelled: L("week_status_cancelled")
        }
    }
}
