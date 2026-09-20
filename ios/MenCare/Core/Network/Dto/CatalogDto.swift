import Foundation

// `GET /v1/catalog` e le rotte del listino, della squadra e del salone.

struct ServiceDto: Decodable {
    let id: String
    let name: String
    let durationMinutes: Int
    let priceCents: Int64
    let description: String?
    let featured: Bool
    let active: Bool

    func toDomain() -> Service {
        Service(
            id: id,
            name: name,
            durationMinutes: durationMinutes,
            priceCents: priceCents,
            description: description,
            featured: featured
        )
    }
}

struct OperatorDto: Decodable {
    let id: String
    let name: String
    let title: String
    let bio: String
    let specialties: [String]
    let isOwner: Bool
    let active: Bool
    let weeklyHours: [String: [TimeRangeDto]]
    let serviceIds: [String]

    func toDomain() -> Operator {
        Operator(
            id: id,
            name: name,
            title: title,
            bio: bio,
            specialties: specialties,
            isOwner: isOwner,
            weeklyHours: WeeklyHoursDto.toDomain(weeklyHours),
            serviceIds: Set(serviceIds)
        )
    }
}

struct SalonDto: Decodable {
    let name: String
    let address: String
    let city: String
    let phone: String?
    let timezone: String
    let weeklyHours: [String: [TimeRangeDto]]

    func toDomain() -> Salon {
        Salon(
            name: name,
            address: address,
            city: city,
            weeklyHours: WeeklyHoursDto.toDomain(weeklyHours)
        )
    }
}

/// Una sola chiamata all'avvio: salone, listino e squadra insieme.
struct CatalogDto: Decodable {
    let salon: SalonDto
    let services: [ServiceDto]
    let operators: [OperatorDto]
}

struct ServicesResponseDto: Decodable {
    let services: [ServiceDto]
}

struct OperatorsResponseDto: Decodable {
    let operators: [OperatorDto]
}

struct HolidayDto: Decodable {
    let id: String
    let operatorId: String
    let from: LocalDate
    let to: LocalDate
    let label: String?

    func toDomain() -> Holiday {
        Holiday(id: id, operatorId: operatorId, from: from, to: to, label: label ?? "")
    }
}

struct HolidaysResponseDto: Decodable {
    let holidays: [HolidayDto]
}

// MARK: - Corpi delle scritture (solo titolare)

struct ServiceWriteDto: Encodable {
    let name: String
    let durationMinutes: Int
    let priceCents: Int64
    let description: String?
    let featured: Bool
}

struct ServiceOperatorsDto: Encodable {
    let operatorIds: [String]
}

struct OperatorWriteDto: Encodable {
    let name: String
    let email: String
    let phone: String
    let title: String?
    let serviceIds: [String]
    let weeklyHours: [String: [TimeRangeDto]]
}

/// `POST /catalog/operators` risponde con il profilo e l'account appena creato:
/// la password provvisoria torna li' e solo li'.
struct NewOperatorResultDto: Decodable {
    struct Account: Decodable {
        let userId: String
        let email: String
        let temporaryPassword: String
    }

    let `operator`: OperatorDto
    let account: Account
}

struct OperatorHoursDto: Encodable {
    let weeklyHours: [String: [TimeRangeDto]]
}

struct OperatorServicesDto: Encodable {
    let serviceIds: [String]
}

struct SalonWriteDto: Encodable {
    let name: String
    let address: String
    let city: String
    let weeklyHours: [String: [TimeRangeDto]]
}

struct HolidayWriteDto: Encodable {
    let from: LocalDate
    let to: LocalDate
    let label: String?
}
