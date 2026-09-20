import SwiftUI

private let cardCorner: CGFloat = 22
private let buttonCorner: CGFloat = 16
private let tileCorner: CGFloat = 18

struct ClientHomeScreen: View {
    @State var viewModel: ClientHomeViewModel
    let onBook: () -> Void
    let onRebook: (String) -> Void
    let onQuickSlot: (QuickSlot) -> Void
    let onHistory: () -> Void
    let onNotifications: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                header
                Loadable(state: viewModel.state, retry: { Task { await viewModel.load() } }) {
                    content
                }
                Spacer(minLength: 24)
            }
        }
        .background(Color.bone)
        .inkStatusBarBackdrop()
        .task { await viewModel.load() }
    }

    private var content: some View {
        VStack(spacing: 11) {
                    if let next = viewModel.nextAppointment {
                        NextAppointmentCard(viewModel: viewModel, next: next)
                    } else {
                        noAppointmentCard
                    }

                    // Stessa larghezza di quando divideva la riga con la lista d'attesa.
                    AccentButton(
                        text: L("client_home_book_now"),
                        action: onBook,
                        height: 54,
                        corner: buttonCorner,
                        leadingSystemImage: "plus"
                    )
                    .containerRelativeFrame(.horizontal) { width, _ in width * 0.58 }

                    if !viewModel.quickSlots.isEmpty {
                        quickSlotsSection
                    }

            if let last = viewModel.lastCompleted {
                rebookSection(last)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 16)
        .readableWidth()
    }

    private var header: some View {
        DarkHeader(
            roundedBottom: true,
            contentPadding: EdgeInsets(top: 6, leading: 20, bottom: 26, trailing: 20)
        ) {
            HStack {
                LogoBadge(size: 42, corner: 13)
                Spacer()
                NotificationBell(hasUnread: viewModel.unreadCount > 0, action: onNotifications)
            }
            .frame(height: 52)
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(formatDateLong(LocalDate.today()).uppercased())
                        .font(Typo.jost(11))
                        .kerning(1.9)
                        .foregroundStyle(Color.oliveWood)
                    Text(L("client_home_greeting", viewModel.user?.firstName ?? ""))
                        .font(Typo.cormorant(34))
                        .foregroundStyle(Color.bone)
                }
            }
            .padding(.top, 14)
        }
    }

    private var noAppointmentCard: some View {
        Text(L("client_home_no_upcoming"))
            .font(Typo.titleSmall)
            .foregroundStyle(Color.bone)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
            .background(RoundedRectangle(cornerRadius: cardCorner).fill(Color.ink))
    }

    private var quickSlotsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(L("client_home_quick_title").uppercased())
                .font(Typo.jost(11, weight: .medium))
                .kerning(1.7)
                .foregroundStyle(Color.ink)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 9) {
                    ForEach(viewModel.quickSlots, id: \.self) { slot in
                        Button {
                            onQuickSlot(slot)
                        } label: {
                            VStack(spacing: 3) {
                                Text(quickDayLabel(slot.date).uppercased())
                                    .font(Typo.jost(9, weight: .medium))
                                    .kerning(1.1)
                                    .foregroundStyle(Color.textMuted)
                                Text(formatTime(slot.time))
                                    .font(Typo.cormorant(20, weight: .regular))
                                    .foregroundStyle(Color.ink)
                            }
                            // Larghezza fissa: i chip restano tutti uguali qualunque sia l'etichetta.
                            .frame(width: 92)
                            .padding(.vertical, 11)
                            .background(RoundedRectangle(cornerRadius: tileCorner).fill(Color.stone))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func quickDayLabel(_ date: LocalDate) -> String {
        let today = LocalDate.today()
        if date == today { return L("client_home_today") }
        if date == today.plusDays(1) { return L("client_home_tomorrow") }
        return formatDateShort(date)
    }

    private func rebookSection(_ last: Appointment) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .bottom) {
                Text(L("client_home_rebook_title").uppercased())
                    .font(Typo.jost(11, weight: .medium))
                    .kerning(1.7)
                    .foregroundStyle(Color.ink)
                Spacer()
                Button(action: onHistory) {
                    Text(L("client_home_history_link"))
                        .font(Typo.jost(12, weight: .medium))
                        .foregroundStyle(Color.oliveWood)
                }
                .buttonStyle(.plain)
            }
            Button {
                onRebook(last.id)
            } label: {
                HStack(spacing: 12) {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color.bone)
                        .frame(width: 46, height: 46)
                        .overlay(
                            Text(viewModel.operators[last.operatorId]?.initials ?? "")
                                .font(Typo.cormorant(15, weight: .regular))
                                .foregroundStyle(Color.ink)
                        )
                    VStack(alignment: .leading, spacing: 2) {
                        Text(last.serviceIds.compactMap { viewModel.services[$0]?.name }.joined(separator: " + "))
                            .font(Typo.jost(15, weight: .medium))
                            .foregroundStyle(Color.ink)
                            .lineLimit(1)
                        Text(
                            [
                                viewModel.operators[last.operatorId]?.name,
                                formatDuration(last.durationMinutes),
                            ].compactMap { $0 }.joined(separator: " · ")
                        )
                        .font(Typo.bodySmall)
                        .foregroundStyle(Color.ink)
                        .lineLimit(1)
                    }
                    Spacer()
                    Text(L("client_home_rebook_cta"))
                        .font(Typo.jost(12, weight: .medium))
                        .foregroundStyle(Color.bone)
                        .padding(.horizontal, 13)
                        .padding(.vertical, 9)
                        .background(RoundedRectangle(cornerRadius: 10).fill(Color.ink))
                }
                .padding(13)
                .background(RoundedRectangle(cornerRadius: tileCorner).fill(Color.stone))
            }
            .buttonStyle(.plain)
        }
    }
}

