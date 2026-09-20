import Foundation

@MainActor
final class NetworkCatalogRepository: CatalogRepository {

    private let client: ApiClient

    init(client: ApiClient) {
        self.client = client
    }

    func catalog() async throws -> CatalogSnapshot {
        try await apiThrowing {
            let dto = try await client.send(CatalogEndpoint.catalog, as: CatalogDto.self)
            return CatalogSnapshot(
                salon: dto.salon.toDomain(),
                services: dto.services.filter(\.active).map { $0.toDomain() },
                operators: dto.operators.filter(\.active).map { $0.toDomain() }
            )
        }
    }

    func services() async throws -> [Service] {
        try await apiThrowing {
            try await client.send(CatalogEndpoint.services, as: ServicesResponseDto.self)
                .services.filter(\.active).map { $0.toDomain() }
        }
    }

    func operators() async throws -> [Operator] {
        try await apiThrowing {
            try await client.send(CatalogEndpoint.operators, as: OperatorsResponseDto.self)
                .operators.filter(\.active).map { $0.toDomain() }
        }
    }

    func holidays(operatorId: String) async throws -> [Holiday] {
        try await apiThrowing {
            try await client.send(CatalogEndpoint.holidays(operatorId: operatorId), as: HolidaysResponseDto.self)
                .holidays.map { $0.toDomain() }
        }
    }

    func saveService(_ service: Service, operatorIds: Set<String>) async -> AppResult<Service> {
        await apiResult {
            let body = ServiceWriteDto(
                name: service.name,
                durationMinutes: service.durationMinutes,
                priceCents: service.priceCents,
                description: service.description,
                featured: service.featured
            )
            let saved = try await client.send(
                service.id.isEmpty
                    ? CatalogEndpoint.createService(body)
                    : CatalogEndpoint.updateService(service.id, body),
                as: ServiceDto.self
            )
            // Le abilitazioni in una scrittura sola: prima erano N chiamate, una
            // per operatore, e bastava che una saltasse per lasciare il listino
            // a meta'.
            let withOperators = try await client.send(
                CatalogEndpoint.setServiceOperators(saved.id, ServiceOperatorsDto(operatorIds: Array(operatorIds))),
                as: ServiceDto.self
            )
            return withOperators.toDomain()
        }
    }

    func createOperator(_ newOperator: NewOperator) async -> AppResult<Operator> {
        await apiResult {
            let result = try await client.send(
                CatalogEndpoint.createOperator(
                    OperatorWriteDto(
                        name: newOperator.name,
                        email: newOperator.email,
                        phone: newOperator.phone,
                        title: newOperator.title.isEmpty ? nil : newOperator.title,
                        serviceIds: Array(newOperator.serviceIds),
                        weeklyHours: WeeklyHoursDto.toWire(newOperator.weeklyHours)
                    )
                ),
                as: NewOperatorResultDto.self
            )
            return result.operator.toDomain()
        }
    }

    func updateSalon(_ salon: Salon) async -> AppResult<Void> {
        await apiResult {
            _ = try await client.send(
                CatalogEndpoint.updateSalon(
                    SalonWriteDto(
                        name: salon.name,
                        address: salon.address,
                        city: salon.city,
                        weeklyHours: WeeklyHoursDto.toWire(salon.weeklyHours)
                    )
                ),
                as: SalonDto.self
            )
        }
    }

    func updateOperatorHours(_ operatorId: String, weeklyHours: [DayOfWeek: [TimeRange]]) async -> AppResult<Void> {
        await apiResult {
            _ = try await client.send(
                CatalogEndpoint.setOperatorHours(
                    operatorId, OperatorHoursDto(weeklyHours: WeeklyHoursDto.toWire(weeklyHours))
                ),
                as: OperatorDto.self
            )
        }
    }

    func updateOperatorServices(_ operatorId: String, serviceIds: Set<String>) async -> AppResult<Void> {
        await apiResult {
            _ = try await client.send(
                CatalogEndpoint.setOperatorServices(operatorId, OperatorServicesDto(serviceIds: Array(serviceIds))),
                as: OperatorDto.self
            )
        }
    }

    func addHoliday(_ holiday: Holiday) async -> AppResult<Holiday> {
        await apiResult {
            try await client.send(
                CatalogEndpoint.addHoliday(
                    operatorId: holiday.operatorId,
                    HolidayWriteDto(
                        from: holiday.from, to: holiday.to,
                        label: holiday.label.isEmpty ? nil : holiday.label
                    )
                ),
                as: HolidayDto.self
            ).toDomain()
        }
    }

    func removeHoliday(_ holidayId: String) async -> AppResult<Void> {
        await apiResult { _ = try await client.send(CatalogEndpoint.deleteHoliday(holidayId)) }
    }
}
