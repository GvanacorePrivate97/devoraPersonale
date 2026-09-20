package com.devora.mencare.core.network.api

import com.devora.mencare.core.network.dto.ClientDetailDto
import com.devora.mencare.core.network.dto.ClientDto
import com.devora.mencare.core.network.dto.ClientPageDto
import com.devora.mencare.core.network.dto.CreateClientBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Rubrica clienti, condivisa fra operatore e titolare: è il server a decidere
 * chi vede gli importi, guardando il ruolo del token.
 */
interface CrmApi {

    @GET("crm/clients")
    suspend fun clients(
        @Query("query") query: String?,
        @Query("segment") segment: String?,
        @Query("limit") limit: Int?,
        @Query("cursor") cursor: String?,
    ): ClientPageDto

    @GET("crm/clients/{id}")
    suspend fun client(@Path("id") id: String): ClientDetailDto

    @POST("crm/clients")
    suspend fun createClient(@Body body: CreateClientBody): ClientDto
}
