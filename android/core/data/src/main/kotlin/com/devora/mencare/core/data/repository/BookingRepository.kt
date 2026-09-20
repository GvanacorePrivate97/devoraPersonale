package com.devora.mencare.core.data.repository

import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.BookingChannel
import com.devora.mencare.core.model.CancellationActor
import com.devora.mencare.core.model.WaitlistEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Availability for one day: bookable start times for a given total duration. */
data class DayAvailability(
    val date: LocalDate,
    val slots: List<LocalTime>,
)

data class BookingRequest(
    val clientId: String,
    /** null = "Qualsiasi operatore": the repository picks an available one. */
    val operatorId: String?,
    val serviceIds: List<String>,
    val start: LocalDateTime,
    val noteForOperator: String? = null,
    val channel: BookingChannel = BookingChannel.APP,
    /**
     * "Modifica": l'appuntamento che questo sostituisce. Il server annulla il
     * vecchio e crea il nuovo nella stessa transazione, così lo stesso orario si
     * può tenere e, se il nuovo non entra, il vecchio resta com'era.
     */
    val replacesAppointmentId: String? = null,
)

@Suppress("TooManyFunctions")
interface BookingRepository {
    fun appointmentsForClient(clientId: String): Flow<List<Appointment>>
    fun appointmentsForOperator(operatorId: String, date: LocalDate): Flow<List<Appointment>>
    fun appointmentsForWeek(weekStart: LocalDate): Flow<List<Appointment>>
    fun appointment(id: String): Flow<Appointment?>

    /**
     * Bookable start times on [date] for [serviceIds] with [operatorId]
     * (null = union of all eligible operators).
     */
    suspend fun availability(
        operatorId: String?,
        serviceIds: List<String>,
        date: LocalDate,
        /** In modifica l'appuntamento stesso non occupa il suo posto. */
        ignoreAppointmentId: String? = null,
    ): DayAvailability

    /** Days in [from]..[to] with at least one bookable slot. */
    suspend fun availableDays(
        operatorId: String?,
        serviceIds: List<String>,
        from: LocalDate,
        to: LocalDate,
        ignoreAppointmentId: String? = null,
    ): Set<LocalDate>

    /**
     * Days in [from]..[to] the salon is open for this selection but whose every
     * slot is already taken: the client can ask to be told when one frees up.
     * Closing days, holidays and past days are never in here.
     */
    suspend fun fullyBookedDays(
        operatorId: String?,
        serviceIds: List<String>,
        from: LocalDate,
        to: LocalDate,
        ignoreAppointmentId: String? = null,
    ): Set<LocalDate>

    /** Re-verifies availability before inserting (slot-taken race guard). */
    suspend fun book(request: BookingRequest): AppResult<Appointment>

    suspend fun reschedule(appointmentId: String, newStart: LocalDateTime, newOperatorId: String? = null): AppResult<Appointment>
    suspend fun cancel(appointmentId: String, by: CancellationActor): AppResult<Unit>
    suspend fun markInProgress(appointmentId: String): AppResult<Unit>
    suspend fun markCompleted(appointmentId: String): AppResult<Unit>

    /**
     * "Non si è presentato": solo a orario d'inizio passato, anche su un
     * appuntamento già completato; [markCompleted] lo corregge. Il cliente
     * riceve una notifica.
     */
    suspend fun markNoShow(appointmentId: String): AppResult<Unit>

    // Waitlist
    /** The client's entries still waiting, each with its live queue position. */
    fun waitlistForClient(clientId: String): Flow<List<WaitlistEntry>>

    /**
     * Queues the client for [date]: [time] null = any time of the day, [operatorId]
     * null = any operator. Joining the same queue twice returns the existing entry.
     */
    @Suppress("LongParameterList")
    suspend fun joinWaitlist(
        clientId: String,
        date: LocalDate,
        time: LocalTime?,
        operatorId: String?,
        serviceIds: List<String>,
    ): AppResult<WaitlistEntry>

    suspend fun leaveWaitlist(entryId: String): AppResult<Unit>
}
