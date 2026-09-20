import SwiftUI

private enum ClientTab: Hashable {
    case home, booking, appointments, profile
}

/// Client area: tab bar + booking wizard, the iOS counterpart of the Android
/// client nav graph. The wizard carries its own bottom action bar, so — as the
/// mockup shows — the tab bar disappears while booking.
struct ClientRoot: View {
    @Environment(\.container) private var container
    let onLoggedOut: () -> Void

    @State private var tab: ClientTab = .home
    @State private var bookingViewModel: BookingViewModel?
    @State private var showNotifications = false

    var body: some View {
        VStack(spacing: 0) {
            Group {
                switch tab {
                case .home:
                    ClientHomeScreen(
                        viewModel: ClientHomeViewModel(
                            auth: container.auth, booking: container.booking,
                            catalog: container.catalog, notifications: container.notificationsRepo
                        ),
                        onBook: { openBooking(.blank) },
                        onRebook: { openBooking(.rebook(appointmentId: $0)) },
                        onQuickSlot: { openBooking(.quickSlot($0)) },
                        onHistory: { tab = .appointments },
                        onNotifications: { showNotifications = true }
                    )
                case .booking:
                    if let bookingViewModel {
                        BookingWizardScreen(
                            viewModel: bookingViewModel,
                            // Annulla porta ai propri appuntamenti, non alla home.
                            onCancel: { closeBooking(to: .appointments) },
                            onConfirmed: { closeBooking(to: .home) }
                        )
                    }
                case .appointments:
                    AppointmentsScreen(
                        viewModel: AppointmentsViewModel(
                            auth: container.auth, booking: container.booking, catalog: container.catalog
                        ),
                        onBook: { openBooking(.blank) },
                        onRebook: { openBooking(.rebook(appointmentId: $0)) },
                        onEdit: { openBooking(.edit(appointmentId: $0)) }
                    )
                case .profile:
                    ProfileScreen(
                        viewModel: ProfileViewModel(auth: container.auth, catalog: container.catalog),
                        onLoggedOut: onLoggedOut
                    )
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            // Il wizard di prenotazione ha la sua barra azioni in fondo: la tab
            // bar ruberebbe quello spazio, come mostra il mockup.
            if tab != .booking {
                tabBar
            }
        }
        .background(Color.bone)
        .fullScreenCover(isPresented: $showNotifications) {
            NotificationsScreen(viewModel: NotificationsViewModel(repository: container.notificationsRepo), onBack: { showNotifications = false })
                .environment(\.appContainer, container)
        }
    }

    private func openBooking(_ entry: BookingEntry) {
        bookingViewModel = BookingViewModel(
            auth: container.auth, booking: container.booking, catalog: container.catalog, entry: entry
        )
        tab = .booking
    }

    private func closeBooking(to target: ClientTab) {
        tab = target
        bookingViewModel = nil
    }

    private var tabBar: some View {
        VStack(spacing: 0) {
            Rectangle().fill(Color.stone).frame(height: 1)
            HStack {
                tabItem(.home, systemImage: "house", label: L("client_tab_home"))
                tabItem(.booking, systemImage: "plus.circle", label: L("client_tab_book"))
                tabItem(.appointments, systemImage: "calendar", label: L("client_tab_appointments"))
                tabItem(.profile, systemImage: "person", label: L("client_tab_profile"))
            }
            .padding(.top, 8)
            .padding(.bottom, 2)
            // Sui tablet le voci restano raccolte al centro, non sparse sui bordi.
            .readableWidth()
        }
        .background(Color.bone)
    }

    private func tabItem(_ target: ClientTab, systemImage: String, label: String) -> some View {
        let selected = tab == target
        return Button {
            if target == .booking {
                openBooking(.blank)
            } else {
                tab = target
            }
        } label: {
            VStack(spacing: 3) {
                Image(systemName: systemImage).font(.system(size: 19))
                Text(label).font(Typo.jost(10, weight: .medium))
            }
            .foregroundStyle(selected ? Color.oliveWood : Color.ink)
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
    }
}
