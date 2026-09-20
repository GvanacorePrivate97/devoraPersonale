import Foundation

@MainActor
final class FakeCatalogRepository: CatalogRepository {

    private let store: InMemoryStore

    init(store: InMemoryStore) {
        self.store = store
    }

    func catalog() async throws -> CatalogSnapshot {
        CatalogSnapshot(salon: store.salon, services: store.services, operators: store.operators)
    }

    func services() async throws -> [Service] { store.services }
    func operators() async throws -> [Operator] { store.operators }

    func holidays(operatorId: String) async throws -> [Holiday] {
        store.holidays.filter { $0.operatorId == operatorId }
    }

    func saveService(_ service: Service, operatorIds: Set<String>) async -> AppResult<Service> {
        if service.name.trimmingCharacters(in: .whitespaces).isEmpty {
            return .failure(.validation(field: "name"))
        }
        if service.durationMinutes <= 0 { return .failure(.validation(field: "duration")) }
        var saved = service
        if saved.id.isEmpty {
            saved = Service(
                id: store.newId("svc"), name: service.name,
                durationMinutes: service.durationMinutes,
                priceCents: service.priceCents, description: service.description, featured: service.featured
            )
        }
        if store.services.contains(where: { $0.id == saved.id }) {
            let id = saved.id
            let replacement = saved
            store.services = store.services.map { $0.id == id ? replacement : $0 }
        } else {
            store.services.append(saved)
        }
        // Abilitazioni in blocco, come fa `PATCH /catalog/services/:id/operators`.
        let serviceId = saved.id
        store.operators = store.operators.map {
            var op = $0
            if operatorIds.contains(op.id) { op.serviceIds.insert(serviceId) } else { op.serviceIds.remove(serviceId) }
            return op
        }
        return .success(saved)
    }

    func createOperator(_ newOperator: NewOperator) async -> AppResult<Operator> {
        let name = newOperator.name.trimmingCharacters(in: .whitespaces)
        let email = newOperator.email.trimmingCharacters(in: .whitespaces)
        if name.isEmpty { return .failure(.validation(field: "name")) }
        if email.isEmpty { return .failure(.validation(field: "email")) }
        if store.users.contains(where: { $0.email.lowercased() == email.lowercased() }) {
            return .failure(.emailAlreadyRegistered)
        }
        let title = newOperator.title.trimmingCharacters(in: .whitespaces)
        let profile = Operator(
            id: store.newId("op"),
            name: name,
            title: title.isEmpty ? "Barbiere" : title,
            bio: title,
            specialties: [],
            weeklyHours: newOperator.weeklyHours,
            serviceIds: newOperator.serviceIds
        )
        store.operators.append(profile)
        // The account is what lets the new hire actually sign in.
        let parts = name.split(separator: " ").map(String.init)
        store.users.append(
            User(
                id: store.newId("user"),
                firstName: parts.first ?? "",
                lastName: parts.dropFirst().joined(separator: " "),
                email: email,
                phone: newOperator.phone.trimmingCharacters(in: .whitespaces),
                role: .staff,
                memberSince: .today(),
                operatorId: profile.id
            )
        )
        return .success(profile)
    }

    func updateSalon(_ salon: Salon) async -> AppResult<Void> {
        if salon.name.trimmingCharacters(in: .whitespaces).isEmpty {
            return .failure(.validation(field: "name"))
        }
        store.salon = salon
        return .success(())
    }

    func updateOperatorHours(_ operatorId: String, weeklyHours: [DayOfWeek: [TimeRange]]) async -> AppResult<Void> {
        store.operators = store.operators.map {
            guard $0.id == operatorId else { return $0 }
            var op = $0
            op.weeklyHours = weeklyHours
            return op
        }
        return .success(())
    }

    func updateOperatorServices(_ operatorId: String, serviceIds: Set<String>) async -> AppResult<Void> {
        store.operators = store.operators.map {
            guard $0.id == operatorId else { return $0 }
            var op = $0
            op.serviceIds = serviceIds
            return op
        }
        return .success(())
    }

    func addHoliday(_ holiday: Holiday) async -> AppResult<Holiday> {
        var saved = holiday
        if saved.id.isEmpty {
            saved = Holiday(id: store.newId("hol"), operatorId: holiday.operatorId, from: holiday.from, to: holiday.to, label: holiday.label)
        }
        store.holidays.append(saved)
        return .success(saved)
    }

    func removeHoliday(_ holidayId: String) async -> AppResult<Void> {
        store.holidays.removeAll { $0.id == holidayId }
        return .success(())
    }
}
