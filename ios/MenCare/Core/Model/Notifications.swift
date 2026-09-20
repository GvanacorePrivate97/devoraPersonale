import Foundation

struct ReminderRule: Identifiable, Hashable {
    let id: String
    var hoursBefore: Int
}

struct NotificationSettings: Hashable {
    var reminders: [ReminderRule]
    var bookingConfirmation: Bool = true
    var cancellationAlert: Bool = true
    var lateOperatorAlert: Bool = false
    var emptyDayPromos: Bool = false
}

enum CampaignSegment: CaseIterable, Hashable {
    case inattivi60, tutti, topSpesa
}

enum CampaignStatus: Hashable {
    case draft, scheduled, sent
}

struct PushCampaign: Identifiable, Hashable {
    let id: String
    var name: String
    var segment: CampaignSegment
    var title: String
    var body: String
    var scheduledAt: LocalDateTime?
    var repeatWeekly: Bool = false
    var sendCap: Int?
    var reachableCount: Int
    var segmentSize: Int
    var status: CampaignStatus = .draft
}

/// An in-app notification, addressed to one user (client, staff or owner).
struct AppNotification: Identifiable, Hashable {
    let id: String
    let userId: String
    var title: String
    var body: String
    var at: LocalDateTime
    var read: Bool = false
}
