import Foundation

@MainActor
final class FakeCrmRepository: CrmRepository {

    private let store: InMemoryStore

    init(store: InMemoryStore) {
        self.store = store
    }

    func clientDetail(_ id: String) async throws -> ClientDetail {
        guard var record = store.clients.first(where: { $0.id == id }) else { throw AppError.notFound }
        let history = store.appointments
            .filter { $0.clientId == id }
            .sorted { $0.start > $1.start }
        // Stessa regola del server: il più frequente fra le visite completate
        // (a parità il più recente) e la media per difetto dei giorni fra l'una e l'altra.
        let completed = history.filter { $0.status == .completed }
        var counts: [String: Int] = [:]
        for apt in completed { counts[apt.operatorId, default: 0] += 1 }
        var best = 0
        for apt in completed where counts[apt.operatorId]! > best {
            best = counts[apt.operatorId]!
            record.favoriteOperatorId = apt.operatorId
        }
        let days = Array(Set(completed.map(\.date))).sorted()
        if days.count >= 2 {
            let total = zip(days, days.dropFirst()).reduce(0) { $0 + ($1.1.epochDay - $1.0.epochDay) }
            record.averageDaysBetweenVisits = total / (days.count - 1)
        }
        return ClientDetail(client: record, history: history)
    }

    func clients(query: String, segment: ClientSegment) async throws -> [ClientRecord] {
        let today = LocalDate.today()
        let q = query.trimmingCharacters(in: .whitespaces)
        return store.clients
            .filter { c in
                q.isEmpty ||
                    c.fullName.range(of: q, options: .caseInsensitive) != nil ||
                    c.phone.replacingOccurrences(of: " ", with: "").contains(q.replacingOccurrences(of: " ", with: "")) ||
                    c.email.range(of: q, options: .caseInsensitive) != nil
            }
            .filter { c in
                switch segment {
                case .tutti: true
                case .fedeli: c.isLoyal(today)
                case .inattivi60: c.isInactiveSince(today)
                case .noShow: c.noShowCount > 0
                case .topSpesa: c.lifetimeSpendCents >= 40_000
                }
            }
            .sorted { $0.lastName < $1.lastName }
    }

    func createClient(firstName: String, lastName: String, phone: String) async -> AppResult<ClientRecord> {
        let first = firstName.trimmingCharacters(in: .whitespaces)
        let last = lastName.trimmingCharacters(in: .whitespaces)
        if first.isEmpty || last.isEmpty { return .failure(.validation(field: "name")) }
        let record = ClientRecord(
            id: store.newId("cli"),
            firstName: first,
            lastName: last,
            phone: phone.trimmingCharacters(in: .whitespaces),
            email: "",
            customerSince: .today(),
            visitCount: 0,
            lifetimeSpendCents: 0,
            noShowCount: 0,
            lastVisit: nil
        )
        store.clients.append(record)
        return .success(record)
    }
}
