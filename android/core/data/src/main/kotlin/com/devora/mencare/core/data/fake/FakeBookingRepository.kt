package com.devora.mencare.core.data.fake

import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.common.formatDateLong
import com.devora.mencare.core.common.formatTime
import com.devora.mencare.core.data.SlotEngine
import com.devora.mencare.core.data.repository.BookingRepository
import com.devora.mencare.core.data.repository.BookingRequest
import com.devora.mencare.core.data.repository.DayAvailability
import com.devora.mencare.core.model.AppNotification
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.AppointmentStatus
import com.devora.mencare.core.model.CancellationActor
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.WaitlistEntry
import com.devora.mencare.core.model.WaitlistStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("TooManyFunctions")
@Singleton
class FakeBookingRepository @Inject constructor(
    private val store: InMemoryStore,
) : BookingRepository {

    private val bookingMutex = Mutex()

    override fun appointmentsForClient(clientId: String): Flow<List<Appointment>> =
        store.appointments.map { list -> list.filter { it.clientId == clientId }.sortedBy { it.start } }

    override fun appointmentsForOperator(operatorId: String, date: LocalDate): Flow<List<Appointment>> =
        store.appointments.map { list ->
            list.filter { it.operatorId == operatorId && it.date == date }.sortedBy { it.start }
        }

    override fun appointmentsForWeek(weekStart: LocalDate): Flow<List<Appointment>> =
        store.appointments.map { list ->
            val weekEnd = weekStart.plusDays(7)
            list.filter { it.date >= weekStart && it.date < weekEnd }.sortedBy { it.start }
        }

    override fun appointment(id: String): Flow<Appointment?> =
        store.appointments.map { list -> list.firstOrNull { it.id == id } }

    private fun eligibleOperators(operatorId: String?, serviceIds: List<String>): List<Operator> =
        store.operators.value.filter { op ->
            (operatorId == null || op.id == operatorId) && serviceIds.all { it in op.serviceIds }
        }

    private fun totalDuration(serviceIds: List<String>): Int =
        store.services.value.filter { it.id in serviceIds }.sumOf { it.durationMinutes }

    /** [appointments] empty = the day's capacity: what would be free with no booking at all. */
    private fun computeSlots(
        operatorId: String?,
        serviceIds: List<String>,
        date: LocalDate,
        appointments: List<Appointment> = store.appointments.value,
    ): List<LocalTime> = SlotEngine.unionSlots(
        date, eligibleOperators(operatorId, serviceIds), store.salon.value, totalDuration(serviceIds),
        appointments, store.timeBlocks.value, store.holidays.value, store.now(),
    )

    /** Appointments that occupy the grid, minus the one being edited (it gives its place up). */
    private fun occupying(ignoreAppointmentId: String?): List<Appointment> =
        store.appointments.value.filter { it.id != ignoreAppointmentId }

    override suspend fun availability(
        operatorId: String?,
        serviceIds: List<String>,
        date: LocalDate,
        ignoreAppointmentId: String?,
    ): DayAvailability = DayAvailability(date, computeSlots(operatorId, serviceIds, date, occupying(ignoreAppointmentId)))

    override suspend fun availableDays(
        operatorId: String?,
        serviceIds: List<String>,
        from: LocalDate,
        to: LocalDate,
        ignoreAppointmentId: String?,
    ): Set<LocalDate> {
        val others = occupying(ignoreAppointmentId)
        val days = mutableSetOf<LocalDate>()
        var d = from
        while (!d.isAfter(to)) {
            if (computeSlots(operatorId, serviceIds, d, others).isNotEmpty()) days += d
            d = d.plusDays(1)
        }
        return days
    }

    override suspend fun fullyBookedDays(
        operatorId: String?,
        serviceIds: List<String>,
        from: LocalDate,
        to: LocalDate,
        ignoreAppointmentId: String?,
    ): Set<LocalDate> {
        val others = occupying(ignoreAppointmentId)
        val days = mutableSetOf<LocalDate>()
        var d = from
        while (!d.isAfter(to)) {
            val full = computeSlots(operatorId, serviceIds, d, others).isEmpty() &&
                computeSlots(operatorId, serviceIds, d, appointments = emptyList()).isNotEmpty()
            if (full) days += d
            d = d.plusDays(1)
        }
        return days
    }

    override suspend fun book(request: BookingRequest): AppResult<Appointment> = bookingMutex.withLock {
        delay(500)
        val services = store.services.value.filter { it.id in request.serviceIds }
        if (services.isEmpty()) return AppResult.Failure(AppError.Validation("services"))

        // Re-verify inside the "transaction": the slot may have been taken
        // meanwhile. In modifica il vecchio appuntamento non conta: lo sostituisce.
        val slots = computeSlots(
            request.operatorId, request.serviceIds, request.start.toLocalDate(), occupying(request.replacesAppointmentId),
        )
        if (request.start.toLocalTime() !in slots) {
            return AppResult.Failure(AppError.SlotNoLongerAvailable)
        }
        request.replacesAppointmentId?.let { replaced ->
            store.appointments.value = store.appointments.value.map {
                if (it.id == replaced) it.copy(status = AppointmentStatus.CANCELLED, cancelledBy = CancellationActor.SALON) else it
            }
        }

        // "Qualsiasi operatore": pick the eligible operator free at that time.
        val operatorId = request.operatorId ?: run {
            eligibleOperators(null, request.serviceIds).firstOrNull { op ->
                request.start.toLocalTime() in SlotEngine.slotsFor(
                    request.start.toLocalDate(), op, store.salon.value, totalDuration(request.serviceIds),
                    store.appointments.value, store.timeBlocks.value, store.holidays.value, store.now(),
                )
            }?.id ?: return AppResult.Failure(AppError.SlotNoLongerAvailable)
        }

        fun instance(start: LocalDateTime) = Appointment(
            id = store.newId("apt"),
            clientId = request.clientId,
            operatorId = operatorId,
            serviceIds = request.serviceIds,
            start = start,
            durationMinutes = services.sumOf { it.durationMinutes },
            totalPriceCents = services.sumOf { it.priceCents },
            status = AppointmentStatus.CONFIRMED,
            channel = request.channel,
            noteForOperator = request.noteForOperator?.takeIf { it.isNotBlank() },
        )

        val first = instance(request.start)
        store.appointments.value += first
        return AppResult.Success(first)
    }

    override suspend fun reschedule(
        appointmentId: String,
        newStart: LocalDateTime,
        newOperatorId: String?,
    ): AppResult<Appointment> = bookingMutex.withLock {
        val current = store.appointments.value.firstOrNull { it.id == appointmentId }
            ?: return AppResult.Failure(AppError.NotFound)
        val operatorId = newOperatorId ?: current.operatorId

        // A manual move is free-form: it only has to fit the operator's real
        // availability, not the client-facing slot grid or its lead time.
        val operator = store.operators.value.firstOrNull { it.id == operatorId }
            ?: return AppResult.Failure(AppError.NotFound)
        val fits = SlotEngine.canPlace(
            date = newStart.toLocalDate(),
            operator = operator,
            salon = store.salon.value,
            start = newStart.toLocalTime(),
            totalDurationMinutes = current.durationMinutes,
            appointments = store.appointments.value,
            blocks = store.timeBlocks.value,
            holidays = store.holidays.value,
            ignoreAppointmentId = appointmentId,
        )
        if (!fits) return AppResult.Failure(AppError.SlotNoLongerAvailable)

        val updated = current.copy(start = newStart, operatorId = operatorId)
        store.appointments.value = store.appointments.value.map { if (it.id == appointmentId) updated else it }
        notifyWaitlist(current.date)
        return AppResult.Success(updated)
    }

    override suspend fun cancel(appointmentId: String, by: CancellationActor): AppResult<Unit> {
        val found = store.appointments.value.firstOrNull { it.id == appointmentId }
            ?: return AppResult.Failure(AppError.NotFound)
        store.appointments.value = store.appointments.value.map {
            if (it.id == appointmentId) it.copy(status = AppointmentStatus.CANCELLED, cancelledBy = by) else it
        }
        notifyWaitlist(found.date)
        return AppResult.Success(Unit)
    }

    private fun setStatus(appointmentId: String, status: AppointmentStatus): AppResult<Unit> {
        val found = store.appointments.value.any { it.id == appointmentId }
        if (!found) return AppResult.Failure(AppError.NotFound)
        store.appointments.value = store.appointments.value.map {
            if (it.id == appointmentId) it.copy(status = status) else it
        }
        return AppResult.Success(Unit)
    }

    override suspend fun markInProgress(appointmentId: String): AppResult<Unit> =
        setStatus(appointmentId, AppointmentStatus.IN_PROGRESS)

    override suspend fun markNoShow(appointmentId: String): AppResult<Unit> {
        val apt = store.appointments.value.firstOrNull { it.id == appointmentId }
            ?: return AppResult.Failure(AppError.NotFound)
        if (!apt.canMarkNoShow(store.now())) return AppResult.Failure(AppError.Validation("status"))
        val wasCompleted = apt.status == AppointmentStatus.COMPLETED
        val result = setStatus(appointmentId, AppointmentStatus.NO_SHOW)
        store.clients.value = store.clients.value.map {
            if (it.id == apt.clientId) {
                it.copy(
                    noShowCount = it.noShowCount + 1,
                    visitCount = if (wasCompleted) it.visitCount - 1 else it.visitCount,
                    lifetimeSpendCents = if (wasCompleted) it.lifetimeSpendCents - apt.totalPriceCents else it.lifetimeSpendCents,
                )
            } else {
                it
            }
        }
        return result
    }

    override suspend fun markCompleted(appointmentId: String): AppResult<Unit> {
        val apt = store.appointments.value.firstOrNull { it.id == appointmentId }
            ?: return AppResult.Failure(AppError.NotFound)
        if (apt.status == AppointmentStatus.COMPLETED) return AppResult.Success(Unit)
        if (apt.status == AppointmentStatus.NO_SHOW) {
            store.clients.value = store.clients.value.map {
                if (it.id == apt.clientId) it.copy(noShowCount = it.noShowCount - 1) else it
            }
        }
        val result = setStatus(appointmentId, AppointmentStatus.COMPLETED)
        // Completion drives the visit counter and spend stats.
        store.clients.value = store.clients.value.map {
            if (it.id == apt.clientId) {
                it.copy(
                    visitCount = it.visitCount + 1,
                    lifetimeSpendCents = it.lifetimeSpendCents + apt.totalPriceCents,
                    lastVisit = apt.date,
                )
            } else {
                it
            }
        }
        return result
    }

    /**
     * Queue position, derived rather than stored: entries for the same day and
     * the same operator choice, in the order they joined. Leaving moves the
     * others up.
     */
    private fun positionOf(entry: WaitlistEntry, all: List<WaitlistEntry>): Int =
        all.filter {
            it.status == WaitlistStatus.WAITING && it.date == entry.date && it.operatorId == entry.operatorId
        }.indexOfFirst { it.id == entry.id } + 1

    override fun waitlistForClient(clientId: String): Flow<List<WaitlistEntry>> =
        store.waitlist.map { list ->
            val today = store.now().toLocalDate()
            list.filter { it.clientId == clientId && it.status == WaitlistStatus.WAITING && it.date >= today }
                .map { it.copy(position = positionOf(it, list)) }
                .sortedWith(compareBy<WaitlistEntry> { it.date }.thenBy { it.time })
        }

    override suspend fun joinWaitlist(
        clientId: String,
        date: LocalDate,
        time: LocalTime?,
        operatorId: String?,
        serviceIds: List<String>,
    ): AppResult<WaitlistEntry> {
        store.waitlist.value.firstOrNull {
            it.clientId == clientId && it.date == date && it.time == time &&
                it.operatorId == operatorId && it.status == WaitlistStatus.WAITING
        }?.let { return AppResult.Success(it.copy(position = positionOf(it, store.waitlist.value))) }

        val services = store.services.value.filter { it.id in serviceIds }
        val entry = WaitlistEntry(
            id = store.newId("wl"),
            clientId = clientId,
            date = date,
            time = time,
            operatorId = operatorId,
            serviceIds = serviceIds,
            durationMinutes = services.sumOf { it.durationMinutes },
            totalPriceCents = services.sumOf { it.priceCents },
            position = 0,
        )
        store.waitlist.value += entry
        return AppResult.Success(entry.copy(position = positionOf(entry, store.waitlist.value)))
    }

    override suspend fun leaveWaitlist(entryId: String): AppResult<Unit> {
        store.waitlist.value = store.waitlist.value.filterNot { it.id == entryId }
        return AppResult.Success(Unit)
    }

    /**
     * Something freed up on [date]: the first entry in line whose services fit
     * again gets a notification and leaves the queue — the next freed slot goes
     * to the next one. The slot isn't held: whoever books first gets it.
     */
    private fun notifyWaitlist(date: LocalDate) {
        val waiting = store.waitlist.value.filter { it.status == WaitlistStatus.WAITING && it.date == date }
        var freed: LocalTime? = null
        val first = waiting.firstOrNull { entry ->
            val slots = computeSlots(entry.operatorId, entry.serviceIds, date)
            freed = when (val wanted = entry.time) {
                null -> slots.firstOrNull()
                else -> wanted.takeIf { it in slots }
            }
            freed != null
        } ?: return
        val time = freed ?: return
        store.waitlist.value = store.waitlist.value.map {
            if (it.id == first.id) it.copy(status = WaitlistStatus.NOTIFIED) else it
        }
        val userId = store.users.value.firstOrNull { it.clientRecordId == first.clientId }?.id ?: return
        val withOperator = first.operatorId
            ?.let { id -> store.operators.value.firstOrNull { it.id == id } }
            ?.let { " con ${it.name.substringBefore(' ')}" }
            .orEmpty()
        store.notifications.value += AppNotification(
            id = store.newId("ntf"),
            userId = userId,
            title = "Lista d'attesa",
            body = "Si è liberato un posto ${formatDateLong(date)} alle ${formatTime(time)}$withOperator: " +
                "prenota prima che lo prenda qualcun altro.",
            at = store.now(),
        )
    }
}
