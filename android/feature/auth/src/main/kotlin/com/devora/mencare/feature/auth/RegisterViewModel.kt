package com.devora.mencare.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.common.ValidationError
import com.devora.mencare.core.common.errorOrNull
import com.devora.mencare.core.common.onFailure
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.common.validateEmail
import com.devora.mencare.core.common.validateName
import com.devora.mencare.core.common.validatePassword
import com.devora.mencare.core.common.validatePasswordConfirm
import com.devora.mencare.core.common.validatePhone
import com.devora.mencare.core.common.valueOrNull
import com.devora.mencare.core.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RegisterField { FIRST_NAME, LAST_NAME, PHONE, EMAIL, PASSWORD, PASSWORD_CONFIRM }

data class RegisterUiState(
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val email: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val termsAccepted: Boolean = false,
    val fieldErrors: Map<RegisterField, ValidationError> = emptyMap(),
    val termsError: Boolean = false,
    val emailTaken: Boolean = false,
    val loading: Boolean = false,
    val genericError: Boolean = false,
    val registered: Boolean = false,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    private var submitted = false

    fun onFieldChange(field: RegisterField, value: String) = _state.update { s ->
        val updated = when (field) {
            RegisterField.FIRST_NAME -> s.copy(firstName = value)
            RegisterField.LAST_NAME -> s.copy(lastName = value)
            RegisterField.PHONE -> s.copy(phone = value)
            RegisterField.EMAIL -> s.copy(email = value, emailTaken = false)
            RegisterField.PASSWORD -> s.copy(password = value)
            RegisterField.PASSWORD_CONFIRM -> s.copy(passwordConfirm = value)
        }
        // Errors are silent until the first submit, then update live per keystroke.
        updated.copy(
            fieldErrors = if (submitted) validate(updated) else emptyMap(),
            genericError = false,
        )
    }

    fun onTermsChange(accepted: Boolean) = _state.update { it.copy(termsAccepted = accepted, termsError = false) }

    /** Le stesse regole del backend: `core/common/Validation.kt` è l'unica copia. */
    private fun validate(s: RegisterUiState): Map<RegisterField, ValidationError> = buildMap {
        validateName(s.firstName).errorOrNull()?.let { put(RegisterField.FIRST_NAME, it) }
        validateName(s.lastName).errorOrNull()?.let { put(RegisterField.LAST_NAME, it) }
        validatePhone(s.phone).errorOrNull()?.let { put(RegisterField.PHONE, it) }
        validateEmail(s.email).errorOrNull()?.let { put(RegisterField.EMAIL, it) }
        validatePassword(s.password).errorOrNull()?.let { put(RegisterField.PASSWORD, it) }
        validatePasswordConfirm(s.password, s.passwordConfirm).errorOrNull()
            ?.let { put(RegisterField.PASSWORD_CONFIRM, it) }
    }

    fun register() {
        val s = _state.value
        if (s.loading) return
        submitted = true
        val errors = validate(s)
        if (errors.isNotEmpty() || !s.termsAccepted) {
            _state.update { it.copy(fieldErrors = errors, termsError = !s.termsAccepted) }
            return
        }
        // Al repository arrivano i valori già normalizzati: nome senza spazi in
        // coda, email minuscola, telefono in E.164 — come li vuole il backend.
        val firstName = validateName(s.firstName).valueOrNull().orEmpty()
        val lastName = validateName(s.lastName).valueOrNull().orEmpty()
        val phone = validatePhone(s.phone).valueOrNull().orEmpty()
        val email = validateEmail(s.email).valueOrNull().orEmpty()
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            authRepository.register(firstName, lastName, phone, email, s.password)
                .onSuccess { _state.update { it.copy(loading = false, registered = true) } }
                .onFailure { error ->
                    _state.update {
                        if (error is AppError.EmailAlreadyRegistered) {
                            it.copy(loading = false, emailTaken = true)
                        } else {
                            it.copy(loading = false, genericError = true)
                        }
                    }
                }
        }
    }
}
