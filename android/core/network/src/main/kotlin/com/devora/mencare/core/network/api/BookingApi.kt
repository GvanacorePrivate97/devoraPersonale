package com.devora.mencare.core.network.api

import com.devora.mencare.core.network.dto.AppointmentDto
import com.devora.mencare.core.network.dto.AppointmentsEnvelope
import com.devora.mencare.core.network.dto.AvailabilityDto
import com.devora.mencare.core.network.dto.AvailableDaysDto
import com.devora.mencare.core.network.dto.CreateAppointmentBody
import com.devora.mencare.core.network.dto.JoinWaitlistBody
import com.devora.mencare.core.network.dto.NextAvailabilityEnvelope
import com.devora.mencare.core.network.dto.RescheduleBody
import com.devora.mencare.core.network.dto.StatusBody
import com.devora.mencare.core.network.dto.WaitlistEnvelope
import com.devora.mencare.core.network.dto.WaitlistEntryDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Agenda e lista d'attesa.
 *
 * La griglia degli slot, i giorni pieni e la prima disponibilità li calcola il
 * server: l'app non ha più un motore degli slot in produzione, così le due app
 * non possono divergere fra loro né dal database.
 *
 * `serviceIds` viaggia come lista ripetuta (`?serviceIds=a&serviceIds=b`): il
 * backend accetta sia quella forma sia la stringa separata da virgole.
 */
interface BookingApi {

    @GET("booking/availability")
    suspend fun availability(
        @Query("operatorId") operatorId: String?,
        @Query("serviceIds") serviceIds: List<String>,
        @Query("date") date: String,
        /** In modifica l'appuntamento stesso non occupa il suo posto. */
        @Query("ignoreAppointmentId") ignoreAppointmentId: String? = null,
    ): AvailabilityDto

    @GET("booking/days")
    suspend fun days(
        @Query("operatorId") operatorId: String?,
        @Query("serviceIds") serviceIds: List<String>,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("ignoreAppointmentId") ignoreAppointmentId: String? = null,
    ): AvailableDaysDto

    @GET("booking/next-availability")
    suspend fun nextAvailability(@Query("serviceIds") serviceIds: List<String>): NextAvailabilityEnvelope

    /** Solo per il ruolo CLIENT: i propri appuntamenti, passati e futuri. */
    @GET("appointments/me")
    suspend fun myAppointments(): AppointmentsEnvelope

    @GET("appointments")
    suspend fun appointmentsForDay(
        @Query("operatorId") operatorId: String?,
        @Query("date") date: String,
    ): AppointmentsEnvelope

    @GET("appointments/week")
    suspend fun appointmentsForWeek(@Query("weekStart") weekStart: String): AppointmentsEnvelope

    @GET("appointments/{id}")
    suspend fun appointment(@Path("id") id: String): AppointmentDto

    @POST("appointments")
    suspend fun book(@Body body: CreateAppointmentBody): AppointmentDto

    @PATCH("appointments/{id}/schedule")
    suspend fun reschedule(@Path("id") id: String, @Body body: RescheduleBody): AppointmentDto

    @POST("appointments/{id}/cancel")
    suspend fun cancel(@Path("id") id: String)

    @POST("appointments/{id}/status")
    suspend fun setStatus(@Path("id") id: String, @Body body: StatusBody): AppointmentDto

    @GET("waitlist/me")
    suspend fun waitlist(): WaitlistEnvelope

    @POST("waitlist")
    suspend fun joinWaitlist(@Body body: JoinWaitlistBody): WaitlistEntryDto

    @DELETE("waitlist/{id}")
    suspend fun leaveWaitlist(@Path("id") id: String)
}
