package com.devora.mencare.core.data.network

import com.devora.mencare.core.common.DispatcherProvider
import com.devora.mencare.core.data.repository.NotificationRepository
import com.devora.mencare.core.model.AppNotification
import com.devora.mencare.core.network.api.NotificationApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Quante notifiche tiene la campanella: è un elenco da leggere, non un archivio. */
private const val PAGE_SIZE = 50

/**
 * La campanella dal backend. I testi li scrive il server — così cliente,
 * operatore e titolare leggono le stesse parole anche quando la stessa
 * notifica arriva come push a telefono spento.
 */
@Singleton
class NetworkNotificationRepository @Inject constructor(
    private val api: NotificationApi,
    private val session: Session,
    private val sync: DataSync,
    private val dispatchers: DispatcherProvider,
) : NotificationRepository {

    override val notifications: Flow<List<AppNotification>> =
        sync.reloading(SyncKeys.NOTIFICATIONS, emptyList<AppNotification>()) {
            val userId = session.user.value?.id ?: return@reloading emptyList()
            api.notifications(PAGE_SIZE, null).notifications
                .map { it.toModel(userId) }
                .sortedByDescending { it.at }
        }

    override suspend fun markAllRead() {
        withContext(dispatchers.io) {
            sync.load<Unit>(SyncKeys.NOTIFICATIONS, Unit) { api.markAllRead() }
            sync.invalidate()
        }
    }
}
