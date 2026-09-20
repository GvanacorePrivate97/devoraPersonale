package com.devora.mencare.core.network.api

import com.devora.mencare.core.network.dto.AvatarDto
import com.devora.mencare.core.network.dto.ChangePasswordBody
import com.devora.mencare.core.network.dto.EmailBody
import com.devora.mencare.core.network.dto.LoginBody
import com.devora.mencare.core.network.dto.NotificationPrefsDto
import com.devora.mencare.core.network.dto.RefreshBody
import com.devora.mencare.core.network.dto.RegisterBody
import com.devora.mencare.core.network.dto.SessionDto
import com.devora.mencare.core.network.dto.SocialLoginBody
import com.devora.mencare.core.network.dto.TokensDto
import com.devora.mencare.core.network.dto.TokensEnvelope
import com.devora.mencare.core.network.dto.UpdateProfileBody
import com.devora.mencare.core.network.dto.UserDto
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * L'API di Men Care, divisa per area come le rotte del backend
 * (`backend/src/modules/`): accesso e profilo stanno qui, listino e squadra in
 * [CatalogApi], agenda in [BookingApi], e così via. Ogni percorso è già
 * relativo al `/v1` del base URL.
 *
 * Nessuna di queste firme prende un id utente: l'account è quello del token,
 * il server non accetta di farselo dire dalla richiesta.
 */
interface AuthApi {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterBody): SessionDto

    @POST("auth/login")
    suspend fun login(@Body body: LoginBody): SessionDto

    @POST("auth/social")
    suspend fun social(@Body body: SocialLoginBody): SessionDto

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshBody): TokensDto

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshBody)

    @POST("auth/password/reset-request")
    suspend fun requestPasswordReset(@Body body: EmailBody)

    @GET("auth/me")
    suspend fun me(): UserDto

    @PATCH("auth/me")
    suspend fun updateProfile(@Body body: UpdateProfileBody): UserDto

    @POST("auth/me/password")
    suspend fun changePassword(@Body body: ChangePasswordBody): TokensEnvelope

    @GET("auth/me/notification-prefs")
    suspend fun notificationPrefs(): NotificationPrefsDto

    @PUT("auth/me/notification-prefs")
    suspend fun updateNotificationPrefs(@Body body: NotificationPrefsDto): NotificationPrefsDto

    @Multipart
    @POST("auth/me/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): AvatarDto

    @DELETE("auth/me/avatar")
    suspend fun deleteAvatar()

    @DELETE("auth/me/devices/{token}")
    suspend fun forgetDevice(@Path("token") token: String)
}
