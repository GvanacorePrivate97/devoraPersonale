package com.devora.mencare.feature.admin.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.common.onFailure
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.data.repository.BookingRepository
import com.devora.mencare.core.data.repository.CatalogRepository
import com.devora.mencare.core.data.repository.CrmRepository
import com.devora.mencare.core.data.repository.NotificationRepository
import com.devora.mencare.core.data.repository.TimeBlockRepository
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.CancellationActor
import com.devora.mencare.core.model.ClientRecord
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.Service
import com.devora.mencare.core.model.TimeBlock
import com.devora.mencare.core.model.TimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

enum class MoveResult { MOVED, UNAVAILABLE }

data class WeeklyAgendaUiState(
    val weekStart: LocalDate = LocalDate.now(),
    val selectedDay: LocalDate = LocalDate.now(),
    val operators: List<Operator> = emptyList(),
    val appointments: List<Appointment> = emptyList(),
    val blocks: List<TimeBlock> = emptyList(),
    val services: Map<String, Service> = emptyMap(),
    val clients: Map<String, ClientRecord> = emptyMap(),
    val moveResult: MoveResult? = null,
    val hasUnreadNotifications: Boolean = false,
) {
    // Come l'agenda dell'operatore: si vedono anche i completati (in stone) e i
    // no-show (spenti, per poterli correggere); spariscono solo gli annullati.
    fun appointmentsFor(operatorId: String): List<Appointment> =
        appointments.filter {
            it.operatorId == operatorId && it.date == selectedDay &&
                it.status != com.devora.mencare.core.model.AppointmentStatus.CANCELLED
        }

    fun blocksFor(operatorId: String): List<TimeBlock> =
        blocks.filter { it.operatorId == operatorId && it.date == selectedDay }

    /** A tap on the grid can start a booking only where the operator isn't blocked. */
    fun isBlocked(operatorId: String, time: LocalTime): Boolean =
        blocksFor(operatorId).any { time >= it.range.start && time < it.range.end }

    /** True when the operator is on shift at [time] (blocks aside). */
    fun onShift(operatorId: String, time: LocalTime): Boolean =
        operators.firstOrNull { it.id == operatorId }
            ?.weeklyHours?.get(selectedDay.dayOfWeek).orEmpty()
            .any { time >= it.start && time < it.end }

    /** True when [operator] is on shift for the whole [durationMinutes] from [time]. */
    fun worksAt(operator: Operator, time: LocalTime, durationMinutes: Int): Boolean {
        val end = time.plusMinutes(durationMinutes.toLong())
        return operator.weeklyHours[selectedDay.dayOfWeek].orEmpty()
            .any { time >= it.start && end <= it.end }
    }

    /**
     * The selected day's stretches where this operator is NOT on shift, within
     * the agenda rail: shaded in the grid so an empty column doesn't read as free.
     */
    fun offDutyRanges(operatorId: String): List<TimeRange> {
        val rail = TimeRange(
            LocalTime.of(com.devora.mencare.core.designsystem.component.AgendaGrid.DAY_START_HOUR, 0),
            LocalTime.of(com.devora.mencare.core.designsystem.component.AgendaGrid.DAY_END_HOUR, 0),
        )
        val working = operators.firstOrNull { it.id == operatorId }
            ?.weeklyHours?.get(selectedDay.dayOfWeek).orEmpty()
            .mapNotNull { it.intersect(rail) }
            .sortedBy { it.start }
        val gaps = mutableListOf<TimeRange>()
        var cursor = rail.start
        working.forEach { shift ->
            if (shift.start > cursor) gaps += TimeRange(cursor, shift.start)
            if (shift.end > cursor) cursor = shift.end
        }
        if (cursor < rail.end) gaps += TimeRange(cursor, rail.end)
        return gaps
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeeklyAgendaViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val blockRepository: TimeBlockRepository,
    catalogRepository: CatalogRepository,
    crmRepository: CrmRepository,
    notificationRepository: NotificationRepository,
) : ViewModel() {

    private val selectedDay = MutableStateFlow(LocalDate.now())
    private val moveResult = MutableStateFlow<MoveResult?>(null)

    private val weekStart = selectedDay

    val state = combine(
        selectedDay,
        combine(catalogRepository.operators, catalogRepository.services) { o, s -> o to s },
        selectedDay.flatMapLatest { day ->
            val monday = day.minusDays((day.dayOfWeek.value - 1).toLong())
            combine(
                bookingRepository.appointmentsForWeek(monday),
                blockRepository.blocksForWeek(monday),
            ) { a, b -> a to b }
        },
        combine(crmRepository.clients, notificationRepository.notifications) { c, n -> c to n.any { !it.read } },
        moveResult,
    ) { day, (operators, services), (appointments, blocks), (clients, hasUnread), move ->
        WeeklyAgendaUiState(
            weekStart = day.minusDays((day.dayOfWeek.value - 1).toLong()),
            selectedDay = day,
            operators = operators,
            appointments = appointments,
            blocks = blocks,
            services = services.associateBy { it.id },
            clients = clients.associateBy { it.id },
            moveResult = move,
            hasUnreadNotifications = hasUnread,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeeklyAgendaUiState())

    fun selectDay(day: LocalDate) {
        selectedDay.value = day
    }

    /** Drag & drop: move an appointment to a new time and/or operator column. */
    fun move(appointmentId: String, newOperatorId: String, newTime: LocalTime) {
        viewModelScope.launch {
            val day = selectedDay.value
            bookingRepository.reschedule(appointmentId, LocalDateTime.of(day, newTime), newOperatorId)
                .onSuccess { moveResult.value = MoveResult.MOVED }
                .onFailure { moveResult.value = MoveResult.UNAVAILABLE }
        }
    }

    /** "Non si è presentato": il cliente riceve una notifica. */
    fun markNoShow(appointmentId: String) {
        viewModelScope.launch { bookingRepository.markNoShow(appointmentId) }
    }

    /** Completato a mano: prima della fine, o per correggere un no-show. */
    fun markCompleted(appointmentId: String) {
        viewModelScope.launch { bookingRepository.markCompleted(appointmentId) }
    }

    /** Cancelled by the salon, not by the client. */
    fun cancel(appointmentId: String) {
        viewModelScope.launch {
            bookingRepository.cancel(appointmentId, CancellationActor.SALON)
        }
    }

    fun consumeMoveResult() {
        moveResult.value = null
    }
}
