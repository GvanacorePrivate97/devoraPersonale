import SwiftUI
import Observation

private let moveFeedbackSeconds: Double = 2.2
private let hourHeight = AgendaGrid.hourHeight
private let baseColumnWidth: CGFloat = 108

enum MoveResult {
    case moved, unavailable
}

@MainActor
@Observable
final class WeeklyAgendaViewModel {

    private let booking: BookingRepository
    private let blockRepo: TimeBlockRepository
    private let catalog: CatalogRepository
    private let crm: CrmRepository
    private let notificationsRepo: NotificationRepository

    var selectedDay: LocalDate = .today() {
        didSet {
            guard oldValue != selectedDay else { return }
            // Si ricarica solo cambiando settimana: scorrere i giorni dentro la
            // stessa settimana lavora sui dati gia' in mano.
            if weekStartOf(oldValue) != weekStart { Task { await loadWeek() } }
        }
    }

    var moveResult: MoveResult?

    let state = LoadState()
    private(set) var operators: [Operator] = []
    private(set) var appointments: [Appointment] = []
    private(set) var blocks: [TimeBlock] = []
    private(set) var services: [String: Service] = [:]
    private(set) var clients: [String: ClientRecord] = [:]
    private(set) var hasUnreadNotifications = false

    init(
        booking: BookingRepository, blocks: TimeBlockRepository, catalog: CatalogRepository,
        crm: CrmRepository, notifications: NotificationRepository
    ) {
        self.booking = booking
        self.blockRepo = blocks
        self.catalog = catalog
        self.crm = crm
        self.notificationsRepo = notifications
    }

    private func weekStartOf(_ day: LocalDate) -> LocalDate {
        day.minusDays(day.dayOfWeek.rawValue - 1)
    }

    var weekStart: LocalDate { weekStartOf(selectedDay) }

    func load() async {
        await state.run {
            let snapshot = try await catalog.catalog()
            operators = snapshot.operators
            services = Dictionary(uniqueKeysWithValues: snapshot.services.map { ($0.id, $0) })
            clients = Dictionary(
                uniqueKeysWithValues: try await crm.clients(query: "", segment: .tutti).map { ($0.id, $0) }
            )
            hasUnreadNotifications = try await notificationsRepo.feed().unreadCount > 0
            try await loadWeekThrowing()
        }
    }

    func loadWeek() async {
        await state.run { try await loadWeekThrowing() }
    }

    private func loadWeekThrowing() async throws {
        appointments = try await booking.appointmentsForWeek(weekStart)
        blocks = try await blockRepo.blocksForWeek(weekStart, operatorId: nil)
    }

    // Come l'agenda dell'operatore: si vedono anche i completati (in stone) e
    // i no-show (spenti, per poterli correggere); spariscono solo gli annullati.
    func appointmentsFor(_ operatorId: String) -> [Appointment] {
        appointments.filter {
            $0.operatorId == operatorId && $0.date == selectedDay && $0.status != .cancelled
        }
    }

    func blocksFor(_ operatorId: String) -> [TimeBlock] {
        blocks.filter { $0.operatorId == operatorId && $0.date == selectedDay }
    }

    /// A tap on the grid can start a booking only where the operator isn't blocked.
    func isBlocked(_ operatorId: String, at time: LocalTime) -> Bool {
        blocksFor(operatorId).contains { $0.range.start <= time && time < $0.range.end }
    }

    /// True when the operator is on shift at `time` (blocks aside).
    func onShift(_ operatorId: String, at time: LocalTime) -> Bool {
        (operators.first { $0.id == operatorId }?.weeklyHours[selectedDay.dayOfWeek] ?? [])
            .contains { time >= $0.start && time < $0.end }
    }

    /// True when the operator is on shift for the whole duration from `time`.
    func worksAt(_ op: Operator, time: LocalTime, durationMinutes: Int) -> Bool {
        let end = time.plusMinutes(durationMinutes)
        return (op.weeklyHours[selectedDay.dayOfWeek] ?? [])
            .contains { time >= $0.start && end <= $0.end }
    }

