package com.devora.mencare.feature.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.data.repository.AuthRepository
import com.devora.mencare.core.data.repository.BookingRepository
import com.devora.mencare.core.data.repository.CatalogRepository
import com.devora.mencare.core.data.repository.CrmRepository
import com.devora.mencare.core.data.repository.NotificationRepository
import com.devora.mencare.core.data.repository.TimeBlockRepository
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.AppointmentStatus
import com.devora.mencare.core.model.ClientRecord
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.Service
import com.devora.mencare.core.model.TimeBlock
import com.devora.mencare.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class AgendaUiState(
    val user: User? = null,
    val operator: Operator? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    /** The day's bookings for the signed-in operator, completed ones included. */
    val appointments: List<Appointment> = emptyList(),
    val blocks: List<TimeBlock> = emptyList(),
    val services: Map<String, Service> = emptyMap(),
    val clients: Map<String, ClientRecord> = emptyMap(),
    val hasUnreadNotifications: Boolean = false,
) {
    /** A tap on the grid can start a booking only where the operator isn't blocked. */
    fun isBlocked(time: LocalTime): Boolean =
        blocks.any { time >= it.range.start && time < it.range.end }

    /** True when the signed-in operator is on shift at [time]. */
    fun onShift(time: LocalTime): Boolean =
        operator?.weeklyHours?.get(selectedDate.dayOfWeek).orEmpty()
            .any { time >= it.start && time < it.end }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val bookingRepository: BookingRepository,
    private val blockRepository: TimeBlockRepository,
    catalogRepository: CatalogRepository,
    crmRepository: CrmRepository,
    notificationRepository: NotificationRepository,
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now())

    val state = combine(
        combine(authRepository.currentUser, selectedDate) { user, date -> user to date },
        catalogRepository.operators,
        catalogRepository.services,
        crmRepository.clients,
        notificationRepository.notifications,
    ) { (user, date), operators, services, clients, notifications ->
        Base(user, date, operators, services, clients, notifications.any { !it.read })
    }.flatMapLatest { base ->
        val opId = base.user?.operatorId
        if (opId == null) {
            flowOf(AgendaUiState(hasUnreadNotifications = base.hasUnread))
        } else {
            combine(
                bookingRepository.appointmentsForOperator(opId, base.date),
                blockRepository.blocksForOperator(opId, base.date),
            ) { appointments, blocks ->
                val operator = base.operators.firstOrNull { it.id == opId }
                AgendaUiState(
                    user = base.user,
                    operator = operator,
                    selectedDate = base.date,
                    // Anche i no-show, spenti: da qui si aprono per correggerli.
                    appointments = appointments.filter { it.status != AppointmentStatus.CANCELLED },
                    blocks = blocks,
                    services = base.services.associateBy { it.id },
                    clients = base.clients.associateBy { it.id },
                    hasUnreadNotifications = base.hasUnread,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgendaUiState())

    private data class Base(
        val user: User?,
        val date: LocalDate,
        val operators: List<Operator>,
        val services: List<Service>,
        val clients: List<ClientRecord>,
        val hasUnread: Boolean,
    )

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }
}
