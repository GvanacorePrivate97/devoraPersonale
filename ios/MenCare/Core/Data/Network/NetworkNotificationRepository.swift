import Foundation

@MainActor
final class NetworkNotificationRepository: NotificationRepository {

    private let pageSize = 50

    private let client: ApiClient
    private let session: AppSession

    init(client: ApiClient, session: AppSession) {
        self.client = client
        self.session = session
    }

    func feed() async throws -> NotificationFeed {
        try await apiThrowing {
            let page = try await client.send(
                NotificationsEndpoint.list(limit: pageSize, cursor: nil), as: NotificationsPageDto.self
            )
            // Le rotte non accettano nessun userId: quello che torna e' gia'
            // solo il proprio. Il campo sul modello resta per i test con i fake.
            let userId = session.user?.id ?? ""
            return NotificationFeed(
                notifications: page.notifications.map { $0.toDomain(userId: userId) },
                unreadCount: page.unreadCount
            )
        }
    }

    func markAllRead() async -> AppResult<Void> {
        await apiResult {
            _ = try await client.send(NotificationsEndpoint.readAll, as: MarkReadResultDto.self)
        }
    }
}
