import Foundation

@MainActor
final class FakeTimeBlockRepository: TimeBlockRepository {

    private let store: InMemoryStore

    init(store: InMemoryStore) {
        self.store = store
    }

    func blocksForOperator(_ operatorId: String?, date: LocalDate) async throws -> [TimeBlock] {
        store.timeBlocks
            .filter { (operatorId == nil || $0.operatorId == operatorId) && $0.date == date }
            .sorted { $0.range.start < $1.range.start }
    }

    func blocksForWeek(_ weekStart: LocalDate, operatorId: String?) async throws -> [TimeBlock] {
        let weekEnd = weekStart.plusDays(7)
        return store.timeBlocks.filter {
            (operatorId == nil || $0.operatorId == operatorId) && $0.date >= weekStart && $0.date < weekEnd
        }
    }

    func upcomingBlocks(from: LocalDate, weeks: Int) async throws -> [TimeBlock] {
        let horizon = from.plusWeeks(max(weeks, 1))
        return store.timeBlocks
            .filter { $0.date >= from && $0.date < horizon }
            .sorted { ($0.date, $0.range.start) < ($1.date, $1.range.start) }
    }

    func conflictsFor(_ block: TimeBlock) async throws -> [Appointment] {
        store.appointments.filter {
            $0.operatorId == block.operatorId && $0.date == block.date && $0.isActive &&
                block.range.overlaps(TimeRange($0.time, $0.end.time))
        }
    }

    func createBlock(_ block: TimeBlock) async -> AppResult<TimeBlock> {
        if !((try? await conflictsFor(block)) ?? []).isEmpty {
            return .failure(.validation(field: "conflicts"))
        }
        var saved = block
        if saved.id.isEmpty {
            saved = TimeBlock(
                id: store.newId("blk"), operatorId: block.operatorId, reason: block.reason,
                date: block.date, range: block.range, label: block.label
            )
        }
        store.timeBlocks.append(saved)
        return .success(saved)
    }

    func deleteBlock(_ blockId: String) async -> AppResult<Void> {
        store.timeBlocks.removeAll { $0.id == blockId }
        return .success(())
    }
}