    /// The selected day's stretches where this operator is NOT on shift, within
    /// the agenda rail: shaded in the grid so an empty column doesn't read as free.
    func offDutyRanges(_ operatorId: String) -> [TimeRange] {
        let rail = TimeRange(
            LocalTime(AgendaGrid.dayStartHour, 0),
            LocalTime(AgendaGrid.dayEndHour, 0)
        )
        let working = (operators.first { $0.id == operatorId }?
            .weeklyHours[selectedDay.dayOfWeek] ?? [])
            .compactMap { $0.intersect(rail) }
            .sorted { $0.start < $1.start }
        var gaps: [TimeRange] = []
        var cursor = rail.start
        for shift in working {
            if shift.start > cursor { gaps.append(TimeRange(cursor, shift.start)) }
            if shift.end > cursor { cursor = shift.end }
        }
        if cursor < rail.end { gaps.append(TimeRange(cursor, rail.end)) }
        return gaps
    }

    /// Drag & drop: move an appointment to a new time and/or operator column.
    func move(_ appointmentId: String, newOperatorId: String, newTime: LocalTime) {
        Task {
            let result = await booking.reschedule(appointmentId, newStart: selectedDay.atTime(newTime), newOperatorId: newOperatorId)
            switch result {
            case .success:
                moveResult = .moved
                await loadWeek()
            case .failure:
                moveResult = .unavailable
                // La griglia torna com'era: la card e' rimasta dov'e'.
                await loadWeek()
            }
        }
    }

    /// "Non si è presentato": il cliente riceve una notifica.
    func markNoShow(_ appointmentId: String) {
        Task {
            _ = await booking.markNoShow(appointmentId)
            await loadWeek()
        }
    }

    /// Completato a mano: prima della fine, o per correggere un no-show.
    func markCompleted(_ appointmentId: String) {
        Task {
            _ = await booking.markCompleted(appointmentId)
            await loadWeek()
        }
    }

    /// Annullato dal salone, non dal cliente.
    func cancel(_ appointmentId: String) {
        Task {
            _ = await booking.cancel(appointmentId, by: .salon)
            await loadWeek()
        }
    }
}

/// An appointment being dragged: where it started, and how far the finger moved.
private struct DragState {
    let appointmentId: String
    let operatorIndex: Int
    let startMinutes: Int
    let durationMinutes: Int
    var offset: CGSize = .zero
}

extension ManualBookingViewModel: Identifiable {}

struct WeeklyAgendaScreen: View {
    @Bindable var viewModel: WeeklyAgendaViewModel
    let onNotifications: () -> Void

    // Stato del gesto: si azzera da solo quando il gesto finisce o viene
    // interrotto (es. dall'apertura del foglio), così una card non resta nera.
    @GestureState private var drag: DragState?
    @State private var bookingSheet: ManualBookingViewModel?
    @State private var blockSheetOpen = false
    @State private var detailFor: String?
    // La striscia degli operatori segue lo scroll orizzontale della griglia,
    // così ogni card resta sotto la sua corsia.
    @State private var hOffset: CGFloat = 0
    @State private var gridWidth: CGFloat = 0
    @Environment(\.container) private var container

    /// Le corsie riempiono lo schermo quando ci stanno tutte (iPad, pochi
    /// operatori); altrimenti larghezza fissa e si scorre in orizzontale.
    private var columnWidth: CGFloat {
        let count = viewModel.operators.count
        guard count > 0, gridWidth > 0 else { return baseColumnWidth }
        return max(baseColumnWidth, (gridWidth - AgendaGrid.gutterWidth) / CGFloat(count))
    }

    private var laneViewport: CGFloat { max(gridWidth - AgendaGrid.gutterWidth, 0) }
    private var lanesContentWidth: CGFloat { columnWidth * CGFloat(viewModel.operators.count) }
    private var canScrollLanesBack: Bool { hOffset < -1 }
    private var canScrollLanesForward: Bool { -hOffset + laneViewport < lanesContentWidth - 1 }

