package com.devora.mencare.core.data.network

import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.common.DispatcherProvider
import com.devora.mencare.core.common.map
import com.devora.mencare.core.data.repository.BookingRepository
import com.devora.mencare.core.data.repository.BookingRequest
import com.devora.mencare.core.data.repository.DayAvailability
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.AppointmentStatus
import com.devora.mencare.core.model.CancellationActor
import com.devora.mencare.core.model.UserRole
import com.devora.mencare.core.model.WaitlistEntry
import com.devora.mencare.core.network.api.BookingApi
import com.devora.mencare.core.network.api.CrmApi
import com.devora.mencare.core.network.apiCall
import com.devora.mencare.core.network.dto.AvailableDaysDto
import com.devora.mencare.core.network.dto.CreateAppointmentBody
import com.devora.mencare.core.network.dto.JoinWaitlistBody
import com.devora.mencare.core.network.dto.RescheduleBody
import com.devora.mencare.core.network.dto.StatusBody
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Quante risposte di `/booking/days` si tengono da parte. */
private const val DAYS_CACHE_MAX = 8

/**
 * Chiave della risposta di `/booking/days`, che porta insieme i giorni liberi e
 * quelli pieni. Il calendario le chiede una dopo l'altra: senza questa memoria
 * sarebbero due richieste identiche a ogni cambio di mese.
 */
private data class DaysKey(
    val operatorId: String?,
    val serviceIds: List<String>,
    val from: LocalDate,
    val to: LocalDate,
    val ignoreAppointmentId: String? = null,
)

/**
 * Agenda e lista d'attesa dal backend.
 *
 * Qui non si calcola più niente: la griglia degli slot, i giorni pieni e la
 * posizione in coda arrivano dal server, che è l'unico a vedere l'agenda
 * intera e a poterla bloccare in transazione. `SlotEngine` resta nel progetto
 * come banco di prova dei test, ma non è più sulla strada dell'app.
 */
