import Foundation

// Campanella — docs/API.md §Notifiche. Le rotte non accettano nessun userId:
// l'account viene dal token, quindi la lista e' sempre e solo la propria.

struct NotificationDto: Decodable {
    let id: String
    let title: String
    let body: String
    let kind: String
    let at: String
    let read: Bool

    /// `at` e' un istante ISO con fuso: si mostra nell'ora del telefono. Se il
    /// formato non si riconosce si ripiega su adesso, che e' preferibile a
    /// nascondere la notifica.
    func toDomain(userId: String) -> AppNotification {
        AppNotification(
            id: id,
            userId: userId,
            title: title,
            body: body,
            at: LocalDateTime.fromInstant(at) ?? .now(),
            read: read
        )
    }
}

struct NotificationsPageDto: Decodable {
    let notifications: [NotificationDto]
    let unreadCount: Int
    let nextCursor: String?
}

struct MarkReadResultDto: Decodable {
    let marked: Int
    let unreadCount: Int
}
