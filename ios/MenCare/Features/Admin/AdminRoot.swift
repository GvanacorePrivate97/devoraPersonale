import SwiftUI

private enum AdminTab: Hashable {
    case dashboard, clients, agenda, manage, profile
}

private enum AdminRoute: Hashable {
    case campaign
    case serviceEdit(String?)
    case operatorNew
    case clientDetail(String)
}

struct AdminRoot: View {
    @Environment(\.container) private var container
    let onLoggedOut: () -> Void

    // L'agenda sta al centro ed è dove si atterra: è la schermata che il
    // titolare apre più spesso.
    @State private var tab: AdminTab = .agenda
    @State private var path: [AdminRoute] = []
    @State private var showNotifications = false

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                Group {
                    switch tab {
                    case .dashboard:
                        DashboardScreen(
                            viewModel: DashboardViewModel(admin: container.admin),
                            onSendCampaign: { path.append(.campaign) }
                        )
                    case .clients:
                        CrmListScreen(
                            viewModel: CrmListViewModel(crm: container.crm),
                            onClient: { path.append(.clientDetail($0)) }
                        )
                    case .agenda:
                        WeeklyAgendaScreen(
                            viewModel: WeeklyAgendaViewModel(
                                booking: container.booking, blocks: container.timeBlocks,
                                catalog: container.catalog, crm: container.crm,
                                notifications: container.notificationsRepo
                            ),
                            onNotifications: { showNotifications = true }
                        )
                    case .manage:
                        ManageScreen(
                            viewModel: ManageViewModel(
                                catalog: container.catalog, timeBlocks: container.timeBlocks,
                                admin: container.admin
                            ),
                            onEditService: { path.append(.serviceEdit($0)) },
                            onNewOperator: { path.append(.operatorNew) }
                        )
                    case .profile:
                        AdminProfileScreen(
                            viewModel: AdminProfileViewModel(
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
            .navigationDestination(for: AdminRoute.self) { route in
                Group {
                    switch route {
                    case .campaign:
                        CampaignScreen(
                            viewModel: CampaignViewModel(admin: container.admin),
                            onBack: { path.removeLast() }
                        )
                    case .serviceEdit(let serviceId):
                        ServiceEditScreen(
                            viewModel: ServiceEditViewModel(catalog: container.catalog, serviceId: serviceId),
                            onBack: { path.removeLast() }
                        )
                    case .operatorNew:
                        OperatorEditScreen(
                            viewModel: OperatorEditViewModel(catalog: container.catalog),
                            onBack: { path.removeLast() }
                        )
                    case .clientDetail(let clientId):
                        CrmDetailScreen(
                            viewModel: CrmDetailViewModel(
                                crm: container.crm, catalog: container.catalog, clientId: clientId
                            ),
                            showEconomics: true,
                            onBack: { path.removeLast() },
                            // Lo sheet di prenotazione vive nell'agenda: ci si porta lì.
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
        // Stessa pagina notifiche del cliente e dell'operatore.
        .fullScreenCover(isPresented: $showNotifications) {
            NotificationsScreen(viewModel: NotificationsViewModel(repository: container.notificationsRepo), onBack: { showNotifications = false })
                .environment(\.appContainer, container)
        }
    }

    private var tabBar: some View {
        BrandBottomBar {
            BrandBottomBarItem(selected: tab == .dashboard, systemImage: "chart.line.uptrend.xyaxis", label: L("admin_tab_dashboard")) { tab = .dashboard }
            BrandBottomBarItem(selected: tab == .clients, systemImage: "person.2", label: L("admin_tab_clients")) { tab = .clients }
            BrandBottomBarItem(selected: tab == .agenda, systemImage: "calendar", label: L("admin_tab_agenda")) { tab = .agenda }
            BrandBottomBarItem(selected: tab == .manage, systemImage: "slider.horizontal.3", label: L("admin_tab_manage")) { tab = .manage }
            BrandBottomBarItem(selected: tab == .profile, systemImage: "person", label: L("admin_tab_profile")) { tab = .profile }
        }
    }
}
