import Foundation

// Lightweight java.time equivalents so the domain and the slot engine port
// 1:1 from the Android app without dragging Foundation.Date semantics
// (time zones, DST) into pure business logic.

enum DayOfWeek: Int, CaseIterable, Comparable, Hashable, Codable {
    case monday = 1, tuesday, wednesday, thursday, friday, saturday, sunday

    static func < (lhs: DayOfWeek, rhs: DayOfWeek) -> Bool { lhs.rawValue < rhs.rawValue }
}

/// Time of day with minute precision, like `java.time.LocalTime`.
struct LocalTime: Comparable, Hashable, Codable {
    let hour: Int
    let minute: Int

    init(_ hour: Int, _ minute: Int) {
        self.hour = hour
        self.minute = minute
    }

    static let min = LocalTime(0, 0)

    var minutesOfDay: Int { hour * 60 + minute }

    /// Wraps at midnight, like java.time.
    func plusMinutes(_ minutes: Int) -> LocalTime {
        let total = ((minutesOfDay + minutes) % 1440 + 1440) % 1440
        return LocalTime(total / 60, total % 60)
    }

    func minusMinutes(_ minutes: Int) -> LocalTime { plusMinutes(-minutes) }

    static func < (lhs: LocalTime, rhs: LocalTime) -> Bool { lhs.minutesOfDay < rhs.minutesOfDay }

    // Sul filo un orario e' la stringa "HH:mm" di docs/API.md, non una coppia di
    // interi: cosi' i DTO decodificano direttamente nel tipo di dominio.
    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        guard let parsed = LocalTime.parse(raw) else {
            throw DecodingError.dataCorrupted(
                .init(codingPath: decoder.codingPath, debugDescription: "Orario non valido: \(raw)")
            )
        }
        self = parsed
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(iso)
    }

    /// "09:30" — accetta anche "09:30:00", che e' quello che scrive Postgres.
    static func parse(_ raw: String) -> LocalTime? {
        let parts = raw.split(separator: ":")
        guard parts.count >= 2, let hour = Int(parts[0]), let minute = Int(parts[1]),
              (0...23).contains(hour), (0...59).contains(minute) else { return nil }
        return LocalTime(hour, minute)
    }

    var iso: String { String(format: "%02d:%02d", hour, minute) }
}

/// Calendar date without time zone, like `java.time.LocalDate`.
/// Backed by an epoch-day count so comparisons and arithmetic are exact.
struct LocalDate: Comparable, Hashable, Codable {
    /// Days since 1970-01-01.
    let epochDay: Int

    init(epochDay: Int) {
        self.epochDay = epochDay
    }

    init(year: Int, month: Int, day: Int) {
        // Howard Hinnant's civil-days algorithm — exact proleptic Gregorian.
        let y = month <= 2 ? year - 1 : year
        let era = (y >= 0 ? y : y - 399) / 400
        let yoe = y - era * 400
        let mp = (month + 9) % 12
        let doy = (153 * mp + 2) / 5 + day - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        self.epochDay = era * 146_097 + doe - 719_468
    }

    static func today(calendar: Calendar = .current) -> LocalDate {
        let c = calendar.dateComponents([.year, .month, .day], from: Date())
        return LocalDate(year: c.year!, month: c.month!, day: c.day!)
    }

    private var civil: (year: Int, month: Int, day: Int) {
        var z = epochDay + 719_468
        let era = (z >= 0 ? z : z - 146_096) / 146_097
        z -= era * 146_097
        let yoe = (z - z / 1460 + z / 36_524 - z / 146_096) / 365
        let y = yoe + era * 400
        let doy = z - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let day = doy - (153 * mp + 2) / 5 + 1
        let month = mp < 10 ? mp + 3 : mp - 9
        return (month <= 2 ? y + 1 : y, month, day)
    }

    var year: Int { civil.year }
    var month: Int { civil.month }
    var day: Int { civil.day }

    var dayOfWeek: DayOfWeek {
        // 1970-01-01 was a Thursday (ISO day 4).
        let iso = ((epochDay + 3) % 7 + 7) % 7 + 1
        return DayOfWeek(rawValue: iso)!
    }

    func plusDays(_ days: Int) -> LocalDate { LocalDate(epochDay: epochDay + days) }
    func minusDays(_ days: Int) -> LocalDate { plusDays(-days) }
    func plusWeeks(_ weeks: Int) -> LocalDate { plusDays(weeks * 7) }
    func minusWeeks(_ weeks: Int) -> LocalDate { plusDays(-weeks * 7) }

    func plusMonths(_ months: Int) -> LocalDate {
        let c = civil
        let total = c.year * 12 + (c.month - 1) + months
        let year = total >= 0 ? total / 12 : (total - 11) / 12
        let month = total - year * 12 + 1
        let day = Swift.min(c.day, LocalDate.lengthOfMonth(year: year, month: month))
        return LocalDate(year: year, month: month, day: day)
    }

