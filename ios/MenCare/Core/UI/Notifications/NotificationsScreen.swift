import SwiftUI
import Observation

/// La campanella aveva la sua lettura dentro `body` e perfino dentro `init`:
/// con una lettura di rete non era piu' possibile, e serviva comunque uno stato
/// di caricamento. Adesso ha un view model come tutte le altre schermate.
@MainActor
@Observable
final class NotificationsViewModel {

    private let repository: NotificationRepository

    let state = LoadState()
    private(set) var notifications: [AppNotification] = []
    /// Quelle non lette quando la pagina si e' aperta: tengono il pallino finche'
    /// l'utente non esce, anche dopo che il server le ha segnate lette.
    private(set) var newIds: Set<String> = []

    init(repository: NotificationRepository) {
        self.repository = repository
    }

    func load() async {
        await state.run {
            let feed = try await repository.feed()
            notifications = feed.notifications
            if newIds.isEmpty {
                newIds = Set(feed.notifications.filter { !$0.read }.map(\.id))
            }
        }
        // Aprire la pagina vale come "viste": il contatore della campanella si
        // azzera sul server, non solo a schermo.
        _ = await repository.markAllRead()
    }
}

/// Lista delle notifiche: la stessa pagina per cliente, operatore e titolare.
struct NotificationsScreen: View {
    @State var viewModel: NotificationsViewModel
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            DarkHeader {
                HStack(spacing: 4) {
                    Button(action: onBack) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 17, weight: .medium))
                            .foregroundStyle(Color.bone)
                            .padding(8)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(L("ds_back"))
                    DarkHeaderTitle(title: L("notifications_title"))
                }
            }
            Loadable(state: viewModel.state, retry: { Task { await viewModel.load() } }) {
                list
            }
        }
        .background(Color.bone)
        .task { await viewModel.load() }
    }

    @ViewBuilder
    private var list: some View {
        if viewModel.notifications.isEmpty {
            VStack(spacing: 0) {
                Text(L("notifications_empty"))
                    .font(Typo.bodyMedium)
                    .foregroundStyle(Color.textMuted)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
                    .readableWidth()
                Spacer()
            }
        } else {
            ScrollView {
                VStack(spacing: 10) {
                    ForEach(viewModel.notifications) { notification in
                        OutlineCard {
                            HStack(alignment: .center, spacing: 12) {
                                // Oliva piena se nuova, oliva tenue se già letta.
                                Circle()
                                    .fill(viewModel.newIds.contains(notification.id) ? Color.oliveWood : Color.oliveWood.opacity(0.3))
                                    .frame(width: 9, height: 9)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(notification.title)
                                        .font(Typo.titleSmall)
                                        .foregroundStyle(Color.ink)
                                    Text(notification.body)
                                        .font(Typo.bodyMedium)
                                        .foregroundStyle(Color.ink)
                                    Text(formatDateTime(notification.at))
                                        .font(Typo.labelSmall)
                                        .foregroundStyle(Color.textMuted)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .padding(14)
                        }
                    }
                }
                .padding(20)
                .readableWidth()
            }
        }
    }
}
