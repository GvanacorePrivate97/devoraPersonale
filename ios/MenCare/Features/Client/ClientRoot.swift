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
        BrandBottomBar {
            BrandBottomBarItem(selected: tab == .home, systemImage: "house", label: L("client_tab_home")) { tab = .home }
            BrandBottomBarItem(selected: tab == .booking, systemImage: "plus.circle", label: L("client_tab_book")) { openBooking(.blank) }
            BrandBottomBarItem(selected: tab == .appointments, systemImage: "calendar", label: L("client_tab_appointments")) { tab = .appointments }
            BrandBottomBarItem(selected: tab == .profile, systemImage: "person", label: L("client_tab_profile")) { tab = .profile }
        }
    }
}
