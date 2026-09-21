package com.devora.mencare.core.designsystem

import com.devora.mencare.core.designsystem.component.AgendaGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The agenda card sizing: what the staff and the owner agenda both read off the
 * grid. Keep in sync with the iOS `AgendaGridTests`.
 */
class AgendaGridTest {

    @Test
    fun `a long appointment is sized by its duration alone`() {
        // 45 minutes already clears the two-line height, so the free time after
        // it changes nothing.
        val height = AgendaGrid.cardHeight(minutes = 45, freeMinutesAfter = 0)
        assertEquals(AgendaGrid.height(45) - AgendaGrid.CardGap, height)
        assertEquals(2, AgendaGrid.serviceLines(height))
    }

    @Test
    fun `half an hour still has room for one line of services`() {
        val height = AgendaGrid.cardHeight(minutes = 30, freeMinutesAfter = 0)
        assertEquals(1, AgendaGrid.serviceLines(height))
    }

    @Test
    fun `the shortest service keeps its first row whole`() {
        // Ten minutes is 16dp of rail: without a floor the name would be cut.
        val height = AgendaGrid.cardHeight(minutes = 10, freeMinutesAfter = 0)
        assertEquals(AgendaGrid.MinCardHeight, height)
        assertTrue(height > AgendaGrid.height(10))
    }

    @Test
    fun `a short appointment borrows the free time in front of it`() {
        val alone = AgendaGrid.cardHeight(minutes = 10, freeMinutesAfter = 60)
        assertEquals(AgendaGrid.TwoServiceLinesHeight, alone)
        assertEquals(2, AgendaGrid.serviceLines(alone))

        val squeezed = AgendaGrid.cardHeight(minutes = 10, freeMinutesAfter = 20)
        assertEquals(1, AgendaGrid.serviceLines(squeezed))
    }

    @Test
    fun `a card never grows past the free time it was given`() {
        // 10 minutes booked + 15 free is not enough for a services line, and the
        // card stops there instead of running over what comes next.
        val height = AgendaGrid.cardHeight(minutes = 10, freeMinutesAfter = 15)
        assertTrue(height <= AgendaGrid.height(25))
        assertEquals(0, AgendaGrid.serviceLines(height))
    }

    @Test
    fun `free time runs to the next booking, or to the end of the day`() {
        // 09:00 + 30' with the next card at 10:00 (60' from the rail's start).
        assertEquals(30, AgendaGrid.freeMinutesAfter(endMinutes = 30, busyStarts = listOf(60, 180)))
        // Nothing after it: the rest of the rail is free.
        assertEquals(AgendaGrid.dayMinutes - 30, AgendaGrid.freeMinutesAfter(30, emptyList()))
        // Back to back.
        assertEquals(0, AgendaGrid.freeMinutesAfter(30, listOf(30)))
        // What starts before the card ends is not free time.
        assertEquals(30, AgendaGrid.freeMinutesAfter(30, listOf(0, 60)))
    }

    @Test
    fun `a tap resolves to the quarter hour under it`() {
        val hourPx = 100f
        assertEquals(java.time.LocalTime.of(9, 0), AgendaGrid.timeAt(10f, hourPx))
        assertEquals(java.time.LocalTime.of(9, 30), AgendaGrid.timeAt(55f, hourPx))
        assertNull(AgendaGrid.timeAt(-1f, hourPx))
        assertNull(AgendaGrid.timeAt(hourPx * 11, hourPx))
    }
}
