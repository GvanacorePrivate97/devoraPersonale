package com.devora.mencare.core.data.repository

import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.model.DashboardPeriod
import com.devora.mencare.core.model.DashboardStats
import com.devora.mencare.core.model.NotificationSettings
import com.devora.mencare.core.model.PushCampaign
import kotlinx.coroutines.flow.Flow

interface AdminRepository {
    fun dashboard(period: DashboardPeriod): Flow<DashboardStats>

    val notificationSettings: Flow<NotificationSettings>
    suspend fun updateNotificationSettings(settings: NotificationSettings): AppResult<Unit>

    val campaigns: Flow<List<PushCampaign>>
    suspend fun saveCampaign(campaign: PushCampaign): AppResult<PushCampaign>
    suspend fun reachFor(segment: com.devora.mencare.core.model.CampaignSegment): Pair<Int, Int>
}
