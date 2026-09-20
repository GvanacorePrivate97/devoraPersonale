import Foundation

/// The salon itself: one single location, so it has no identity to select.
struct Salon: Hashable {
    var name: String
    var address: String
    var city: String
    /// Opening hours of the salon: a day with no ranges is a closing day.
    var weeklyHours: [DayOfWeek: [TimeRange]] = [:]

    var closingDays: Set<DayOfWeek> {
        Set(DayOfWeek.allCases.filter { weeklyHours[$0]?.isEmpty ?? true })
    }
}
