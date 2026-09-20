import Foundation

// ATTENZIONE: da quando c'e' il backend gli slot li calcola il server
// (`/booking/availability`, `/booking/days`, `/booking/next-availability`).
// Questo motore resta solo come supporto ai repository finti di
// `Core/Data/Fake`, che sono le fixture di `MenCareTests`: nessuna schermata e
// nessun repository di rete lo chiama piu'. Due copie della stessa regola
// finirebbero per divergere, e quella buona e' quella del server.

/// Pure slot computation: operator weekly hours minus closing days, holidays,
/// personal blocks and existing active appointments, sampled on a fixed grid
/// for the requested duration.
/// Direct port of the Android `SlotEngine` — keep the two in sync.
enum SlotEngine {

    static let gridMinutes = 30

    /// Minimum lead time for same-day bookings.
    static let leadMinutes = 30

    static func slotsFor(
        date: LocalDate,
        operator op: Operator,
        salon: Salon,
        totalDurationMinutes: Int,
        appointments: [Appointment],
        blocks: [TimeBlock],
        holidays: [Holiday],
        now: LocalDateTime
    ) -> [LocalTime] {
        if date < now.date { return [] }
        if holidays.contains(where: { $0.operatorId == op.id && $0.from <= date && date <= $0.to }) { return [] }

        let openRanges = salon.weeklyHours[date.dayOfWeek] ?? []
        if openRanges.isEmpty { return [] }

        // An operator can never be bookable outside the salon's own opening hours.
        let workRanges = (op.weeklyHours[date.dayOfWeek] ?? [])
            .flatMap { shift in openRanges.compactMap { shift.intersect($0) } }
        if workRanges.isEmpty { return [] }

        var busy: [TimeRange] = []
        for apt in appointments where apt.operatorId == op.id && apt.date == date && apt.isActive {
            busy.append(TimeRange(apt.time, apt.end.time))
        }
        // Every block (permesso, pausa, ferie, corso) takes its slots away.
        for block in blocks where block.operatorId == op.id && block.date == date {
            busy.append(block.range)
        }

        let earliest = date == now.date ? now.time.plusMinutes(leadMinutes) : LocalTime.min

        var result: [LocalTime] = []
        for range in workRanges {
            var t = range.start
            while t.plusMinutes(totalDurationMinutes) <= range.end &&
                t.plusMinutes(totalDurationMinutes) > t { // guard midnight wrap
                let candidate = TimeRange(t, t.plusMinutes(totalDurationMinutes))
                if t >= earliest && !busy.contains(where: { $0.overlaps(candidate) }) {
                    result.append(t)
                }
                t = t.plusMinutes(gridMinutes)
                if t == LocalTime.min { break }
            }
        }
        return result.sorted()
    }

    /// Can this appointment sit here? Staff-side check for a manual move: the
    /// client-facing 30-minute grid and the same-day lead time don't apply when
    /// the owner drags a card, only the operator's real availability does —
    /// opening hours, holidays, personal blocks and the other appointments.
    ///
    /// `ignoreAppointmentId` is the card being moved: it must not block itself.
    static func canPlace(
        date: LocalDate,
        operator op: Operator,
        salon: Salon,
        start: LocalTime,
        totalDurationMinutes: Int,
        appointments: [Appointment],
        blocks: [TimeBlock],
        holidays: [Holiday],
        ignoreAppointmentId: String? = nil
    ) -> Bool {
        if holidays.contains(where: { $0.operatorId == op.id && $0.from <= date && date <= $0.to }) { return false }

        let end = start.plusMinutes(totalDurationMinutes)
        if end <= start { return false } // the service would run past midnight

        let candidate = TimeRange(start, end)
        let openRanges = salon.weeklyHours[date.dayOfWeek] ?? []
        let workRanges = (op.weeklyHours[date.dayOfWeek] ?? [])
            .flatMap { shift in openRanges.compactMap { shift.intersect($0) } }
        guard workRanges.contains(where: { $0.start <= candidate.start && candidate.end <= $0.end }) else {
            return false
        }

        var busy: [TimeRange] = []
        for apt in appointments where apt.operatorId == op.id && apt.date == date && apt.isActive &&
            apt.id != ignoreAppointmentId {
            busy.append(TimeRange(apt.time, apt.end.time))
        }
        for block in blocks where block.operatorId == op.id && block.date == date {
            busy.append(block.range)
        }
        return !busy.contains(where: { $0.overlaps(candidate) })
    }

    /// "Qualsiasi operatore": union of every eligible operator's slots.
    static func unionSlots(
        date: LocalDate,
        operators: [Operator],
        salon: Salon,
        totalDurationMinutes: Int,
        appointments: [Appointment],
        blocks: [TimeBlock],
        holidays: [Holiday],
        now: LocalDateTime
    ) -> [LocalTime] {
        var seen = Set<LocalTime>()
        var result: [LocalTime] = []
        for op in operators {
            for slot in slotsFor(
                date: date, operator: op, salon: salon,
                totalDurationMinutes: totalDurationMinutes,
                appointments: appointments, blocks: blocks, holidays: holidays, now: now
            ) where seen.insert(slot).inserted {
                result.append(slot)
            }
        }
        return result.sorted()
    }
}
