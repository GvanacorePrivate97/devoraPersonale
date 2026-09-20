import SwiftUI
import Observation

@MainActor
@Observable
final class AgendaViewModel {

    private let auth: AuthRepository
    private let booking: BookingRepository
    private let blocks: TimeBlockRepository
    private let catalog: CatalogRepository
    private let crm: CrmRepository
    private let notificationsRepo: NotificationRepository

    var selectedDate: LocalDate = .today() {
        didSet {
            guard oldValue != selectedDate else { return }
            Task { await loadDay() }
        }
    }

    let state = LoadState()

    private(set) var user: User?
    private(set) var op: Operator?
    private(set) var services: [String: Service] = [:]
    private(set) var clients: [String: ClientRecord] = [:]
    /// Gli appuntamenti del giorno per l'operatore che ha fatto l'accesso,
    /// completati compresi. Il server serve comunque solo la propria poltrona.
    private(set) var appointments: [Appointment] = []
    private(set) var dayBlocks: [TimeBlock] = []
    private(set) var hasUnreadNotifications = false

    init(
        auth: AuthRepository, booking: BookingRepository, blocks: TimeBlockRepository,
        catalog: CatalogRepository, crm: CrmRepository, notifications: NotificationRepository
    ) {
        self.auth = auth
        self.booking = booking
        self.blocks = blocks
        self.catalog = catalog
        self.crm = crm
        self.notificationsRepo = notifications
    }

    func load() async {
        await state.run {
            let me = try await auth.currentUser()
            user = me
            let snapshot = try await catalog.catalog()
            op = snapshot.operators.first { $0.id == me?.operatorId }
            services = Dictionary(uniqueKeysWithValues: snapshot.services.map { ($0.id, $0) })
            clients = Dictionary(
                uniqueKeysWithValues: try await crm.clients(query: "", segment: .tutti).map { ($0.id, $0) }
            )
            hasUnreadNotifications = try await notificationsRepo.feed().unreadCount > 0
            try await loadDayThrowing()
        }
    }

    /// Cambio giorno: si ricarica solo l'agenda, non tutto il resto.
    func loadDay() async {
        await state.run { try await loadDayThrowing() }
    }

    private func loadDayThrowing() async throws {
        let operatorId = user?.operatorId
        appointments = try await booking.appointmentsForOperator(operatorId, date: selectedDate)
            // Anche i no-show, spenti: da qui si aprono per correggerli.
            .filter { $0.status != .cancelled }
        dayBlocks = try await blocks.blocksForOperator(operatorId, date: selectedDate)
    }

    /// Un tap sulla griglia apre una prenotazione solo dove l'operatore e' libero.
    func isBlocked(at time: LocalTime) -> Bool {
        dayBlocks.contains { $0.range.start <= time && time < $0.range.end }
    }

    /// True when the signed-in operator is on shift at `time`.
    func onShift(at time: LocalTime) -> Bool {
        (op?.weeklyHours[selectedDate.dayOfWeek] ?? [])
            .contains { time >= $0.start && time < $0.end }
    }
}

extension StaffBookingViewModel: Identifiable {}

struct AgendaScreen: View {
    @Bindable var viewModel: AgendaViewModel
    let onAppointment: (String) -> Void
    let onProfile: () -> Void
    let onNotifications: () -> Void

    @State private var bookingSheet: StaffBookingViewModel?
    @State private var blockSheetOpen = false
    @Environment(\.container) private var container

