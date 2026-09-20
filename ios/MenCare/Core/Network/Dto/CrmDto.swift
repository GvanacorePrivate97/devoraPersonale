import Foundation

// Rubrica clienti — docs/API.md §Clienti (CRM).

struct ClientDto: Decodable {
    let id: String
    let firstName: String
    let lastName: String
    let phone: String
    let email: String?
    let customerSince: LocalDate
    let visitCount: Int
    /// Il totale speso lo vede solo il titolare: allo staff il campo non arriva
    /// proprio, e qui vale 0.
    let lifetimeSpendCents: Int64?
    let noShowCount: Int
    let lastVisit: LocalDate?
    let preferredServiceIds: [String]
    let preferredOperatorId: String?
    let marketingOptIn: Bool

    func toDomain() -> ClientRecord {
        ClientRecord(
            id: id,
            firstName: firstName,
            lastName: lastName,
            phone: phone,
            email: email ?? "",
            customerSince: customerSince,
            // Visite, ultima visita e spesa sono statistiche del server: le app
            // non le ricalcolano piu' scorrendo lo storico.
            visitCount: visitCount,
            lifetimeSpendCents: lifetimeSpendCents ?? 0,
            noShowCount: noShowCount,
            lastVisit: lastVisit,
            preferredServiceIds: preferredServiceIds,
            preferredOperatorId: preferredOperatorId,
            marketingOptIn: marketingOptIn
        )
    }
}

struct ClientsPageDto: Decodable {
    let clients: [ClientDto]
    let nextCursor: String?
}

/// `GET /crm/clients/:id` — scheda piu' storico gia' pronto, cosi' la schermata
/// non deve incrociare due chiamate.
struct ClientDetailDto: Decodable {
    struct HistoryEntry: Decodable {
        let id: String
        let operatorId: String
        let date: LocalDate
        let time: LocalTime
        let durationMinutes: Int
        let status: String
        let channel: String
        let serviceIds: [String]
        let serviceNames: [String]
        let totalPriceCents: Int64?

        func toDomain(clientId: String) -> Appointment {
            Appointment(
                id: id,
                clientId: clientId,
                operatorId: operatorId,
                serviceIds: serviceIds,
                start: date.atTime(time),
                durationMinutes: durationMinutes,
                totalPriceCents: totalPriceCents ?? 0,
                status: WireEnum.appointmentStatus(status),
                channel: WireEnum.channel(channel)
            )
        }
    }

    /// Abitudini calcolate dal server sulle visite completate.
    struct Insights: Decodable {
        let favoriteOperatorId: String?
        let averageDaysBetweenVisits: Int?
    }

    let client: ClientDto
    let insights: Insights?
    let appointments: [HistoryEntry]
}

struct CreateClientRequestDto: Encodable {
    let firstName: String
    let lastName: String
    let phone: String
    let email: String?
}
