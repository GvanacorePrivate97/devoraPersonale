import SwiftUI
import Observation

@MainActor
@Observable
final class BlockViewModel {

    private let auth: AuthRepository
    private let blockRepo: TimeBlockRepository
    private let booking: BookingRepository
    private let catalog: CatalogRepository

    var reason: BlockReason = .permesso {
        didSet { scheduleConflictCheck() }
    }

    private(set) var date: LocalDate = .today()
    private(set) var from = LocalTime(15, 30)
    private(set) var to = LocalTime(18, 0)
    var resolvedConflictIds: Set<String> = []
    var saved = false

    let state = LoadState()
    private(set) var colleagues: [Operator] = []
    /// Chi verrebbe travolto dal blocco: lo dice `GET /blocks/conflicts`, non
    /// piu' un confronto fatto in casa sull'agenda locale.
    private(set) var conflicts: [Appointment] = []
    private(set) var checkingConflicts = false
    var saveError: String?

    @ObservationIgnored private var operatorId: String?
    @ObservationIgnored private var conflictTask: Task<Void, Never>?

    init(auth: AuthRepository, blocks: TimeBlockRepository, booking: BookingRepository, catalog: CatalogRepository) {
        self.auth = auth
        self.blockRepo = blocks
        self.booking = booking
        self.catalog = catalog
    }

    func load() async {
        await state.run {
            operatorId = try await auth.currentUser()?.operatorId
            colleagues = try await catalog.operators().filter { $0.id != operatorId }
        }
        await checkConflicts()
    }

    private var totalMinutes: Int { to.minutesOfDay - from.minutesOfDay }

    var unresolvedConflicts: [Appointment] {
        conflicts.filter { !resolvedConflictIds.contains($0.id) }
    }

    var canSave: Bool { totalMinutes > 0 && unresolvedConflicts.isEmpty && !checkingConflicts }

    /// Ogni modifica della finestra rimanda la domanda al server: farla a ogni
    /// tocco del selettore vorrebbe dire una richiesta per minuto scorso.
    private func scheduleConflictCheck() {
        conflictTask?.cancel()
        conflictTask = Task {
            try? await Task.sleep(for: .milliseconds(250))
            guard !Task.isCancelled else { return }
            await checkConflicts()
        }
    }

    private func checkConflicts() async {
        guard let operatorId, totalMinutes > 0 else {
            conflicts = []
            return
        }
        checkingConflicts = true
        let candidate = TimeBlock(
            id: "", operatorId: operatorId, reason: reason, date: date, range: TimeRange(from, to)
        )
        conflicts = (try? await blockRepo.conflictsFor(candidate)) ?? []
        checkingConflicts = false
    }

    func setDate(_ newDate: LocalDate) {
        date = newDate
        resolvedConflictIds = []
        scheduleConflictCheck()
    }

    func setFrom(_ newFrom: LocalTime) {
        from = newFrom
        if newFrom >= to { to = newFrom.plusMinutes(60) }
        resolvedConflictIds = []
        scheduleConflictCheck()
    }

    func setTo(_ newTo: LocalTime) {
        to = newTo <= from ? from.plusMinutes(60) : newTo
        resolvedConflictIds = []
        scheduleConflictCheck()
    }

    func halfDay() { setRange(from: LocalTime(9, 0), to: LocalTime(13, 0)) }
    func fullDay() { setRange(from: LocalTime(9, 0), to: LocalTime(19, 0)) }

    private func setRange(from: LocalTime, to: LocalTime) {
        self.from = from
        self.to = to
        resolvedConflictIds = []
        scheduleConflictCheck()
    }

    /// Reassign a conflicting appointment to a colleague at the same time.
    func reassign(_ appointmentId: String, colleagueId: String) {
        guard let apt = conflicts.first(where: { $0.id == appointmentId }) else { return }
        Task {
            saveError = nil
            switch await booking.reschedule(appointmentId, newStart: apt.start, newOperatorId: colleagueId) {
            case .success: resolvedConflictIds.insert(appointmentId)
            case .failure(let error): saveError = error.displayMessage
            }
        }
    }

