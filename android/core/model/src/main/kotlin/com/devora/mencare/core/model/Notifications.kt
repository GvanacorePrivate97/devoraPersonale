package com.devora.mencare.core.model

import java.time.LocalDateTime

data class ReminderRule(
    val id: String,
    val hoursBefore: Int,
)

data class NotificationSettings(
    val reminders: List<ReminderRule>,
    val bookingConfirmation: Boolean = true,
    val cancellationAlert: Boolean = true,
    val lateOperatorAlert: Boolean = false,
    val emptyDayPromos: Boolean = false,
)

enum class CampaignSegment { INATTIVI_60, TUTTI, TOP_SPESA }

enum class CampaignStatus { DRAFT, SCHEDULED, SENT }

data class PushCampaign(
    val id: String,
    val name: String,
    val segment: CampaignSegment,
    val title: String,
    val body: String,
    val scheduledAt: LocalDateTime? = null,
    val repeatWeekly: Boolean = false,
    val sendCap: Int? = null,
    val reachableCount: Int,
    val segmentSize: Int,
    val status: CampaignStatus = CampaignStatus.DRAFT,
)

/** An in-app notification, addressed to one user (client, staff or owner). */
data class AppNotification(
    val id: String,
    val userId: String,
    val title: String,
    val body: String,
    val at: LocalDateTime,
    val read: Boolean = false,
)
