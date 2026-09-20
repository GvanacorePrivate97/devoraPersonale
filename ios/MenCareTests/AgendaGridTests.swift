import XCTest
@testable import MenCare

/// Direct port of the Android `AgendaGridTest` — keep the two in sync.
final class AgendaGridTests: XCTestCase {

    func testLongAppointmentIsSizedByItsDurationAlone() {
        // 45 minutes already clears the two-line height, so the free time after
        // it changes nothing.
        let height = AgendaGrid.cardHeight(minutes: 45, freeMinutesAfter: 0)
        XCTAssertEqual(height, AgendaGrid.height(minutes: 45) - AgendaGrid.cardGap, accuracy: 0.01)
        XCTAssertEqual(AgendaGrid.serviceLines(cardHeight: height), 2)
    }

    func testHalfAnHourStillHasRoomForOneLineOfServices() {
        let height = AgendaGrid.cardHeight(minutes: 30, freeMinutesAfter: 0)
        XCTAssertEqual(AgendaGrid.serviceLines(cardHeight: height), 1)
    }

    func testShortestServiceKeepsItsFirstRowWhole() {
        // Ten minutes is 16pt of rail: without a floor the name would be cut.
        let height = AgendaGrid.cardHeight(minutes: 10, freeMinutesAfter: 0)
        XCTAssertEqual(height, AgendaGrid.minCardHeight, accuracy: 0.01)
        XCTAssertGreaterThan(height, AgendaGrid.height(minutes: 10))
    }

    func testShortAppointmentBorrowsTheFreeTimeInFrontOfIt() {
        let alone = AgendaGrid.cardHeight(minutes: 10, freeMinutesAfter: 60)
        XCTAssertEqual(alone, AgendaGrid.twoServiceLinesHeight, accuracy: 0.01)
        XCTAssertEqual(AgendaGrid.serviceLines(cardHeight: alone), 2)

        let squeezed = AgendaGrid.cardHeight(minutes: 10, freeMinutesAfter: 20)
        XCTAssertEqual(AgendaGrid.serviceLines(cardHeight: squeezed), 1)
    }

    func testCardNeverGrowsPastTheFreeTimeItWasGiven() {
        // 10 minutes booked + 15 free is not enough for a services line, and the
        // card stops there instead of running over what comes next.
        let height = AgendaGrid.cardHeight(minutes: 10, freeMinutesAfter: 15)
        XCTAssertLessThanOrEqual(height, AgendaGrid.height(minutes: 25))
        XCTAssertEqual(AgendaGrid.serviceLines(cardHeight: height), 0)
    }

    func testFreeTimeRunsToTheNextBookingOrToTheEndOfTheDay() {
        // 09:00 + 30' with the next card at 10:00 (60' from the rail's start).
        XCTAssertEqual(AgendaGrid.freeMinutesAfter(endMinutes: 30, busyStarts: [60, 180]), 30)
        // Nothing after it: the rest of the rail is free.
        XCTAssertEqual(AgendaGrid.freeMinutesAfter(endMinutes: 30, busyStarts: []), AgendaGrid.dayMinutes - 30)
        // Back to back.
        XCTAssertEqual(AgendaGrid.freeMinutesAfter(endMinutes: 30, busyStarts: [30]), 0)
        // What starts before the card ends is not free time.
        XCTAssertEqual(AgendaGrid.freeMinutesAfter(endMinutes: 30, busyStarts: [0, 60]), 30)
    }

    func testTapResolvesToTheQuarterHourUnderIt() {
        let hour = AgendaGrid.hourHeight
        XCTAssertEqual(AgendaGrid.time(atY: hour * 0.1), LocalTime(9, 0))
        XCTAssertEqual(AgendaGrid.time(atY: hour * 0.55), LocalTime(9, 30))
        XCTAssertNil(AgendaGrid.time(atY: -1))
        XCTAssertNil(AgendaGrid.time(atY: hour * 11))
    }
}
