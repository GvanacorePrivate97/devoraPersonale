import Foundation

struct TimeRange: Hashable, Comparable {
    let start: LocalTime
    let end: LocalTime

    init(_ start: LocalTime, _ end: LocalTime) {
        precondition(start < end, "start must be before end")
        self.start = start
        self.end = end
    }

    func overlaps(_ other: TimeRange) -> Bool { start < other.end && other.start < end }

    /// Overlapping part of the two ranges, or nil when they don't overlap.
    func intersect(_ other: TimeRange) -> TimeRange? {
        let from = max(start, other.start)
        let to = min(end, other.end)
        return from < to ? TimeRange(from, to) : nil
    }

    static func < (lhs: TimeRange, rhs: TimeRange) -> Bool { lhs.start < rhs.start }
}

struct Operator: Identifiable, Hashable {
    let id: String
    var name: String
    var title: String
    var bio: String
    var specialties: [String]
    var isOwner: Bool = false
    var weeklyHours: [DayOfWeek: [TimeRange]]
    var serviceIds: Set<String>

    var initials: String {
        name.split(separator: " ").prefix(2).compactMap { $0.first.map(String.init)?.uppercased() }.joined()
    }
}

enum BlockReason: CaseIterable, Hashable {
    case permesso, pausa, ferie, corso
}

struct TimeBlock: Identifiable, Hashable {
    let id: String
    let operatorId: String
    var reason: BlockReason
    var date: LocalDate
    var range: TimeRange
    var label: String?
}

struct Holiday: Identifiable, Hashable {
    let id: String
    let operatorId: String
    var from: LocalDate
    var to: LocalDate
    var label: String
}
