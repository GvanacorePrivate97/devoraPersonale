package com.devora.mencare.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class AppointmentStatus { CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW }

enum class BookingChannel { APP, PHONE, WALK_IN }

enum class CancellationActor { CLIENT, SALON }

data class Appointment(
    val id: String,
    val clientId: String,
    val operatorId: String,
    val serviceIds: List<String>,
    val start: LocalDateTime,
    val durationMinutes: Int,
    val totalPriceCents: Long,
    val status: AppointmentStatus,
    val channel: BookingChannel = BookingChannel.APP,
    val noteForOperator: String? = null,
    val cancelledBy: CancellationActor? = null,
) {
    val end: LocalDateTime get() = start.plusMinutes(durationMinutes.toLong())

    val date: LocalDate get() = start.toLocalDate()
    val time: LocalTime get() = start.toLocalTime()

    val isActive: Boolean
        get() = status == AppointmentStatus.CONFIRMED || status == AppointmentStatus.IN_PROGRESS

    /**
     * In corso e completato arrivano da soli (lo decide il server a orario); a
     * mano resta il no-show: da orario d'inizio passato, anche su un
     * appuntamento già chiuso come completato.
     */
    fun canMarkNoShow(now: LocalDateTime = LocalDateTime.now()): Boolean =
        !start.isAfter(now) && (isActive || status == AppointmentStatus.COMPLETED)

    /** Un no-show segnato per sbaglio si corregge riportandolo a completato. */
    val canRevertNoShow: Boolean get() = status == AppointmentStatus.NO_SHOW
}

enum class WaitlistStatus { WAITING, NOTIFIED, EXPIRED }

data class WaitlistEntry(
    val id: String,
    val clientId: String,
    val date: LocalDate,
    val time: LocalTime?,
    val operatorId: String?,
    val serviceIds: List<String>,
    val durationMinutes: Int,
    val totalPriceCents: Long,
    val position: Int,
    val status: WaitlistStatus = WaitlistStatus.WAITING,
)