    var body: some View {
        VStack(spacing: 0) {
            // L'agenda usa tutta la larghezza del tablet: la banda la segue,
            // così data e frecce restano allineate alla griglia.
            DarkHeader(
                contentPadding: EdgeInsets(top: 8, leading: 20, bottom: 16, trailing: 20),
                fullWidthContent: true
            ) {
                // Stessa impaginazione dell'agenda operatore: chi sono / cosa
                // guardo in alto, la navigazione del giorno sotto.
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(L("week_title"))
                            .font(Typo.headlineMedium)
                            .foregroundStyle(Color.bone)
                            .lineLimit(1)
                            .minimumScaleFactor(0.75)
                        Text(L(
                            "week_subtitle",
                            viewModel.operators.count,
                            viewModel.appointments.filter { $0.date == viewModel.selectedDay && $0.isActive }.count
                        ))
                        .font(Typo.bodySmall)
                        .foregroundStyle(Color.onDarkMuted)
                    }
                    Spacer()
                    NotificationBell(
                        hasUnread: viewModel.hasUnreadNotifications,
                        action: onNotifications
                    )
                    .padding(.leading, 4)
                }
                AgendaDayBar(
                    selected: viewModel.selectedDay,
                    onSelect: { viewModel.selectedDay = $0 }
                )
                .padding(.top, 16)
            }

            if let error = viewModel.state.error {
                InlineErrorBar(message: error.displayMessage, retry: { Task { await viewModel.load() } })
            }

