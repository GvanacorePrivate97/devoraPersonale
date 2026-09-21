import SwiftUI

struct AppointmentsScreen: View {
    @State var viewModel: AppointmentsViewModel
    let onBook: () -> Void
    let onRebook: (String) -> Void
    let onEdit: (String) -> Void

    @State private var tab = 0
    @State private var pendingCancel: String?

    var body: some View {
        VStack(spacing: 0) {
            DarkHeader(contentPadding: EdgeInsets(top: 8, leading: 20, bottom: 16, trailing: 20)) {
                Text(L("apts_title"))
                    .font(Typo.cormorant(30))
                    .foregroundStyle(Color.bone)
                SegmentedTabs(
                    options: [
                        L("apts_tab_upcoming", viewModel.upcoming.count),
                        L("apts_tab_past", viewModel.past.count),
                    ],
                    selectedIndex: tab,
                    onSelect: { tab = $0 }
                )
                .padding(.top, 14)
            }

            Loadable(state: viewModel.state, retry: { Task { await viewModel.load() } }) {
                list
            }
        }
        .background(Color.bone)
        .task { await viewModel.load() }
        .alert(L("apts_cancel_confirm_title"), isPresented: Binding(
            get: { pendingCancel != nil },
            set: { if !$0 { pendingCancel = nil } }
        )) {
            Button(L("apts_cancel_confirm_yes")) {
                if let id = pendingCancel { viewModel.cancel(id) }
                pendingCancel = nil
            }
            Button(L("apts_cancel_confirm_no"), role: .cancel) { pendingCancel = nil }
        } message: {
            if let id = pendingCancel, let apt = viewModel.upcoming.first(where: { $0.id == id }) {
                Text(L(
                    "apts_cancel_confirm_body",
                    formatDateShort(apt.date),
                    formatTime(apt.time),
                    viewModel.operators[apt.operatorId]?.name ?? ""
                ))
            }
        }
    }

