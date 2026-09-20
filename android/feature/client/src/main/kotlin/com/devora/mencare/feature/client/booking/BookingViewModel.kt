package com.devora.mencare.feature.client.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.common.NOTE_MAX
import com.devora.mencare.core.common.onFailure
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.common.validateNote
import com.devora.mencare.core.common.valueOrNull
import com.devora.mencare.core.data.network.DataSync
import com.devora.mencare.core.data.network.SyncKeys
import com.devora.mencare.core.data.repository.AuthRepository
import com.devora.mencare.core.data.repository.BookingRepository
import com.devora.mencare.core.data.repository.BookingRequest
import com.devora.mencare.core.data.repository.CatalogRepository
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.Salon
import com.devora.mencare.core.model.Service
import com.devora.mencare.core.model.WaitlistEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject

/** Wizard steps 1–4 plus the final confirmation screen. */
enum class WizardStep { OPERATOR, SERVICES, DATETIME, SUMMARY, CONFIRMED }

data class OperatorOption(
    val operator: Operator,
    val nextAvailability: LocalDateTime?,
) {
    val availableSoon: Boolean get() = nextAvailability != null
}

data class BookingUiState(
    val step: WizardStep = WizardStep.OPERATOR,
    val salon: Salon? = null,
    // Step 1
    val operatorOptions: List<OperatorOption> = emptyList(),
    /** null while unset; Some(null) is modeled by anyOperator=true. */
    val selectedOperatorId: String? = null,
    val anyOperator: Boolean = false,
    // Step 2
    val services: List<Service> = emptyList(),
    val selectedServiceIds: List<String> = emptyList(),
    // Step 3
    val month: YearMonth = YearMonth.now(),
    val availableDays: Set<LocalDate> = emptySet(),
    /** Open days whose every slot is taken: selectable, they offer the waitlist. */
    val fullyBookedDays: Set<LocalDate> = emptySet(),
    val selectedDate: LocalDate? = null,
    val slots: List<LocalTime> = emptyList(),
    val slotsLoading: Boolean = false,
    val selectedSlot: LocalTime? = null,
    // Step 4
    val note: String = "",
    val submitting: Boolean = false,
    val error: BookingError? = null,
    val confirmed: Appointment? = null,
    // Waitlist: set when the chosen slot turns out taken at confirm time.
    val takenSlot: LocalTime? = null,
    val waitlistJoinedPosition: Int? = null,
    /** The client's waiting entries: a full day they already queued for shows as joined. */
    val waitlist: List<WaitlistEntry> = emptyList(),
    val joiningWaitlist: Boolean = false,
    /** Set when the wizard edits an upcoming appointment instead of creating one. */
    val editingAppointmentId: String? = null,
    /** Listino e squadra non ancora arrivati: il primo passo è da disegnare. */
    val loadingCatalog: Boolean = true,
    /** Guasto in lettura (listino, disponibilità), distinto da [error] che è la conferma. */
    val loadError: AppError? = null,
) {
    val isEditing: Boolean get() = editingAppointmentId != null

    val operatorChosen: Boolean get() = anyOperator || selectedOperatorId != null

    val selectedServices: List<Service> get() = services.filter { it.id in selectedServiceIds }

    val totalDurationMinutes: Int get() = selectedServices.sumOf { it.durationMinutes }

    /** Duration used for slot generation. */
    val totalSlotMinutes: Int get() = totalDurationMinutes

    val totalPriceCents: Long get() = selectedServices.sumOf { it.priceCents }

    /** The chosen day has no free slot at all: the "Avvisami" page replaces the grid. */
    val selectedDayFull: Boolean
        get() = selectedDate != null && selectedDate in fullyBookedDays && !slotsLoading && slots.isEmpty()

    /** The client's queue entry for the whole chosen day with this operator choice, if any. */
    val dayWaitlistEntry: WaitlistEntry?
        get() = waitlist.firstOrNull {
            it.date == selectedDate && it.time == null &&
                it.operatorId == selectedOperatorId.takeUnless { anyOperator }
        }

    val selectedOperator: Operator? get() = operatorOptions.firstOrNull { it.operator.id == selectedOperatorId }?.operator

    /** Services the chosen operator can perform (all when "Qualsiasi"). */
    val eligibleServices: List<Service>
        get() = when {
            // Un servizio ritirato dal listino resta nell'elenco per dare il
            // nome allo storico, ma non si può più prenotare.
            anyOperator -> services.filter { it.active }
            else -> selectedOperator
                ?.let { op -> services.filter { it.active && it.id in op.serviceIds } }
                .orEmpty()
        }
}

