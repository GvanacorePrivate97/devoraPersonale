package com.devora.mencare.feature.admin.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.common.ValidationError
import com.devora.mencare.core.common.errorOrNull
import com.devora.mencare.core.common.onFailure
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.common.validateEmail
import com.devora.mencare.core.common.validateName
import com.devora.mencare.core.common.validatePhone
import com.devora.mencare.core.common.valueOrNull
import com.devora.mencare.core.data.repository.CatalogRepository
import com.devora.mencare.core.data.repository.NewOperator
import com.devora.mencare.core.model.Service
import com.devora.mencare.core.model.TimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

enum class OperatorField { NAME, EMAIL, PHONE }

data class OperatorEditUiState(
    val name: String = "",
    val title: String = "",
    val email: String = "",
    val phone: String = "",
    val services: List<Service> = emptyList(),
    val selectedServiceIds: Set<String> = emptySet(),
    val workingDays: Set<DayOfWeek> = DEFAULT_DAYS,
    val from: LocalTime = LocalTime.of(9, 0),
    val to: LocalTime = LocalTime.of(19, 0),
    val errors: Map<OperatorField, ValidationError> = emptyMap(),
    val emailTaken: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean
        get() = name.isNotBlank() && email.isNotBlank() && phone.isNotBlank() &&
            selectedServiceIds.isNotEmpty() && workingDays.isNotEmpty() && from < to
}

private val DEFAULT_DAYS = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
)

/**
 * New hire form. Saving creates the operator profile *and* the staff account
 * that lets them sign in — both live behind CatalogRepository.createOperator.
 */
@HiltViewModel
class OperatorEditViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OperatorEditUiState())
    val state: StateFlow<OperatorEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val services = catalogRepository.services.first()
            _state.update { it.copy(services = services) }
        }
    }

    fun onFieldChange(field: OperatorField, value: String) = _state.update { s ->
        val updated = when (field) {
            OperatorField.NAME -> s.copy(name = value)
            OperatorField.EMAIL -> s.copy(email = value, emailTaken = false)
            OperatorField.PHONE -> s.copy(phone = value)
        }
        updated.copy(errors = s.errors - field, saveFailed = false)
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value) }

    fun toggleService(serviceId: String) = _state.update { s ->
        val next = if (serviceId in s.selectedServiceIds) {
            s.selectedServiceIds - serviceId
        } else {
            s.selectedServiceIds + serviceId
        }
        s.copy(selectedServiceIds = next)
    }

    fun toggleDay(day: DayOfWeek) = _state.update { s ->
        val next = if (day in s.workingDays) s.workingDays - day else s.workingDays + day
        s.copy(workingDays = next)
    }

    fun setFrom(time: LocalTime) = _state.update { s ->
        s.copy(from = time, to = if (time >= s.to) time.plusHours(1) else s.to)
    }

    fun setTo(time: LocalTime) = _state.update { s ->
        s.copy(to = if (time <= s.from) s.from.plusHours(1) else time)
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        // Nome, email e telefono passano dalle stesse regole della registrazione.
        val name = validateName(s.name)
        val email = validateEmail(s.email)
        val phone = validatePhone(s.phone)
        val errors = buildMap {
            name.errorOrNull()?.let { put(OperatorField.NAME, it) }
            email.errorOrNull()?.let { put(OperatorField.EMAIL, it) }
            phone.errorOrNull()?.let { put(OperatorField.PHONE, it) }
        }
        if (errors.isNotEmpty() || !s.canSave) {
            _state.update { it.copy(errors = errors, saveFailed = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, saveFailed = false) }
            val shift = listOf(TimeRange(s.from, s.to))
            catalogRepository.createOperator(
                NewOperator(
                    name = name.valueOrNull().orEmpty(),
                    title = s.title.trim(),
                    email = email.valueOrNull().orEmpty(),
                    phone = phone.valueOrNull().orEmpty(),
                    serviceIds = s.selectedServiceIds,
                    weeklyHours = s.workingDays.associateWith { shift },
                ),
            )
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { error ->
                    _state.update {
                        if (error is AppError.EmailAlreadyRegistered) {
                            it.copy(saving = false, emailTaken = true)
                        } else {
                            it.copy(saving = false, saveFailed = true)
                        }
                    }
                }
        }
    }
}
