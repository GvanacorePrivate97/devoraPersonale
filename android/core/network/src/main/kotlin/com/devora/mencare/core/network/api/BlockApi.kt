package com.devora.mencare.core.network.api

import com.devora.mencare.core.network.dto.AppointmentsEnvelope
import com.devora.mencare.core.network.dto.BlocksEnvelope
import com.devora.mencare.core.network.dto.CreateBlockBody
import com.devora.mencare.core.network.dto.TimeBlockDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Permessi, pause, ferie e corsi. L'`operatorId` che manda un operatore non
 * conta: il server usa sempre quello del token, così nessuno blocca l'agenda
 * di un collega.
 */
interface BlockApi {

    @GET("blocks")
    suspend fun blocksForDay(
        @Query("operatorId") operatorId: String?,
        @Query("date") date: String,
    ): BlocksEnvelope

    @GET("blocks/week")
    suspend fun blocksForWeek(
        @Query("operatorId") operatorId: String?,
        @Query("weekStart") weekStart: String,
    ): BlocksEnvelope

    /** Anteprima prima di bloccare: quali appuntamenti verrebbero travolti. */
    @GET("blocks/conflicts")
    suspend fun conflicts(
        @Query("operatorId") operatorId: String?,
        @Query("date") date: String,
        @Query("start") start: String,
        @Query("end") end: String,
    ): AppointmentsEnvelope

    @POST("blocks")
    suspend fun createBlock(@Body body: CreateBlockBody): TimeBlockDto

    @DELETE("blocks/{id}")
    suspend fun deleteBlock(@Path("id") id: String)
}