enum class BookingError { SLOT_TAKEN, GENERIC }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val bookingRepository: BookingRepository,
    private val catalogRepository: CatalogRepository,
    private val sync: DataSync,
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    private val rebookId: String? = savedStateHandle.get<String>("rebookId")
    private val editId: String? = savedStateHandle.get<String>("editId")?.takeIf { it.isNotBlank() }

    // Slot scelto dalla home: giorno, ora (in secondi), operatore e servizi.
    private val quickDate: LocalDate? =
        savedStateHandle.get<String>("date")?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
    private val quickTime: LocalTime? =
        savedStateHandle.get<String>("time")?.toIntOrNull()?.let { LocalTime.ofSecondOfDay(it.toLong()) }
    private val quickOperatorId: String? =
        savedStateHandle.get<String>("operatorId")?.takeIf { it.isNotBlank() }
    private val quickServiceIds: List<String> =
        savedStateHandle.get<String>("services").orEmpty().split(',').filter { it.isNotBlank() }

    private val _state = MutableStateFlow(BookingUiState())
    val state: StateFlow<BookingUiState> = _state.asStateFlow()

    init {
        authRepository.currentUser
            .flatMapLatest { user ->
                user?.clientRecordId?.let(bookingRepository::waitlistForClient) ?: flowOf(emptyList())
            }
            .onEach { entries -> _state.update { it.copy(waitlist = entries) } }
            .launchIn(viewModelScope)

        // Il guasto di lettura non passa dai `Flow` dei dati: le interfacce dei
        // repository tornano valori, non esiti. Arriva da qui.
        sync.state(SyncKeys.CATALOG, SyncKeys.APPOINTMENTS, SyncKeys.WAITLIST)
            .onEach { status -> _state.update { it.copy(loadError = status.error) } }
            .launchIn(viewModelScope)

        viewModelScope.launch { loadCatalog() }
    }

    /** "Riprova" del wizard: si rilegge tutto e si ricostruisce il primo passo. */
    fun retry() {
        _state.update { it.copy(loadingCatalog = true, loadError = null) }
        sync.retry()
        viewModelScope.launch { loadCatalog() }
    }

    private suspend fun loadCatalog() {
        val salon = catalogRepository.salon.first()
        val operators = catalogRepository.operators.first()
        val services = catalogRepository.services.first()

        // Una richiesta per operatore, non una in fila all'altra: aprire il
        // wizard non deve costare la somma di quattro andate e ritorni.
        val options = coroutineScope {
            operators
                .map { op -> async { OperatorOption(op, nextAvailabilityFor(op.id, op.serviceIds.take(1))) } }
                .map { it.await() }
        }
        _state.update {
            it.copy(salon = salon, operatorOptions = options, services = services, loadingCatalog = false)
        }

        if (editId != null) {
            bookingRepository.appointment(editId).first()?.let { prefillForEdit(it) }
        } else if (quickDate != null && quickTime != null && quickServiceIds.isNotEmpty()) {
            // Slot scelto dalla home: si atterra sullo step data, già compilato.
            applyQuickSlot(quickDate, quickTime, quickOperatorId, quickServiceIds)
        } else {
            // One-tap rebook: preselect the past appointment's operator and services.
            rebookId?.takeIf { it.isNotBlank() }?.let { id ->
                bookingRepository.appointment(id).first()?.let { prefillFrom(it) }
            }
        }
    }

    /**
     * Apre il wizard sullo slot scelto in home. Gli slot vengono ricalcolati
     * qui: se nel frattempo qualcuno ha preso quell'ora, il giorno resta
     * selezionato ma l'ora no, e il cliente ne sceglie un'altra.
     */
    private suspend fun applyQuickSlot(
        date: LocalDate,
        time: LocalTime,
        operatorId: String?,
        serviceIds: List<String>,
    ) {
        val month = YearMonth.from(date)
        val from = maxOf(month.atDay(1), LocalDate.now())
        val days = bookingRepository.availableDays(operatorId, serviceIds, from, month.atEndOfMonth())
        val full = bookingRepository.fullyBookedDays(operatorId, serviceIds, from, month.atEndOfMonth())
        val slots = bookingRepository.availability(operatorId, serviceIds, date).slots
        _state.update {
            it.copy(
                step = WizardStep.DATETIME,
                selectedOperatorId = operatorId,
                anyOperator = operatorId == null,
                selectedServiceIds = serviceIds,
                month = month,
                availableDays = days,
                fullyBookedDays = full,
                selectedDate = date,
                slots = slots,
                selectedSlot = time.takeIf { chosen -> chosen in slots },
                slotsLoading = false,
            )
        }
    }

    /** First bookable slot in the next two weeks, probing with a 30-min service. */
    private suspend fun nextAvailabilityFor(operatorId: String, probeServiceIds: List<String>): LocalDateTime? {
        if (probeServiceIds.isEmpty()) return null
        val today = LocalDate.now()
        val days = bookingRepository.availableDays(operatorId, probeServiceIds, today, today.plusDays(14))
        val firstDay = days.minOrNull() ?: return null
        val slots = bookingRepository.availability(operatorId, probeServiceIds, firstDay).slots
        return slots.firstOrNull()?.let { firstDay.atTime(it) }
    }

    // --- Step 1 ---

    fun selectOperator(operatorId: String?) {
        _state.update { s ->
            val next = s.copy(
                selectedOperatorId = operatorId,
                anyOperator = operatorId == null,
                // Selection changes filtering: drop services no longer eligible.
                selectedServiceIds = s.selectedServiceIds.filter { id ->
                    operatorId == null || s.operatorOptions.firstOrNull { o -> o.operator.id == operatorId }?.operator?.serviceIds?.contains(id) == true
                },
                selectedDate = null,
                selectedSlot = null,
                slots = emptyList(),
            )
            next
        }
    }

    fun goToStep(step: WizardStep) = _state.update { it.copy(step = step, error = null) }

    fun continueFromOperator() {
        if (_state.value.operatorChosen) goToStep(WizardStep.SERVICES)
    }

    // --- Step 2 ---

    fun toggleService(serviceId: String) {
        _state.update { s ->
            val selected = if (serviceId in s.selectedServiceIds) {
                s.selectedServiceIds - serviceId
            } else {
                s.selectedServiceIds + serviceId
            }
            s.copy(selectedServiceIds = selected, selectedDate = null, selectedSlot = null, slots = emptyList())
        }
    }

    fun continueFromServices() {
        val s = _state.value
        if (s.selectedServiceIds.isEmpty()) return
        _state.update { it.copy(step = WizardStep.DATETIME) }
        loadMonth(s.month)
    }

    // --- Step 3 ---

    fun loadMonth(month: YearMonth) {
        _state.update { it.copy(month = month, slotsLoading = true) }
        viewModelScope.launch {
            val s = _state.value
            val today = LocalDate.now()
            val from = maxOf(month.atDay(1), today)
            val to = month.atEndOfMonth()
            val operatorId = s.selectedOperatorId.takeUnless { s.anyOperator }
            val (days, full) = if (to < today) {
                emptySet<LocalDate>() to emptySet<LocalDate>()
            } else {
                // In modifica l'appuntamento originale non occupa il suo posto.
                bookingRepository.availableDays(operatorId, s.selectedServiceIds, from, to, s.editingAppointmentId) to
                    bookingRepository.fullyBookedDays(operatorId, s.selectedServiceIds, from, to, s.editingAppointmentId)
            }
            _state.update { it.copy(availableDays = days, fullyBookedDays = full, slotsLoading = false) }
        }
    }

    fun selectDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date, selectedSlot = null, slotsLoading = true) }
        viewModelScope.launch {
            val s = _state.value
            val availability = bookingRepository.availability(
                s.selectedOperatorId.takeUnless { s.anyOperator }, s.selectedServiceIds, date, s.editingAppointmentId,
            )
            _state.update { it.copy(slots = availability.slots, slotsLoading = false) }
        }
    }

    fun selectSlot(slot: LocalTime) = _state.update {
        // Picking a new time closes the lost-slot waitlist prompt.
        it.copy(selectedSlot = slot, takenSlot = null, error = null, waitlistJoinedPosition = null)
    }

    fun continueFromDatetime() {
        val s = _state.value
        if (s.selectedDate != null && s.selectedSlot != null) goToStep(WizardStep.SUMMARY)
    }

    // --- Step 4 ---

    fun onNoteChange(value: String) = _state.update { it.copy(note = value.take(NOTE_MAX)) }

    fun confirm() {
        val s = _state.value
        val date = s.selectedDate ?: return
        val slot = s.selectedSlot ?: return
        if (s.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            val user = authRepository.currentUser.first()
            val clientId = user?.clientRecordId
            if (clientId == null) {
                _state.update { it.copy(submitting = false, error = BookingError.GENERIC) }
                return@launch
            }
            bookingRepository.book(
                BookingRequest(
                    clientId = clientId,
                    operatorId = s.selectedOperatorId.takeUnless { s.anyOperator },
                    serviceIds = s.selectedServiceIds,
                    start = date.atTime(slot),
                    noteForOperator = validateNote(s.note).valueOrNull(),
                    // Modifica: il server sostituisce l'originale nella stessa transazione.
                    replacesAppointmentId = s.editingAppointmentId,
                ),
            ).onSuccess { appointment ->
                _state.update { it.copy(submitting = false, confirmed = appointment, step = WizardStep.CONFIRMED) }
            }.onFailure { error ->
                if (error is AppError.SlotNoLongerAvailable) {
                    // The mockup's flagged race: bounce back to slot choice with fresh
                    // data, remembering the lost slot so the waitlist can target it.
                    _state.update {
                        it.copy(
                            submitting = false,
                            error = BookingError.SLOT_TAKEN,
                            step = WizardStep.DATETIME,
                            takenSlot = slot,
                        )
                    }
                    // The lost slot may have been the last one: the day can turn full.
                    loadMonth(s.month)
                    selectDate(date)
                } else {
                    _state.update { it.copy(submitting = false, error = BookingError.GENERIC) }
                }
            }
        }
    }

    // --- Waitlist ---

    /**
     * Joins the queue for the slot the client just lost: a push arrives if it
     * frees up again. Only reachable after a [BookingError.SLOT_TAKEN] bounce.
     */
    fun joinWaitlist() {
        val s = _state.value
        val date = s.selectedDate ?: return
        val slot = s.takenSlot ?: return
        viewModelScope.launch {
            val user = authRepository.currentUser.first()
            val clientId = user?.clientRecordId ?: return@launch
            bookingRepository.joinWaitlist(
                clientId, date, slot,
                s.selectedOperatorId.takeUnless { s.anyOperator }, s.selectedServiceIds,
            ).onSuccess { entry ->
                _state.update { it.copy(waitlistJoinedPosition = entry.position) }
            }
        }
    }

    /**
     * "Avvisami" on a fully booked day: queues the client for any time of that
     * day, with the chosen operator (or any), sized on the chosen services.
     */
    fun joinDayWaitlist() {
        val s = _state.value
        val date = s.selectedDate ?: return
        if (s.joiningWaitlist || s.dayWaitlistEntry != null) return
        viewModelScope.launch {
            val clientId = authRepository.currentUser.first()?.clientRecordId ?: return@launch
            _state.update { it.copy(joiningWaitlist = true) }
            bookingRepository.joinWaitlist(
                clientId, date, null,
                s.selectedOperatorId.takeUnless { s.anyOperator }, s.selectedServiceIds,
            )
            _state.update { it.copy(joiningWaitlist = false) }
        }
    }

    /** One-tap rebook: preselect operator + services of a past appointment. */
    fun prefillFrom(appointment: Appointment) {
        _state.update {
            it.copy(
                selectedOperatorId = appointment.operatorId,
                anyOperator = false,
                selectedServiceIds = appointment.serviceIds,
                step = WizardStep.DATETIME,
            )
        }
        loadMonth(YearMonth.now())
    }

    /**
     * Modifica: operatore, servizi e note dell'appuntamento sono già scelti,
     * ma il wizard riparte dal primo step così il cliente può rivedere tutto.
     */
    fun prefillForEdit(appointment: Appointment) {
        _state.update {
            it.copy(
                editingAppointmentId = appointment.id,
                selectedOperatorId = appointment.operatorId,
                anyOperator = false,
                selectedServiceIds = appointment.serviceIds,
                note = appointment.noteForOperator.orEmpty(),
                month = YearMonth.from(appointment.date),
                step = WizardStep.OPERATOR,
            )
        }
    }

    fun reset() {
        _state.update {
            BookingUiState(
                salon = it.salon,
                operatorOptions = it.operatorOptions,
                services = it.services,
                waitlist = it.waitlist,
                // Il listino è già in mano: ripartire non vuol dire ricaricarlo.
                loadingCatalog = it.loadingCatalog,
            )
        }
    }
}
