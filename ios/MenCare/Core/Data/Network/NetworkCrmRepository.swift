import Foundation

@MainActor
final class NetworkCrmRepository: CrmRepository {

    /// La rubrica e' paginata a cursore; le schermate ne mostrano una lista
    /// piatta, quindi si chiede una pagina generosa e basta.
    private let pageSize = 100

    private let client: ApiClient

    init(client: ApiClient) {
        self.client = client
    }

    func clients(query: String, segment: ClientSegment) async throws -> [ClientRecord] {
        try await apiThrowing {
            let trimmed = query.trimmingCharacters(in: .whitespaces)
            let page = try await client.send(
                CrmEndpoint.clients(
                    query: trimmed.isEmpty ? nil : trimmed, segment: segment, limit: pageSize, cursor: nil
                ),
                as: ClientsPageDto.self
            )
            return page.clients.map { $0.toDomain() }
        }
    }

    func clientDetail(_ id: String) async throws -> ClientDetail {
        try await apiThrowing {
            let dto = try await client.send(CrmEndpoint.client(id), as: ClientDetailDto.self)
            var record = dto.client.toDomain()
            record.favoriteOperatorId = dto.insights?.favoriteOperatorId
            record.averageDaysBetweenVisits = dto.insights?.averageDaysBetweenVisits
            return ClientDetail(
                client: record,
                history: dto.appointments
                    .map { $0.toDomain(clientId: dto.client.id) }
                    .sorted { $0.start > $1.start }
            )
        }
    }

    func createClient(firstName: String, lastName: String, phone: String) async -> AppResult<ClientRecord> {
        await apiResult {
            try await client.send(
                CrmEndpoint.createClient(
                    CreateClientRequestDto(firstName: firstName, lastName: lastName, phone: phone, email: nil)
                ),
                as: ClientDto.self
            ).toDomain()
        }
    }
}
