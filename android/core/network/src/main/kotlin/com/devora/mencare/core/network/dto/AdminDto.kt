package com.devora.mencare.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Area titolare: cruscotto, regole delle notifiche e campagne push (`/v1/admin/…`). */

@Serializable
data class OperatorOccupancyDto(val operatorId: String, val operatorName: String, val percent: Int)

@Serializable
data class UpcomingDayDto(
    val date: String,
    val occupancyPercent: Int = 0,
    val waitlistCount: Int = 0,
    val closed: Boolean = false,
)

@Serializable
data class DashboardDto(
    val period: String,
    val from: String,
    val to: String,
    val revenueCents: Long = 0,
    val revenueTrendPercent: Int = 0,
    val appointmentCount: Int = 0,
    val noShowPercent: Double = 0.0,
    val averageTicketCents: Long = 0,
    val operatorOccupancy: List<OperatorOccupancyDto> = emptyList(),
    val upcomingDays: List<UpcomingDayDto> = emptyList(),
    val inactiveClients: List<ClientDto> = emptyList(),
)

@Serializable
data class SalonNotificationSettingsDto(
    val bookingConfirmation: Boolean = true,
    val cancellationAlert: Boolean = true,
    val lateOperatorAlert: Boolean = false,
    val emptyDayPromos: Boolean = false,
)

@Serializable
data class ReminderRuleDto(val id: String, val hoursBefore: Int)

@Serializable
data class ReminderRulesEnvelope(val rules: List<ReminderRuleDto> = emptyList())

@Serializable
data class CreateReminderRuleBody(val hoursBefore: Int)

@Serializable
data class CampaignDto(
    val id: String,
    val name: String,
    val segment: String,
    val title: String,
    val body: String,
    val scheduledAt: String? = null,
    /** Orario da muro scelto dal titolare: è quello che la schermata rimostra. */
    val scheduledDate: String? = null,
    val scheduledTime: String? = null,
    val repeatWeekly: Boolean = false,
    val sendCap: Int? = null,
    val reachableCount: Int = 0,
    val segmentSize: Int = 0,
    val sentCount: Int = 0,
    val status: String = "DRAFT",
    val lastSentAt: String? = null,
)

@Serializable
data class CampaignsEnvelope(val campaigns: List<CampaignDto> = emptyList())

/**
 * Il corpo di una campagna.
 *
 * `scheduledAt` e `sendCap` sono [JsonElement] e non `String?`/`Int?` di
 * proposito: sul `PATCH` il backend distingue `null` ("togli la
 * programmazione") da campo assente ("lascia com'era"), e con
 * `explicitNulls = false` un `null` Kotlin sparirebbe dal JSON. Passandoci
 * `JsonNull` il `null` arriva davvero. Si costruiscono con `jsonOrNull(...)`.
 */
@Serializable
data class CampaignBody(
    val name: String,
    val segment: String,
    val title: String,
    val body: String,
    val scheduledAt: JsonElement,
    val repeatWeekly: Boolean = false,
    val sendCap: JsonElement,
    val status: String? = null,
)

/** Destinatari davvero raggiungibili di un segmento: li conta il server. */
@Serializable
data class CampaignReachDto(val reachable: Int = 0, val segmentSize: Int = 0)
