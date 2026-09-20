package com.devora.mencare.core.network.api

import com.devora.mencare.core.network.dto.CatalogDto
import com.devora.mencare.core.network.dto.CreateHolidayBody
import com.devora.mencare.core.network.dto.CreateOperatorBody
import com.devora.mencare.core.network.dto.CreateServiceBody
import com.devora.mencare.core.network.dto.HolidayDto
import com.devora.mencare.core.network.dto.HolidaysEnvelope
import com.devora.mencare.core.network.dto.NewOperatorDto
import com.devora.mencare.core.network.dto.OperatorDto
import com.devora.mencare.core.network.dto.OperatorIdsBody
import com.devora.mencare.core.network.dto.OperatorsEnvelope
import com.devora.mencare.core.network.dto.SalonDto
import com.devora.mencare.core.network.dto.ServiceDto
import com.devora.mencare.core.network.dto.ServiceIdsBody
import com.devora.mencare.core.network.dto.ServicesEnvelope
import com.devora.mencare.core.network.dto.UpdateSalonBody
import com.devora.mencare.core.network.dto.UpdateServiceBody
import com.devora.mencare.core.network.dto.WeeklyHoursBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Listino, squadra, salone e ferie. Leggere è di tutti i ruoli — il wizard di
 * prenotazione del cliente vive su questi dati — scrivere è del solo titolare,
 * tranne le ferie che l'operatore gestisce per sé.
 */
interface CatalogApi {

    /** Una sola chiamata all'avvio: salone, listino e squadra insieme. */
    @GET("catalog")
    suspend fun catalog(): CatalogDto

    @GET("catalog/services")
    suspend fun services(): ServicesEnvelope

    @GET("catalog/operators")
    suspend fun operators(): OperatorsEnvelope

    @POST("catalog/services")
    suspend fun createService(@Body body: CreateServiceBody): ServiceDto

    @PATCH("catalog/services/{id}")
    suspend fun updateService(@Path("id") id: String, @Body body: UpdateServiceBody): ServiceDto

    @PATCH("catalog/services/{id}/operators")
    suspend fun setServiceOperators(@Path("id") id: String, @Body body: OperatorIdsBody): ServiceDto

    @POST("catalog/operators")
    suspend fun createOperator(@Body body: CreateOperatorBody): NewOperatorDto

    @PUT("catalog/operators/{id}/hours")
    suspend fun setOperatorHours(@Path("id") id: String, @Body body: WeeklyHoursBody): OperatorDto

    @PUT("catalog/operators/{id}/services")
    suspend fun setOperatorServices(@Path("id") id: String, @Body body: ServiceIdsBody): OperatorDto

    @PUT("catalog/salon")
    suspend fun updateSalon(@Body body: UpdateSalonBody): SalonDto

    @GET("catalog/operators/{id}/holidays")
    suspend fun holidays(@Path("id") operatorId: String): HolidaysEnvelope

    @POST("catalog/operators/{id}/holidays")
    suspend fun addHoliday(@Path("id") operatorId: String, @Body body: CreateHolidayBody): HolidayDto

    @DELETE("catalog/holidays/{id}")
    suspend fun deleteHoliday(@Path("id") holidayId: String)
}
