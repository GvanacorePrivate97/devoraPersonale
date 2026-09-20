// Un mapper per forma JSON: sono tante funzioni corte e tutte uguali, ed è la
// ragione per cui stanno in un file solo invece che sparpagliate.
@file:Suppress("TooManyFunctions")

package com.devora.mencare.core.data.network

import com.devora.mencare.core.model.AppNotification
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.AppointmentStatus
import com.devora.mencare.core.model.BlockReason
import com.devora.mencare.core.model.BookingChannel
import com.devora.mencare.core.model.CampaignSegment
import com.devora.mencare.core.model.CampaignStatus
import com.devora.mencare.core.model.CancellationActor
import com.devora.mencare.core.model.ClientNotificationPrefs
import com.devora.mencare.core.model.ClientRecord
import com.devora.mencare.core.model.DashboardPeriod
import com.devora.mencare.core.model.DashboardStats
import com.devora.mencare.core.model.Holiday
import com.devora.mencare.core.model.NotificationSettings
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.OperatorOccupancy
import com.devora.mencare.core.model.PushCampaign
import com.devora.mencare.core.model.ReminderRule
import com.devora.mencare.core.model.Salon
import com.devora.mencare.core.model.Service
import com.devora.mencare.core.model.TimeBlock
import com.devora.mencare.core.model.TimeRange
import com.devora.mencare.core.model.UpcomingDay
import com.devora.mencare.core.model.User
import com.devora.mencare.core.model.UserRole
import com.devora.mencare.core.model.WaitlistEntry
import com.devora.mencare.core.model.WaitlistStatus
import com.devora.mencare.core.network.dto.AppNotificationDto
import com.devora.mencare.core.network.dto.AppointmentDto
import com.devora.mencare.core.network.dto.CampaignDto
import com.devora.mencare.core.network.dto.ClientDto
import com.devora.mencare.core.network.dto.ClientHistoryDto
import com.devora.mencare.core.network.dto.DashboardDto
import com.devora.mencare.core.network.dto.HolidayDto
import com.devora.mencare.core.network.dto.NotificationPrefsDto
import com.devora.mencare.core.network.dto.OperatorDto
import com.devora.mencare.core.network.dto.ReminderRuleDto
import com.devora.mencare.core.network.dto.SalonDto
import com.devora.mencare.core.network.dto.SalonNotificationSettingsDto
import com.devora.mencare.core.network.dto.ServiceDto
import com.devora.mencare.core.network.dto.TimeBlockDto
import com.devora.mencare.core.network.dto.TimeRangeDto
import com.devora.mencare.core.network.dto.UserDto
import com.devora.mencare.core.network.dto.WaitlistEntryDto
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Traduzione fra le forme JSON del contratto (`docs/API.md`) e i modelli di
 * dominio. Sta in `:core:data` di proposito: `:core:network` resta puro
 * trasporto, e i modelli non sanno che esiste un backend.
 *
 * Regola sola: qui non si calcola niente. Se un numero arriva dal server lo si
 * copia; se manca, manca. Le derivazioni (slot liberi, posizione in coda,
 * incassi) stanno tutte di là.
 */

/**
 * Il salone è uno e sta a Napoli: le date "da muro" degli appuntamenti sono in
 * questo fuso, e un istante ISO va riportato qui per essere mostrato. Il
 * contratto lo dichiara in `salon.timezone`; qui serve come valore di partenza
 * per i campi che non passano dal listino (le notifiche).
 */
val SalonZone: ZoneId = ZoneId.of("Europe/Rome")

private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
    this?.let { raw -> runCatching { enumValueOf<T>(raw) }.getOrNull() } ?: fallback

private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
    this?.let { raw -> runCatching { enumValueOf<T>(raw) }.getOrNull() }

/** "09:00" o "09:00:00": Postgres manda anche i secondi, l'app ne usa cinque caratteri. */
private fun parseTime(raw: String): LocalTime = LocalTime.parse(raw.take(5))

private fun parseDate(raw: String): LocalDate = LocalDate.parse(raw)

/** Istante ISO con fuso → ora del salone, che è quella che l'utente legge. */
private fun parseInstantAtSalon(raw: String): LocalDateTime =
    Instant.parse(raw).atZone(SalonZone).toLocalDateTime()

