import Foundation
import Observation

/// Single source of truth for all fake repositories. Every property is
/// observation-tracked, so any screen reading the data updates live —
/// the SwiftUI counterpart of the Android `MutableStateFlow` store.
@Observable
final class InMemoryStore {

    var salon = Salon(name: "", address: "", city: "")
    var services: [Service] = []
    var operators: [Operator] = []
    var holidays: [Holiday] = []
    var users: [User] = []
    var clients: [ClientRecord] = []
    var appointments: [Appointment] = []
    var waitlist: [WaitlistEntry] = []
    var timeBlocks: [TimeBlock] = []
    var campaigns: [PushCampaign] = []
    var notificationSettings = NotificationSettings(reminders: [])
    var notifications: [AppNotification] = []

    var currentUser: User?
    var clientPrefs = ClientNotificationPrefs()

    @ObservationIgnored private var counter: Int64 = 1000

    func newId(_ prefix: String) -> String {
        counter += 1
        return "\(prefix)_\(counter)"
    }

    func now() -> LocalDateTime { .now() }

    init() {
        DemoSeed.seed(self)
    }
}
