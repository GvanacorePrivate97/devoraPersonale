package com.devora.mencare.core.data.fake

import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.data.repository.AuthRepository
import com.devora.mencare.core.data.repository.SocialProvider
import com.devora.mencare.core.model.ClientNotificationPrefs
import com.devora.mencare.core.model.User
import com.devora.mencare.core.model.UserRole
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Demo auth: any seeded e-mail + any password of 6+ characters signs in.
 * Social buttons sign in as the demo client.
 */
@Singleton
class FakeAuthRepository @Inject constructor(
    private val store: InMemoryStore,
) : AuthRepository {

    override val currentUser: Flow<User?> = store.currentUser

    override suspend fun login(email: String, password: String): AppResult<User> {
        delay(600)
        if (password.length < 6) return AppResult.Failure(AppError.InvalidCredentials)
        val user = store.users.value.firstOrNull { it.email.equals(email.trim(), ignoreCase = true) }
            ?: return AppResult.Failure(AppError.InvalidCredentials)
        store.currentUser.value = user
        return AppResult.Success(user)
    }

    override suspend fun loginWithProvider(provider: SocialProvider): AppResult<User> {
        delay(600)
        val user = store.users.value.first { it.id == DemoSeed.USER_CLIENT }
        store.currentUser.value = user
        return AppResult.Success(user)
    }

    override suspend fun register(
        firstName: String,
        lastName: String,
        phone: String,
        email: String,
        password: String,
    ): AppResult<User> {
        delay(800)
        if (store.users.value.any { it.email.equals(email.trim(), ignoreCase = true) }) {
            return AppResult.Failure(AppError.EmailAlreadyRegistered)
        }
        val record = com.devora.mencare.core.model.ClientRecord(
            id = store.newId("cli"),
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            phone = phone.trim(),
            email = email.trim(),
            customerSince = LocalDate.now(),
            visitCount = 0,
            lifetimeSpendCents = 0,
            noShowCount = 0,
            lastVisit = null,
        )
        store.clients.value += record
        val user = User(
            id = store.newId("user"),
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            email = email.trim(),
            phone = phone.trim(),
            role = UserRole.CLIENT,
            memberSince = LocalDate.now(),
            visitCount = 0,
            clientRecordId = record.id,
        )
        store.users.value += user
        store.currentUser.value = user
        return AppResult.Success(user)
    }

    override suspend fun requestPasswordReset(email: String): AppResult<Unit> {
        delay(600)
        return AppResult.Success(Unit)
    }

    override suspend fun updateProfile(
        firstName: String,
        lastName: String,
        email: String,
        phone: String,
    ): AppResult<User> {
        val current = store.currentUser.value ?: return AppResult.Failure(AppError.NotFound)
        val updated = current.copy(
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            email = email.trim(),
            phone = phone.trim(),
        )
        store.users.value = store.users.value.map { if (it.id == current.id) updated else it }
        store.currentUser.value = updated
        return AppResult.Success(updated)
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): AppResult<Unit> {
        delay(600)
        store.currentUser.value ?: return AppResult.Failure(AppError.NotFound)
        // Demo auth never persists a real password (any 6+ char password logs in), so the
        // "current password" check mirrors login's own rule rather than comparing to a stored value.
        if (currentPassword.length < 6) return AppResult.Failure(AppError.InvalidCredentials)
        return AppResult.Success(Unit)
    }

    override val notificationPrefs: Flow<ClientNotificationPrefs> = store.clientPrefs

    override suspend fun updateNotificationPrefs(prefs: ClientNotificationPrefs) {
        store.clientPrefs.value = prefs
    }

    override suspend fun logout() {
        store.currentUser.value = null
    }
}
