package com.devora.mencare.core.data.network

import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.common.DispatcherProvider
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.data.repository.TimeBlockRepository
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.TimeBlock
import com.devora.mencare.core.network.api.BlockApi
import com.devora.mencare.core.network.apiCall
import com.devora.mencare.core.network.dto.CreateBlockBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quante settimane si guardano avanti per "prossime assenze". Il backend
 * espone il giorno e la settimana, non una finestra aperta: un mese è quanto
 * basta alla schermata del titolare, e costa quattro richieste in parallelo.
 */
private const val UPCOMING_WEEKS = 4

/** Permessi, pause, ferie e corsi dal backend. */
@Singleton
class NetworkTimeBlockRepository @Inject constructor(
    private val api: BlockApi,
    private val session: Session,
    private val sync: DataSync,
    private val dispatchers: DispatcherProvider,
) : TimeBlockRepository {

    override fun blocksForOperator(operatorId: String, date: LocalDate): Flow<List<TimeBlock>> =
        sync.reloading(SyncKeys.BLOCKS, emptyList<TimeBlock>()) {
            api.blocksForDay(operatorId, date.toString()).blocks
                .mapNotNull { it.toModel() }
                .sortedBy { it.range.start }
        }

    override fun blocksForWeek(weekStart: LocalDate): Flow<List<TimeBlock>> =
        sync.reloading(SyncKeys.BLOCKS, emptyList<TimeBlock>()) { weekBlocks(weekStart) }

    override fun upcomingBlocks(from: LocalDate): Flow<List<TimeBlock>> =
        sync.reloading(SyncKeys.BLOCKS, emptyList<TimeBlock>()) {
            val firstWeek = from.with(DayOfWeek.MONDAY)
            coroutineScope {
                (0 until UPCOMING_WEEKS)
                    .map { week -> async { weekBlocks(firstWeek.plusWeeks(week.toLong())) } }
                    .awaitAll()
                    .flatten()
                    .filter { it.date >= from }
                    .sortedWith(compareBy({ it.date }, { it.range.start }))
            }
        }

    /** Senza `operatorId`: il titolare vede tutti, l'operatore solo sé stesso. */
    private suspend fun weekBlocks(weekStart: LocalDate): List<TimeBlock> =
        api.blocksForWeek(null, weekStart.toString()).blocks.mapNotNull { it.toModel() }

    override suspend fun conflictsFor(block: TimeBlock): List<Appointment> = withContext(dispatchers.io) {
        sync.load(SyncKeys.BLOCKS, emptyList<Appointment>()) {
            api.conflicts(
                block.operatorId,
                block.date.toString(),
                block.range.start.toString(),
                block.range.end.toString(),
            ).appointments.map { it.toModel() }
        }
    }

    /**
     * Il controllo dei conflitti e l'inserimento stanno nella stessa
     * transazione sul server: fra "non c'è niente" e "scrivo" non può infilarsi
     * una prenotazione. Se ce n'è una, torna `Validation("conflicts")`, che è
     * esattamente ciò che la schermata del blocco aspetta.
     */
    override suspend fun createBlock(block: TimeBlock): AppResult<TimeBlock> = withContext(dispatchers.io) {
        apiCall {
            val created = api.createBlock(
                CreateBlockBody(
                    // Per un operatore il server ignora questo campo e usa il
                    // token: qui vale solo quando a bloccare è il titolare.
                    operatorId = block.operatorId.takeIf { session.operatorId != it },
                    reason = block.reason.name,
                    date = block.date.toString(),
                    start = block.range.start.toString(),
                    end = block.range.end.toString(),
                    label = block.label?.takeIf { it.isNotBlank() },
                ),
            )
            checkNotNull(created.toModel()) { "Fascia oraria non valida" }
        }.onSuccess { sync.invalidate() }
    }

    override suspend fun deleteBlock(blockId: String): AppResult<Unit> = withContext(dispatchers.io) {
        apiCall { api.deleteBlock(blockId) }.onSuccess { sync.invalidate() }
    }
}
