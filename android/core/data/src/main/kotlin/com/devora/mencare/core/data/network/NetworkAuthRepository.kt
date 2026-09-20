package com.devora.mencare.core.data.network

import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.common.DispatcherProvider
import com.devora.mencare.core.common.map
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.data.di.ApplicationScope
import com.devora.mencare.core.data.repository.AuthRepository
import com.devora.mencare.core.data.repository.SocialProvider
import com.devora.mencare.core.model.ClientNotificationPrefs
import com.devora.mencare.core.model.User
import com.devora.mencare.core.network.SessionExpiryNotifier
import com.devora.mencare.core.network.SessionTokens
import com.devora.mencare.core.network.TokenStore
import com.devora.mencare.core.network.api.AuthApi
import com.devora.mencare.core.network.apiCall
import com.devora.mencare.core.network.dto.ChangePasswordBody
import com.devora.mencare.core.network.dto.EmailBody
import com.devora.mencare.core.network.dto.LoginBody
import com.devora.mencare.core.network.dto.RefreshBody
import com.devora.mencare.core.network.dto.RegisterBody
import com.devora.mencare.core.network.dto.SessionDto
import com.devora.mencare.core.network.dto.TokensDto
import com.devora.mencare.core.network.dto.UpdateProfileBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accesso, profilo e sessione contro il backend.
 *
 * La sessione vive nei token cifrati di `:core:network`: all'avvio, se ce n'è
 * una, si prova a riprendere il profilo con `GET /auth/me` invece di chiedere di
 * nuovo la password. Se non è più valida — refresh scaduto o revocato — l'app
 * torna alla schermata di accesso da sola, avvisata dal
 * [SessionExpiryNotifier].
 */
@Singleton
class NetworkAuthRepository @Inject constructor(
    private val api: AuthApi,
    private val tokens: TokenStore,
    private val session: Session,
    private val sync: DataSync,
    private val dispatchers: DispatcherProvider,
    expiry: SessionExpiryNotifier,
    @ApplicationScope private val scope: CoroutineScope,
) : AuthRepository {

    /** Una sola volta per avvio: `hasSession` dice se vale la pena provarci. */
    @Volatile
    private var restoreStarted = false

    init {
        // Il rinnovo è fallito: i token li ha già cancellati l'Authenticator,
        // qui si spegne la sessione e si svuota ciò che le schermate mostrano.
        scope.launch {
            expiry.expired.collect { endSession() }
        }
    }

    override val currentUser: Flow<User?> = session.user.onStart { restoreSession() }

    private fun restoreSession() {
        if (restoreStarted || !tokens.hasSession || session.user.value != null) return
        restoreStarted = true
        scope.launch {
            val restored = sync.load<User?>(SyncKeys.PROFILE, null) { api.me().toModel() }
            if (restored != null) session.set(restored) else tokens.clear()
        }
    }

    override suspend fun login(email: String, password: String): AppResult<User> =
        withContext(dispatchers.io) {
            apiCall { api.login(LoginBody(email, password)) }.map { beginSession(it) }
        }

    /**
     * L'accesso social vuole un `idToken` rilasciato da Google sul telefono: il
     * backend è pronto (`POST /auth/social`), l'app no — manca ancora
     * l'integrazione con Credential Manager. Finché non c'è, il bottone
     * risponde con un errore invece di far finta di funzionare.
     */
    override suspend fun loginWithProvider(provider: SocialProvider): AppResult<User> =
        AppResult.Failure(AppError.Unknown(null))

    override suspend fun register(
        firstName: String,
        lastName: String,
        phone: String,
        email: String,
        password: String,
    ): AppResult<User> = withContext(dispatchers.io) {
        apiCall { api.register(RegisterBody(firstName, lastName, phone, email, password)) }
            .map { beginSession(it) }
    }

    override suspend fun requestPasswordReset(email: String): AppResult<Unit> =
        withContext(dispatchers.io) { apiCall { api.requestPasswordReset(EmailBody(email)) } }

    override suspend fun updateProfile(
        firstName: String,
        lastName: String,
        email: String,
        phone: String,
    ): AppResult<User> = withContext(dispatchers.io) {
        apiCall { api.updateProfile(UpdateProfileBody(firstName, lastName, email, phone)).toModel() }
            .onSuccess { session.set(it) }
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            apiCall { api.changePassword(ChangePasswordBody(currentPassword, newPassword)) }
                .map { envelope ->
                    // Cambiare password chiude le altre sessioni: questa resta
                    // viva solo se si salva subito la coppia nuova.
                    tokens.save(envelope.tokens.toSession())
                }
        }

    override val notificationPrefs: Flow<ClientNotificationPrefs> =
        sync.reloading(SyncKeys.PROFILE, ClientNotificationPrefs()) {
            if (session.user.value == null) ClientNotificationPrefs() else api.notificationPrefs().toModel()
        }

    override suspend fun updateNotificationPrefs(prefs: ClientNotificationPrefs) {
        withContext(dispatchers.io) {
            sync.load<Unit>(SyncKeys.PROFILE, Unit) { api.updateNotificationPrefs(prefs.toDto()) }
            sync.invalidate()
        }
    }

    override suspend fun logout() {
        withContext(dispatchers.io) {
            // L'uscita si annuncia al server per revocare il refresh token, ma
            // se la rete non c'è si esce lo stesso: sul telefono non deve
            // restare una sessione che l'utente crede chiusa.
            tokens.refreshToken?.let { refresh -> apiCall { api.logout(RefreshBody(refresh)) } }
            endSession()
        }
    }

    private fun beginSession(dto: SessionDto): User {
        tokens.save(dto.tokens.toSession())
        val user = dto.user.toModel()
        session.set(user)
        // Le cache appartenevano a chi c'era prima: si rileggono tutte.
        sync.invalidate()
        return user
    }

    private fun endSession() {
        tokens.clear()
        session.set(null)
        restoreStarted = false
        sync.invalidate()
    }
}

private fun TokensDto.toSession() = SessionTokens(accessToken, refreshToken, expiresIn)
