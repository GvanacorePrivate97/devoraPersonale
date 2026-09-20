package com.devora.mencare.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.common.ValidationError
import com.devora.mencare.core.common.errorOrNull
import com.devora.mencare.core.common.onFailure
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.common.validateEmail
import com.devora.mencare.core.common.valueOrNull
import com.devora.mencare.core.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecoverUiState(
    val email: String = "",
    val loading: Boolean = false,
    val sent: Boolean = false,
    val emailError: ValidationError? = null,
    val genericError: Boolean = false,
)

@HiltViewModel
class RecoverViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RecoverUiState())
    val state: StateFlow<RecoverUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update {
        it.copy(email = value, emailError = null, genericError = false)
    }

    fun send() {
        val current = _state.value
        if (current.loading) return
        // Stessa regola dell'email in registrazione: niente più controllo debole qui.
        val result = validateEmail(current.email)
        val error = result.errorOrNull()
        if (error != null) {
            _state.update { it.copy(emailError = error, sent = false) }
            return
        }
        val email = result.valueOrNull().orEmpty()
        viewModelScope.launch {
            _state.update { it.copy(loading = true, genericError = false) }
            authRepository.requestPasswordReset(email)
                .onSuccess { _state.update { it.copy(loading = false, sent = true, email = email) } }
                .onFailure { _state.update { it.copy(loading = false, genericError = true) } }
        }
    }
}
