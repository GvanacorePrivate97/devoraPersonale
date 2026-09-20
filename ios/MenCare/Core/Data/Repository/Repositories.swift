import Foundation

// Contratti dei repository: il confine fra le schermate e l'API.
//
// In Fase 1 le letture erano proprieta' sincrone su uno store `@Observable`, e
// bastava. Dietro c'e' una rete: una lettura puo' metterci mezzo secondo, puo'
// fallire e puo' scadere la sessione, e niente di tutto questo sta dentro un
// `var`. Quindi:
//
// - **lettura** → `async throws`: l'errore e' un `AppError`, e la schermata lo
//   mostra con il suo riprova;
// - **scrittura** → `async -> AppResult`, come prima: i form hanno gia' il loro
//   modo di raccontare un salvataggio andato male, campo per campo.
//
// Le cose che il server calcola (slot, giorni pieni, posizione in coda, KPI,
// copertura di una campagna) arrivano gia' pronte: le app non le ricalcolano.

enum SocialProvider {
    /// "Sign in with Apple" e' offerto solo su iOS; il contratto condiviso
    /// tiene tutti e due i casi per il backend comune.
    case apple, google
}

@MainActor
protocol AuthRepository: AnyObject {
    /// Chi ha la sessione aperta, o nil se non c'e'. Al primo avvio dice anche
    /// se i token in portachiavi valgono ancora.
    func currentUser() async throws -> User?

    func login(email: String, password: String) async -> AppResult<User>
    func loginWithProvider(_ provider: SocialProvider) async -> AppResult<User>
    func register(firstName: String, lastName: String, phone: String, email: String, password: String) async -> AppResult<User>
    func requestPasswordReset(email: String) async -> AppResult<Void>
    func updateProfile(firstName: String, lastName: String, email: String, phone: String) async -> AppResult<User>
    /// Richiede la password attuale corretta; altrimenti `AppError.invalidCredentials`.
    func changePassword(currentPassword: String, newPassword: String) async -> AppResult<Void>

    func notificationPrefs() async throws -> ClientNotificationPrefs
    func updateNotificationPrefs(_ prefs: ClientNotificationPrefs) async -> AppResult<ClientNotificationPrefs>

    func logout() async
}

/// Foto di profilo dell'account: il chiamante passa l'immagine scelta e
/// riceve l'URL da mostrare.
@MainActor
protocol AvatarRepository: AnyObject {
    /// `imageData` arriva dal selettore foto di sistema.
    func save(imageData: Data) async -> AppResult<String>
    /// Toglie la foto: tornano le iniziali.
    func clear() async -> AppResult<Void>
}

/// Disponibilita' di un giorno: orari di inizio prenotabili per una durata data.
struct DayAvailability {
    let date: LocalDate
    let slots: [LocalTime]
}

/// Il calendario del wizard in una chiamata sola: giorni con posto e giorni
/// aperti ma pieni (quelli offrono la lista d'attesa).
struct DayRangeAvailability {
    let available: Set<LocalDate>
    let fullyBooked: Set<LocalDate>

    static let empty = DayRangeAvailability(available: [], fullyBooked: [])
}

struct BookingRequest {
    /// Chi prenota per se' (il cliente) lo lascia nil: lo decide il server dal
    /// token. Staff e titolare indicano la scheda cliente.
    var clientId: String?
    /// nil = "Qualsiasi operatore": la scelta la fa il server.
    let operatorId: String?
    let serviceIds: [String]
    let start: LocalDateTime
    var noteForOperator: String?
    var channel: BookingChannel = .app
    /// "Modifica": l'appuntamento che questo sostituisce. Il server annulla il
    /// vecchio e crea il nuovo nella stessa transazione, così lo stesso orario
    /// si può tenere e, se il nuovo non entra, il vecchio resta com'era.
    var replacesAppointmentId: String?
}

@MainActor
protocol BookingRepository: AnyObject {
    /// Gli appuntamenti del cliente che ha la sessione aperta.
    func myAppointments() async throws -> [Appointment]
    /// L'agenda di un operatore in un giorno. Per uno STAFF il server ignora
    /// l'id e serve comunque la propria poltrona.
    func appointmentsForOperator(_ operatorId: String?, date: LocalDate) async throws -> [Appointment]
    func appointmentsForWeek(_ weekStart: LocalDate) async throws -> [Appointment]
    func appointment(_ id: String) async throws -> Appointment?

    /// Orari prenotabili il giorno `date` per `serviceIds` con `operatorId`
    /// (nil = unione di tutti gli operatori abilitati).
    /// `ignoreAppointmentId`: in modifica l'appuntamento stesso non occupa il suo posto.
    func availability(
        operatorId: String?, serviceIds: [String], date: LocalDate, ignoreAppointmentId: String?
    ) async throws -> DayAvailability

    /// Giorni disponibili e giorni pieni fra `from` e `to`.
    func days(
        operatorId: String?, serviceIds: [String], from: LocalDate, to: LocalDate, ignoreAppointmentId: String?
    ) async throws -> DayRangeAvailability

    /// Il server riverifica la disponibilita' dentro la transazione: se l'orario
    /// e' appena stato preso torna `.slotNoLongerAvailable`.
    func book(_ request: BookingRequest) async -> AppResult<Appointment>

    func reschedule(_ appointmentId: String, newStart: LocalDateTime, newOperatorId: String?) async -> AppResult<Appointment>
    func cancel(_ appointmentId: String, by: CancellationActor) async -> AppResult<Void>
    func markInProgress(_ appointmentId: String) async -> AppResult<Void>
    func markCompleted(_ appointmentId: String) async -> AppResult<Void>
    /// "Non si è presentato": solo a orario d'inizio passato, anche su un
    /// appuntamento già completato; `markCompleted` lo corregge. Il cliente
    /// riceve una notifica.
    func markNoShow(_ appointmentId: String) async -> AppResult<Void>

