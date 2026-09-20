package com.devora.mencare.core.data.network

import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.common.DispatcherProvider
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.data.repository.CrmRepository
import com.devora.mencare.core.model.ClientRecord
import com.devora.mencare.core.model.ClientSegment
import com.devora.mencare.core.network.api.CrmApi
import com.devora.mencare.core.network.apiCall
import com.devora.mencare.core.network.dto.CreateClientBody
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Quanti clienti per pagina, e quante pagine al massimo si rincorrono. */
private const val PAGE_SIZE = 100
private const val MAX_PAGES = 10

/**
 * Rubrica clienti dal backend.
 *
 * Ricerca e segmenti li applica il server: "inattivi da 60 giorni" o "top
 * spesa" dipendono dallo storico intero, che l'app non ha e non deve avere.
 * La paginazione è a cursore: qui si scorre fino a [MAX_PAGES] perché le
 * schermate mostrano una lista, non un archivio — oltre, si cerca.
 */
@Singleton
class NetworkCrmRepository @Inject constructor(
    private val api: CrmApi,
    private val sync: DataSync,
    private val dispatchers: DispatcherProvider,
) : CrmRepository {

    override val clients: Flow<List<ClientRecord>> =
        sync.reloading(SyncKeys.CLIENTS, emptyList<ClientRecord>()) { fetchAll(null, ClientSegment.TUTTI) }

    override fun client(id: String): Flow<ClientRecord?> =
        sync.reloading<ClientRecord?>(SyncKeys.CLIENTS, null) {
            // Le abitudini arrivano solo con la scheda: si copiano sul record.
            val detail = api.client(id)
            detail.client.toModel().copy(
                favoriteOperatorId = detail.insights?.favoriteOperatorId,
                averageDaysBetweenVisits = detail.insights?.averageDaysBetweenVisits,
            )
        }

    override fun search(query: String, segment: ClientSegment): Flow<List<ClientRecord>> =
        sync.reloading(SyncKeys.CLIENTS, emptyList<ClientRecord>()) {
            fetchAll(query.trim().ifBlank { null }, segment)
        }

    private suspend fun fetchAll(query: String?, segment: ClientSegment): List<ClientRecord> {
        val found = mutableListOf<ClientRecord>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val page = api.clients(query, segment.name, PAGE_SIZE, cursor)
            found += page.clients.map { it.toModel() }
            cursor = page.nextCursor ?: return found
        }
        return found
    }

    override suspend fun createClient(
        firstName: String,
        lastName: String,
        phone: String,
    ): AppResult<ClientRecord> = withContext(dispatchers.io) {
        apiCall { api.createClient(CreateClientBody(firstName, lastName, phone)).toModel() }
            .onSuccess { sync.invalidate() }
    }
}
