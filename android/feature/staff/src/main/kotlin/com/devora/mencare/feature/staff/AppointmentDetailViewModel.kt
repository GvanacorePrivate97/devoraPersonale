package com.devora.mencare.feature.staff

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.data.repository.BookingRepository
import com.devora.mencare.core.data.repository.CatalogRepository
import com.devora.mencare.core.data.repository.CrmRepository
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.AppointmentStatus
import com.devora.mencare.core.model.CancellationActor
import com.devora.mencare.core.model.ClientRecord
import com.devora.mencare.core.model.Service
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppointmentDetailUiState(
    val appointment: Appointment? = null,
    val client: ClientRecord? = null,
    val services: Map<String, Service> = emptyMap(),
    val history: List<Appointment> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppointmentDetailViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    crmRepository: CrmRepository,
    catalogRepository: CatalogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val appointmentId: String = checkNotNull(savedStateHandle["appointmentId"])

    val state = bookingRepository.appointment(appointmentId).flatMapLatest { appointment ->
        if (appointment == null) {
            flowOf(AppointmentDetailUiState())
        } else {
            combine(
                crmRepository.client(appointment.clientId),
                catalogRepository.services,
                bookingRepository.appointmentsForClient(appointment.clientId),
            ) { client, services, clientAppointments ->
                AppointmentDetailUiState(
                    appointment = appointment,
                    client = client,
                    services = services.associateBy { it.id },
                    history = clientAppointments
                        .filter { it.status == AppointmentStatus.COMPLETED && it.id != appointmentId }
                        .sortedByDescending { it.start },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppointmentDetailUiState())

    fun markCompleted() {
        viewModelScope.launch {
            bookingRepository.markCompleted(appointmentId)
        }
    }

    /** "Non si è presentato": il cliente riceve una notifica; [markCompleted] lo corregge. */
    fun markNoShow() {
        viewModelScope.launch {
            bookingRepository.markNoShow(appointmentId)
        }
    }

    /** Cancelled from behind the chair: the salon is the actor, not the client. */
    fun cancel(onDone: () -> Unit) {
        viewModelScope.launch {
            bookingRepository.cancel(appointmentId, CancellationActor.SALON)
            onDone()
        }
    }
}
