import XCTest
@testable import MenCare

private let maxChips = 4
private let maxPerDay = 2

/// Direct port of the Android `ClientHomeViewModelTest` — keep the two in sync.
@MainActor
final class ClientHomeViewModelTests: XCTestCase {

    private var store: InMemoryStore!

    override func setUp() async throws {
        store = InMemoryStore()
    }

    private func viewModel() -> ClientHomeViewModel {
        ClientHomeViewModel(
            auth: FakeAuthRepository(store: store),
            booking: FakeBookingRepository(store: store),
            catalog: FakeCatalogRepository(store: store),
            notifications: FakeNotificationRepository(store: store)
        )
    }

    func testQuickSlotsSpanAllOperatorsAndStayInsideTheCaps() async {
        store.currentUser = store.users.first { $0.id == DemoSeed.userClient }
        guard let lastVisit = store.appointments
            .filter({ $0.clientId == DemoSeed.clientMarco && $0.status == .completed })
            .max(by: { $0.start < $1.start })
        else { return XCTFail("il seed non ha visite completate per il cliente demo") }

        let viewModel = viewModel()
        await viewModel.load()
        let slots = viewModel.quickSlots

        XCTAssertFalse(slots.isEmpty)
        XCTAssertLessThanOrEqual(slots.count, maxChips)
        XCTAssertTrue(Dictionary(grouping: slots, by: \.date).values.allSatisfy { $0.count <= maxPerDay })
        // Global availability: no chip is pinned to one operator.
        XCTAssertTrue(slots.allSatisfy { $0.operatorId == nil })
        XCTAssertTrue(slots.allSatisfy { $0.serviceIds == lastVisit.serviceIds })
        XCTAssertTrue(slots.allSatisfy { $0.date >= LocalDate.today() })
        // Ordered: the nearest slot first.
        XCTAssertEqual(slots.sorted { $0.date.atTime($0.time) < $1.date.atTime($1.time) }, slots)
    }

    func testClientWithoutHistoryIsOfferedFeaturedServiceWithAnyOperator() async {
        store.currentUser = User(
            id: "user_nuovo",
            firstName: "Nuovo",
            lastName: "Cliente",
            email: "nuovo@example.com",
            phone: "+39 000 000 0000",
            role: .client,
            memberSince: LocalDate.today(),
            clientRecordId: "cli_nuovo"
        )

        let viewModel = viewModel()
        await viewModel.load()
        let slots = viewModel.quickSlots

        XCTAssertFalse(slots.isEmpty)
        XCTAssertTrue(slots.allSatisfy { $0.operatorId == nil })
        XCTAssertEqual(slots.first?.serviceIds, [DemoSeed.svcRasatura])
    }
}
