package com.devora.mencare.core.data

import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.Holiday
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.Salon
import com.devora.mencare.core.model.TimeBlock
import com.devora.mencare.core.model.TimeRange
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Pure slot computation: operator weekly hours minus closing days, holidays,
 * personal blocks and existing active appointments, sampled on a fixed grid for
 * the requested total duration.
 *
 * **Non è più sulla strada dell'app.** Dalla Fase 2 la griglia degli slot la
 * calcola il server, che è l'unico a vedere l'agenda intera e a poterla
 * bloccare in transazione: due copie dello stesso calcolo, una per app, erano
 * due copie che potevano divergere fra loro e dal database. Resta qui perché
 * regge i test dei dati finti (`FakeBookingRepository`, `SlotEngineTest`), che
 * sono la palestra in cui si verifica la logica di prenotazione senza rete.
 */
object SlotEngine {

    const val GRID_MINUTES: Int = 30

    /** Minimum lead time for same-day bookings. */
    const val LEAD_MINUTES: Long = 30

    @Suppress("LongParameterList", "CyclomaticComplexMethod", "ReturnCount")
    fun slotsFor(
        date: LocalDate,
        operator: Operator,
        salon: Salon,
        totalDurationMinutes: Int,
        appointments: List<Appointment>,
        blocks: List<TimeBlock>,
        holidays: List<Holiday>,
        now: LocalDateTime,
    ): List<LocalTime> {
        if (date < now.toLocalDate()) return emptyList()
        if (holidays.any { it.operatorId == operator.id && date in it.from..it.to }) return emptyList()

        val openRanges = salon.weeklyHours[date.dayOfWeek].orEmpty()
        if (openRanges.isEmpty()) return emptyList()

        // An operator can never be bookable outside the salon's own opening hours.
        val workRanges = operator.weeklyHours[date.dayOfWeek].orEmpty()
            .flatMap { shift -> openRanges.mapNotNull { open -> shift.intersect(open) } }
        if (workRanges.isEmpty()) return emptyList()

        val busy = buildList {
            appointments
                .filter { it.operatorId == operator.id && it.date == date && it.isActive }
                .forEach { add(TimeRange(it.time, it.end.toLocalTime())) }
            // Every block (permesso, pausa, ferie, corso) takes its slots away.
            blocks
                .filter { it.operatorId == operator.id && it.date == date }
                .forEach { add(it.range) }
        }

        val earliest = if (date == now.toLocalDate()) now.toLocalTime().plusMinutes(LEAD_MINUTES) else LocalTime.MIN

        val result = mutableListOf<LocalTime>()
        for (range in workRanges) {
            var t = range.start
            while (!t.plusMinutes(totalDurationMinutes.toLong()).isAfter(range.end) &&
                t.plusMinutes(totalDurationMinutes.toLong()) > t // guard midnight wrap
            ) {
                val candidate = TimeRange(t, t.plusMinutes(totalDurationMinutes.toLong()))
                if (t >= earliest && busy.none { it.overlaps(candidate) }) {
                    result += t
                }
                t = t.plusMinutes(GRID_MINUTES.toLong())
                if (t == LocalTime.MIN) break
            }
        }
        return result.sorted()
    }

    /**
     * Can this appointment sit here? Staff-side check for a manual move: the
     * client-facing 30-minute grid and the same-day lead time don't apply when
     * the owner drags a card, only the operator's real availability does —
     * opening hours, holidays, personal blocks and the other appointments.
     *
     * [ignoreAppointmentId] is the card being moved: it must not block itself.
     */
    @Suppress("LongParameterList", "ReturnCount")
    fun canPlace(
        date: LocalDate,
        operator: Operator,
        salon: Salon,
        start: LocalTime,
        totalDurationMinutes: Int,
        appointments: List<Appointment>,
        blocks: List<TimeBlock>,
        holidays: List<Holiday>,
        ignoreAppointmentId: String? = null,
    ): Boolean {
        if (holidays.any { it.operatorId == operator.id && date in it.from..it.to }) return false

        val end = start.plusMinutes(totalDurationMinutes.toLong())
        if (end <= start) return false // the service would run past midnight

        val candidate = TimeRange(start, end)
        val openRanges = salon.weeklyHours[date.dayOfWeek].orEmpty()
        val workRanges = operator.weeklyHours[date.dayOfWeek].orEmpty()
            .flatMap { shift -> openRanges.mapNotNull { open -> shift.intersect(open) } }
        if (workRanges.none { it.start <= candidate.start && candidate.end <= it.end }) return false

        val busy = buildList {
            appointments
                .filter {
                    it.operatorId == operator.id && it.date == date && it.isActive &&
                        it.id != ignoreAppointmentId
                }
                .forEach { add(TimeRange(it.time, it.end.toLocalTime())) }
            blocks
                .filter { it.operatorId == operator.id && it.date == date }
                .forEach { add(it.range) }
        }
        return busy.none { it.overlaps(candidate) }
    }

    /** "Qualsiasi operatore": union of every eligible operator's slots. */
    @Suppress("LongParameterList")
    fun unionSlots(
        date: LocalDate,
        operators: List<Operator>,
        salon: Salon,
        totalDurationMinutes: Int,
        appointments: List<Appointment>,
        blocks: List<TimeBlock>,
        holidays: List<Holiday>,
        now: LocalDateTime,
    ): List<LocalTime> = operators
        .flatMap { slotsFor(date, it, salon, totalDurationMinutes, appointments, blocks, holidays, now) }
        .distinct()
        .sorted()
}