/**
 * Una fascia con fine prima dell'inizio non è rappresentabile ([TimeRange] lo
 * vieta): invece di far cadere tutta la risposta, la riga si scarta.
 */
private fun TimeRangeDto.toModelOrNull(): TimeRange? {
    val from = parseTime(start)
    val to = parseTime(end)
    return if (from < to) TimeRange(from, to) else null
}

/** Chiavi "1".."7" (ISO, 1 = lunedì); un giorno assente vuol dire chiuso. */
fun Map<String, List<TimeRangeDto>>.toWeeklyHours(): Map<DayOfWeek, List<TimeRange>> =
    mapNotNull { (key, ranges) ->
        val day = key.toIntOrNull()?.takeIf { it in 1..7 }?.let(DayOfWeek::of) ?: return@mapNotNull null
        val mapped = ranges.mapNotNull { it.toModelOrNull() }
        if (mapped.isEmpty()) null else day to mapped
    }.toMap()

fun Map<DayOfWeek, List<TimeRange>>.toHoursDto(): Map<String, List<TimeRangeDto>> =
    filterValues { it.isNotEmpty() }
        .map { (day, ranges) ->
            day.value.toString() to ranges.map { TimeRangeDto(it.start.toString(), it.end.toString()) }
        }
        .toMap()

fun UserDto.toModel(): User = User(
    id = id,
    firstName = firstName,
    lastName = lastName,
    email = email,
    phone = phone,
    role = role.toEnum(UserRole.CLIENT),
    memberSince = parseDate(memberSince),
    visitCount = visitCount,
    // Con il backend la foto è un URL, non più un file locale: il componente
    // che la disegna accetta entrambi.
    avatarPath = avatarUrl,
    clientRecordId = clientId,
    operatorId = operatorId,
)

fun ServiceDto.toModel(): Service = Service(
    id = id,
    name = name,
    durationMinutes = durationMinutes,
    priceCents = priceCents,
    description = description,
    featured = featured,
    active = active,
)

fun OperatorDto.toModel(): Operator = Operator(
    id = id,
    name = name,
    title = title,
    bio = bio,
    specialties = specialties,
    isOwner = isOwner,
    weeklyHours = weeklyHours.toWeeklyHours(),
    serviceIds = serviceIds.toSet(),
)

fun SalonDto.toModel(): Salon = Salon(
    name = name,
    address = address,
    city = city,
    weeklyHours = weeklyHours.toWeeklyHours(),
)

fun HolidayDto.toModel(): Holiday = Holiday(
    id = id,
    operatorId = operatorId,
    from = parseDate(from),
    to = parseDate(to),
    label = label,
)

fun AppointmentDto.toModel(): Appointment = Appointment(
    id = id,
    clientId = clientId,
    operatorId = operatorId,
    serviceIds = serviceIds,
    start = parseDate(date).atTime(parseTime(time)),
    durationMinutes = durationMinutes,
    // Allo staff gli importi non vengono proprio inviati: zero, non "sconosciuto".
    totalPriceCents = totalPriceCents ?: 0L,
    status = status.toEnum(AppointmentStatus.CONFIRMED),
    channel = channel.toEnum(BookingChannel.APP),
    noteForOperator = noteForOperator,
    cancelledBy = cancelledBy.toEnumOrNull<CancellationActor>(),
)

/**
 * Lo storico che la rubrica restituisce è la stessa cosa vista dal lato salone:
 * porta i nomi dei servizi ma non ripete l'id del cliente, che è quello della
 * scheda che si sta guardando.
 */
fun ClientHistoryDto.toModel(clientId: String): Appointment = Appointment(
    id = id,
    clientId = clientId,
    operatorId = operatorId,
    serviceIds = serviceIds,
    start = parseDate(date).atTime(parseTime(time)),
    durationMinutes = durationMinutes,
    totalPriceCents = totalPriceCents ?: 0L,
    status = status.toEnum(AppointmentStatus.CONFIRMED),
    channel = channel.toEnum(BookingChannel.APP),
)

