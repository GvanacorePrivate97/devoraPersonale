package com.devora.mencare.feature.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.common.ValidationError
import com.devora.mencare.core.common.errorOrNull
import com.devora.mencare.core.common.onFailure
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.common.validateName
import com.devora.mencare.core.common.validatePhone
import com.devora.mencare.core.common.valueOrNull
import com.devora.mencare.core.data.repository.AuthRepository
import com.devora.mencare.core.data.repository.BookingRepository
import com.devora.mencare.core.data.repository.BookingRequest
import com.devora.mencare.core.data.repository.CatalogRepository
import com.devora.mencare.core.data.repository.CrmRepository
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.BookingChannel
import com.devora.mencare.core.model.ClientRecord
import com.devora.mencare.core.model.ClientSegment
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.Service
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * Same flow as the admin manual booking, but scoped to the signed-in staff
 * member's own chair: no operator picker, and the service list is limited to
 * what that operator actually performs (so slot generation never comes back
 * empty because of an eligibility mismatch the staff member can't see).
 */
/** Perché la prenotazione non è andata: lo slot preso, oppure un guasto generico. */
enum class StaffBookingError { SLOT_TAKEN, GENERIC }

data class StaffBookingUiState(
    val query: String = "",
    val results: List<ClientRecord> = emptyList(),
    val selectedClient: ClientRecord? = null,
    val creatingClient: Boolean = false,
    val newFirst: String = "",
    val newLast: String = "",
    val newPhone: String = "",
    val operator: Operator? = null,
    val services: List<Service> = emptyList(),
    val date: LocalDate = LocalDate.now(),
    val selectedServiceIds: List<String> = emptyList(),
    val slots: List<LocalTime> = emptyList(),
    val selectedSlot: LocalTime? = null,
    /**
     * Time the sheet was opened on (a tap on the agenda) or last picked:
     * selected again as soon as the chosen services fit there.
     */
    val preferredSlot: LocalTime? = null,
    val sendSms: Boolean = true,
    val newFirstError: ValidationError? = null,
    val newLastError: ValidationError? = null,
    val newPhoneError: ValidationError? = null,
    val newClientFailed: Boolean = false,
    val creatingClientBusy: Boolean = false,
    val bookingError: StaffBookingError? = null,
    val saving: Boolean = false,
    val done: Boolean = false,
    /** "Modifica" dal dettaglio appuntamento: l'appuntamento che questo modulo sostituisce. */
    val editingId: String? = null,
) {
    val totalMinutes: Int
        get() = services.filter { it.id in selectedServiceIds }.sumOf { it.durationMinutes }

    val canSave: Boolean
        get() = selectedClient != null && selectedServiceIds.isNotEmpty() && selectedSlot != null

    /** The preferred time is taken for the chosen services. */
    val preferredUnavailable: Boolean
        get() = preferredSlot != null && selectedServiceIds.isNotEmpty() &&
            selectedSlot == null && preferredSlot !in slots
}

@HiltViewModel
class StaffBookingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val bookingRepository: BookingRepository,
    private val crmRepository: CrmRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StaffBookingUiState())
    val state: StateFlow<StaffBookingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val user = authRepository.currentUser.first()
            val operator = user?.operatorId?.let { opId ->
                catalogRepository.operators.first().firstOrNull { it.id == opId }
            }
            val services = catalogRepository.services.first()
                .filter { it.id in operator?.serviceIds.orEmpty() }
            _state.update { it.copy(operator = operator, services = services) }
            // Una modifica può arrivare prima dell'operatore: gli orari si calcolano ora.
            refreshSlots()
        }
    }

    private var presetApplied = false

    /** Opened from a tap on the agenda: day and time already known. */
    fun applyPreset(date: LocalDate, time: LocalTime?) {
        if (presetApplied) return
        presetApplied = true
        _state.update { it.copy(date = date, preferredSlot = time) }
    }

    /**
     * "Modifica" dal dettaglio appuntamento: il modulo parte compilato con
     * l'appuntamento esistente, e al salvataggio il server lo sostituisce con il
     * nuovo nella stessa transazione.
     */
    fun applyEdit(appointment: Appointment, client: ClientRecord?) {
        if (presetApplied) return
        presetApplied = true
        _state.update {
            it.copy(
                editingId = appointment.id,
                selectedClient = client,
                query = client?.fullName.orEmpty(),
                date = appointment.date,
                selectedServiceIds = appointment.serviceIds,
                preferredSlot = appointment.time,
            )
        }
        refreshSlots()
    }

    fun search(query: String) {
        _state.update { it.copy(query = query) }
        viewModelScope.launch {
            val results = if (query.isBlank()) {
                emptyList()
            } else {
                crmRepository.search(query, ClientSegment.TUTTI).first().take(5)
            }
            _state.update { it.copy(results = results) }
        }
    }

    fun selectClient(client: ClientRecord) = _state.update {
        it.copy(selectedClient = client, results = emptyList(), query = client.fullName, creatingClient = false)
    }

    fun startCreateClient() = _state.update { it.copy(creatingClient = true, selectedClient = null) }

    fun setNewClientField(field: String, value: String) = _state.update {
        val cleared = it.copy(newClientFailed = false)
        when (field) {
            "first" -> cleared.copy(newFirst = value, newFirstError = null)
            "last" -> cleared.copy(newLast = value, newLastError = null)
            "phone" -> cleared.copy(newPhone = value, newPhoneError = null)
            else -> it
        }
    }

    /** Stesse regole della registrazione cliente: nome, cognome e telefono validi. */
    fun createClient() {
        val s = _state.value
        if (s.creatingClientBusy) return
        val first = validateName(s.newFirst)
        val last = validateName(s.newLast)
        val phone = validatePhone(s.newPhone)
        val firstError = first.errorOrNull()
        val lastError = last.errorOrNull()
        val phoneError = phone.errorOrNull()
        if (firstError != null || lastError != null || phoneError != null) {
            _state.update {
                it.copy(
                    newFirstError = firstError,
                    newLastError = lastError,
                    newPhoneError = phoneError,
                    newClientFailed = false,
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(creatingClientBusy = true, newClientFailed = false) }
            crmRepository.createClient(
                first.valueOrNull().orEmpty(),
                last.valueOrNull().orEmpty(),
                phone.valueOrNull().orEmpty(),
            )
                .onSuccess { record ->
                    _state.update { it.copy(creatingClientBusy = false) }
                    selectClient(record)
                }
                .onFailure { _state.update { it.copy(creatingClientBusy = false, newClientFailed = true) } }
        }
    }

    fun selectDate(date: LocalDate) {
        _state.update { it.copy(date = date, selectedSlot = null) }
        refreshSlots()
    }

    fun toggleService(serviceId: String) {
        _state.update { s ->
            val selected = if (serviceId in s.selectedServiceIds) {
                s.selectedServiceIds - serviceId
            } else {
                s.selectedServiceIds + serviceId
            }
            s.copy(selectedServiceIds = selected, selectedSlot = null)
        }
        refreshSlots()
    }

    fun selectSlot(slot: LocalTime) = _state.update { it.copy(selectedSlot = slot, preferredSlot = slot) }

    fun setSendSms(send: Boolean) = _state.update { it.copy(sendSms = send) }

    private fun refreshSlots() {
        viewModelScope.launch {
            val s = _state.value
            val operatorId = s.operator?.id
            if (operatorId == null || s.selectedServiceIds.isEmpty()) {
                _state.update { it.copy(slots = emptyList()) }
                return@launch
            }
            // In modifica l'appuntamento non occupa il proprio posto: il suo orario resta sceglibile.
            val availability = bookingRepository.availability(
                operatorId, s.selectedServiceIds, s.date, ignoreAppointmentId = s.editingId,
            )
            _state.update { it.withSlots(availability.slots) }
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val client = s.selectedClient ?: return
        val slot = s.selectedSlot ?: return
        val operatorId = s.operator?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, bookingError = null) }
            bookingRepository.book(
                BookingRequest(
                    clientId = client.id,
                    operatorId = operatorId,
                    serviceIds = s.selectedServiceIds,
                    start = s.date.atTime(slot),
                    channel = BookingChannel.PHONE,
                    // Modifica: il server sostituisce l'originale nella stessa transazione.
                    replacesAppointmentId = s.editingId,
                ),
            ).onSuccess {
                _state.update { it.copy(saving = false, done = true) }
            }.onFailure { error ->
                // Lo slot può sparire mentre si conferma: si torna a scegliere,
                // come fa il wizard del cliente, invece di non dire nulla.
                val taken = error is AppError.SlotNoLongerAvailable
                _state.update {
                    it.copy(
                        saving = false,
                        bookingError = if (taken) StaffBookingError.SLOT_TAKEN else StaffBookingError.GENERIC,
                        selectedSlot = if (taken) null else it.selectedSlot,
                    )
                }
                if (taken) refreshSlots()
            }
        }
    }
}

private fun StaffBookingUiState.withSlots(slots: List<LocalTime>): StaffBookingUiState {
    val auto = preferredSlot?.takeIf { selectedSlot == null && it in slots }
    return copy(slots = slots, selectedSlot = auto ?: selectedSlot)
}
