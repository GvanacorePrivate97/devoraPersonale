import Foundation

enum UserRole: String, Codable {
    case client
    case staff
    case owner
}

struct User: Identifiable, Hashable {
    let id: String
    var firstName: String
    var lastName: String
    var email: String
    var phone: String
    let role: UserRole
    let memberSince: LocalDate
    var visitCount: Int = 0
    /// Profile photo saved by the app; nil = show the initials.
    var avatarPath: String?
    /// CRM record backing this account (clients only).
    var clientRecordId: String?
    /// Operator profile backing this account (staff/owner only).
    var operatorId: String?

    var fullName: String { "\(firstName) \(lastName)" }
    var initials: String {
        let f = firstName.first.map(String.init) ?? ""
        let l = lastName.first.map(String.init) ?? ""
        return (f + l).trimmingCharacters(in: .whitespaces)
    }
}

struct ClientNotificationPrefs: Hashable {
    var appointmentReminder: Bool = true
    var waitlistAlerts: Bool = true
    var marketing: Bool = false
}
