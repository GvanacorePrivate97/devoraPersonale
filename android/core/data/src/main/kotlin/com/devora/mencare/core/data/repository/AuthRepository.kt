package com.devora.mencare.core.data.repository

import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.model.ClientNotificationPrefs
import com.devora.mencare.core.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<User?>

    suspend fun login(email: String, password: String): AppResult<User>

    suspend fun loginWithProvider(provider: SocialProvider): AppResult<User>

    suspend fun register(
        firstName: String,
        lastName: String,
        phone: String,
        email: String,
        password: String,
    ): AppResult<User>

    suspend fun requestPasswordReset(email: String): AppResult<Unit>

    suspend fun updateProfile(firstName: String, lastName: String, email: String, phone: String): AppResult<User>

    /** Requires the correct current password; fails with [com.devora.mencare.core.common.AppError.InvalidCredentials] otherwise. */
    suspend fun changePassword(currentPassword: String, newPassword: String): AppResult<Unit>

    val notificationPrefs: Flow<ClientNotificationPrefs>

    suspend fun updateNotificationPrefs(prefs: ClientNotificationPrefs)

    suspend fun logout()
}

/**
 * Provider di login social supportati dal backend.
 * [APPLE] non è esposto dall'app Android: "Sign in with Apple" è riservato all'app iOS.
 */
enum class SocialProvider { APPLE, GOOGLE }
