import Foundation
import Observation

/// Quanti slot rapidi mostrare in home e quanti per giorno.
private let quickSlotsPerDay = 2
private let quickSlotCount = 4

/// Uno slot prenotabile offerto in home: un tap apre il wizard gia' su quello.
struct QuickSlot: Hashable {
    let date: LocalDate
    let time: LocalTime
    /// nil = "Qualsiasi operatore".
    let operatorId: String?
    let serviceIds: [String]
}

@MainActor
@Observable
final class ClientHomeViewModel {

    private let auth: AuthRepository
    private let booking: BookingRepository
    private let catalog: CatalogRepository
    private let notificationsRepo: NotificationRepository

    let state = LoadState()

    private(set) var user: User?
    private(set) var nextAppointment: Appointment?
    private(set) var lastCompleted: Appointment?
    private(set) var services: [String: Service] = [:]
    private(set) var operators: [String: Operator] = [:]
    private(set) var unreadCount = 0

    /// Gli slot costano una chiamata in piu': la home si disegna subito e la
    /// sezione compare appena il server risponde.
    private(set) var quickSlots: [QuickSlot] = []

    init(auth: AuthRepository, booking: BookingRepository, catalog: CatalogRepository, notifications: NotificationRepository) {
        self.auth = auth
        self.booking = booking
        self.catalog = catalog
        self.notificationsRepo = notifications
    }

    func load() async {
        await state.run {
            let user = try await auth.currentUser()
            let catalogSnapshot = try await catalog.catalog()
            let appointments = try await booking.myAppointments()
            let feed = try await notificationsRepo.feed()

            self.user = user
            services = Dictionary(uniqueKeysWithValues: catalogSnapshot.services.map { ($0.id, $0) })
            operators = Dictionary(uniqueKeysWithValues: catalogSnapshot.operators.map { ($0.id, $0) })
            unreadCount = feed.unreadCount

            let now = LocalDateTime.now()
            nextAppointment = appointments
                .filter { $0.isActive && $0.start >= now.minusMinutes(30) }
                .min { $0.start < $1.start }
            lastCompleted = appointments
                .filter { $0.status == .completed }
                .max { $0.start < $1.start }
        }
        // Gli slot arrivano dopo: un errore qui non deve cancellare la home.
        await refreshQuickSlots()
    }

    /// Prime disponibilita' del salone, su qualsiasi operatore. I servizi di
    /// riferimento sono quelli dell'ultima visita (o uno in evidenza per chi non
    /// ha storico): servono solo a dimensionare la durata dello slot.
    func refreshQuickSlots() async {
        let serviceIds: [String]
        if let last = lastCompleted, !last.serviceIds.isEmpty {
            serviceIds = last.serviceIds
        } else if let fallback = defaultServiceId() {
            serviceIds = [fallback]
        } else {
            quickSlots = []
            return
        }
        quickSlots = (try? await nearestSlots(serviceIds: serviceIds)) ?? []
    }

    private func defaultServiceId() -> String? {
        let all = services.values.sorted { $0.name < $1.name }
        return (all.first { $0.featured } ?? all.first)?.id
    }

    /// I primi giorni con posto li dice il server (`/booking/days`), poi si
    /// chiedono gli orari solo per quei giorni: prima si interrogava un giorno
    /// alla volta, anche quando il salone era chiuso.
    private func nearestSlots(serviceIds: [String]) async throws -> [QuickSlot] {
        let today = LocalDate.today()
        let range = try await booking.days(
            operatorId: nil, serviceIds: serviceIds, from: today, to: today.plusDays(14)
        )
        var found: [QuickSlot] = []
        for day in range.available.sorted() where found.count < quickSlotCount {
            let slots = try await booking.availability(operatorId: nil, serviceIds: serviceIds, date: day).slots
            for slot in slots.prefix(min(quickSlotsPerDay, quickSlotCount - found.count)) {
                found.append(QuickSlot(date: day, time: slot, operatorId: nil, serviceIds: serviceIds))
            }
        }
        return found
    }
}
