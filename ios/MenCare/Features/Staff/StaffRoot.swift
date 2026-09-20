import SwiftUI

private enum StaffTab: Hashable {
    case agenda, clients, profile
}

private enum StaffRoute: Hashable {
    case appointment(String)
    case clientDetail(String)
}

struct StaffRoot: View {
    @Environment(\.container) private var container
    let onLoggedOut: () -> Void

    @State private var tab: StaffTab = .agenda
    @State private var path: [StaffRoute] = []
    @State private var showNotifications = false

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                Group {
                    switch tab {
                    case .agenda:
                        AgendaScreen(
                            viewModel: AgendaViewModel(
                                auth: container.auth, booking: container.booking,
                                blocks: container.timeBlocks, catalog: container.catalog, crm: container.crm,
                                notifications: container.notificationsRepo
                            ),
                            onAppointment: { path.append(.appointment($0)) },
                            onProfile: { tab = .profile },
                            onNotifications: { showNotifications = true }
                        )
                    case .clients:
                        // Stessa lista del titolare.
                        CrmListScreen(
                            viewModel: CrmListViewModel(crm: container.crm),
                            onClient: { path.append(.clientDetail($0)) }
                        )
                    case .profile:
                        StaffProfileScreen(
                            viewModel: StaffProfileViewModel(
                                auth: container.auth, avatar: container.avatar, catalog: container.catalog
                            ),
                            onLoggedOut: onLoggedOut
                        )
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                tabBar
            }
            .background(Color.bone)
            .navigationBarHidden(true)
            .navigationDestination(for: StaffRoute.self) { route in
                Group {
                    switch route {
                    case .appointment(let id):
                        AppointmentDetailScreen(
                            viewModel: AppointmentDetailViewModel(
                                booking: container.booking, crm: container.crm,
                                catalog: container.catalog, appointmentId: id
                            ),
                            onBack: { path.removeLast() }
                        )
                    case .clientDetail(let clientId):
                        // Stessa scheda del titolare.
                        CrmDetailScreen(
                            viewModel: CrmDetailViewModel(
                                crm: container.crm, catalog: container.catalog, clientId: clientId
                            ),
                            showEconomics: false,
                            onBack: { path.removeLast() },
                            // La nuova prenotazione si apre dall'agenda: ci si porta lì.
                            onNewBooking: {
                                path.removeLast()
                                tab = .agenda
                            }
                        )
                    }
                }
                .navigationBarHidden(true)
            }
        }
        // Stessa pagina notifiche del cliente e del titolare.
        .fullScreenCover(isPresented: $showNotifications) {
            NotificationsScreen(viewModel: NotificationsViewModel(repository: container.notificationsRepo), onBack: { showNotifications = false })
                .environment(\.appContainer, container)
        }
    }

    private var tabBar: some View {
        VStack(spacing: 0) {
            Rectangle().fill(Color.stone).frame(height: 1)
            HStack {
                tabItem(.agenda, systemImage: "calendar", label: L("staff_tab_agenda"))
                tabItem(.clients, systemImage: "person.2", label: L("staff_tab_clients"))
                tabItem(.profile, systemImage: "person", label: L("staff_tab_profile"))
            }
            .padding(.top, 8)
            .padding(.bottom, 2)
            // Sui tablet le voci restano raccolte al centro, non sparse sui bordi.
            .readableWidth()
        }
        .background(Color.bone)
    }

    private func tabItem(_ target: StaffTab, systemImage: String, label: String) -> some View {
        let selected = tab == target
        return Button {
            tab = target
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