            ZStack(alignment: .bottom) {
                grid
                // Durante il trascinamento i bottoni si tolgono di mezzo: la
                // card deve poter atterrare anche nell'angolo che occupano.
                if drag == nil {
                    fabs
                }
                feedback
            }
            .frame(maxHeight: .infinity)
        }
        .background(Color.bone)
        .task { await viewModel.load() }
        .sheet(item: $bookingSheet) { sheetViewModel in
            ManualBookingScreen(
                viewModel: sheetViewModel,
                onDismiss: {
                    bookingSheet = nil
                    Task { await viewModel.loadWeek() }
                }
            )
        }
        .sheet(isPresented: $blockSheetOpen) {
            AdminBlockSheet(
                viewModel: AdminBlockViewModel(
                    blocks: container.timeBlocks, booking: container.booking, catalog: container.catalog
                ),
                onDismiss: {
                    blockSheetOpen = false
                    Task { await viewModel.loadWeek() }
                }
            )
        }
        .anchoredBottomSheet(item: Binding(
            get: { detailFor.map(SheetId.init) },
            set: { detailFor = $0?.id }
        )) { sheet, close in
            AppointmentActionsSheet(
                viewModel: viewModel,
                appointmentId: sheet.id,
                onEditAppointment: {
                    close()
                    if let appointment = viewModel.appointments.first(where: { $0.id == sheet.id }) {
                        openEdit(appointment)
                    }
                },
                onCancelAppointment: {
                    viewModel.cancel(sheet.id)
                    close()
                },
                onMarkNoShow: { viewModel.markNoShow(sheet.id) },
                onMarkCompleted: { viewModel.markCompleted(sheet.id) },
                onDismiss: close
            )
        }
        .onChange(of: viewModel.moveResult != nil) {
            guard viewModel.moveResult != nil else { return }
            Task {
                try? await Task.sleep(for: .seconds(moveFeedbackSeconds))
                viewModel.moveResult = nil
            }
        }
    }

    private struct SheetId: Identifiable {
        let id: String
    }

    private var grid: some View {
        ScrollViewReader { lanes in
            VStack(spacing: 0) {
                // La striscia non scorre da sola: segue la griglia, offset alla mano.
                ZStack {
                    HStack(spacing: 0) {
                        Color.clear.frame(width: AgendaGrid.gutterWidth, height: 1)
                        HStack(spacing: 0) {
                            ForEach(viewModel.operators) { op in
                                VStack(spacing: 4) {
                                    Circle()
                                        .fill(Color.stone)
                                        .frame(width: 32, height: 32)
                                        .overlay(
                                            Text(op.initials)
                                                .font(Typo.cormorant(12, weight: .regular))
                                                .foregroundStyle(Color.ink)
                                        )
                                    Text(String(op.name.split(separator: " ").first ?? ""))
                                        .font(Typo.jost(12))
                                        .foregroundStyle(Color.ink)
                                        .lineLimit(1)
                                }
                                .frame(width: columnWidth)
                            }
                        }
                        .offset(x: hOffset)
                        .frame(width: laneViewport, alignment: .leading)
                        .clipped()
                    }
                    .padding(.vertical, 10)
                    // Altri operatori fuori dallo schermo: la freccia lo dice e ci porta.
                    if canScrollLanesBack {
                        scrollHint("chevron.left", alignment: .leading) {
                            scrollLanes(by: -1, with: lanes)
                        }
                    }
                    if canScrollLanesForward {
                        scrollHint("chevron.right", alignment: .trailing) {
                            scrollLanes(by: 1, with: lanes)
                        }
                    }
                }
                Rectangle().fill(Color.stoneBorder).frame(height: 1)
                ScrollView(.vertical, showsIndicators: false) {
                    HStack(alignment: .top, spacing: 0) {
                        AgendaHourLabels()
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 0) {
                                ForEach(Array(viewModel.operators.enumerated()), id: \.element.id) { index, op in
                                    operatorColumn(index: index, operatorId: op.id)
                                        .id(index)
                                }
                            }
                            .background(
                                GeometryReader { proxy in
                                    let minX = proxy.frame(in: .named("agendaLanes")).minX
                                    Color.clear
                                        .onAppear { hOffset = minX }
                                        .onChange(of: minX) { _, new in hOffset = new }
                                }
                            )
                        }
                        .coordinateSpace(name: "agendaLanes")
                    }
                }
            }
            .background(
                GeometryReader { proxy in
                    Color.clear
                        .onAppear { gridWidth = proxy.size.width }
                        .onChange(of: proxy.size.width) { _, new in gridWidth = new }
                }
            )
        }
    }

    private func scrollLanes(by delta: Int, with lanes: ScrollViewProxy) {
        let current = Int((-hOffset / columnWidth).rounded())
        let target = min(max(current + delta, 0), max(viewModel.operators.count - 1, 0))
        withAnimation { lanes.scrollTo(target, anchor: .leading) }
    }

    private func scrollHint(_ systemImage: String, alignment: Alignment, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Circle()
                .fill(Color.ink.opacity(0.85))
                .frame(width: 30, height: 30)
                .overlay(
                    Image(systemName: systemImage)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.bone)
                )
        }
        .buttonStyle(.plain)
        .frame(maxWidth: .infinity, alignment: alignment)
        .padding(.horizontal, 6)
    }

    private func operatorColumn(index: Int, operatorId: String) -> some View {
        let offRanges = viewModel.offDutyRanges(operatorId)
        let wholeDayOff = offRanges.count == 1 &&
            offRanges[0].start == LocalTime(AgendaGrid.dayStartHour, 0) &&
            offRanges[0].end == LocalTime(AgendaGrid.dayEndHour, 0)
        // In ordine di orario: se una card corta sborda di qualche punto finisce
        // sotto quella dopo, non sopra il suo testo.
        let ordered = viewModel.appointmentsFor(operatorId)
            .sorted { $0.time.minutesOfDay < $1.time.minutesOfDay }
        return ZStack(alignment: .topLeading) {
            // Tap su uno spazio libero: nuova prenotazione con operatore e ora già scelti.
            AgendaHourLines()
                .contentShape(Rectangle())
                .onTapGesture { location in
                    // Niente prenotazioni su blocchi o fuori turno.
                    guard let time = AgendaGrid.time(atY: location.y),
                          !viewModel.isBlocked(operatorId, at: time),
                          viewModel.onShift(operatorId, at: time) else { return }
                    openBooking(operatorId: operatorId, time: time)
                }
            // Fuori turno e blocchi (pausa, ferie…): stessa fascia grigia, etichetta
            // centrata. Una colonna vuota non deve sembrare libera.
            ForEach(Array(offRanges.enumerated()), id: \.offset) { _, range in
                AgendaUnavailableBand(
                    range: range,
                    label: wholeDayOff ? L("week_off_duty") : nil,
                    width: columnWidth
                )
            }
            ForEach(viewModel.blocksFor(operatorId)) { block in
                AgendaUnavailableBand(
                    range: block.range,
                    label: block.label ?? blockReasonLabel(block.reason),
                    width: columnWidth
                )
            }
            ForEach(ordered) { appointment in
                appointmentCard(appointment, columnIndex: index)
            }
        }
        .frame(width: columnWidth, height: AgendaGrid.totalHeight, alignment: .topLeading)
    }

    /// Minuti liberi fra la fine di `appointment` e il prossimo impegno della
    /// colonna: e' lo spazio che una card corta puo' prendersi per restare intera.
    private func freeMinutesAfter(_ appointment: Appointment, operatorId: String) -> Int {
        let end = AgendaGrid.minutesFromStart(appointment.time) + appointment.durationMinutes
        var busyStarts = viewModel.appointmentsFor(operatorId)
            .filter { $0.id != appointment.id }
            .map { AgendaGrid.minutesFromStart($0.time) }
        busyStarts += viewModel.blocksFor(operatorId).map { AgendaGrid.minutesFromStart($0.range.start) }
        busyStarts += viewModel.offDutyRanges(operatorId).map { AgendaGrid.minutesFromStart($0.start) }
        return AgendaGrid.freeMinutesAfter(endMinutes: end, busyStarts: busyStarts)
    }

    private func appointmentCard(_ appointment: Appointment, columnIndex: Int) -> some View {
        let top = minutesFromStart(appointment.time)
        let dragging = drag?.appointmentId == appointment.id
        let dragOffset = dragging ? (drag?.offset ?? .zero) : .zero
        let client = viewModel.clients[appointment.clientId]
        // Nero solo mentre si trascina; per il resto olive attivo, stone
        // completato, spento con il filo se il cliente non si è presentato.
        let completed = appointment.status == .completed
        let noShow = appointment.status == .noShow
        let background: Color = dragging ? .ink : (noShow ? .bone : (completed ? .stone : .oliveWood))
        let foreground: Color = noShow ? .textMuted : (completed ? .ink : .bone)
        // La durata detta l'altezza, ma una card corta si allarga nel tempo
        // libero che ha davanti: anche dieci minuti restano leggibili per intero.
        let height = AgendaGrid.cardHeight(
            minutes: appointment.durationMinutes,
            freeMinutesAfter: freeMinutesAfter(appointment, operatorId: appointment.operatorId)
        )
        let serviceLines = AgendaGrid.serviceLines(cardHeight: height)
        return VStack(alignment: .leading, spacing: 2) {
            Text([client?.firstName.first.map { "\($0)." }, client?.lastName].compactMap { $0 }.joined(separator: " "))
                .font(Typo.jost(11, weight: .medium))
                .lineLimit(1)
            if serviceLines > 0 {
                Text(noShow ? L("week_status_no_show") : appointment.serviceIds.compactMap { viewModel.services[$0]?.name }.joined(separator: " + "))
                    .font(Typo.jost(10, weight: .medium))
                    .lineLimit(serviceLines)
            }
        }
        .foregroundStyle(foreground)
        .padding(.horizontal, 8)
        .padding(.vertical, 5)
        .frame(width: columnWidth - 6, alignment: .leading)
        .frame(minHeight: height, alignment: serviceLines == 0 ? .leading : .topLeading)
        .background(RoundedRectangle(cornerRadius: 10).fill(background))
        .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(noShow ? Color.stoneBorder : .clear, lineWidth: 1))
        .offset(x: 3 + dragOffset.width, y: hourHeight * CGFloat(top) / 60 + 2 + dragOffset.height)
        .zIndex(dragging ? 1 : 0)
        .opacity(dragging ? 0.9 : 1)
        .onTapGesture {
            var transaction = Transaction()
            transaction.disablesAnimations = true
            withTransaction(transaction) { detailFor = appointment.id }
        }
        // Un appuntamento concluso non si sposta più.
        .gesture(
            LongPressGesture(minimumDuration: 0.3)
                .sequenced(before: DragGesture())
                .updating($drag) { value, state, _ in
                    // Nero dal momento in cui la card è "presa", fino al rilascio.
                    guard case .second(true, let dragValue) = value else { return }
                    var next = state ?? DragState(
                        appointmentId: appointment.id,
                        operatorIndex: columnIndex,
                        startMinutes: minutesFromStart(appointment.time),
                        durationMinutes: appointment.durationMinutes
                    )
                    if let dragValue {
                        next.offset = CGSize(width: dragValue.translation.width, height: dragValue.translation.height)
                    }
                    state = next
                }
                .onEnded { value in
                    guard case .second(true, let dragValue?) = value else { return }
                    var final = DragState(
                        appointmentId: appointment.id,
                        operatorIndex: columnIndex,
                        startMinutes: minutesFromStart(appointment.time),
                        durationMinutes: appointment.durationMinutes
                    )
                    final.offset = CGSize(width: dragValue.translation.width, height: dragValue.translation.height)
                    if let target = dropTarget(final) {
                        viewModel.move(
                            appointment.id,
                            newOperatorId: viewModel.operators[target.index].id,
                            newTime: target.time
                        )
                    }
                },
            including: appointment.isActive ? .all : .subviews
        )
    }

    /// Turns the finger travel into a column and a time, snapped to the grid.
    /// Nil when the drop would fall outside the day or the operator strip.
    private func dropTarget(_ drag: DragState) -> (index: Int, time: LocalTime)? {
        let columnDelta = Int((drag.offset.width / columnWidth).rounded())
        let targetIndex = drag.operatorIndex + columnDelta
        guard (0..<viewModel.operators.count).contains(targetIndex) else { return nil }

        let minutesDelta = Int((drag.offset.height / hourHeight * 60).rounded())
        let raw = drag.startMinutes + minutesDelta
        let snap = AgendaGrid.snapMinutes
        let snapped = Int((Double(raw) / Double(snap)).rounded()) * snap
        let lastStart = (AgendaGrid.dayEndHour - AgendaGrid.dayStartHour) * 60 - drag.durationMinutes
        guard snapped >= 0, snapped <= lastStart else { return nil }
        return (targetIndex, LocalTime(AgendaGrid.dayStartHour, 0).plusMinutes(snapped))
    }

    private func openBooking(operatorId: String?, time: LocalTime?) {
        bookingSheet = ManualBookingViewModel(
            booking: container.booking, catalog: container.catalog, crm: container.crm,
            operatorId: operatorId, date: viewModel.selectedDay, time: time
        )
    }

    /// "Modifica" dal foglio: il modulo manuale parte compilato con l'appuntamento.
    private func openEdit(_ appointment: Appointment) {
        bookingSheet = ManualBookingViewModel(
            booking: container.booking, catalog: container.catalog, crm: container.crm,
            editAppointment: appointment, editClient: viewModel.clients[appointment.clientId]
        )
    }

    /// Le stesse due azioni dell'agenda operatore, con le stesse parole: due
    /// tondi senza etichetta lasciavano indovinare quale fosse quale, e le due
    /// agende non si somigliavano più.
    private var fabs: some View {
        HStack(spacing: 10) {
            Button {
                blockSheetOpen = true
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "calendar.badge.minus").font(.system(size: 16))
                    Text(L("week_new_block")).font(Typo.jost(14, weight: .medium)).lineLimit(1)
                }
                .foregroundStyle(Color.ink)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(RoundedRectangle(cornerRadius: Radii.md).fill(Color.bone))
                .overlay(RoundedRectangle(cornerRadius: Radii.md).strokeBorder(Color.ink, lineWidth: 1.5))
            }
            .buttonStyle(.plain)
            Button {
                openBooking(operatorId: nil, time: nil)
            } label: {
                // Nero, non oliva: i pulsanti galleggiano sopra le card
                // dell'agenda, che sono oliva — un'azione dello stesso colore
                // di ciò che copre sparisce.
                HStack(spacing: 6) {
                    Image(systemName: "plus").font(.system(size: 16))
                    Text(L("week_new_booking")).font(Typo.jost(14, weight: .medium)).lineLimit(1)
                }
                .foregroundStyle(Color.bone)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(RoundedRectangle(cornerRadius: Radii.md).fill(Color.ink))
                .shadow(color: Color.ink.opacity(0.22), radius: 10, y: 4)
            }
            .buttonStyle(.plain)
            .layoutPriority(1)
        }
        .padding(20)
    }

    @ViewBuilder
    private var feedback: some View {
        if let current = drag {
            // While dragging, spell out where the card would land.
            let target = dropTarget(current)
            snackbar(
                target.map { found in
                    let op = viewModel.operators[found.index]
                    let firstName = String(op.name.split(separator: " ").first ?? "")
                    // Fuori turno lo si dice subito, senza aspettare il rifiuto.
                    return viewModel.worksAt(op, time: found.time, durationMinutes: current.durationMinutes)
                        ? L("week_drag_target", firstName, formatTime(found.time))
                        : L("week_drag_target_off", firstName, formatTime(found.time))
                } ?? L("week_drag_out")
            )
        } else if let move = viewModel.moveResult {
            snackbar(L(move == .moved ? "week_moved" : "week_move_error"))
        }
    }

    private func snackbar(_ text: String) -> some View {
        Text(text)
            .font(Typo.bodyMedium)
            .foregroundStyle(Color.bone)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color.ink))
            .padding(16)
    }

}

private func minutesFromStart(_ time: LocalTime) -> Int {
    AgendaGrid.minutesFromStart(time)
}