    func minusMonths(_ months: Int) -> LocalDate { plusMonths(-months) }

    func daysBetween(_ other: LocalDate) -> Int { other.epochDay - epochDay }

    var startOfMonth: LocalDate { LocalDate(year: year, month: month, day: 1) }
    var endOfMonth: LocalDate {
        LocalDate(year: year, month: month, day: LocalDate.lengthOfMonth(year: year, month: month))
    }
    var lengthOfMonth: Int { LocalDate.lengthOfMonth(year: year, month: month) }

    static func lengthOfMonth(year: Int, month: Int) -> Int {
        switch month {
        case 1, 3, 5, 7, 8, 10, 12: return 31
        case 4, 6, 9, 11: return 30
        default:
            let leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
            return leap ? 29 : 28
        }
    }

    func atTime(_ time: LocalTime) -> LocalDateTime { LocalDateTime(date: self, time: time) }

    static func < (lhs: LocalDate, rhs: LocalDate) -> Bool { lhs.epochDay < rhs.epochDay }

    // Sul filo una data e' "2026-09-20": il conteggio di giorni dall'epoca resta
    // un dettaglio interno, l'API parla ISO.
    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        guard let parsed = LocalDate.parse(raw) else {
            throw DecodingError.dataCorrupted(
                .init(codingPath: decoder.codingPath, debugDescription: "Data non valida: \(raw)")
            )
        }
        self = parsed
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(iso)
    }

    static func parse(_ raw: String) -> LocalDate? {
        let parts = raw.prefix(10).split(separator: "-")
        guard parts.count == 3, let year = Int(parts[0]), let month = Int(parts[1]), let day = Int(parts[2]),
              (1...12).contains(month), (1...31).contains(day) else { return nil }
        return LocalDate(year: year, month: month, day: day)
    }

    var iso: String { String(format: "%04d-%02d-%02d", year, month, day) }
}

/// Date + time of day, like `java.time.LocalDateTime`.
struct LocalDateTime: Comparable, Hashable, Codable {
    let date: LocalDate
    let time: LocalTime

    static func now(calendar: Calendar = .current) -> LocalDateTime {
        let c = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: Date())
        return LocalDateTime(
            date: LocalDate(year: c.year!, month: c.month!, day: c.day!),
            time: LocalTime(c.hour!, c.minute!)
        )
    }

    func plusMinutes(_ minutes: Int) -> LocalDateTime {
        let total = time.minutesOfDay + minutes
        let dayShift = total >= 0 ? total / 1440 : (total - 1439) / 1440
        let minsOfDay = total - dayShift * 1440
        return LocalDateTime(date: date.plusDays(dayShift), time: LocalTime(minsOfDay / 60, minsOfDay % 60))
    }

    func minusMinutes(_ minutes: Int) -> LocalDateTime { plusMinutes(-minutes) }
    func plusHours(_ hours: Int) -> LocalDateTime { plusMinutes(hours * 60) }
    func minusHours(_ hours: Int) -> LocalDateTime { plusMinutes(-hours * 60) }
    func plusDays(_ days: Int) -> LocalDateTime { LocalDateTime(date: date.plusDays(days), time: time) }
    func minusDays(_ days: Int) -> LocalDateTime { plusDays(-days) }
    func plusWeeks(_ weeks: Int) -> LocalDateTime { plusDays(weeks * 7) }

    func minutesUntil(_ other: LocalDateTime) -> Int {
        date.daysBetween(other.date) * 1440 + (other.time.minutesOfDay - time.minutesOfDay)
    }

    static func < (lhs: LocalDateTime, rhs: LocalDateTime) -> Bool {
        lhs.date != rhs.date ? lhs.date < rhs.date : lhs.time < rhs.time
    }

    /// "2026-09-20T10:30" — la forma che l'API accetta come orario da muro del
    /// salone (le campagne programmate).
    var isoLocal: String { "\(date.iso)T\(time.iso)" }

    /// Istante ISO completo con fuso (`2026-09-20T08:30:00.000Z`) riportato
    /// all'ora locale del telefono: le notifiche arrivano cosi'.
    static func fromInstant(_ raw: String, calendar: Calendar = .current) -> LocalDateTime? {
        guard let date = instantFormatter.date(from: raw) ?? instantFormatterNoFraction.date(from: raw) else {
            return nil
        }
        let c = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        guard let year = c.year, let month = c.month, let day = c.day, let hour = c.hour, let minute = c.minute else {
            return nil
        }
        return LocalDateTime(date: LocalDate(year: year, month: month, day: day), time: LocalTime(hour, minute))
    }
}

private let instantFormatter: ISO8601DateFormatter = {
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return f
}()

private let instantFormatterNoFraction: ISO8601DateFormatter = {
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime]
    return f
}()
