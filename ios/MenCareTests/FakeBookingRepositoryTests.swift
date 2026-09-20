import XCTest
@testable import MenCare

/// Direct port of the Android `FakeBookingRepositoryTest` — keep the two in sync.
@MainActor
final class FakeBookingRepositoryTests: XCTestCase {

    private var store: InMemoryStore!
    private var repository: FakeBookingRepository!

    /// Next Tuesday two weeks out: every seeded operator works, salon open.
    private var day: LocalDate {
        let base = LocalDate.today().plusWeeks(2)
        let offset = (DayOfWeek.tuesday.rawValue - base.dayOfWeek.rawValue + 7) % 7
        return base.plusDays(offset)
    }

    override func setUp() async throws {
        store = InMemoryStore()
        repository = FakeBookingRepository(store: store)
        signIn(as: DemoSeed.clientMarco)
    }

    /// La coda e gli appuntamenti "miei" ora vengono dall'account con la
    /// sessione aperta, come fa il server dai claims del token: il test dichiara
    /// chi sta usando l'app invece di passare un id a ogni chiamata.
    private func signIn(as clientId: String) {
        if let seeded = store.users.first(where: { $0.clientRecordId == clientId }) {
            store.currentUser = seeded
            return
        }
        store.currentUser = User(
            id: "user_\(clientId)",
            firstName: "Test",
            lastName: clientId,
            email: "\(clientId)@example.com",
            phone: "+390000000000",
            role: .client,
            memberSince: .today(),
            clientRecordId: clientId
        )
    }

    private func request(
        time: LocalTime = LocalTime(9, 0),
        operatorId: String? = DemoSeed.opLuca,
        services: [String] = [DemoSeed.svcTaglio]
    ) -> BookingRequest {
        BookingRequest(
            clientId: DemoSeed.clientMarco,
            operatorId: operatorId,
            serviceIds: services,
            start: day.atTime(time)
        )
    }

    func testBookingFreeSlotSucceedsAndTotalsServices() async {
        let result = await repository.book(request(services: [DemoSeed.svcTaglio, DemoSeed.svcRasatura]))
        guard let appointment = result.value else { return XCTFail("expected success, got \(result)") }
        XCTAssertEqual(appointment.durationMinutes, 75)
        XCTAssertEqual(appointment.totalPriceCents, 2200)
    }

    func testBookingSameSlotTwiceFailsWithSlotNoLongerAvailable() async {
        _ = await repository.book(request())
        let second = await repository.book(request())
        XCTAssertEqual(second.error, .slotNoLongerAvailable)
    }

    func testAnyOperatorBookingPicksEligibleFreeOperator() async {
        let result = await repository.book(request(operatorId: nil))
        guard let appointment = result.value else { return XCTFail("expected success, got \(result)") }
        let op = store.operators.first { $0.id == appointment.operatorId }
        XCTAssertNotNil(op)
        XCTAssertTrue(op!.serviceIds.contains(DemoSeed.svcTaglio))
    }

    func testEveryBookingIsConfirmedStraightAway() async {
        let result = await repository.book(
            request(time: LocalTime(10, 0), operatorId: DemoSeed.opGiulia, services: [DemoSeed.svcBaby])
        )
        guard let appointment = result.value else { return XCTFail("expected success, got \(result)") }
        XCTAssertEqual(appointment.status, .confirmed)
    }

    func testWaitlistPositionsAreFifoPerQueue() async {
        signIn(as: "cli_a")
        let first = await repository.joinWaitlist(
            date: day, time: LocalTime(17, 30),
            operatorId: DemoSeed.opLuca, serviceIds: [DemoSeed.svcTaglio]
        )
        signIn(as: "cli_b")
        let second = await repository.joinWaitlist(
            date: day, time: LocalTime(17, 30),
            operatorId: DemoSeed.opLuca, serviceIds: [DemoSeed.svcTaglio]
        )
        XCTAssertEqual(first.value?.position, 1)
        XCTAssertEqual(second.value?.position, 2)
    }

    func testReschedulingLandsOnQuarterHourOffClientSlotGrid() async {
        guard let appointment = await repository.book(request(time: LocalTime(9, 0))).value else {
            return XCTFail("booking failed")
        }
        // 09:45 is never a client-bookable start (30-min grid) but the owner may drop a card there.
        let moved = await repository.reschedule(appointment.id, newStart: day.atTime(LocalTime(9, 45)), newOperatorId: DemoSeed.opLuca)
        XCTAssertEqual(moved.value?.start, day.atTime(LocalTime(9, 45)))
        XCTAssertEqual(store.appointments.first { $0.id == appointment.id }?.start, day.atTime(LocalTime(9, 45)))
    }

