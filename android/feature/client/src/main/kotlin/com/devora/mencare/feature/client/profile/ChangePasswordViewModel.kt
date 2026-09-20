package com.devora.mencare.feature.client.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.common.ValidationError
import com.devora.mencare.core.common.errorOrNull
import com.devora.mencare.core.common.onFailure
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.common.validatePassword
import com.devora.mencare.core.common.validatePasswordConfirm
import com.devora.mencare.core.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ChangePasswordField { CURRENT, NEW, CONFIRM }

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val fieldErrors: Map<ChangePasswordField, ValidationError> = emptyMap(),
    val wrongCurrent: Boolean = false,
    val loading: Boolean = false,
    val genericError: Boolean = false,
    val done: Boolean = false,
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    private var submitted = false

    fun onFieldChange(field: ChangePasswordField, value: String) = _state.update { s ->
        val updated = when (field) {
            ChangePasswordField.CURRENT -> s.copy(currentPassword = value, wrongCurrent = false)
            ChangePasswordField.NEW -> s.copy(newPassword = value)
            ChangePasswordField.CONFIRM -> s.copy(confirmPassword = value)
        }
        updated.copy(
            fieldErrors = if (submitted) validate(updated) else emptyMap(),
            genericError = false,
        )
    }

    /**
     * Le stesse regole della registrazione: `core/common/Validation.kt`.
     * Sulla password attuale si controlla solo che ci sia — è già stata accettata
     * quando è stata scelta, e le regole possono essere cambiate da allora.
     */
    private fun validate(s: ChangePasswordUiState): Map<ChangePasswordField, ValidationError> = buildMap {
        if (s.currentPassword.isEmpty()) put(ChangePasswordField.CURRENT, ValidationError.REQUIRED)
        validatePassword(s.newPassword).errorOrNull()?.let { put(ChangePasswordField.NEW, it) }
        validatePasswordConfirm(s.newPassword, s.confirmPassword).errorOrNull()
            ?.let { put(ChangePasswordField.CONFIRM, it) }
    }

    fun save() {
        val s = _state.value
        if (s.loading) return
        submitted = true
        val errors = validate(s)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            authRepository.changePassword(s.currentPassword, s.newPassword)
                .onSuccess { _state.update { it.copy(loading = false, done = true) } }
                .onFailure { error ->
                    _state.update {
                        if (error is AppError.InvalidCredentials) {
                            it.copy(loading = false, wrongCurrent = true)
                        } else {
                            it.copy(loading = false, genericError = true)
                        }
                    }
                }
        }
    }
}
