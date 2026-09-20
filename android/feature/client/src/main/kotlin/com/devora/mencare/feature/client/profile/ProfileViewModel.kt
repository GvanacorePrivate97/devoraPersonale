package com.devora.mencare.feature.client.profile

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
import com.devora.mencare.core.data.network.DataSync
import com.devora.mencare.core.data.network.SyncKeys
import com.devora.mencare.core.data.repository.AuthRepository
import com.devora.mencare.core.data.repository.CatalogRepository
import com.devora.mencare.core.model.ClientNotificationPrefs
import com.devora.mencare.core.model.Salon
import com.devora.mencare.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ProfileField { FIRST_NAME, LAST_NAME, EMAIL, PHONE }

data class ProfileUiState(
    val user: User? = null,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val salon: Salon? = null,
    val prefs: ClientNotificationPrefs = ClientNotificationPrefs(),
    val fieldErrors: Map<ProfileField, ValidationError> = emptyMap(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val saveFailed: Boolean = false,
    val loggedOut: Boolean = false,
    /** Guasto in *lettura* (profilo, salone, preferenze), distinto da [saveFailed]. */
    val loadError: AppError? = null,
    val loading: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    catalogRepository: CatalogRepository,
    private val sync: DataSync,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private var submitted = false

    init {
        combine(
            authRepository.currentUser,
            catalogRepository.salon,
            authRepository.notificationPrefs,
        ) { user, salon, prefs ->
            _state.update { s ->
                val base = s.copy(user = user, salon = salon, prefs = prefs)
                if (!s.dirty && user != null) {
                    base.copy(
                        firstName = user.firstName,
                        lastName = user.lastName,
                        email = user.email,
                        phone = user.phone,
                    )
                } else {
                    base
                }
            }
        }.launchIn(viewModelScope)

        // Il guasto di lettura viaggia fuori dai `Flow` dei dati: le interfacce
        // dei repository espongono valori, non esiti. Qui si riaggancia.
        sync.state(SyncKeys.PROFILE, SyncKeys.CATALOG)
            .onEach { status ->
                _state.update { it.copy(loading = status.loading, loadError = status.error) }
            }
            .launchIn(viewModelScope)
    }

    /** "Riprova": si rileggono profilo, salone e preferenze. */
    fun retry() = sync.retry()

    fun onFieldChange(field: ProfileField, value: String) = _state.update { s ->
        val updated = when (field) {
            ProfileField.FIRST_NAME -> s.copy(firstName = value)
            ProfileField.LAST_NAME -> s.copy(lastName = value)
            ProfileField.EMAIL -> s.copy(email = value)
            ProfileField.PHONE -> s.copy(phone = value)
        }
        updated.copy(
            fieldErrors = if (submitted) validate(updated) else s.fieldErrors - field,
            dirty = true,
            saved = false,
            saveFailed = false,
        )
    }

    /** Gli stessi campi della registrazione, quindi le stesse regole. */
    private fun validate(s: ProfileUiState): Map<ProfileField, ValidationError> = buildMap {
        validateName(s.firstName).errorOrNull()?.let { put(ProfileField.FIRST_NAME, it) }
        validateName(s.lastName).errorOrNull()?.let { put(ProfileField.LAST_NAME, it) }
        validateEmail(s.email).errorOrNull()?.let { put(ProfileField.EMAIL, it) }
        validatePhone(s.phone).errorOrNull()?.let { put(ProfileField.PHONE, it) }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        submitted = true
        val errors = validate(s)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors, saved = false, saveFailed = false) }
            return
        }
        val firstName = validateName(s.firstName).valueOrNull().orEmpty()
        val lastName = validateName(s.lastName).valueOrNull().orEmpty()
        val email = validateEmail(s.email).valueOrNull().orEmpty()
        val phone = validatePhone(s.phone).valueOrNull().orEmpty()
        viewModelScope.launch {
            _state.update { it.copy(saving = true, saveFailed = false) }
            // Niente ottimismo: `saved` si accende solo quando il repository conferma.
            authRepository.updateProfile(firstName, lastName, email, phone)
                .onSuccess { user ->
                    _state.update {
                        it.copy(
                            firstName = user.firstName,
                            lastName = user.lastName,
                            email = user.email,
                            phone = user.phone,
                            saving = false,
                            dirty = false,
                            saved = true,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(saving = false, saveFailed = true) } }
        }
    }

    fun onPrefChange(prefs: ClientNotificationPrefs) {
        viewModelScope.launch {
            authRepository.updateNotificationPrefs(prefs)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _state.update { it.copy(loggedOut = true) }
        }
    }
}