    @ViewBuilder
    private var list: some View {
        VStack(spacing: 0) {
            // Un annullamento che non passa va detto sopra la lista, dove si e'
            // appena premuto.
            if let actionError = viewModel.actionError {
                InlineErrorBar(message: actionError, retry: { Task { await viewModel.load() } })
            }
            if tab == 0 && viewModel.upcoming.isEmpty && viewModel.waitlist.isEmpty {
                emptyUpcoming
            } else {
                ScrollView {
                    VStack(spacing: 11) {
                        if tab == 0 {
                            ForEach(viewModel.upcoming) { appointment in
                                UpcomingCard(
                                    appointment: appointment,
                                    viewModel: viewModel,
                                    onEdit: { onEdit(appointment.id) },
                                    onCancel: { pendingCancel = appointment.id }
                                )
                            }
                            if !viewModel.waitlist.isEmpty {
                                HStack(spacing: 10) {
                                    BrandSectionLabel(text: L("apts_waitlist_title"))
                                    Rectangle().fill(Color.stone).frame(height: 1)
                                    Text(L("apts_waitlist_slots", viewModel.waitlist.count))
                                        .font(Typo.jost(11))
                                        .foregroundStyle(Color.textMuted)
                                }
                                .padding(.top, 8)
                                ForEach(viewModel.waitlist) { entry in
                                    waitlistRow(entry)
                                }
                            }
                        } else {
                            ForEach(viewModel.past) { appointment in
                                pastRow(appointment)
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .padding(.bottom, 24)
                    .readableWidth()
                }
            }
        }
    }

    private func waitlistRow(_ entry: WaitlistEntry) -> some View {
        let first = entry.position == 1
        return HStack(spacing: 10) {
            Circle()
                .fill(first ? Color.oliveWood : Color.textMuted)
                .frame(width: 6, height: 6)
            VStack(alignment: .leading, spacing: 0) {
                Text("\(formatDateShort(entry.date)) · \(entry.time.map(formatTime) ?? L("apts_waitlist_any_time"))")
                    .font(Typo.titleSmall)
                    .foregroundStyle(first ? Color.bone : Color.ink)
                Text(
                    [
                        entry.operatorId.flatMap { viewModel.operators[$0]?.name } ?? L("apts_waitlist_any"),
                        L("apts_waitlist_status"),
                    ].joined(separator: " · ")
                )
                .font(Typo.jost(12))
                .foregroundStyle(first ? Color.bone : Color.textMuted)
            }
            Spacer()
            // La posizione si vede sempre e tutti possono lasciare la coda.
            statusPill(L("apts_waitlist_position", entry.position), accent: true)
            Button {
                viewModel.leaveWaitlist(entry.id)
            } label: {
                Text(L("apts_waitlist_leave"))
                    .font(Typo.titleSmall)
                    .foregroundStyle(Color.oliveWood)
                    .padding(6)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 15)
        .padding(.vertical, 14)
        .background(RoundedRectangle(cornerRadius: 16).fill(first ? Color.ink : Color.stone))
    }

    /// Stesso riquadro data dei prossimi; spento se la visita non c'è stata.
    private func pastRow(_ appointment: Appointment) -> some View {
        let cancelled = appointment.status == .cancelled || appointment.status == .noShow
        let detail: String? = cancelled
            ? L(
                appointment.status == .noShow
                    ? "apts_no_show"
                    : (appointment.cancelledBy == .salon ? "apts_cancelled_by_salon" : "apts_cancelled_by_client")
            )
            : viewModel.operators[appointment.operatorId]?.name
        return HStack(spacing: 12) {
            AppointmentDateBlock(date: appointment.date, style: cancelled ? .muted : .accent)
            VStack(alignment: .leading, spacing: 2) {
                Text(appointment.serviceIds.compactMap { viewModel.services[$0]?.name }.joined(separator: " + "))
                    .font(Typo.jost(14, weight: .medium))
                    .foregroundStyle(cancelled ? Color.textMuted : Color.ink)
                    .lineLimit(2)
                Text([formatTime(appointment.time), detail].compactMap { $0 }.joined(separator: " · "))
                .font(Typo.jost(12))
                .foregroundStyle(Color.textMuted)
                .lineLimit(1)
            }
            Spacer()
            if !cancelled {
                Button {
                    onRebook(appointment.id)
                } label: {
                    Text(L("apts_rebook"))
                        .font(Typo.jost(12, weight: .medium))
                        .foregroundStyle(Color.bone)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 10).fill(Color.ink))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: Radii.md).fill(Color.bone))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md)
                .strokeBorder(Color.stoneBorder, lineWidth: 1.5)
        )
    }

    private var emptyUpcoming: some View {
        ScrollView {
            VStack(spacing: 0) {
                RoundedRectangle(cornerRadius: 28)
                    .fill(Color.stone)
                    .frame(width: 96, height: 96)
                    .overlay(
                        Image(systemName: "calendar")
                            .font(.system(size: 36))
                            .foregroundStyle(Color.textMuted)
                    )
                    .padding(.top, 56)
                Text(L("apts_empty_title"))
                    .font(Typo.cormorant(28))
                    .foregroundStyle(Color.ink)
                    .multilineTextAlignment(.center)
                    .padding(.top, 26)
                Text(L("apts_empty_body"))
                    .font(Typo.bodyMedium)
                    .foregroundStyle(Color.textMuted)
                    .multilineTextAlignment(.center)
                    .padding(.top, 10)
                AccentButton(text: L("apts_empty_book"), action: onBook, height: 54, corner: 16)
                    .padding(.top, 24)
                if let last = viewModel.lastCompleted {
                    Button {
                        onRebook(last.id)
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 0) {
                                Text(L("apts_empty_rebook"))
                                    .font(Typo.titleSmall)
                                    .foregroundStyle(Color.ink)
                                Text(
                                    [
                                        last.serviceIds.compactMap { viewModel.services[$0]?.name }.joined(separator: " + "),
                                        viewModel.operators[last.operatorId]?.name,
                                    ].compactMap { $0 }.joined(separator: " · ")
                                )
                                .font(Typo.jost(12))
                                .foregroundStyle(Color.textMuted)
                                .lineLimit(1)
                            }
                            Spacer()
                            Image(systemName: "arrow.right")
                                .font(.system(size: 15))
                                .foregroundStyle(Color.ink)
                        }
                        .padding(.horizontal, 15)
                        .padding(.vertical, 14)
                        .background(RoundedRectangle(cornerRadius: Radii.md).fill(Color.bone))
                        .overlay(
                            RoundedRectangle(cornerRadius: Radii.md)
                                .strokeBorder(Color.stoneBorder, lineWidth: 1.5)
                        )
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 10)
                }
            }
            .padding(.horizontal, 28)
            .readableWidth()
        }
    }

    private func statusPill(_ text: String, accent: Bool) -> some View {
        Text(text.uppercased())
            .font(Typo.jost(11, weight: .medium))
            .kerning(1.3)
            .foregroundStyle(accent ? Color.bone : Color.ink)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(RoundedRectangle(cornerRadius: 6).fill(accent ? Color.oliveWood : Color.stone))
    }
}

