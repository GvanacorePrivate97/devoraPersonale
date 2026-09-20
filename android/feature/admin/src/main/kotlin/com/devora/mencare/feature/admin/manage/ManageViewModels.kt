package com.devora.mencare.feature.admin.manage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.common.ValidationError
import com.devora.mencare.core.common.clampDuration
import com.devora.mencare.core.common.errorOrNull
import com.devora.mencare.core.common.formatCentsAsInput
import com.devora.mencare.core.common.onFailure
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.common.validateDuration
import com.devora.mencare.core.common.validatePriceInput
import com.devora.mencare.core.common.validateRequiredText
import com.devora.mencare.core.common.valueOrNull
import com.devora.mencare.core.data.repository.AdminRepository
import com.devora.mencare.core.data.repository.CatalogRepository
import com.devora.mencare.core.data.repository.TimeBlockRepository
import com.devora.mencare.core.model.NotificationSettings
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.ReminderRule
import com.devora.mencare.core.model.Service
import com.devora.mencare.core.model.TimeBlock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// --- Service list + operators ---

data class ManageUiState(
    val services: List<Service> = emptyList(),
    val operators: List<Operator> = emptyList(),
    /** Upcoming holidays and courses, keyed by operator. */
    val blocksByOperator: Map<String, List<TimeBlock>> = emptyMap(),
)

@HiltViewModel
class ManageViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
    timeBlockRepository: TimeBlockRepository,
) : ViewModel() {

    val state = combine(
        catalogRepository.services,
        catalogRepository.operators,
        timeBlockRepository.upcomingBlocks(LocalDate.now()),
    ) { services, operators, blocks ->
        ManageUiState(
            services = services,
            operators = operators,
            blocksByOperator = blocks.groupBy { it.operatorId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageUiState())

}

// --- Service editor ---

data class ServiceEditUiState(
    val isNew: Boolean = true,
    val name: String = "",
    val durationMinutes: Int = 30,
    val priceEuros: String = "",
    val operators: List<Operator> = emptyList(),
    val enabledOperatorIds: Set<String> = emptySet(),
    val nameError: ValidationError? = null,
    val durationError: ValidationError? = null,
    val priceError: ValidationError? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class ServiceEditViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serviceId: String = savedStateHandle.get<String>("serviceId").orEmpty()

    private val _state = MutableStateFlow(ServiceEditUiState())
    val state: StateFlow<ServiceEditUiState> = _state.asStateFlow()

    private var editing: Service? = null

    init {
        viewModelScope.launch {
            val operators = catalogRepository.operators.first()
            val service = catalogRepository.services.first().firstOrNull { it.id == serviceId }
            editing = service
            _state.update {
                if (service == null) {
                    it.copy(operators = operators)
                } else {
                    it.copy(
                        isNew = false,
                        name = service.name,
                        durationMinutes = service.durationMinutes,
                        priceEuros = formatCentsAsInput(service.priceCents),
                        operators = operators,
                        enabledOperatorIds = operators.filter { op -> service.id in op.serviceIds }.map { op -> op.id }.toSet(),
                    )
                }
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value, nameError = null, saveFailed = false) }

    fun setDuration(minutes: Int) = _state.update {
        it.copy(
            durationMinutes = clampDuration(minutes),
            durationError = null,
            saveFailed = false,
        )
    }

    /** Il filtro sui caratteri vive nel `PriceField`: qui arriva già ripulito. */
    fun setPrice(value: String) = _state.update { it.copy(priceEuros = value, priceError = null, saveFailed = false) }

    fun toggleOperator(operatorId: String) = _state.update {
        val set = if (operatorId in it.enabledOperatorIds) it.enabledOperatorIds - operatorId else it.enabledOperatorIds + operatorId
        it.copy(enabledOperatorIds = set)
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val name = validateRequiredText(s.name)
        val duration = validateDuration(s.durationMinutes)
        // Centesimi arrotondati, non troncati: 19,99 € vale 1999, mai 1998.
        val price = validatePriceInput(s.priceEuros)
        val nameError = name.errorOrNull()
        val durationError = duration.errorOrNull()
        val priceError = price.errorOrNull()
        if (nameError != null || durationError != null || priceError != null) {
            _state.update {
                it.copy(
                    nameError = nameError,
                    durationError = durationError,
                    priceError = priceError,
                    saveFailed = false,
                )
            }
            return
        }
        val service = Service(
            id = editing?.id.orEmpty(),
            name = name.valueOrNull().orEmpty(),
            durationMinutes = duration.valueOrNull() ?: s.durationMinutes,
            priceCents = price.valueOrNull() ?: 0L,
            description = editing?.description,
            featured = editing?.featured ?: false,
            // L'editor non ha (ancora) l'interruttore "a listino": si riscrive
            // quello che c'era, invece di riattivare in silenzio un servizio
            // che il titolare aveva tolto.
            active = editing?.active ?: true,
        )
        viewModelScope.launch {
            _state.update { it.copy(saving = true, saveFailed = false) }
            catalogRepository.saveService(service)
                .onSuccess { saved -> applyEligibility(saved, s.enabledOperatorIds) }
                .onFailure { _state.update { it.copy(saving = false, saveFailed = true) } }
        }
    }

    /**
     * La checklist di abilitazione guida il filtro operatore/servizio del wizard.
     * Oggi sono N scritture in fila: se una fallisce il catalogo resta a metà, e
     * l'errore va mostrato invece di sparire (in Fase 2 diventa un solo payload).
     */
    private suspend fun applyEligibility(saved: Service, enabledOperatorIds: Set<String>) {
        var failed = false
        _state.value.operators.forEach { op ->
            val ids = if (op.id in enabledOperatorIds) op.serviceIds + saved.id else op.serviceIds - saved.id
            if (ids != op.serviceIds) {
                catalogRepository.updateOperatorServices(op.id, ids).onFailure { failed = true }
            }
        }
        _state.update { it.copy(saving = false, saveFailed = failed, saved = !failed) }
    }
}

// --- Notification settings ---

/** Hours-before choices of the reminder at [index]: days for the first, hours for the others. */
internal fun reminderOptions(index: Int): List<Int> = if (index == 0) listOf(24, 48) else listOf(2, 4)

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    val settings = adminRepository.notificationSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationSettings(emptyList()))

    fun update(transform: (NotificationSettings) -> NotificationSettings) {
        viewModelScope.launch {
            adminRepository.updateNotificationSettings(transform(settings.value))
        }
    }

    /** The new reminder starts on an option no other reminder uses yet, when there is one. */
    fun addReminder() {
        update { s ->
            val options = reminderOptions(s.reminders.size)
            val hours = options.firstOrNull { h -> s.reminders.none { it.hoursBefore == h } } ?: options.first()
            s.copy(reminders = s.reminders + ReminderRule("rem_${s.reminders.size + 1}_${System.currentTimeMillis()}", hours))
        }
    }

    fun removeReminder(id: String) {
        update { s -> s.copy(reminders = s.reminders.filterNot { it.id == id }) }
    }

    fun setReminderHours(id: String, hours: Int) {
        update { s -> s.copy(reminders = s.reminders.map { if (it.id == id) it.copy(hoursBefore = hours) else it }) }
    }
}
