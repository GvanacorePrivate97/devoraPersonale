package com.devora.mencare.core.model

import java.time.LocalDate

enum class UserRole { CLIENT, STAFF, OWNER }

data class User(
    val id: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val role: UserRole,
    val memberSince: LocalDate,
    val visitCount: Int = 0,
    /** Profile photo saved by the app; null = show the initials. */
    val avatarPath: String? = null,
    /** CRM record backing this account (clients only). */
    val clientRecordId: String? = null,
    /** Operator profile backing this account (staff/owner only). */
    val operatorId: String? = null,
) {
    val fullName: String get() = "$firstName $lastName"
    val initials: String get() = "${firstName.firstOrNull() ?: ' '}${lastName.firstOrNull() ?: ' '}".trim()
}

data class ClientNotificationPrefs(
    val appointmentReminder: Boolean = true,
    val waitlistAlerts: Boolean = true,
    val marketing: Boolean = false,
)
