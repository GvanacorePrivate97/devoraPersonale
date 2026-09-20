import Foundation
import Observation

/// Stato della sessione condiviso con l'interfaccia: chi e' entrato e se la
/// sessione e' caduta. E' `@Observable` perche' `RootView` deve reagire subito
/// quando il rinnovo del token fallisce, senza che nessuna schermata se ne
/// accorga per prima.
@MainActor
@Observable
final class AppSession {
    private(set) var user: User?
    /// Sale di uno ogni volta che la sessione scade: `RootView` lo osserva e
    /// riporta all'accesso. Un contatore, non un booleano, cosi' due scadenze
    /// di fila si notano entrambe.
    private(set) var expiredCount = 0

    func set(_ user: User?) {
        self.user = user
    }

    func markExpired() {
        user = nil
        expiredCount += 1
    }
}

/// Adattatore fra l'`ApiClient` (un actor, fuori dal main) e `AppSession`
/// (che vive sul main): tiene il riferimento debole all'app, non il contrario.
final class SessionBridge: SessionObserver, @unchecked Sendable {
    private let session: AppSession

    init(session: AppSession) {
        self.session = session
    }

    func sessionDidExpire() async {
        await MainActor.run { session.markExpired() }
    }
}

@MainActor
final class NetworkAuthRepository: AuthRepository {

    private let client: ApiClient
    private let session: AppSession

    init(client: ApiClient, session: AppSession) {
        self.client = client
        self.session = session
    }

    func currentUser() async throws -> User? {
        // Nessun token in portachiavi: non ha senso chiamare `/auth/me` per
        // sentirsi rispondere 401, e farebbe partire un rinnovo a vuoto.
        guard await client.hasSession else {
            session.set(nil)
            return nil
        }
        do {
            let dto = try await client.send(AuthEndpoint.me, as: UserDto.self)
            let user = dto.toDomain()
            session.set(user)
            return user
        } catch AppError.unauthorized {
            session.set(nil)
            return nil
        } catch {
            throw ApiError.appError(from: error)
        }
    }

    func login(email: String, password: String) async -> AppResult<User> {
        await authenticate(AuthEndpoint.login(LoginRequestDto(email: email, password: password)))
    }

    func loginWithProvider(_ provider: SocialProvider) async -> AppResult<User> {
        // Il token del provider lo restituisce l'SDK nativo (Sign in with Apple
        // o Google Sign-In): finche' non e' integrato la chiamata parte comunque
        // e il server risponde con le sue regole, senza finti accessi locali.
        let wire = provider == .apple ? "APPLE" : "GOOGLE"
        return await authenticate(
            AuthEndpoint.social(SocialLoginRequestDto(provider: wire, idToken: ""))
        )
    }

    func register(
        firstName: String, lastName: String, phone: String, email: String, password: String
    ) async -> AppResult<User> {
        await authenticate(
            AuthEndpoint.register(
                RegisterRequestDto(
                    firstName: firstName, lastName: lastName, phone: phone,
                    email: email, password: password
                )
            )
        )
    }

    func requestPasswordReset(email: String) async -> AppResult<Void> {
        await apiResult {
            _ = try await client.send(AuthEndpoint.passwordResetRequest(PasswordResetRequestDto(email: email)))
        }
    }

    func updateProfile(firstName: String, lastName: String, email: String, phone: String) async -> AppResult<User> {
        await apiResult {
            let dto = try await client.send(
                AuthEndpoint.updateMe(
                    ProfileRequestDto(firstName: firstName, lastName: lastName, email: email, phone: phone)
                ),
                as: UserDto.self
            )
            let user = dto.toDomain()
            session.set(user)
            return user
        }
    }

    func changePassword(currentPassword: String, newPassword: String) async -> AppResult<Void> {
        await apiResult {
            // Cambiare password fa cadere le altre sessioni e ruota la coppia di
            // questa: i token nuovi vanno salvati subito, o la schermata dopo
            // troverebbe un access token gia' revocato.
            let result = try await client.send(
                AuthEndpoint.changePassword(
                    ChangePasswordRequestDto(currentPassword: currentPassword, newPassword: newPassword)
                ),
                as: AuthResultDto.self
            )
            await client.store(result.tokens.toDomain())
        }
    }

    func notificationPrefs() async throws -> ClientNotificationPrefs {
        try await apiThrowing {
            try await client.send(AuthEndpoint.notificationPrefs, as: NotificationPrefsDto.self).toDomain()
        }
    }

    func updateNotificationPrefs(_ prefs: ClientNotificationPrefs) async -> AppResult<ClientNotificationPrefs> {
        await apiResult {
            try await client.send(
                AuthEndpoint.saveNotificationPrefs(NotificationPrefsDto(prefs)), as: NotificationPrefsDto.self
            ).toDomain()
        }
    }

    func logout() async {
        // Il refresh token va revocato sul server: se la chiamata non riesce
        // (aereo, server giu') si esce lo stesso, ma i token locali spariscono
        // in ogni caso — restare "dentro" dopo un logout non e' un'opzione.
        if let refreshToken = await client.refreshToken {
            _ = try? await client.send(AuthEndpoint.logout(RefreshRequestDto(refreshToken: refreshToken)))
        }
        await client.clearSession()
        session.set(nil)
    }

    private func authenticate(_ request: ApiRequest) async -> AppResult<User> {
        await apiResult {
            let result = try await client.send(request, as: AuthResultDto.self)
            await client.store(result.tokens.toDomain())
            guard let dto = result.user else {
                // Senza utente nel corpo si chiede a `/auth/me`: la risposta di
                // accesso lo contiene sempre, ma il contratto lo dichiara opzionale.
                let me = try await client.send(AuthEndpoint.me, as: UserDto.self).toDomain()
                session.set(me)
                return me
            }
            let user = dto.toDomain()
            session.set(user)
            return user
        }
    }
}