    var body: some View {
        VStack(spacing: 0) {
            DarkHeader(contentPadding: EdgeInsets(top: 8, leading: 20, bottom: 14, trailing: 20)) {
                HStack(spacing: 12) {
                    // Le iniziali (o la foto) portano al profilo, dove la foto si cambia.
                    ProfileAvatar(
                        initials: viewModel.op?.initials ?? "",
                        photoPath: viewModel.user?.avatarPath,
                        size: 44,
                        corner: 14,
                        onTap: onProfile
                    )
                    VStack(alignment: .leading, spacing: 0) {
                        Text(viewModel.op?.name ?? "")
                            .font(Typo.jost(17, weight: .medium))
                            .foregroundStyle(Color.bone)
                        Text(viewModel.op?.title ?? "")
                            .font(Typo.jost(12))
                            .foregroundStyle(Color.bone)
                    }
                    Spacer()
                    NotificationBell(hasUnread: viewModel.hasUnreadNotifications, action: onNotifications)
                }
                weekStrip
                    .padding(.top, 14)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(formatDateLong(viewModel.selectedDate).capitalizedFirst)
                    .font(Typo.cormorant(24))
                    .foregroundStyle(Color.ink)
                if viewModel.appointments.isEmpty {
                    Text(L("staff_no_appointments"))
                        .font(Typo.jost(12))
                        .foregroundStyle(Color.textMuted)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 10)
            .readableWidth()

            if let error = viewModel.state.error {
                InlineErrorBar(message: error.displayMessage, retry: { Task { await viewModel.load() } })
            }

            ZStack(alignment: .bottom) {
                // La griglia c'è sempre, anche a giornata vuota: un tap su un
                // orario libero apre la prenotazione a quell'ora.
                ScrollView {
                    HStack(alignment: .top, spacing: 0) {
                        AgendaHourLabels()
                        dayColumn
                    }
                    .padding(.leading, 12)
                    .padding(.trailing, 20)
                    .padding(.top, 6)
                    .padding(.bottom, 96)
                    .readableWidth()
                }
                HStack(spacing: 10) {
                    Button {
                        blockSheetOpen = true
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "plus").font(.system(size: 15))
                            Text(L("staff_block_fab")).font(Typo.jost(14, weight: .medium)).lineLimit(1)
                        }
                        .foregroundStyle(Color.ink)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color.bone))
                        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.stoneBorder, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    Button {
                        openBooking(time: nil)
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "plus").font(.system(size: 15))
                            Text(L("staff_booking_fab")).font(Typo.jost(14, weight: .medium)).lineLimit(1)
                        }
                        .foregroundStyle(Color.bone)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color.oliveWood))
                    }
                    .buttonStyle(.plain)
                }
                .padding(20)
                .readableWidth()
            }
        }
        .background(Color.bone)
        .task { await viewModel.load() }
        .sheet(item: $bookingSheet) { sheetViewModel in
            StaffBookingScreen(
                viewModel: sheetViewModel,
                onDismiss: {
                    bookingSheet = nil
                    Task { await viewModel.load() }
                }
            )
        }
        .sheet(isPresented: $blockSheetOpen) {
            BlockSheet(
                viewModel: BlockViewModel(
                    auth: container.auth, blocks: container.timeBlocks,
                    booking: container.booking, catalog: container.catalog
                ),
                onDismiss: {
                    blockSheetOpen = false
                    Task { await viewModel.load() }
                }
            )
        }
    }

    private func openBooking(time: LocalTime?) {
        bookingSheet = StaffBookingViewModel(
            auth: container.auth, booking: container.booking,
            catalog: container.catalog, crm: container.crm,
            date: viewModel.selectedDate, time: time
        )
    }

    private var dayColumn: some View {
        ZStack(alignment: .topLeading) {
            AgendaHourLines()
                .contentShape(Rectangle())
                .onTapGesture { location in
                    // Niente prenotazioni su blocchi o fuori turno.
                    guard let time = AgendaGrid.time(atY: location.y),
                          !viewModel.isBlocked(at: time),
                          viewModel.onShift(at: time) else { return }
                    openBooking(time: time)
                }
            ForEach(viewModel.dayBlocks) { block in
                blockCard(block)
            }
            ForEach(viewModel.appointments) { appointment in
                bookingCard(appointment)
            }
        }
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .frame(height: AgendaGrid.totalHeight, alignment: .topLeading)
    }

    private var weekStrip: some View {
        let start = viewModel.selectedDate.minusDays(2)
        return HStack(spacing: 6) {
            ForEach(0..<6, id: \.self) { offset in
                let day = start.plusDays(offset)
                let isSelected = day == viewModel.selectedDate
                let isToday = day == LocalDate.today()
                Button {
                    viewModel.selectedDate = day
                } label: {
                    VStack(spacing: 2) {
                        Text(formatDateShort(day).prefix(3).uppercased())
                            .font(Typo.jost(9, weight: .medium))
                            .kerning(0.9)
                        Text("\(day.day)")
                            .font(Typo.cormorant(19, weight: .regular))
                    }
                    .foregroundStyle(Color.bone)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 9)
                    .background(
                        RoundedRectangle(cornerRadius: 13)
                            .fill(isSelected ? Color.oliveWood : (isToday ? Color.bone.opacity(0.12) : Color.clear))
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func bookingCard(_ appointment: Appointment) -> some View {
        let inProgress = appointment.status == .inProgress
        let completed = appointment.status == .completed
        let noShow = appointment.status == .noShow
        // Olive anche in corso: il nero in agenda e' solo il feedback del
        // trascinamento. Il no-show è spento, con il filo, come dal titolare.
        let background: Color = noShow ? .bone : (completed ? .stone : .oliveWood)
        let foreground: Color = noShow ? .textMuted : (completed ? .ink : .bone)
        let height = AgendaGrid.height(minutes: appointment.durationMinutes) - 4
        // Sotto la mezz'ora la card ha spazio per una riga sola.
        let compact = height < 40
        let client = viewModel.clients[appointment.clientId]
        return Button {
            onAppointment(appointment.id)
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text([client?.firstName, client?.lastName].compactMap { $0 }.joined(separator: " "))
                        .font(Typo.jost(13, weight: .medium))
                        .lineLimit(1)
                    Spacer(minLength: 4)
                    Text(
                        inProgress
                            ? L("staff_in_progress")
                            : "\(formatTime(appointment.time)) – \(formatTime(appointment.time.plusMinutes(appointment.durationMinutes)))"
                    )
                    .font(Typo.jost(11))
                    .lineLimit(1)
                }
                if !compact {
                    Text(
                        noShow
                            ? L("apt_detail_no_show")
                            : appointment.serviceIds.compactMap { viewModel.services[$0]?.name }.joined(separator: " + ") +
                                (appointment.channel == .walkIn ? " · \(L("staff_walk_in"))" : "")
                    )
                    .font(Typo.jost(11))
                    .lineLimit(2)
                }
            }
            .foregroundStyle(foreground)
            .padding(.horizontal, 10)
            .padding(.vertical, compact ? 2 : 8)
            .frame(maxWidth: .infinity, alignment: .topLeading)
            .frame(height: height, alignment: compact ? .leading : .topLeading)
            .background(RoundedRectangle(cornerRadius: 10).fill(background))
            .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(noShow ? Color.stoneBorder : .clear, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 3)
        .offset(y: AgendaGrid.y(for: appointment.time) + 2)
    }

    private func blockCard(_ block: TimeBlock) -> some View {
        AgendaUnavailableBand(range: block.range, label: block.label ?? blockReasonLabel(block.reason))
    }
}

func blockReasonLabel(_ reason: BlockReason) -> String {
    switch reason {
    case .permesso: L("block_reason_permesso")
    case .pausa: L("block_reason_pausa")
    case .ferie: L("block_reason_ferie")
    case .corso: L("block_reason_corso")
    }
}
