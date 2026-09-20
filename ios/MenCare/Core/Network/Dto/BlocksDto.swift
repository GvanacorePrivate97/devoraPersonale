import Foundation

// Permessi e assenze — docs/API.md §Permessi e assenze.

struct TimeBlockDto: Decodable {
    let id: String
    let operatorId: String
    let reason: String
    let date: LocalDate
    let start: LocalTime
    let end: LocalTime
    let label: String?

    /// Una fascia con fine prima dell'inizio non e' rappresentabile: si scarta
    /// invece di far cadere l'app dentro la precondizione di `TimeRange`.
    func toDomain() -> TimeBlock? {
        guard start < end else { return nil }
        return TimeBlock(
            id: id,
            operatorId: operatorId,
            reason: WireEnum.blockReason(reason),
            date: date,
            range: TimeRange(start, end),
            label: label
        )
    }
}

struct BlocksResponseDto: Decodable {
    let blocks: [TimeBlockDto]
}

struct BlockWriteDto: Encodable {
    let operatorId: String?
    let reason: String
    let date: LocalDate
    let start: LocalTime
    let end: LocalTime
    let label: String?
}