@Suppress("TooManyFunctions")
@Singleton
class NetworkBookingRepository @Inject constructor(
    private val api: BookingApi,
    private val crmApi: CrmApi,
    private val session: Session,
    private val sync: DataSync,
    private val dispatchers: DispatcherProvider,
) : BookingRepository {

    private val daysMutex = Mutex()

    /** Poche voci: bastano il mese aperto sul calendario e i sondaggi degli operatori. */
    private val daysCache = LinkedHashMap<DaysKey, AvailableDaysDto>()

    /**
     * Lo storico di un cliente ha due sorgenti, e a deciderlo è il ruolo: il
     * cliente vede i propri appuntamenti da `/appointments/me`, salone e
     * titolare li vedono dentro la scheda della rubrica. Un cliente non può
     * leggere le schede altrui e il server non glielo lascerebbe fare comunque.
     */
    override fun appointmentsForClient(clientId: String): Flow<List<Appointment>> =
        sync.reloading(SyncKeys.APPOINTMENTS, emptyList<Appointment>()) {
            when {
                session.role == UserRole.CLIENT && clientId == session.clientId ->
                    api.myAppointments().appointments.map { it.toModel() }
                session.isSalonSide ->
                    crmApi.client(clientId).appointments.map { it.toModel(clientId) }
                else -> emptyList()
            }.sortedBy { it.start }
        }

    override fun appointmentsForOperator(operatorId: String, date: LocalDate): Flow<List<Appointment>> =
        sync.reloading(SyncKeys.APPOINTMENTS, emptyList<Appointment>()) {
            api.appointmentsForDay(operatorId, date.toString()).appointments
                .map { it.toModel() }
                .sortedBy { it.start }
        }

    override fun appointmentsForWeek(weekStart: LocalDate): Flow<List<Appointment>> =
        sync.reloading(SyncKeys.APPOINTMENTS, emptyList<Appointment>()) {
            api.appointmentsForWeek(weekStart.toString()).appointments
                .map { it.toModel() }
                .sortedBy { it.start }
        }

    override fun appointment(id: String): Flow<Appointment?> =
        sync.reloading<Appointment?>(SyncKeys.APPOINTMENTS, null) { api.appointment(id).toModel() }

    override suspend fun availability(
        operatorId: String?,
        serviceIds: List<String>,
        date: LocalDate,
        ignoreAppointmentId: String?,
    ): DayAvailability = withContext(dispatchers.io) {
        val slots = sync.load(SyncKeys.APPOINTMENTS, emptyList<LocalTime>()) {
            api.availability(operatorId, serviceIds, date.toString(), ignoreAppointmentId).slots.map { LocalTime.parse(it) }
        }
        DayAvailability(date, slots)
    }

    override suspend fun availableDays(
        operatorId: String?,
        serviceIds: List<String>,
        from: LocalDate,
        to: LocalDate,
        ignoreAppointmentId: String?,
    ): Set<LocalDate> =
        days(DaysKey(operatorId, serviceIds, from, to, ignoreAppointmentId)).available.mapTo(mutableSetOf()) { LocalDate.parse(it) }

    override suspend fun fullyBookedDays(
        operatorId: String?,
        serviceIds: List<String>,
        from: LocalDate,
        to: LocalDate,
        ignoreAppointmentId: String?,
    ): Set<LocalDate> =
        days(DaysKey(operatorId, serviceIds, from, to, ignoreAppointmentId)).fullyBooked.mapTo(mutableSetOf()) { LocalDate.parse(it) }

    private suspend fun days(key: DaysKey): AvailableDaysDto = withContext(dispatchers.io) {
        if (key.serviceIds.isEmpty()) return@withContext AvailableDaysDto()
        daysMutex.withLock { daysCache[key] }?.let { return@withContext it }

        // Il lucchetto protegge la mappa, non la chiamata: le sonde dei quattro
        // operatori devono poter partire insieme, e due richieste identiche in
        // volo nello stesso istante costano poco e non fanno danno.
        val fresh = sync.loadOrNull(SyncKeys.APPOINTMENTS) {
            api.days(key.operatorId, key.serviceIds, key.from.toString(), key.to.toString(), key.ignoreAppointmentId)
        } ?: return@withContext AvailableDaysDto()

        daysMutex.withLock {
            daysCache[key] = fresh
            // Una lettura fallita non si memorizza mai: la volta dopo si riprova.
            while (daysCache.size > DAYS_CACHE_MAX) {
                daysCache.remove(daysCache.keys.first())
            }
        }
        fresh
    }

    override suspend fun book(request: BookingRequest): AppResult<Appointment> = mutate {
        api.book(
            CreateAppointmentBody(
                // Il cliente prenota per sé e il server lo sa dal token; staff e
                // titolare devono dire per chi.
                clientId = request.clientId.takeIf { session.isSalonSide },
                operatorId = request.operatorId,
                serviceIds = request.serviceIds,
                date = request.start.toLocalDate().toString(),
                time = request.start.toLocalTime().toString(),
                noteForOperator = request.noteForOperator,
                channel = request.channel.name,
                replacesAppointmentId = request.replacesAppointmentId,
            ),
        ).toModel()
    }

    override suspend fun reschedule(
        appointmentId: String,
        newStart: LocalDateTime,
        newOperatorId: String?,
    ): AppResult<Appointment> = mutate {
        api.reschedule(
            appointmentId,
            RescheduleBody(
                date = newStart.toLocalDate().toString(),
                time = newStart.toLocalTime().toString(),
                operatorId = newOperatorId,
            ),
        ).toModel()
    }

    /**
     * Chi ha annullato lo decide il server dal ruolo del token, non il
     * parametro: un cliente non può far risultare che l'annullamento venga dal
     * salone (e viceversa).
     */
    override suspend fun cancel(appointmentId: String, by: CancellationActor): AppResult<Unit> =
        mutate { api.cancel(appointmentId) }

    override suspend fun markInProgress(appointmentId: String): AppResult<Unit> =
        setStatus(appointmentId, AppointmentStatus.IN_PROGRESS)

    override suspend fun markCompleted(appointmentId: String): AppResult<Unit> =
        setStatus(appointmentId, AppointmentStatus.COMPLETED)

    override suspend fun markNoShow(appointmentId: String): AppResult<Unit> =
        setStatus(appointmentId, AppointmentStatus.NO_SHOW)

    private suspend fun setStatus(appointmentId: String, status: AppointmentStatus): AppResult<Unit> =
        mutate { api.setStatus(appointmentId, StatusBody(status.name)) }.map { }

    override fun waitlistForClient(clientId: String): Flow<List<WaitlistEntry>> =
        sync.reloading(SyncKeys.WAITLIST, emptyList<WaitlistEntry>()) {
            if (session.role != UserRole.CLIENT) {
                emptyList()
            } else {
                api.waitlist().entries.map { it.toModel() }
            }
        }

    override suspend fun joinWaitlist(
        clientId: String,
        date: LocalDate,
        time: LocalTime?,
        operatorId: String?,
        serviceIds: List<String>,
    ): AppResult<WaitlistEntry> = mutate {
        api.joinWaitlist(
            JoinWaitlistBody(
                date = date.toString(),
                time = time?.toString(),
                operatorId = operatorId,
                serviceIds = serviceIds,
            ),
        ).toModel()
    }

    override suspend fun leaveWaitlist(entryId: String): AppResult<Unit> =
        mutate { api.leaveWaitlist(entryId) }

    /**
     * Ogni scrittura, riuscita o no, butta via i giorni memorizzati e fa
     * rileggere ciò che le schermate stanno guardando. Anche il fallimento:
     * "questo orario è appena stato preso" vuol dire proprio che la griglia in
     * mano all'app non è più quella vera.
     */
    private suspend fun <T> mutate(block: suspend () -> T): AppResult<T> = withContext(dispatchers.io) {
        val result = apiCall(block)
        daysMutex.withLock { daysCache.clear() }
        sync.invalidate()
        result
    }
}
