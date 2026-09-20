package com.devora.mencare.core.data

import com.devora.mencare.core.common.AppError
import com.devora.mencare.core.common.AppResult
import com.devora.mencare.core.data.fake.DemoSeed
import com.devora.mencare.core.data.fake.FakeBookingRepository
import com.devora.mencare.core.data.fake.InMemoryStore
import com.devora.mencare.core.data.repository.BookingRequest
import com.devora.mencare.core.model.CancellationActor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class FakeBookingRepositoryTest {

    private lateinit var store: InMemoryStore
    private lateinit var repository: FakeBookingRepository

    /** Next Tuesday: every seeded operator works, salon open. */
    private val day: LocalDate = LocalDate.now().plusWeeks(2).let {
        it.plusDays(((DayOfWeek.TUESDAY.value - it.dayOfWeek.value + 7) % 7).toLong())
    }

    @Before
    fun setUp() {
        store = InMemoryStore()
        repository = FakeBookingRepository(store)
    }

    private fun request(
        time: LocalTime = LocalTime.of(9, 0),
        operatorId: String? = DemoSeed.OP_LUCA,
        services: List<String> = listOf(DemoSeed.SVC_TAGLIO),
    ) = BookingRequest(
        clientId = DemoSeed.CLIENT_MARCO,
        operatorId = operatorId,
        serviceIds = services,
        start = day.atTime(time),
    )

    @Test
    fun `booking a free slot succeeds and totals services`() = runTest {
        val result = repository.book(request(services = listOf(DemoSeed.SVC_TAGLIO, DemoSeed.SVC_RASATURA)))
        val appointment = (result as AppResult.Success).data
        assertEquals(75, appointment.durationMinutes)
        assertEquals(2200, appointment.totalPriceCents)
    }

    @Test
    fun `booking the same slot twice fails with SlotNoLongerAvailable`() = runTest {
        repository.book(request())
        val second = repository.book(request())
        assertTrue(second is AppResult.Failure && (second as AppResult.Failure).error is AppError.SlotNoLongerAvailable)
    }

    @Test
    fun `any operator booking picks an eligible free operator`() = runTest {
        val result = repository.book(request(operatorId = null))
        val appointment = (result as AppResult.Success).data
        val operator = store.operators.value.first { it.id == appointment.operatorId }
        assertTrue(DemoSeed.SVC_TAGLIO in operator.serviceIds)
    }

    @Test
    fun `every booking is confirmed straight away`() = runTest {
        val result = repository.book(
            request(operatorId = DemoSeed.OP_GIULIA, services = listOf(DemoSeed.SVC_BABY), time = LocalTime.of(10, 0)),
        )
        val appointment = (result as AppResult.Success).data
        assertEquals(com.devora.mencare.core.model.AppointmentStatus.CONFIRMED, appointment.status)
    }

    @Test
    fun `waitlist positions are FIFO per queue`() = runTest {
        val first = repository.joinWaitlist(
            "cli_a", day, LocalTime.of(17, 30), DemoSeed.OP_LUCA, listOf(DemoSeed.SVC_TAGLIO),
        )
        val second = repository.joinWaitlist(
            "cli_b", day, LocalTime.of(17, 30), DemoSeed.OP_LUCA, listOf(DemoSeed.SVC_TAGLIO),
        )
        assertEquals(1, (first as AppResult.Success).data.position)
        assertEquals(2, (second as AppResult.Success).data.position)
    }

    @Test
    fun `rescheduling lands on a quarter-hour off the client slot grid`() = runTest {
        val appointment = (repository.book(request(time = LocalTime.of(9, 0))) as AppResult.Success).data
        // 09:45 is never a client-bookable start (30-min grid) but the owner may drop a card there.
        val moved = repository.reschedule(appointment.id, day.atTime(9, 45), DemoSeed.OP_LUCA)
        assertEquals(day.atTime(9, 45), (moved as AppResult.Success).data.start)
        assertEquals(day.atTime(9, 45), store.appointments.value.first { it.id == appointment.id }.start)
    }

    @Test
    fun `rescheduling onto a busy operator fails`() = runTest {
        val first = (repository.book(request(time = LocalTime.of(9, 0))) as AppResult.Success).data
        repository.book(request(time = LocalTime.of(10, 0)))
        val result = repository.reschedule(first.id, day.atTime(10, 0), DemoSeed.OP_LUCA)
        assertTrue(result is AppResult.Failure)
        assertEquals(day.atTime(9, 0), store.appointments.value.first { it.id == first.id }.start)
    }

    @Test
    fun `rescheduling outside the operator hours fails`() = runTest {
        val appointment = (repository.book(request()) as AppResult.Success).data
        val result = repository.reschedule(appointment.id, day.atTime(20, 0), DemoSeed.OP_LUCA)
        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun `completing an appointment updates the client record`() = runTest {
        val appointment = (repository.book(request()) as AppResult.Success).data
        val before = store.clients.value.first { it.id == DemoSeed.CLIENT_MARCO }
        repository.markCompleted(appointment.id)
        val after = store.clients.value.first { it.id == DemoSeed.CLIENT_MARCO }
        assertEquals(before.visitCount + 1, after.visitCount)
        assertEquals(before.lifetimeSpendCents + appointment.totalPriceCents, after.lifetimeSpendCents)
    }

    /** Fills Luca's whole [day] with one-hour appointments. */
    private suspend fun fillLucasDay(): List<String> =
        listOf(9, 10, 11, 12, 14, 15, 16, 17, 18).map { hour ->
            (
                repository.book(
                    request(time = LocalTime.of(hour, 0), services = listOf(DemoSeed.SVC_TAGLIO_BARBA)),
                ) as AppResult.Success
                ).data.id
        }

    @Test
    fun `a day with every slot taken is fully booked, a closing day is not`() = runTest {
        fillLucasDay()
        val sunday = day.plusDays((DayOfWeek.SUNDAY.value - day.dayOfWeek.value).toLong())
        val full = repository.fullyBookedDays(DemoSeed.OP_LUCA, listOf(DemoSeed.SVC_TAGLIO), day, sunday)
        assertEquals(setOf(day), full)
        assertTrue(day !in repository.availableDays(DemoSeed.OP_LUCA, listOf(DemoSeed.SVC_TAGLIO), day, day))
    }

    @Test
    fun `joining the same queue twice keeps a single entry`() = runTest {
        repeat(2) { repository.joinWaitlist("cli_a", day, null, DemoSeed.OP_LUCA, listOf(DemoSeed.SVC_TAGLIO)) }
        assertEquals(1, repository.waitlistForClient("cli_a").first().size)
    }

    @Test
    fun `leaving the queue moves the next client up`() = runTest {
        val first = (repository.joinWaitlist("cli_a", day, null, DemoSeed.OP_LUCA, listOf(DemoSeed.SVC_TAGLIO)) as AppResult.Success).data
        repository.joinWaitlist("cli_b", day, null, DemoSeed.OP_LUCA, listOf(DemoSeed.SVC_TAGLIO))
        repository.leaveWaitlist(first.id)
        assertEquals(1, repository.waitlistForClient("cli_b").first().single().position)
    }

    @Test
    fun `a cancellation notifies the first client in line`() = runTest {
        val booked = fillLucasDay()
        repository.joinWaitlist(DemoSeed.CLIENT_MARCO, day, null, DemoSeed.OP_LUCA, listOf(DemoSeed.SVC_TAGLIO))
        val before = store.notifications.value.size

        repository.cancel(booked.first(), CancellationActor.SALON)

        assertEquals(before + 1, store.notifications.value.size)
        assertEquals(DemoSeed.USER_CLIENT, store.notifications.value.last().userId)
        assertTrue(repository.waitlistForClient(DemoSeed.CLIENT_MARCO).first().none { it.date == day })
    }
}
