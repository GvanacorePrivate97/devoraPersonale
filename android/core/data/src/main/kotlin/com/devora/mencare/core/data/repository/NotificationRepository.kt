package com.devora.mencare.core.data.repository

import com.devora.mencare.core.model.AppNotification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    val notifications: Flow<List<AppNotification>>
    suspend fun markAllRead()
}