private struct UpcomingCard: View {
    let appointment: Appointment
    let viewModel: AppointmentsViewModel
    let onEdit: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .top, spacing: 12) {
                AppointmentDateBlock(date: appointment.date, style: .accent)
                VStack(alignment: .leading, spacing: 4) {
                    // Ogni prenotazione è confermata appena fatta (§6.2): un
                    // badge che dice sempre la stessa cosa non informa.
                    Text(formatTime(appointment.time))
                        .font(Typo.jost(19, weight: .medium))
                        .foregroundStyle(Color.ink)
                    Text(appointment.serviceIds.compactMap { viewModel.services[$0]?.name }.joined(separator: " · "))
                        .font(Typo.jost(12))
                        .foregroundStyle(Color.ink)
                        .lineLimit(2)
                    HStack(spacing: 7) {
                        Circle()
                            .fill(Color.stone)
                            .frame(width: 22, height: 22)
                            .overlay(
                                Text(viewModel.operators[appointment.operatorId]?.initials ?? "")
                                    .font(Typo.cormorant(11, weight: .regular))
                                    .foregroundStyle(Color.ink)
                            )
                        Text(
                            [
                                viewModel.operators[appointment.operatorId]?.name,
                                formatDuration(appointment.durationMinutes),
                            ].compactMap { $0 }.joined(separator: " · ")
                        )
                        .font(Typo.jost(12))
                        .foregroundStyle(Color.ink)
                        .lineLimit(1)
                    }
                    .padding(.top, 2)
                }
            }
            .padding(14)
            Rectangle().fill(Color.stoneBorder).frame(height: 1)
            HStack(spacing: 0) {
                cardAction(L("apts_edit"), action: onEdit)
                Rectangle().fill(Color.stoneBorder).frame(width: 1)
                cardAction(L("apts_cancel"), action: onCancel)
            }
            .frame(height: 46)
        }
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.bone))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(Color.oliveWood, lineWidth: 1.5)
        )
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private func cardAction(_ text: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text)
                .font(Typo.titleSmall)
                .foregroundStyle(Color.ink)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .buttonStyle(.plain)
    }

    private func statusPill(_ text: String, accent: Bool) -> some View {
        Text(text.uppercased())
            .font(Typo.jost(11, weight: .medium))
            .kerning(1.3)
            .foregroundStyle(accent ? Color.bone : Color.ink)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(RoundedRectangle(cornerRadius: 6).fill(accent ? Color.oliveWood : Color.stone))
    }
}

/// Riquadro giorno/data/mese delle card appuntamento, uguale nei due tab.
private struct AppointmentDateBlock: View {
    enum Style { case accent, muted }

    let date: LocalDate
    let style: Style

    var body: some View {
        let parts = formatDateShort(date).split(separator: " ").map(String.init)
        VStack(spacing: 0) {
            Text((parts.first ?? "").uppercased())
                .font(Typo.jost(11, weight: .medium))
                .kerning(0.9)
            Text(parts.count > 1 ? parts[1] : "")
                .font(Typo.cormorant(20, weight: .regular))
            Text((parts.count > 2 ? parts[2] : "").uppercased())
                .font(Typo.jost(11, weight: .medium))
                .kerning(0.9)
        }
        .foregroundStyle(style == .accent ? Color.bone : Color.textMuted)
        .frame(width: 54, height: 62)
        .background(RoundedRectangle(cornerRadius: 16).fill(style == .accent ? Color.oliveWood : Color.bone))
    }
}
