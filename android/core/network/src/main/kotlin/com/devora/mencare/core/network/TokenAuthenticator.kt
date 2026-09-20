package com.devora.mencare.core.network

import com.devora.mencare.core.network.api.AuthApi
import com.devora.mencare.core.network.dto.RefreshBody
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Oltre questo numero di tentativi la richiesta ha già visto un token nuovo e
 * ha ripreso 401: insistere vorrebbe dire entrare in ciclo.
 */
private const val MAX_RETRIES = 1

/**
 * Rinnovo della sessione quando il server risponde 401.
 *
 * Tre regole, tutte e tre necessarie:
 *
 * 1. **Un rinnovo solo alla volta.** OkHttp può avere dieci richieste in volo:
 *    se scadessero insieme partirebbero dieci rinnovi, e siccome il refresh
 *    token *ruota a ogni uso* nove di quelli invaliderebbero il decimo,
 *    buttando fuori l'utente. Il blocco `synchronized` fa passare il primo; gli
 *    altri, quando entrano, trovano già il token nuovo e si limitano a riusarlo.
 * 2. **Mai un ciclo.** Se la richiesta ripetuta torna ancora 401 ci si ferma
 *    ([MAX_RETRIES]): un token buono non può dare 401 due volte di fila.
 * 3. **Il fallimento chiude la sessione.** Refresh scaduto o revocato vuol dire
 *    che non si rientra: i token si cancellano e si avvisa l'app, che riporta
 *    alla schermata di accesso.
 *
 * [AuthApi] arriva come [Provider] perché è costruito dallo stesso Retrofit che
 * monta questo Authenticator: senza il rinvio, il grafo sarebbe circolare.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokens: TokenStore,
    private val authApi: Provider<AuthApi>,
    private val onSessionLost: SessionExpiryNotifier,
) : Authenticator {

    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Le rotte pubbliche non hanno una sessione da rinnovare: un 401 lì è
        // "credenziali sbagliate", e va restituito così com'è alla schermata.
        if (response.request.isPublic()) return null
        if (response.retryCount() > MAX_RETRIES) return null

        val used = response.request.header(HEADER_AUTHORIZATION)?.removePrefix("Bearer ")

        val fresh = synchronized(refreshLock) {
            // Qualcun altro ha già rinnovato mentre aspettavamo il lucchetto.
            val current = tokens.accessToken
            if (current != null && current != used) current else refresh()
        } ?: return null

        return response.request.newBuilder()
            .header(HEADER_AUTHORIZATION, "Bearer $fresh")
            .build()
    }

    /**
     * `runBlocking` è voluto: [Authenticator] è un'API sincrona di OkHttp e
     * gira già su un thread di rete suo, mai sul main thread.
     */
    private fun refresh(): String? {
        val refreshToken = tokens.refreshToken ?: return null
        return runCatching {
            runBlocking { authApi.get().refresh(RefreshBody(refreshToken)) }
        }.fold(
            onSuccess = { renewed ->
                tokens.save(SessionTokens(renewed.accessToken, renewed.refreshToken, renewed.expiresIn))
                renewed.accessToken
            },
            onFailure = {
                // Refresh scaduto, revocato o ruotato da un'altra sessione:
                // niente da salvare e niente da riprovare.
                tokens.clear()
                onSessionLost.notifyExpired()
                null
            },
        )
    }
}

/** Quante volte OkHttp ha già rifatto questa richiesta per un 401. */
private fun Response.retryCount(): Int {
    var count = 1
    var previous = priorResponse
    while (previous != null) {
        count++
        previous = previous.priorResponse
    }
    return count
}
