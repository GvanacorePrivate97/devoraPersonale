package com.devora.mencare.feature.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.data.repository.AuthRepository
import com.devora.mencare.core.data.repository.AvatarRepository
import com.devora.mencare.core.data.repository.CatalogRepository
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StaffProfileUiState(
    val user: User? = null,
    val operator: Operator? = null,
)

@HiltViewModel
class StaffProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val avatarRepository: AvatarRepository,
    catalogRepository: CatalogRepository,
) : ViewModel() {

    val state = combine(
        authRepository.currentUser,
        catalogRepository.operators,
    ) { user, operators ->
        StaffProfileUiState(user = user, operator = operators.firstOrNull { it.id == user?.operatorId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StaffProfileUiState())

    fun onPhotoPicked(sourceUri: String) {
        viewModelScope.launch { avatarRepository.save(sourceUri) }
    }

    fun onPhotoRemoved() {
        viewModelScope.launch { avatarRepository.clear() }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }
}
