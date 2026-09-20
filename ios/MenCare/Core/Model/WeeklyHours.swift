import Foundation

extension [DayOfWeek: [TimeRange]] {
    /// Collapses consecutive days that share the same hours, the way an opening
    /// schedule is normally read: "Lun—Mer 10:00–19:00", "Domenica chiuso".
    ///
    /// A day missing from the map — or mapped to no range — counts as closed,
    /// so the result always covers the full week in order.
    func groupConsecutiveDays() -> [(days: [DayOfWeek], range: TimeRange?)] {
        var groups: [(days: [DayOfWeek], range: TimeRange?)] = []
        for day in DayOfWeek.allCases.sorted() {
            let range = self[day]?.first
            if let last = groups.indices.last, groups[last].range == range {
                groups[last].days.append(day)
            } else {
                groups.append((days: [day], range: range))
            }
        }
        return groups
    }
}
