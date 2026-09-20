package com.devora.mencare.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.common.ValidationError
import com.devora.mencare.core.common.errorOrNull
import com.devora.mencare.core.common.onFailure
import com.devora.mencare.core.common.onSuccess
import com.devora.mencare.core.common.validateEmail
import com.devora.mencare.core.common.valueOrNull
import com.devora.mencare.core.data.repository.AuthRepository
import com.devora.mencare.core.data.repository.SocialProvider
import com.devora.mencare.core.model.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: ValidationError? = null,
    val passwordError: ValidationError? = null,
    val loading: Boolean = false,
    val error: LoginError? = null,
    val loggedInRole: UserRole? = null,
)

/**
 * [SOCIAL_UNAVAILABLE]: l'accesso con Google non è ancora collegato nell'app —
 * il backend lo accetta, ma serve un `idToken` che solo Credential Manager può
 * rilasciare. Meglio dirlo con parole precise che un generico "riprova" su un
 * bottone che oggi non può funzionare.
 */
enum class LoginError { CREDENTIALS, GENERIC, SOCIAL_UNAVAILABLE }

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null, error = null) }

    fun login() {
        val s = _state.value
        if (s.loading) return
        val emailError = validateEmail(s.email).errorOrNull()
        // Sulla password d'accesso si controlla solo che ci sia: le regole di
        // robustezza valgono quando se ne sceglie una nuova, non qui.
        val passwordError = if (s.password.isEmpty()) ValidationError.REQUIRED else null
        if (emailError != null || passwordError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        val email = validateEmail(s.email).valueOrNull().orEmpty()
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            authRepository.login(email, s.password)
                .onSuccess { user -> _state.update { it.copy(loading = false, loggedInRole = user.role) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = if (error is AppError.InvalidCredentials) LoginError.CREDENTIALS else LoginError.GENERIC,
                        )
                    }
                }
        }
    }

    fun loginWithProvider(provider: SocialProvider) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            authRepository.loginWithProvider(provider)
                .onSuccess { user -> _state.update { it.copy(loading = false, loggedInRole = user.role) } }
                .onFailure { _state.update { it.copy(loading = false, error = LoginError.SOCIAL_UNAVAILABLE) } }
        }
    }

    fun consumeLogin() = _state.update { it.copy(loggedInRole = null) }
}
