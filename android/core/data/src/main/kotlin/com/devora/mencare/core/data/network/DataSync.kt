package com.devora.mencare.core.data.network

import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.common.DispatcherProvider
import com.devora.mencare.core.network.ErrorMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nomi delle letture, per dire a una schermata *quali* guasti la riguardano.
 * Sono pochi di proposito: una chiave per area di dati, non per chiamata.
 */
object SyncKeys {
    const val CATALOG = "catalog"
    const val HOLIDAYS = "holidays"
    const val APPOINTMENTS = "appointments"
    const val WAITLIST = "waitlist"
    const val BLOCKS = "blocks"
    const val CLIENTS = "clients"
    const val DASHBOARD = "dashboard"
    const val CAMPAIGNS = "campaigns"
    const val SETTINGS = "settings"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
}

/** Come sta andando il caricamento delle letture a cui una schermata tiene. */
data class SyncState(
    val loading: Boolean = false,
    val error: AppError? = null,
) {
    val failed: Boolean get() = error != null
}

/**
 * Il pezzo che mancava quando i dati erano finti: **una lettura può fallire**.
 *
 * Le interfacce dei repository espongono `Flow` e non `AppResult`, perché una
 * schermata che osserva l'agenda non vuole un risultato una volta sola: vuole
 * restare agganciata. Il guasto quindi non può viaggiare nel tipo di ritorno, e
 * passa di qui: ogni lettura si annuncia con una chiave, e chi disegna chiede
 * lo stato delle chiavi che la riguardano.
 *
 * ## Come si aggiornano i dati
 *
 * C'è un solo contatore di revisione per tutta l'app. Ogni lettura è un `Flow`
 * che rilegge a ogni scatto del contatore, e ogni scrittura fa scattare il
 * contatore: prenotare aggiorna l'agenda, ma anche i giorni liberi, la coda e i
 * numeri del titolare, senza che nessuno debba ricordarsi di elencarli. È una
 * lettura in più ogni tanto, ma su un salone solo il traffico è minuscolo e in
 * cambio non esiste la classe di errori "ho salvato ma la schermata mostra
 * ancora il vecchio".
 */
@Singleton
class DataSync @Inject constructor(
    private val dispatchers: DispatcherProvider,
) {

    private val revision = MutableStateFlow(0L)
    private val running = MutableStateFlow(0)
    private val failures = MutableStateFlow<Map<String, AppError>>(emptyMap())

    /** Stato delle sole letture indicate; senza chiavi, di tutte. */
    fun state(vararg keys: String): Flow<SyncState> {
        val watched = keys.toSet()
        return combine(running, failures) { inFlight, errors ->
            val relevant = if (watched.isEmpty()) errors else errors.filterKeys { it in watched }
            SyncState(loading = inFlight > 0, error = relevant.values.firstOrNull())
        }.distinctUntilChanged()
    }

    /** Solo "sta caricando qualcosa", per le barre sottili in cima. */
    val loading: Flow<Boolean> = running.map { it > 0 }.distinctUntilChanged()

    /** Una scrittura è andata a buon fine: tutto ciò che è osservato si rilegge. */
    fun invalidate() {
        revision.update { it + 1 }
    }

    /**
     * "Riprova" di una schermata: si dimenticano i guasti e si rilegge. Vale per
     * tutte le letture attive, non solo per quella visibile, perché se la rete
     * era giù erano cadute tutte insieme.
     */
    fun retry() {
        failures.value = emptyMap()
        invalidate()
    }

    /**
     * Una lettura che si riallinea a ogni revisione.
     *
     * [initial] è quello che la schermata vede prima della prima risposta;
     * dopo un guasto si tiene l'ultimo valore buono invece di svuotare la
     * schermata — accanto compare l'errore, e nessuno perde di vista l'agenda
     * per un timeout.
     */
    fun <T> reloading(key: String, initial: T, fetch: suspend () -> T): Flow<T> = flow {
        var last = initial
        revision.collect {
            last = load(key, last, fetch)
            emit(last)
        }
    }.distinctUntilChanged().flowOn(dispatchers.io)

    /**
     * Come [load], ma dice se è andata male: `null` è il fallimento, non un
     * valore. Serve a chi non deve memorizzare il ripiego — una risposta
     * mancata non è una risposta da tenere in cache.
     */
    suspend fun <T : Any> loadOrNull(key: String, fetch: suspend () -> T): T? =
        load<T?>(key, null, fetch)

    /** Una lettura sola, contata e con l'errore registrato sotto [key]. */
    suspend fun <T> load(key: String, fallback: T, fetch: suspend () -> T): T {
        running.update { it + 1 }
        return try {
            val value = fetch()
            failures.update { it - key }
            value
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            failures.update { it + (key to ErrorMapper.map(throwable)) }
            fallback
        } finally {
            running.update { it - 1 }
        }
    }
}