    // Lista d'attesa
    /// Le proprie richieste ancora in coda, ognuna con la posizione calcolata
    /// dal server.
    func myWaitlist() async throws -> [WaitlistEntry]
    /// Mette in coda per `date`: `time` nil = qualsiasi ora del giorno,
    /// `operatorId` nil = qualsiasi operatore.
    func joinWaitlist(date: LocalDate, time: LocalTime?, operatorId: String?, serviceIds: [String]) async -> AppResult<WaitlistEntry>
    func leaveWaitlist(_ entryId: String) async -> AppResult<Void>
}

/// Salone, listino e squadra insieme: una sola chiamata all'avvio di ogni area.
struct CatalogSnapshot {
    let salon: Salon
    let services: [Service]
    let operators: [Operator]

    static let empty = CatalogSnapshot(
        salon: Salon(name: "", address: "", city: ""), services: [], operators: []
    )
}

/// Tutto quello che serve ad assumere un operatore: profilo piu' account.
struct NewOperator {
    let name: String
    let title: String
    let email: String
    let phone: String
    let serviceIds: Set<String>
    let weeklyHours: [DayOfWeek: [TimeRange]]
}

@MainActor
protocol CatalogRepository: AnyObject {
    func catalog() async throws -> CatalogSnapshot
    func services() async throws -> [Service]
    func operators() async throws -> [Operator]
    func holidays(operatorId: String) async throws -> [Holiday]

    /// Salva il servizio e, in una scrittura sola, chi lo esegue: le due cose
    /// insieme sono quello che la schermata del titolare compila.
    func saveService(_ service: Service, operatorIds: Set<String>) async -> AppResult<Service>

    /// Crea il profilo operatore *e* l'account STAFF collegato: un operatore
    /// senza accesso non potrebbe aprire l'app il primo giorno.
    func createOperator(_ newOperator: NewOperator) async -> AppResult<Operator>

    func updateSalon(_ salon: Salon) async -> AppResult<Void>
    func updateOperatorHours(_ operatorId: String, weeklyHours: [DayOfWeek: [TimeRange]]) async -> AppResult<Void>
    func updateOperatorServices(_ operatorId: String, serviceIds: Set<String>) async -> AppResult<Void>
    func addHoliday(_ holiday: Holiday) async -> AppResult<Holiday>
    func removeHoliday(_ holidayId: String) async -> AppResult<Void>
}

/// Scheda cliente e storico, come li manda `GET /crm/clients/:id`.
struct ClientDetail {
    let client: ClientRecord
    let history: [Appointment]
}

@MainActor
protocol CrmRepository: AnyObject {
    /// Ricerca e segmento li applica il server; `query` vuota = tutti.
    func clients(query: String, segment: ClientSegment) async throws -> [ClientRecord]
    func clientDetail(_ id: String) async throws -> ClientDetail
    func createClient(firstName: String, lastName: String, phone: String) async -> AppResult<ClientRecord>
}

/// Le notifiche dell'account piu' il contatore dei non letti, che e' quello che
/// accende il pallino sulla campanella.
struct NotificationFeed {
    let notifications: [AppNotification]
    let unreadCount: Int

    static let empty = NotificationFeed(notifications: [], unreadCount: 0)
}

@MainActor
protocol NotificationRepository: AnyObject {
    func feed() async throws -> NotificationFeed
    func markAllRead() async -> AppResult<Void>
}

@MainActor
protocol TimeBlockRepository: AnyObject {
    func blocksForOperator(_ operatorId: String?, date: LocalDate) async throws -> [TimeBlock]
    func blocksForWeek(_ weekStart: LocalDate, operatorId: String?) async throws -> [TimeBlock]

    /// Blocchi da `from` in avanti per tutti gli operatori — ferie e corsi.
    /// `weeks` limita quanto lontano guardare: l'API ragiona per settimane.
    func upcomingBlocks(from: LocalDate, weeks: Int) async throws -> [TimeBlock]

    /// Appuntamenti che il blocco travolgerebbe: vanno risolti prima di salvare.
    func conflictsFor(_ block: TimeBlock) async throws -> [Appointment]

    /// Fallisce con `.validation("conflicts")` se restano appuntamenti sotto.
    func createBlock(_ block: TimeBlock) async -> AppResult<TimeBlock>

    func deleteBlock(_ blockId: String) async -> AppResult<Void>
}

@MainActor
protocol AdminRepository: AnyObject {
    func dashboard(_ period: DashboardPeriod) async throws -> DashboardStats

    func notificationSettings() async throws -> NotificationSettings
    func updateNotificationSettings(_ settings: NotificationSettings) async -> AppResult<NotificationSettings>

    func campaigns() async throws -> [PushCampaign]
    func saveCampaign(_ campaign: PushCampaign) async -> AppResult<PushCampaign>
    /// Quante persone il segmento contiene e a quante arriverebbe davvero.
    func reachFor(_ segment: CampaignSegment) async throws -> (reachable: Int, size: Int)
}

extension BookingRepository {
    /// Fuori dalla modifica non c'è niente da ignorare.
    func availability(operatorId: String?, serviceIds: [String], date: LocalDate) async throws -> DayAvailability {
        try await availability(operatorId: operatorId, serviceIds: serviceIds, date: date, ignoreAppointmentId: nil)
    }

    func days(
        operatorId: String?, serviceIds: [String], from: LocalDate, to: LocalDate
    ) async throws -> DayRangeAvailability {
        try await days(operatorId: operatorId, serviceIds: serviceIds, from: from, to: to, ignoreAppointmentId: nil)
    }
}
