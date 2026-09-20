import XCTest
@testable import MenCare

final class CoreTimeTests: XCTestCase {

    func testCivilRoundTrip() {
        let date = LocalDate(year: 2026, month: 9, day: 14)
        XCTAssertEqual(date.year, 2026)
        XCTAssertEqual(date.month, 9)
        XCTAssertEqual(date.day, 14)
        XCTAssertEqual(date.dayOfWeek, .monday)
    }

    func testEpochReference() {
        XCTAssertEqual(LocalDate(year: 1970, month: 1, day: 1).epochDay, 0)
        XCTAssertEqual(LocalDate(year: 1970, month: 1, day: 1).dayOfWeek, .thursday)
    }

    func testPlusDaysAcrossMonths() {
        let date = LocalDate(year: 2026, month: 1, day: 31).plusDays(1)
        XCTAssertEqual(date, LocalDate(year: 2026, month: 2, day: 1))
    }

    func testPlusMonthsClampsDay() {
        let date = LocalDate(year: 2026, month: 1, day: 31).plusMonths(1)
        XCTAssertEqual(date, LocalDate(year: 2026, month: 2, day: 28))
    }

    func testLeapYear() {
        XCTAssertEqual(LocalDate.lengthOfMonth(year: 2024, month: 2), 29)
        XCTAssertEqual(LocalDate.lengthOfMonth(year: 2026, month: 2), 28)
    }

    func testTimeArithmeticWrapsAtMidnight() {
        XCTAssertEqual(LocalTime(23, 45).plusMinutes(30), LocalTime(0, 15))
        XCTAssertEqual(LocalTime(0, 15).minusMinutes(30), LocalTime(23, 45))
    }

    func testDateTimeCarriesDays() {
        let dt = LocalDate(year: 2026, month: 9, day: 14).atTime(LocalTime(23, 30)).plusMinutes(45)
        XCTAssertEqual(dt.date, LocalDate(year: 2026, month: 9, day: 15))
        XCTAssertEqual(dt.time, LocalTime(0, 15))
    }
}
