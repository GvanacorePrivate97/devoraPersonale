import Foundation
import SwiftUI

/// Cablaggio dell'app: ogni repository parla con l'API. E' il gemello SwiftUI
/// del `DataModule` Hilt di Android. I fake restano nel progetto come fixture
/// dei test (`MenCareTests`), non come strato dell'app.
@MainActor
final class AppContainer {

    let session: AppSession
    let client: ApiClient
    let auth: AuthRepository
    let avatar: AvatarRepository
    let booking: BookingRepository
    let catalog: CatalogRepository
    let crm: CrmRepository
    let notificationsRepo: NotificationRepository
    let timeBlocks: TimeBlockRepository
    let admin: AdminRepository

    /// Il ponte verso l'`ApiClient` va tenuto vivo: l'actor lo referenzia
    /// debolmente, per non trattenere l'app quando la sessione finisce.
    private let sessionBridge: SessionBridge

    init(tokenStorage: TokenStorage = KeychainTokenStore()) {
        let session = AppSession()
        let client = ApiClient(tokens: tokenStorage)
        let bridge = SessionBridge(session: session)

        self.session = session
        self.client = client
        sessionBridge = bridge

        auth = NetworkAuthRepository(client: client, session: session)
        avatar = NetworkAvatarRepository(client: client, session: session)
        booking = NetworkBookingRepository(client: client)
        catalog = NetworkCatalogRepository(client: client)
        crm = NetworkCrmRepository(client: client)
        notificationsRepo = NetworkNotificationRepository(client: client, session: session)
        timeBlocks = NetworkTimeBlockRepository(client: client)
        admin = NetworkAdminRepository(client: client)

        // Quando il rinnovo del token fallisce l'app deve tornare all'accesso:
        // e' l'unico modo di uscire da una sessione morta senza schermate rotte.
        Task { await client.setSessionObserver(bridge) }
    }
}

private struct AppContainerKey: EnvironmentKey {
    static let defaultValue: AppContainer? = nil
}

extension EnvironmentValues {
    /// Lato scrittura: si imposta una volta alla radice con `.environment(\.appContainer, …)`.
    var appContainer: AppContainer? {
        get { self[AppContainerKey.self] }
        set { self[AppContainerKey.self] = newValue }
    }

    /// Lato lettura: force-unwrap perche' i punti d'uso restino asciutti. Di
    /// sola lettura apposta: `.environment(_:_:)` scrive attraverso un key path
    /// scrivibile, e scrivere una proprieta' calcolata ne chiama prima il getter
    /// — che sul valore ancora vuoto faceva cadere l'app all'avvio.
    var container: AppContainer { self[AppContainerKey.self]! }
}
