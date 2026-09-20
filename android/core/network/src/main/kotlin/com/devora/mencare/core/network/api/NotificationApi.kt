package com.devora.mencare.core.network.api

import com.devora.mencare.core.network.dto.MarkReadDto
import com.devora.mencare.core.network.dto.NotificationPageDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * La campanella. Nessuna rotta accetta un `userId`: l'account è quello del
 * token, altrimenti basterebbe cambiarlo per leggere le notifiche di un altro.
 */
interface NotificationApi {

    @GET("notifications")
    suspend fun notifications(
        @Query("limit") limit: Int?,
        @Query("cursor") cursor: String?,
    ): NotificationPageDto

    @POST("notifications/read-all")
    suspend fun markAllRead(): MarkReadDto

    @POST("notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String): MarkReadDto
}
