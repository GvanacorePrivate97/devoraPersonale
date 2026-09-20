package com.devora.mencare.core.network.api

import com.devora.mencare.core.network.dto.CampaignBody
import com.devora.mencare.core.network.dto.CampaignDto
import com.devora.mencare.core.network.dto.CampaignReachDto
import com.devora.mencare.core.network.dto.CampaignsEnvelope
import com.devora.mencare.core.network.dto.CreateReminderRuleBody
import com.devora.mencare.core.network.dto.DashboardDto
import com.devora.mencare.core.network.dto.ReminderRuleDto
import com.devora.mencare.core.network.dto.ReminderRulesEnvelope
import com.devora.mencare.core.network.dto.SalonNotificationSettingsDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Area titolare: tutte rotte `OWNER`. I numeri del cruscotto arrivano già
 * calcolati — incassi, occupazione, no-show, scontrino medio — perché sono
 * conti sul database, non sulla manciata di righe che l'app ha in memoria.
 */
interface AdminApi {

    @GET("admin/dashboard")
    suspend fun dashboard(@Query("period") period: String): DashboardDto

    @GET("admin/notification-settings")
    suspend fun notificationSettings(): SalonNotificationSettingsDto

    @PUT("admin/notification-settings")
    suspend fun updateNotificationSettings(
        @Body body: SalonNotificationSettingsDto,
    ): SalonNotificationSettingsDto

    @GET("admin/reminder-rules")
    suspend fun reminderRules(): ReminderRulesEnvelope

    @POST("admin/reminder-rules")
    suspend fun addReminderRule(@Body body: CreateReminderRuleBody): ReminderRuleDto

    @DELETE("admin/reminder-rules/{id}")
    suspend fun removeReminderRule(@Path("id") id: String)

    @GET("admin/campaigns")
    suspend fun campaigns(): CampaignsEnvelope

    @GET("admin/campaigns/reach")
    suspend fun campaignReach(@Query("segment") segment: String): CampaignReachDto

    @POST("admin/campaigns")
    suspend fun createCampaign(@Body body: CampaignBody): CampaignDto

    @PATCH("admin/campaigns/{id}")
    suspend fun updateCampaign(@Path("id") id: String, @Body body: CampaignBody): CampaignDto
}
