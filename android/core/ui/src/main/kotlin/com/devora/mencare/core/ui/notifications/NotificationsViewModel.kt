package com.devora.mencare.core.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devora.mencare.core.data.repository.NotificationRepository
import com.devora.mencare.core.model.AppNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val notifications: List<AppNotification> = emptyList(),
    /** Unread when the page opened: they keep their dot until the user leaves. */
    val newIds: Set<String> = emptySet(),
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val newIds = MutableStateFlow<Set<String>>(emptySet())

    val state = combine(notificationRepository.notifications, newIds) { list, new ->
        NotificationsUiState(list, new)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState())

    init {
        // Opening the page reads everything; remember what was new first.
        viewModelScope.launch {
            newIds.value = notificationRepository.notifications.first()
                .filterNot { it.read }
                .map { it.id }
                .toSet()
            notificationRepository.markAllRead()
        }
    }
}
