import Foundation
import Security

/// La coppia di token della sessione, piu' la scadenza dell'access token.
struct AuthTokens: Codable, Equatable {
    let accessToken: String
    let refreshToken: String
    /// Istante (epoch) in cui l'access token smette di valere.
    let expiresAt: Date

    /// `expiresIn` arriva in secondi: si converte subito in un istante, cosi'
    /// il confronto non dipende da quando e' stato letto.
    init(accessToken: String, refreshToken: String, expiresIn: Int, now: Date = Date()) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.expiresAt = now.addingTimeInterval(TimeInterval(expiresIn))
    }

    /// Si rinnova 30 secondi prima della scadenza vera: il tempo di volo della
    /// richiesta non deve far arrivare un token gia' morto.
    func isExpired(now: Date = Date(), leeway: TimeInterval = 30) -> Bool {
        now.addingTimeInterval(leeway) >= expiresAt
    }
}

/// I token stanno nel portachiavi e da nessun'altra parte: `UserDefaults` e' un
/// file in chiaro dentro il backup del telefono, e un token di rinnovo vale
/// trenta giorni. Non vengono mai stampati nei log.
protocol TokenStorage: Sendable {
    func read() -> AuthTokens?
    func write(_ tokens: AuthTokens)
    func clear()
}

final class KeychainTokenStore: TokenStorage, @unchecked Sendable {

    private let service: String
    private let account: String
    private let lock = NSLock()

    init(service: String = "com.devora.mencare.session", account: String = "auth-tokens") {
        self.service = service
        self.account = account
    }

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    func read() -> AuthTokens? {
        lock.lock()
        defer { lock.unlock() }
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return try? JSONDecoder().decode(AuthTokens.self, from: data)
    }

    func write(_ tokens: AuthTokens) {
        guard let data = try? JSONEncoder().encode(tokens) else { return }
        lock.lock()
        defer { lock.unlock() }
        // Si cancella e si riscrive: `SecItemUpdate` su una voce inesistente
        // fallisce, e distinguere i due casi non porta nulla.
        SecItemDelete(baseQuery as CFDictionary)
        var query = baseQuery
        query[kSecValueData as String] = data
        // Leggibile solo a telefono sbloccato e mai copiato su un altro
        // dispositivo tramite backup: e' una credenziale di questa installazione.
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(query as CFDictionary, nil)
    }

    func clear() {
        lock.lock()
        defer { lock.unlock() }
        SecItemDelete(baseQuery as CFDictionary)
    }
}

/// Usato dai test e dalle anteprime: stessa interfaccia, niente portachiavi.
final class InMemoryTokenStore: TokenStorage, @unchecked Sendable {
    private let lock = NSLock()
    private var tokens: AuthTokens?

    init(tokens: AuthTokens? = nil) {
        self.tokens = tokens
    }

    func read() -> AuthTokens? {
        lock.lock()
        defer { lock.unlock() }
        return tokens
    }

    func write(_ tokens: AuthTokens) {
        lock.lock()
        defer { lock.unlock() }
        self.tokens = tokens
    }

    func clear() {
        lock.lock()
        defer { lock.unlock() }
        tokens = nil
    }
}
