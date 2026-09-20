package com.devora.mencare.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Agenda, disponibilità e lista d'attesa: `/v1/booking/…`, `/v1/appointments…`
 * e `/v1/waitlist*`.
 *
 * `date` e `time` sono l'orario da muro del salone (Europe/Rome) ed è quello
 * che l'app mostra; `startsAt` è lo stesso momento in ISO completo e serve solo
 * a chi deve ordinare fra fusi diversi.
 */

@Serializable
data class AppointmentDto(
    val id: String,
    val clientId: String,
    val operatorId: String,
    val serviceIds: List<String> = emptyList(),
    val date: String,
    val time: String,
    val startsAt: String? = null,
    val durationMinutes: Int,
    /** Assente nelle risposte al ruolo STAFF: gli importi non gli vengono proprio selezionati. */
    val totalPriceCents: Long? = null,
    val status: String,
    val channel: String = "APP",
    val noteForOperator: String? = null,
    val cancelledBy: String? = null,
)

@Serializable
data class AppointmentsEnvelope(val appointments: List<AppointmentDto> = emptyList())

@Serializable
data class AvailabilityDto(val date: String, val slots: List<String> = emptyList())

/** `/booking/days`: giorni con almeno uno slot libero e giorni aperti ma pieni. */
@Serializable
data class AvailableDaysDto(
    val available: List<String> = emptyList(),
    val fullyBooked: List<String> = emptyList(),
)

@Serializable
data class NextAvailabilityDto(val operatorId: String, val date: String, val time: String)

@Serializable
data class NextAvailabilityEnvelope(val operators: List<NextAvailabilityDto> = emptyList())

@Serializable
data class CreateAppointmentBody(
    val clientId: String? = null,
    /** Assente = "qualsiasi operatore": la scelta la fa il server. */
    val operatorId: String? = null,
    val serviceIds: List<String>,
    val date: String,
    val time: String,
    val noteForOperator: String? = null,
    val channel: String? = null,
    /** "Modifica": l'appuntamento che questo sostituisce, nella stessa transazione. */
    val replacesAppointmentId: String? = null,
)

@Serializable
data class RescheduleBody(val date: String, val time: String, val operatorId: String? = null)

@Serializable
data class StatusBody(val status: String)

@Serializable
data class WaitlistEntryDto(
    val id: String,
    val clientId: String,
    val date: String,
    /** `null` = tutta la giornata. */
    val time: String? = null,
    val operatorId: String? = null,
    val serviceIds: List<String> = emptyList(),
    val durationMinutes: Int = 0,
    val totalPriceCents: Long = 0,
    /** Posizione in coda: la calcola il server, l'app non la ricostruisce. */
    val position: Int = 0,
    val status: String = "WAITING",
)

@Serializable
data class WaitlistEnvelope(val entries: List<WaitlistEntryDto> = emptyList())

@Serializable
data class JoinWaitlistBody(
    val date: String,
    val time: String? = null,
    val operatorId: String? = null,
    val serviceIds: List<String>,
)
