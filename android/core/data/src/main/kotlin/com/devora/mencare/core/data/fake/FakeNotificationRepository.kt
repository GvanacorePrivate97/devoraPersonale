package com.devora.mencare.core.data.fake

import com.devora.mencare.core.data.repository.NotificationRepository
import com.devora.mencare.core.model.AppNotification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Only the signed-in user's notifications: client, staff and owner each see their own. */
@Singleton
class FakeNotificationRepository @Inject constructor(
    private val store: InMemoryStore,
) : NotificationRepository {

    override val notifications: Flow<List<AppNotification>> =
        combine(store.notifications, store.currentUser) { list, user ->
            list.filter { it.userId == user?.id }.sortedByDescending { it.at }
        }

    override suspend fun markAllRead() {
        val userId = store.currentUser.value?.id ?: return
        store.notifications.value = store.notifications.value.map {
            if (it.userId == userId) it.copy(read = true) else it
        }
    }
}