fun WaitlistEntryDto.toModel(): WaitlistEntry = WaitlistEntry(
    id = id,
    clientId = clientId,
    date = parseDate(date),
    time = time?.let(::parseTime),
    operatorId = operatorId,
    serviceIds = serviceIds,
    durationMinutes = durationMinutes,
    totalPriceCents = totalPriceCents,
    position = position,
    status = status.toEnum(WaitlistStatus.WAITING),
)

fun TimeBlockDto.toModel(): TimeBlock? {
    val range = TimeRangeDto(start, end).toModelOrNull() ?: return null
    return TimeBlock(
        id = id,
        operatorId = operatorId,
        reason = reason.toEnum(BlockReason.PERMESSO),
        date = parseDate(date),
        range = range,
        label = label,
    )
}

fun ClientDto.toModel(): ClientRecord = ClientRecord(
    id = id,
    firstName = firstName,
    lastName = lastName,
    phone = phone,
    email = email.orEmpty(),
    customerSince = parseDate(customerSince),
    visitCount = visitCount,
    // Assente per il ruolo STAFF: la scheda che gli si mostra non ha importi.
    lifetimeSpendCents = lifetimeSpendCents ?: 0L,
    noShowCount = noShowCount,
    lastVisit = lastVisit?.let(::parseDate),
    preferredServiceIds = preferredServiceIds,
    preferredOperatorId = preferredOperatorId,
    marketingOptIn = marketingOptIn,
)

fun AppNotificationDto.toModel(userId: String): AppNotification = AppNotification(
    id = id,
    userId = userId,
    title = title,
    body = body,
    at = parseInstantAtSalon(at),
    read = read,
)

fun NotificationPrefsDto.toModel(): ClientNotificationPrefs = ClientNotificationPrefs(
    appointmentReminder = appointmentReminder,
    waitlistAlerts = waitlistAlerts,
    marketing = marketing,
)

fun ClientNotificationPrefs.toDto(): NotificationPrefsDto = NotificationPrefsDto(
    appointmentReminder = appointmentReminder,
    waitlistAlerts = waitlistAlerts,
    marketing = marketing,
)

fun ReminderRuleDto.toModel(): ReminderRule = ReminderRule(id = id, hoursBefore = hoursBefore)

fun SalonNotificationSettingsDto.toModel(rules: List<ReminderRuleDto>): NotificationSettings =
    NotificationSettings(
        reminders = rules.map { it.toModel() }.sortedBy { it.hoursBefore },
        bookingConfirmation = bookingConfirmation,
        cancellationAlert = cancellationAlert,
        lateOperatorAlert = lateOperatorAlert,
        emptyDayPromos = emptyDayPromos,
    )

fun NotificationSettings.toDto(): SalonNotificationSettingsDto = SalonNotificationSettingsDto(
    bookingConfirmation = bookingConfirmation,
    cancellationAlert = cancellationAlert,
    lateOperatorAlert = lateOperatorAlert,
    emptyDayPromos = emptyDayPromos,
)

fun DashboardDto.toModel(): DashboardStats = DashboardStats(
    period = period.toEnum(DashboardPeriod.WEEK),
    revenueCents = revenueCents,
    revenueTrendPercent = revenueTrendPercent,
    appointmentCount = appointmentCount,
    noShowPercent = noShowPercent,
    averageTicketCents = averageTicketCents,
    operatorOccupancy = operatorOccupancy.map { OperatorOccupancy(it.operatorId, it.operatorName, it.percent) },
    upcomingDays = upcomingDays.map {
        UpcomingDay(parseDate(it.date), it.occupancyPercent, it.waitlistCount, it.closed)
    },
    inactiveClients = inactiveClients.map { it.toModel() },
)

fun CampaignDto.toModel(): PushCampaign = PushCampaign(
    id = id,
    name = name,
    segment = segment.toEnum(CampaignSegment.TUTTI),
    title = title,
    body = body,
    // Si usa l'orario da muro che il titolare ha scelto, non l'istante: è quello
    // che la schermata deve rimostrare senza sorprese di fuso.
    scheduledAt = scheduledDate?.let { day -> parseDate(day).atTime(parseTime(scheduledTime ?: "00:00")) },
    repeatWeekly = repeatWeekly,
    sendCap = sendCap,
    reachableCount = reachableCount,
    segmentSize = segmentSize,
    status = status.toEnum(CampaignStatus.DRAFT),
)
