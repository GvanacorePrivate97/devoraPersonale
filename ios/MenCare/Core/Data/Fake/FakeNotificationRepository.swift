import Foundation

/// Only the signed-in user's notifications: client, staff and owner each see their own.
@MainActor
final class FakeNotificationRepository: NotificationRepository {

    private let store: InMemoryStore

    init(store: InMemoryStore) {
        self.store = store
    }

    func feed() async throws -> NotificationFeed {
        let mine = store.notifications
            .filter { $0.userId == store.currentUser?.id }
            .sorted { $0.at > $1.at }
        return NotificationFeed(notifications: mine, unreadCount: mine.filter { !$0.read }.count)
    }

    func markAllRead() async -> AppResult<Void> {
        guard let userId = store.currentUser?.id else { return .success(()) }
        store.notifications = store.notifications.map {
            var n = $0
            if n.userId == userId { n.read = true }
            return n
        }
        return .success(())
    }
}
