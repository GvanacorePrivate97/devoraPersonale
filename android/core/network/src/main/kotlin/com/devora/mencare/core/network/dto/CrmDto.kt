package com.devora.mencare.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Rubrica clienti: `/v1/crm/…`.
 *
 * `lifetimeSpendCents` manca nelle risposte al ruolo STAFF — non è nascosto
 * dalla schermata, non viene proprio selezionato — quindi qui è opzionale.
 */

@Serializable
data class ClientDto(
    val id: String,
    val firstName: String,
    val lastName: String,
    val phone: String,
    val email: String? = null,
    val customerSince: String,
    val visitCount: Int = 0,
    val lifetimeSpendCents: Long? = null,
    val noShowCount: Int = 0,
    val lastVisit: String? = null,
    val preferredServiceIds: List<String> = emptyList(),
    val preferredOperatorId: String? = null,
    val marketingOptIn: Boolean = true,
)

/** Pagina della rubrica: `nextCursor` è `null` quando non c'è altro da leggere. */
@Serializable
data class ClientPageDto(
    val clients: List<ClientDto> = emptyList(),
    val nextCursor: String? = null,
)

/** Riga dello storico del cliente: più ricca dell'appuntamento, porta i nomi dei servizi. */
@Serializable
data class ClientHistoryDto(
    val id: String,
    val operatorId: String,
    val date: String,
    val time: String,
    val startsAt: String? = null,
    val durationMinutes: Int,
    val status: String,
    val channel: String = "APP",
    val serviceIds: List<String> = emptyList(),
    val serviceNames: List<String> = emptyList(),
    val totalPriceCents: Long? = null,
)

/** Abitudini calcolate dal server sulle visite completate. */
@Serializable
data class ClientInsightsDto(
    val favoriteOperatorId: String? = null,
    val averageDaysBetweenVisits: Int? = null,
)

@Serializable
data class ClientDetailDto(
    val client: ClientDto,
    val insights: ClientInsightsDto? = null,
    val appointments: List<ClientHistoryDto> = emptyList(),
)

@Serializable
data class CreateClientBody(
    val firstName: String,
    val lastName: String,
    val phone: String,
    val email: String? = null,
)
