import Foundation

@MainActor
final class NetworkTimeBlockRepository: TimeBlockRepository {

    private let client: ApiClient

    init(client: ApiClient) {
        self.client = client
    }

    func blocksForOperator(_ operatorId: String?, date: LocalDate) async throws -> [TimeBlock] {
        try await apiThrowing {
            try await client.send(BlocksEndpoint.day(operatorId: operatorId, date: date), as: BlocksResponseDto.self)
                .blocks.compactMap { $0.toDomain() }
                .sorted { $0.range.start < $1.range.start }
        }
    }

    func blocksForWeek(_ weekStart: LocalDate, operatorId: String?) async throws -> [TimeBlock] {
        try await apiThrowing {
            try await client.send(
                BlocksEndpoint.week(operatorId: operatorId, weekStart: weekStart), as: BlocksResponseDto.self
            )
            .blocks.compactMap { $0.toDomain() }
        }
    }

    func upcomingBlocks(from: LocalDate, weeks: Int) async throws -> [TimeBlock] {
        // L'API ragiona per settimane: le prossime `weeks` si chiedono insieme e
        // si uniscono qui. Serve alla scheda operatore del titolare, che mostra
        // ferie e corsi come periodi.
        let starts = (0..<max(weeks, 1)).map { from.plusWeeks($0) }
        return try await apiThrowing {
            var all: [TimeBlock] = []
            for weekStart in starts {
                all += try await blocksForWeek(weekStart, operatorId: nil)
            }
            return all
                .filter { $0.date >= from }
                .sorted { ($0.date, $0.range.start) < ($1.date, $1.range.start) }
        }
    }

    func conflictsFor(_ block: TimeBlock) async throws -> [Appointment] {
        try await apiThrowing {
            try await client.send(
                BlocksEndpoint.conflicts(
                    operatorId: block.operatorId.isEmpty ? nil : block.operatorId,
                    date: block.date, start: block.range.start, end: block.range.end
                ),
                as: AppointmentsResponseDto.self
            )
            .appointments.map { $0.toDomain() }
        }
    }

    func createBlock(_ block: TimeBlock) async -> AppResult<TimeBlock> {
        await apiResult {
            let dto = try await client.send(
                BlocksEndpoint.create(
                    BlockWriteDto(
                        operatorId: block.operatorId.isEmpty ? nil : block.operatorId,
                        reason: WireEnum.blockReasonWire(block.reason),
                        date: block.date,
                        start: block.range.start,
                        end: block.range.end,
                        label: block.label
                    )
                ),
                as: TimeBlockDto.self
            )
            guard let created = dto.toDomain() else { throw AppError.unknown(message: nil) }
            return created
        }
    }

    func deleteBlock(_ blockId: String) async -> AppResult<Void> {
        await apiResult { _ = try await client.send(BlocksEndpoint.delete(blockId)) }
    }
}
