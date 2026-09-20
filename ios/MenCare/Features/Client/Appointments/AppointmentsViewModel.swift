import Foundation
import Observation

@MainActor
@Observable
final class AppointmentsViewModel {

    private let auth: AuthRepository
    private let booking: BookingRepository
    private let catalog: CatalogRepository

    let state = LoadState()

    private(set) var upcoming: [Appointment] = []
    private(set) var past: [Appointment] = []
    private(set) var waitlist: [WaitlistEntry] = []
    private(set) var services: [String: Service] = [:]
    private(set) var operators: [String: Operator] = [:]

    private(set) var totalVisits = 0
    /// Nome di chi l'ha servito piu' spesso. Al posto della spesa: le cifre le
    /// vede solo il titolare.
    private(set) var favoriteOperatorName: String?
    private(set) var avgDaysBetweenVisits: Int?
    private(set) var lastCompleted: Appointment?

    /// Un annullamento o un'uscita dalla coda che non riesce va detto: prima
    /// spariva in silenzio e la riga restava dov'era senza spiegazioni.
    var actionError: String?

    init(auth: AuthRepository, booking: BookingRepository, catalog: CatalogRepository) {
        self.auth = auth
        self.booking = booking
        self.catalog = catalog
    }

    func load() async {
        await state.run {
            let catalogSnapshot = try await catalog.catalog()
            let appointments = try await booking.myAppointments()
            let queue = try await booking.myWaitlist()

            services = Dictionary(uniqueKeysWithValues: catalogSnapshot.services.map { ($0.id, $0) })
            operators = Dictionary(uniqueKeysWithValues: catalogSnapshot.operators.map { ($0.id, $0) })
            waitlist = queue

            let now = LocalDateTime.now()
            upcoming = appointments.filter { $0.isActive && $0.end >= now }.sorted { $0.start < $1.start }
            past = appointments.filter { !$0.isActive || $0.end < now }.sorted { $0.start > $1.start }

            let completed = appointments.filter { $0.status == .completed }.sorted { $0.start < $1.start }
            totalVisits = completed.count
            lastCompleted = completed.last
            favoriteOperatorName = favourite(in: completed)
            avgDaysBetweenVisits = cadence(of: completed)
        }
    }

    private func favourite(in completed: [Appointment]) -> String? {
        let counts = Dictionary(grouping: completed, by: \.operatorId).mapValues(\.count)
        guard let top = counts.max(by: { $0.value < $1.value })?.key else { return nil }
        return operators[top]?.name.split(separator: " ").first.map(String.init)
    }

    private func cadence(of completed: [Appointment]) -> Int? {
        let dates = completed.map(\.date).reduce(into: [LocalDate]()) { acc, d in
            if acc.last != d { acc.append(d) }
        }
        guard dates.count > 1 else { return nil }
        let gaps = zip(dates, dates.dropFirst()).map { $0.daysBetween($1) }
        return gaps.reduce(0, +) / gaps.count
    }

    func cancel(_ appointmentId: String) {
        Task {
            actionError = nil
            switch await booking.cancel(appointmentId, by: .client) {
            case .success: await load()
            case .failure(let error): actionError = error.displayMessage
            }
        }
    }

    func leaveWaitlist(_ entryId: String) {
        Task {
            actionError = nil
            switch await booking.leaveWaitlist(entryId) {
            case .success: await load()
            case .failure(let error): actionError = error.displayMessage
            }
        }
    }
}
