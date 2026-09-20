package com.devora.mencare.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Listino, squadra, salone e ferie: `/v1/catalog/…`.
 *
 * Gli orari settimanali sono mappe con chiavi `"1".."7"` (ISO, 1 = lunedì) e
 * un giorno assente vuol dire chiuso — è il formato del contratto, non una
 * scelta dell'app: la traduzione in `DayOfWeek` sta nei mapper.
 */

@Serializable
data class TimeRangeDto(val start: String, val end: String)

@Serializable
data class ServiceDto(
    val id: String,
    val name: String,
    val durationMinutes: Int,
    val priceCents: Long,
    val description: String? = null,
    val featured: Boolean = false,
    val active: Boolean = true,
)

@Serializable
data class OperatorDto(
    val id: String,
    val name: String,
    val title: String = "",
    val bio: String = "",
    val specialties: List<String> = emptyList(),
    val isOwner: Boolean = false,
    val active: Boolean = true,
    val weeklyHours: Map<String, List<TimeRangeDto>> = emptyMap(),
    val serviceIds: List<String> = emptyList(),
)

@Serializable
data class SalonDto(
    val name: String,
    val address: String,
    val city: String,
    val phone: String? = null,
    val timezone: String = "Europe/Rome",
    val weeklyHours: Map<String, List<TimeRangeDto>> = emptyMap(),
)

/** `/catalog`: salone, listino e squadra in una sola chiamata di avvio. */
@Serializable
data class CatalogDto(
    val salon: SalonDto,
    val services: List<ServiceDto> = emptyList(),
    val operators: List<OperatorDto> = emptyList(),
)

@Serializable
data class ServicesEnvelope(val services: List<ServiceDto> = emptyList())

@Serializable
data class OperatorsEnvelope(val operators: List<OperatorDto> = emptyList())

@Serializable
data class CreateServiceBody(
    val name: String,
    val durationMinutes: Int,
    val priceCents: Long,
    val description: String? = null,
    val featured: Boolean = false,
    val active: Boolean = true,
)

/**
 * `description` è [JsonElement]: sul `PATCH` un campo assente lascia la
 * descrizione com'era, mentre `null` la cancella — e con
 * `explicitNulls = false` un `null` Kotlin non arriverebbe mai. Si costruisce
 * con `jsonOrNull(...)`.
 */
@Serializable
data class UpdateServiceBody(
    val name: String,
    val durationMinutes: Int,
    val priceCents: Long,
    val description: JsonElement,
    val featured: Boolean = false,
    val active: Boolean = true,
)

@Serializable
data class OperatorIdsBody(val operatorIds: List<String>)

@Serializable
data class ServiceIdsBody(val serviceIds: List<String>)

@Serializable
data class WeeklyHoursBody(val weeklyHours: Map<String, List<TimeRangeDto>>)

@Serializable
data class CreateOperatorBody(
    val name: String,
    val email: String,
    val phone: String,
    val title: String? = null,
    val weeklyHours: Map<String, List<TimeRangeDto>>? = null,
    val serviceIds: List<String>? = null,
)

/**
 * La password provvisoria del nuovo operatore torna una volta sola, alla
 * creazione: dopo non è più recuperabile da nessuna parte.
 */
@Serializable
data class NewOperatorAccountDto(val userId: String, val email: String, val temporaryPassword: String)

@Serializable
data class NewOperatorDto(val operator: OperatorDto, val account: NewOperatorAccountDto)

@Serializable
data class UpdateSalonBody(
    val name: String,
    val address: String,
    val city: String,
    val phone: String? = null,
    val weeklyHours: Map<String, List<TimeRangeDto>>,
)

@Serializable
data class HolidayDto(
    val id: String,
    val operatorId: String,
    val from: String,
    val to: String,
    val label: String = "",
)

@Serializable
data class HolidaysEnvelope(val holidays: List<HolidayDto> = emptyList())

@Serializable
data class CreateHolidayBody(val from: String, val to: String, val label: String? = null)
