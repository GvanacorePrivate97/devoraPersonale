import XCTest
@testable import MenCare

/// Direct port of the Android `SlotEngineTest` — keep the two in sync.
final class SlotEngineTests: XCTestCase {

    private let salon = Salon(
        name: "Men Care", address: "Via Scarlatti 120", city: "Napoli",
        weeklyHours: Dictionary(
            uniqueKeysWithValues: [DayOfWeek.monday, .tuesday, .wednesday, .thursday, .friday, .saturday].map {
                ($0, [TimeRange(LocalTime(9, 0), LocalTime(19, 0))])
            }
        )
    )

    /// A Monday.
    private let monday = LocalDate(year: 2026, month: 9, day: 14)
    private var now: LocalDateTime { monday.minusDays(3).atTime(LocalTime(12, 0)) }

    private func makeOperator(
        hours: [DayOfWeek: [TimeRange]] = [.monday: [TimeRange(LocalTime(9, 0), LocalTime(13, 0))]]
    ) -> Operator {
        Operator(
            id: "op1", name: "Luca Ferrante", title: "Barbiere", bio: "bio",
            specialties: [], weeklyHours: hours, serviceIds: []
        )
    }

    private func apt(
        _ start: LocalTime, _ duration: Int,
        status: AppointmentStatus = .confirmed, operatorId: String = "op1"
    ) -> Appointment {
        Appointment(
            id: "a_\(start.hour)_\(start.minute)", clientId: "c1", operatorId: operatorId,
            serviceIds: ["s1"], start: monday.atTime(start),
            durationMinutes: duration, totalPriceCents: 1000, status: status
        )
    }

    private func slots(
        duration: Int = 30,
        appointments: [Appointment] = [],
        blocks: [TimeBlock] = [],
        holidays: [Holiday] = [],
        op: Operator? = nil,
        date: LocalDate? = nil,
        at: LocalDateTime? = nil
    ) -> [LocalTime] {
        SlotEngine.slotsFor(
            date: date ?? monday,
            operator: op ?? makeOperator(),
            salon: salon,
            totalDurationMinutes: duration,
            appointments: appointments,
            blocks: blocks,
            holidays: holidays,
            now: at ?? now
        )
    }

    func testFreeMorningYieldsGridSlots() {
        XCTAssertEqual(
            slots(duration: 60),
            [LocalTime(9, 0), LocalTime(9, 30), LocalTime(10, 0), LocalTime(10, 30), LocalTime(11, 0), LocalTime(11, 30), LocalTime(12, 0)]
        )
    }

    func testSlotMustFitInsideWorkingRange() {
        XCTAssertEqual(slots(duration: 240), [LocalTime(9, 0)])
    }

    func testBookedAppointmentRemovesOverlappingSlots() {
        // 10:00–10:45 busy: 09:30 (ends 10:00) ok, 10:00/10:30 gone, 11:00 ok.
        XCTAssertEqual(
            slots(duration: 30, appointments: [apt(LocalTime(10, 0), 45)]),
            [LocalTime(9, 0), LocalTime(9, 30), LocalTime(11, 0), LocalTime(11, 30), LocalTime(12, 0), LocalTime(12, 30)]
        )
    }

    func testCancelledAppointmentsFreeTheirSlot() {
        let result = slots(duration: 30, appointments: [apt(LocalTime(10, 0), 45, status: .cancelled)])
        XCTAssertTrue(result.contains(LocalTime(10, 0)))
    }

    func testEveryBlockHidesItsSlotsFromClients() {
        let block = TimeBlock(
            id: "b1", operatorId: "op1", reason: .pausa, date: monday,
            range: TimeRange(LocalTime(9, 0), LocalTime(11, 0))
        )
        XCTAssertEqual(
            slots(duration: 30, blocks: [block]),
            [LocalTime(11, 0), LocalTime(11, 30), LocalTime(12, 0), LocalTime(12, 30)]
        )
    }

    func testClosingDayYieldsNoSlots() {
        let sunday = monday.minusDays(1)
        let op = makeOperator(hours: [.sunday: [TimeRange(LocalTime(9, 0), LocalTime(13, 0))]])
        XCTAssertTrue(slots(op: op, date: sunday).isEmpty)
    }

    func testHolidayYieldsNoSlots() {
        let holiday = Holiday(id: "h1", operatorId: "op1", from: monday.minusDays(1), to: monday.plusDays(1), label: "Ferie")
        XCTAssertTrue(slots(holidays: [holiday]).isEmpty)
    }

    func testPastDaysYieldNoSlots() {
        XCTAssertTrue(slots(at: monday.plusDays(1).atTime(LocalTime(10, 0))).isEmpty)
    }

    func testSameDayRespectsLeadTime() {
        // 9:45 + 30 min lead = 10:15 → first slot 10:30.
        let result = slots(duration: 30, at: monday.atTime(LocalTime(9, 45)))
        XCTAssertEqual(result.first, LocalTime(10, 30))
        XCTAssertFalse(result.contains(LocalTime(10, 0)))
    }

    func testUnionOfOperatorsDoublesAvailability() {
        let op1 = makeOperator()
        let op2 = Operator(
            id: "op2", name: op1.name, title: op1.title, bio: op1.bio, specialties: [],
            weeklyHours: [.monday: [TimeRange(LocalTime(14, 0), LocalTime(18, 0))]], serviceIds: []
        )
        let result = SlotEngine.unionSlots(
            date: monday, operators: [op1, op2], salon: salon, totalDurationMinutes: 30,
            appointments: [], blocks: [], holidays: [], now: now
        )
        XCTAssertTrue(result.contains(LocalTime(9, 0)))
        XCTAssertTrue(result.contains(LocalTime(14, 0)))
    }

    func testUnionDeduplicatesSharedSlots() {
        let op1 = makeOperator()
        let op2 = Operator(
            id: "op2", name: op1.name, title: op1.title, bio: op1.bio, specialties: [],
            weeklyHours: op1.weeklyHours, serviceIds: []
        )
        let result = SlotEngine.unionSlots(
            date: monday, operators: [op1, op2], salon: salon, totalDurationMinutes: 30,
            appointments: [], blocks: [], holidays: [], now: now
        )
        XCTAssertEqual(result.count, Set(result).count)
    }

    func testAppointmentOfOtherOperatorDoesNotAffectSlots() {
        let other = apt(LocalTime(10, 0), 45, operatorId: "op_other")
        XCTAssertTrue(slots(duration: 30, appointments: [other]).contains(LocalTime(10, 0)))
    }

    func testSplitShiftsProduceSlotsInBothRangesOnly() {
        let op = makeOperator(hours: [
            .monday: [
                TimeRange(LocalTime(9, 0), LocalTime(11, 0)),
                TimeRange(LocalTime(15, 0), LocalTime(17, 0)),
            ],
        ])
        XCTAssertEqual(
            slots(duration: 60, op: op),
            [LocalTime(9, 0), LocalTime(9, 30), LocalTime(10, 0), LocalTime(15, 0), LocalTime(15, 30), LocalTime(16, 0)]
        )
    }

    func testOperatorHoursClampedToSalonOpening() {
        // Salon closes at 19:00 even if the operator claims 21:00.
        let op = makeOperator(hours: [.monday: [TimeRange(LocalTime(17, 0), LocalTime(21, 0))]])
        let result = slots(duration: 60, op: op)
        XCTAssertEqual(result.last, LocalTime(18, 0))
    }
}
