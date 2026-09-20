package com.devora.mencare.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Corpi e risposte di `/v1/auth/…`, uno a uno con `docs/API.md`.
 *
 * Le date sono stringhe ISO (`2026-09-20`) e gli istanti ISO con fuso: la
 * conversione a `java.time` avviene nel layer dati, non qui, così i DTO
 * restano puro trasporto e si possono confrontare a occhio con il contratto.
 */

@Serializable
data class TokensDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)

@Serializable
data class UserDto(
    val id: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val role: String,
    val memberSince: String,
    val visitCount: Int = 0,
    val avatarUrl: String? = null,
    val clientId: String? = null,
    val operatorId: String? = null,
)

@Serializable
data class SessionDto(
    val user: UserDto,
    val tokens: TokensDto,
)

@Serializable
data class LoginBody(val email: String, val password: String)

@Serializable
data class SocialLoginBody(val provider: String, val idToken: String)

@Serializable
data class RegisterBody(
    val firstName: String,
    val lastName: String,
    val phone: String,
    val email: String,
    val password: String,
)

@Serializable
data class RefreshBody(val refreshToken: String)

@Serializable
data class EmailBody(val email: String)

@Serializable
data class UpdateProfileBody(
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
)

@Serializable
data class ChangePasswordBody(val currentPassword: String, val newPassword: String)

/** `/auth/me/password` rinnova la sessione: le altre vengono invalidate. */
@Serializable
data class TokensEnvelope(val tokens: TokensDto)

@Serializable
data class NotificationPrefsDto(
    val appointmentReminder: Boolean,
    val waitlistAlerts: Boolean,
    val marketing: Boolean,
)

@Serializable
data class AvatarDto(val avatarUrl: String)