private struct NextAppointmentCard: View {
    let viewModel: ClientHomeViewModel
    let next: Appointment

    var body: some View {
        ZStack(alignment: .topTrailing) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(L("client_home_next_title").uppercased())
                        .font(Typo.jost(9, weight: .medium))
                        .kerning(1.4)
                        .foregroundStyle(Color.bone)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(RoundedRectangle(cornerRadius: 6).fill(Color.oliveWood))
                    Spacer()
                    Text(countdownLabel(next.start).uppercased())
                        .font(Typo.jost(11, weight: .medium))
                        .kerning(1.1)
                        .foregroundStyle(Color.bone)
                        // Alone di luce centrato sul countdown: la card lo ritaglia ai bordi.
                        .background(
                            Circle()
                                .fill(Color.oliveWood.opacity(0.16))
                                .frame(width: 112, height: 112)
                        )
                }
                HStack(alignment: .bottom, spacing: 10) {
                    Text(formatTime(next.time))
                        .font(Typo.cormorant(40, weight: .regular))
                        .foregroundStyle(Color.bone)
                    Text(dayAndDuration)
                        .font(Typo.titleSmall)
                        .foregroundStyle(Color.bone)
                        .padding(.bottom, 6)
                }
                HStack(spacing: 10) {
                    Circle()
                        .fill(Color.bone.opacity(0.14))
                        .frame(width: 34, height: 34)
                        .overlay(
                            Text(viewModel.operators[next.operatorId]?.initials ?? "")
                                .font(Typo.cormorant(12, weight: .regular))
                                .foregroundStyle(Color.bone)
                        )
                    VStack(alignment: .leading, spacing: 0) {
                        Text(viewModel.operators[next.operatorId]?.name ?? "")
                            .font(Typo.titleSmall)
                            .foregroundStyle(Color.bone)
                        Text(next.serviceIds.compactMap { viewModel.services[$0]?.name }.joined(separator: " · "))
                            .font(Typo.jost(12, weight: .medium))
                            .foregroundStyle(Color.bone)
                            .lineLimit(1)
                    }
                }
                Rectangle().fill(Color.bone.opacity(0.14)).frame(height: 1)
                // Niente prezzo: le cifre le vede solo il titolare.
                HStack(spacing: 8) {
                    Image(systemName: "calendar")
                        .font(.system(size: 13))
                    Text(L("client_home_add_calendar"))
                        .font(Typo.jost(12, weight: .medium))
                }
                .foregroundStyle(Color.bone)
                .frame(maxWidth: .infinity)
                .frame(height: 38)
                .background(RoundedRectangle(cornerRadius: 10).fill(Color.bone.opacity(0.12)))
            }
            .padding(18)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.ink)
        .clipShape(RoundedRectangle(cornerRadius: cardCorner))
    }

    private var dayAndDuration: String {
        let day = next.date == LocalDate.today() ? L("client_home_today") : formatDateShort(next.date)
        return "\(day) · \(formatDuration(next.durationMinutes))"
    }

    private func countdownLabel(_ start: LocalDateTime) -> String {
        let minutes = max(LocalDateTime.now().minutesUntil(start), 0)
        if minutes < 60 { return L("client_home_in_minutes", minutes) }
        if minutes < 60 * 24 { return L("client_home_in_hours", minutes / 60) }
        return L("client_home_in_days", minutes / (60 * 24))
    }
}
