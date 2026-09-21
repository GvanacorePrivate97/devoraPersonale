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
                VStack(alignment: .leading, spacing: 10) {
                    // Le due letture della pagina — "da leggere" e "già viste"
                    // — hanno due superfici, non due tonalità dello stesso
                    // pallino, con la data a separarle come nel mockup.
                    let today = LocalDate.today()
                    let groups = Dictionary(grouping: viewModel.notifications) { $0.at.date == today }
                    ForEach([true, false], id: \.self) { isToday in
                        let rows = groups[isToday] ?? []
                        if !rows.isEmpty {
                            BrandSectionLabel(text: L(isToday ? "notifications_today" : "notifications_earlier"))
                                .padding(.top, 4)
                            ForEach(rows) { notification in
                                row(notification)
                            }
                        }
                    }
                }
                .padding(20)
                .readableWidth()
            }
        }
    }

    /// Una riga della campanella. Non letta: card chiara col filo e il punto
    /// d'accento. Già letta: fondo tenue, nessun filo e il punto spento — la
    /// differenza si vede dalla superficie, non solo da un pallino più chiaro.
    private func row(_ notification: AppNotification) -> some View {
        let isNew = viewModel.newIds.contains(notification.id)
        return HStack(alignment: .center, spacing: 12) {
            Circle()
                .fill(isNew ? Color.oliveWood : Color.stoneBorder)
                .frame(width: 9, height: 9)
            VStack(alignment: .leading, spacing: 2) {
                Text(notification.title)
                    .font(isNew ? Typo.titleSmall : Typo.bodyLarge)
                    .foregroundStyle(Color.ink)
                Text(notification.body)
                    .font(Typo.bodyMedium)
                    .foregroundStyle(Color.textMuted)
                Text(formatDateTime(notification.at))
                    .font(Typo.labelSmall)
                    .foregroundStyle(Color.textMuted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: Radii.md).fill(isNew ? Color.bone : Color.stoneSoft))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md)
                .strokeBorder(Color.stoneBorder, lineWidth: isNew ? 1.5 : 0)
        )
    }
}