    func testReschedulingOntoBusyOperatorFails() async {
        guard let first = await repository.book(request(time: LocalTime(9, 0))).value else {
            return XCTFail("booking failed")
        }
        _ = await repository.book(request(time: LocalTime(10, 0)))
        let result = await repository.reschedule(first.id, newStart: day.atTime(LocalTime(10, 0)), newOperatorId: DemoSeed.opLuca)
        XCTAssertNotNil(result.error)
        XCTAssertEqual(store.appointments.first { $0.id == first.id }?.start, day.atTime(LocalTime(9, 0)))
    }

    func testReschedulingOutsideOperatorHoursFails() async {
        guard let appointment = await repository.book(request()).value else {
            return XCTFail("booking failed")
        }
        let result = await repository.reschedule(appointment.id, newStart: day.atTime(LocalTime(20, 0)), newOperatorId: DemoSeed.opLuca)
        XCTAssertNotNil(result.error)
    }

    func testCompletingAppointmentUpdatesClientRecord() async {
        guard let appointment = await repository.book(request()).value else {
            return XCTFail("booking failed")
        }
        guard let before = store.clients.first(where: { $0.id == DemoSeed.clientMarco }) else {
            return XCTFail("seed client missing")
        }
        _ = await repository.markCompleted(appointment.id)
        let after = store.clients.first { $0.id == DemoSeed.clientMarco }
        XCTAssertEqual(after?.visitCount, before.visitCount + 1)
        XCTAssertEqual(after?.lifetimeSpendCents, before.lifetimeSpendCents + appointment.totalPriceCents)
    }

    /// Fills Luca's whole `day` with one-hour appointments.
    private func fillLucasDay() async -> [String] {
        var ids: [String] = []
        for hour in [9, 10, 11, 12, 14, 15, 16, 17, 18] {
            let result = await repository.book(request(time: LocalTime(hour, 0), services: [DemoSeed.svcTaglioBarba]))
            if let appointment = result.value { ids.append(appointment.id) }
        }
        return ids
    }

    func testDayWithEverySlotTakenIsFullyBookedClosingDayIsNot() async {
        _ = await fillLucasDay()
        let sunday = day.plusDays(DayOfWeek.sunday.rawValue - day.dayOfWeek.rawValue)
        let range = try? await repository.days(
            operatorId: DemoSeed.opLuca, serviceIds: [DemoSeed.svcTaglio], from: day, to: sunday
        )
        XCTAssertEqual(range?.fullyBooked, [day])
        XCTAssertFalse(range?.available.contains(day) ?? true)
    }

    func testJoiningTheSameQueueTwiceKeepsASingleEntry() async {
        signIn(as: "cli_a")
        for _ in 0..<2 {
            _ = await repository.joinWaitlist(date: day, time: nil, operatorId: DemoSeed.opLuca, serviceIds: [DemoSeed.svcTaglio])
        }
        let mine = try? await repository.myWaitlist()
        XCTAssertEqual(mine?.count, 1)
    }

    func testLeavingTheQueueMovesTheNextClientUp() async {
        signIn(as: "cli_a")
        let first = await repository.joinWaitlist(date: day, time: nil, operatorId: DemoSeed.opLuca, serviceIds: [DemoSeed.svcTaglio])
        signIn(as: "cli_b")
        _ = await repository.joinWaitlist(date: day, time: nil, operatorId: DemoSeed.opLuca, serviceIds: [DemoSeed.svcTaglio])
        guard let firstEntry = first.value else { return XCTFail("expected success") }
        _ = await repository.leaveWaitlist(firstEntry.id)
        let second = try? await repository.myWaitlist()
        XCTAssertEqual(second?.first?.position, 1)
    }

    func testCancellationNotifiesTheFirstClientInLine() async {
        let booked = await fillLucasDay()
        _ = await repository.joinWaitlist(date: day, time: nil, operatorId: DemoSeed.opLuca, serviceIds: [DemoSeed.svcTaglio])
        let before = store.notifications.count

        _ = await repository.cancel(booked[0], by: .salon)

        XCTAssertEqual(store.notifications.count, before + 1)
        XCTAssertEqual(store.notifications.last?.userId, DemoSeed.userClient)
        let mine = (try? await repository.myWaitlist()) ?? []
        XCTAssertFalse(mine.contains { $0.date == day })
    }
}
