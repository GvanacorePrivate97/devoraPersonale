package com.devora.mencare.core.data

import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.AppointmentStatus
import com.devora.mencare.core.model.BlockReason
import com.devora.mencare.core.model.Holiday
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.Salon
import com.devora.mencare.core.model.TimeBlock
import com.devora.mencare.core.model.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SlotEngineTest {

    // Salon open Monday to Saturday: the operator hours below are clamped to it.
    private val salon = Salon(
        "Men Care", "Via Scarlatti 120", "Napoli",
        DayOfWeek.entries.filterNot { it == DayOfWeek.SUNDAY }
            .associateWith { listOf(TimeRange(LocalTime.of(9, 0), LocalTime.of(19, 0))) },
    )

    // A Monday.
    private val monday: LocalDate = LocalDate.of(2026, 9, 14)
    private val now: LocalDateTime = monday.minusDays(3).atTime(12, 0)

    private fun operator(
        hours: Map<DayOfWeek, List<TimeRange>> = mapOf(
            DayOfWeek.MONDAY to listOf(TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0))),
        ),
    ) = Operator("op1", "Luca Ferrante", "Barbiere", "bio", emptyList(), weeklyHours = hours, serviceIds = emptySet())

    private fun apt(start: LocalTime, duration: Int, status: AppointmentStatus = AppointmentStatus.CONFIRMED) =
        Appointment(
            "a_$start", "c1", "op1", listOf("s1"), monday.atTime(start),
            durationMinutes = duration, totalPriceCents = 1000, status = status,
        )

    private fun slots(
        duration: Int = 30,
        appointments: List<Appointment> = emptyList(),
        blocks: List<TimeBlock> = emptyList(),
        holidays: List<Holiday> = emptyList(),
        op: Operator = operator(),
        date: LocalDate = monday,
        at: LocalDateTime = now,
    ) = SlotEngine.slotsFor(date, op, salon, duration, appointments, blocks, holidays, at)

    @Test
    fun `free morning yields grid slots that fit the duration`() {
        val result = slots(duration = 60)
        assertEquals(
            listOf("09:00", "09:30", "10:00", "10:30", "11:00", "11:30", "12:00"),
            result.map { it.toString() },
        )
    }

    @Test
    fun `slot must fit entirely inside working range`() {
        val result = slots(duration = 240)
        assertEquals(listOf(LocalTime.of(9, 0)), result)
    }

    @Test
    fun `booked appointment removes overlapping slots`() {
        val result = slots(duration = 30, appointments = listOf(apt(LocalTime.of(10, 0), 45)))
        // 10:00–10:45 busy: 09:30 (ends 10:00) ok, 10:00/10:30 gone, 11:00 ok.
        assertEquals(
            listOf("09:00", "09:30", "11:00", "11:30", "12:00", "12:30"),
            result.map { it.toString() },
        )
    }

    @Test
    fun `cancelled appointments free their slot`() {
        val result = slots(
            duration = 30,
            appointments = listOf(apt(LocalTime.of(10, 0), 45, status = AppointmentStatus.CANCELLED)),
        )
        assertTrue(LocalTime.of(10, 0) in result)
    }

    @Test
    fun `every block hides its slots from clients`() {
        val block = TimeBlock("b1", "op1", BlockReason.PAUSA, monday, TimeRange(LocalTime.of(9, 0), LocalTime.of(11, 0)))
        val result = slots(duration = 30, blocks = listOf(block))
        assertEquals(listOf("11:00", "11:30", "12:00", "12:30"), result.map { it.toString() })
    }

    @Test
    fun `closing day yields no slots`() {
        val sunday = monday.minusDays(1)
        val op = operator(hours = mapOf(DayOfWeek.SUNDAY to listOf(TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)))))
        assertTrue(slots(op = op, date = sunday).isEmpty())
    }

    @Test
    fun `holiday yields no slots`() {
        val holiday = Holiday("h1", "op1", monday.minusDays(1), monday.plusDays(1), "Ferie")
        assertTrue(slots(holidays = listOf(holiday)).isEmpty())
    }

    @Test
    fun `past days yield no slots`() {
        assertTrue(slots(at = monday.plusDays(1).atTime(10, 0)).isEmpty())
    }

    @Test
    fun `same day respects lead time`() {
        val result = slots(duration = 30, at = monday.atTime(9, 45))
        // 9:45 + 30 min lead = 10:15 → first slot 10:30.
        assertEquals(LocalTime.of(10, 30), result.first())
        assertFalse(LocalTime.of(10, 0) in result)
    }

    @Test
    fun `union of operators doubles availability`() {
        val op1 = operator()
        val op2 = op1.copy(
            id = "op2",
            weeklyHours = mapOf(DayOfWeek.MONDAY to listOf(TimeRange(LocalTime.of(14, 0), LocalTime.of(18, 0)))),
        )
        val result = SlotEngine.unionSlots(monday, listOf(op1, op2), salon, 30, emptyList(), emptyList(), emptyList(), now)
        assertTrue(LocalTime.of(9, 0) in result)
        assertTrue(LocalTime.of(14, 0) in result)
    }

    @Test
    fun `union deduplicates shared slots`() {
        val op1 = operator()
        val op2 = op1.copy(id = "op2")
        val result = SlotEngine.unionSlots(monday, listOf(op1, op2), salon, 30, emptyList(), emptyList(), emptyList(), now)
        assertEquals(result.size, result.distinct().size)
    }

    @Test
    fun `appointment of other operator does not affect slots`() {
        val other = apt(LocalTime.of(10, 0), 45).copy(operatorId = "op_other")
        val result = slots(duration = 30, appointments = listOf(other))
        assertTrue(LocalTime.of(10, 0) in result)
    }

    @Test
    fun `split shifts produce slots in both ranges only`() {
        val op = operator(
            hours = mapOf(
                DayOfWeek.MONDAY to listOf(
                    TimeRange(LocalTime.of(9, 0), LocalTime.of(11, 0)),
                    TimeRange(LocalTime.of(15, 0), LocalTime.of(17, 0)),
                ),
            ),
        )
        val result = slots(duration = 60, op = op)
        assertEquals(
            listOf("09:00", "09:30", "10:00", "15:00", "15:30", "16:00"),
            result.map { it.toString() },
        )
    }
}
