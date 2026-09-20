package com.devora.mencare.feature.client.appointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.data.network.DataSync
import com.devora.mencare.core.data.network.SyncKeys
import com.devora.mencare.core.data.repository.AuthRepository
import com.devora.mencare.core.data.repository.BookingRepository
import com.devora.mencare.core.data.repository.CatalogRepository
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.AppointmentStatus
import com.devora.mencare.core.model.CancellationActor
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.Service
import com.devora.mencare.core.model.WaitlistEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class AppointmentsUiState(
    val upcoming: List<Appointment> = emptyList(),
    val past: List<Appointment> = emptyList(),
    val waitlist: List<WaitlistEntry> = emptyList(),
    val services: Map<String, Service> = emptyMap(),
    val operators: Map<String, Operator> = emptyMap(),
    val totalVisits: Int = 0,
    /** Nome di chi l'ha servito più spesso. Al posto della spesa: le cifre le vede solo il titolare. */
    val favoriteOperatorName: String? = null,
    val avgDaysBetweenVisits: Int? = null,
    val lastCompleted: Appointment? = null,
    val loading: Boolean = false,
    val error: AppError? = null,
) {
    /** Prima risposta non ancora arrivata: non c'è ancora niente da elencare. */
    val isEmpty: Boolean
        get() = upcoming.isEmpty() && past.isEmpty() && waitlist.isEmpty() && services.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppointmentsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val bookingRepository: BookingRepository,
    catalogRepository: CatalogRepository,
    private val sync: DataSync,
) : ViewModel() {

    private val clientAppointments = authRepository.currentUser.flatMapLatest { user ->
        val id = user?.clientRecordId
        if (id == null) flowOf(emptyList()) else bookingRepository.appointmentsForClient(id)
    }

    private val clientWaitlist = authRepository.currentUser.flatMapLatest { user ->
        val id = user?.clientRecordId
        if (id == null) flowOf(emptyList()) else bookingRepository.waitlistForClient(id)
    }

    val state = combine(
        clientAppointments,
        clientWaitlist,
        catalogRepository.services,
        catalogRepository.operators,
        sync.state(SyncKeys.APPOINTMENTS, SyncKeys.WAITLIST, SyncKeys.CATALOG),
    ) { appointments, waitlist, services, operators, status ->
        val now = LocalDateTime.now()
        val completed = appointments
            .filter { it.status == AppointmentStatus.COMPLETED }
            .sortedBy { it.start }
        val visitDates = completed.map { it.date }.distinct()
        AppointmentsUiState(
            upcoming = appointments
                .filter { it.isActive && it.end >= now }
                .sortedBy { it.start },
            past = appointments
                .filter { !it.isActive || it.end < now }
                .sortedByDescending { it.start },
            waitlist = waitlist,
            services = services.associateBy { it.id },
            operators = operators.associateBy { it.id },
            totalVisits = completed.size,
            favoriteOperatorName = completed
                .groupingBy { it.operatorId }.eachCount()
                .maxByOrNull { it.value }
                ?.let { operators.firstOrNull { op -> op.id == it.key } }
                ?.name?.substringBefore(' '),
            avgDaysBetweenVisits = visitDates
                .zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }
                .takeIf { it.isNotEmpty() }
                ?.average()?.toInt(),
            lastCompleted = completed.lastOrNull(),
            loading = status.loading,
            error = status.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppointmentsUiState())

    /** "Riprova": si dimenticano i guasti e si rilegge tutto ciò che è osservato. */
    fun retry() = sync.retry()

    fun cancel(appointmentId: String) {
        viewModelScope.launch {
            bookingRepository.cancel(appointmentId, CancellationActor.CLIENT)
        }
    }

    fun leaveWaitlist(entryId: String) {
        viewModelScope.launch {
            bookingRepository.leaveWaitlist(entryId)
        }
    }
}