    /// "Proponi altro orario": Phase 1 marks the conflict handled — the client
    /// would get a reschedule proposal via push in Phase 2.
    func proposeNewTime(_ appointmentId: String) {
        resolvedConflictIds.insert(appointmentId)
    }

    func save() {
        guard let operatorId, canSave else { return }
        Task {
            saveError = nil
            let result = await blockRepo.createBlock(
                TimeBlock(
                    id: "", operatorId: operatorId, reason: reason, date: date,
                    range: TimeRange(from, to)
                )
            )
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

/// Ferie e permessi dell'operatore: la stessa tendina del titolare, senza la
/// scelta dell'operatore (è chi ha fatto l'accesso) e con i conflitti che si
/// risolvono qui.
struct BlockSheet: View {
    @Bindable var viewModel: BlockViewModel
    let onDismiss: () -> Void

    @State private var editing: Editing?
    @State private var headerHeight: CGFloat = 0
    @State private var formHeight: CGFloat = 0
    @State private var footerHeight: CGFloat = 0

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                BarAction(text: L("block_cancel"), action: onDismiss, color: .ink)
                    .frame(width: 72, alignment: .leading)
                Text(L("block_title"))
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
                    BrandSectionLabel(text: L("block_reason"))
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
                        PickerTile(label: L("block_date"), value: formatDateShort(viewModel.date)) { editing = .date }
                        PickerTile(label: L("block_from"), value: formatTime(viewModel.from)) { editing = .from }
                        PickerTile(label: L("block_to"), value: formatTime(viewModel.to)) { editing = .to }
                    }
                    HStack(spacing: 9) {
                        BrandChip(text: L("block_half_day"), selected: false, action: viewModel.halfDay, fill: true)
                        BrandChip(text: L("block_full_day"), selected: false, action: viewModel.fullDay, fill: true)
                    }

                    if !viewModel.unresolvedConflicts.isEmpty {
                        conflictsCard
                    }
                    FormErrorBanner(message: viewModel.saveError)
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 12)
                .measureHeight($formHeight)
            }
            .scrollBounceBehavior(.basedOnSize)

            // Fuori dallo scroll: il bottone resta sempre a vista in fondo.
            AccentButton(
                text: L("block_cta", formatTime(viewModel.from), formatTime(viewModel.to)),
                action: viewModel.save,
                enabled: viewModel.canSave,
                height: 56,
                corner: 16
            )
            .padding(.horizontal, 20)
            .padding(.bottom, 12)
            .measureHeight($footerHeight)
        }
        .background(Color.bone)
        .task { await viewModel.load() }
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
                        viewModel.setDate(date)
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

    private var conflictsCard: some View {
        AccentOutlinedCard {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 10) {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(Color.oliveWood)
                        .frame(width: 26, height: 26)
                        .overlay(Text("!").font(Typo.titleSmall).foregroundStyle(Color.bone))
                    Text(L("block_conflicts_count", viewModel.unresolvedConflicts.count))
                        .font(Typo.jost(15, weight: .medium))
                        .foregroundStyle(Color.ink)
                }
                ForEach(viewModel.unresolvedConflicts) { conflict in
                    Text(L(
                        "block_conflict_desc",
                        formatTime(conflict.time),
                        formatDurationLong(conflict.durationMinutes),
                        formatDateShort(conflict.date)
                    ))
                    .font(Typo.jost(12))
                    .foregroundStyle(Color.textMuted)
                    .padding(.top, 8)
                    HStack(spacing: 9) {
                        if let colleague = viewModel.colleagues.first {
                            Button {
                                viewModel.reassign(conflict.id, colleagueId: colleague.id)
                            } label: {
                                Text(L("block_reassign", String(colleague.name.split(separator: " ").first ?? "")))
                                    .font(Typo.titleSmall)
                                    .foregroundStyle(Color.bone)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 11)
                                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.ink))
                            }
                            .buttonStyle(.plain)
                        }
                        Button {
                            viewModel.proposeNewTime(conflict.id)
                        } label: {
                            Text(L("block_propose"))
                                .font(Typo.titleSmall)
                                .foregroundStyle(Color.ink)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 11)
                                .background(RoundedRectangle(cornerRadius: 10).fill(Color.stone))
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.top, 10)
                }
            }
            .padding(14)
        }
    }
}
