package com.devora.mencare.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.StoneSoft
import com.devora.mencare.core.designsystem.theme.TextMuted
import java.time.LocalTime

/**
 * Day timetable shared by the owner's and the staff agenda: same hours, same
 * scale, and a tap on an empty spot resolves to the quarter hour under it.
 *
 * The rail is tall enough that the shortest service in the catalogue still gets
 * a card whose text is whole, and a card never shrinks below [MinCardHeight]:
 * duration drives the height, but legibility sets its floor.
 */
object AgendaGrid {
    const val DAY_START_HOUR = 9
    const val DAY_END_HOUR = 20
    const val SNAP_MINUTES = 15
    val HourHeight: Dp = 96.dp
    val GutterWidth: Dp = 46.dp

    /** Breathing room between a card and the one below it. */
    val CardGap: Dp = 4.dp

    /**
     * Height a card needs for each of its three shapes: name row alone, name
     * row plus one line of services, plus two. The budget is a 16dp name row,
     * 2dp, then 14dp a line, inside 5dp of vertical padding; cards take these
     * as a floor, so a longer font or a wider line can only push them taller,
     * never cut a word in half.
     */
    val MinCardHeight: Dp = 26.dp
    val OneServiceLineHeight: Dp = 42.dp
    val TwoServiceLinesHeight: Dp = 56.dp

    val hours: IntRange get() = DAY_START_HOUR until DAY_END_HOUR
    val dayMinutes: Int get() = (DAY_END_HOUR - DAY_START_HOUR) * 60
    val TotalHeight: Dp get() = HourHeight * (DAY_END_HOUR - DAY_START_HOUR)

    fun minutesFromStart(time: LocalTime): Int = time.toSecondOfDay() / 60 - DAY_START_HOUR * 60

    fun y(time: LocalTime): Dp = HourHeight * (minutesFromStart(time) / 60f)

    fun height(minutes: Int): Dp = HourHeight * (minutes / 60f)

    /**
     * The height the card of a [minutes]-long appointment is given — a floor,
     * never a ceiling.
     *
     * Duration sets it, but a short appointment borrows the empty time in front
     * of it — [freeMinutesAfter], up to the next appointment or block in the
     * column — so even a ten-minute service shows its line in full. The card
     * stops growing once the services fit: it never runs over what is booked
     * next. When nothing is free after it, the card still keeps its single row
     * whole; the few dp it then overhangs sit under the next card, which is
     * drawn over it.
     */
    fun cardHeight(minutes: Int, freeMinutesAfter: Int = 0): Dp {
        val own = height(minutes) - CardGap
        if (own >= TwoServiceLinesHeight) return own
        val grown = height(minutes + freeMinutesAfter) - CardGap
        return maxOf(own, minOf(grown, TwoServiceLinesHeight), MinCardHeight)
    }

    /** Lines of services a card of [cardHeight] has room for: 0, 1 or 2. */
    fun serviceLines(cardHeight: Dp): Int = when {
        cardHeight >= TwoServiceLinesHeight -> 2
        cardHeight >= OneServiceLineHeight -> 1
        else -> 0
    }

    /** Start of the quarter hour at [yPx], or null outside the day. */
    fun timeAt(yPx: Float, hourPx: Float): LocalTime? {
        val minutes = (yPx / hourPx * 60f).toInt()
        val snapped = minutes / SNAP_MINUTES * SNAP_MINUTES
        if (yPx < 0 || snapped >= dayMinutes) return null
        return LocalTime.of(DAY_START_HOUR, 0).plusMinutes(snapped.toLong())
    }

    /**
     * Minutes of empty rail between [endMinutes] and the next thing in the
     * column, whose starts are [busyStarts] (minutes from the rail's start).
     */
    fun freeMinutesAfter(endMinutes: Int, busyStarts: List<Int>): Int {
        val next = busyStarts.filter { it >= endMinutes }.minOrNull() ?: dayMinutes
        return (next - endMinutes).coerceAtLeast(0)
    }
}

/**
 * Hour labels down the left edge of the timetable. The hour rules run under
 * them too, on the same background as the columns: rail and grid read as one.
 */
@Composable
fun AgendaHourLabels(modifier: Modifier = Modifier) {
    Box(modifier.width(AgendaGrid.GutterWidth)) {
        AgendaHourLines(Modifier.fillMaxWidth())
        Column {
            for (hour in AgendaGrid.hours) {
                Box(
                    Modifier.height(AgendaGrid.HourHeight).padding(start = 8.dp, top = 4.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Text(
                        "%02d:00".format(hour),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextMuted,
                    )
                }
            }
        }
    }
}

/**
 * A stretch of a column that can't be booked — off shift, lunch break,
 * holidays, a course: one grey band, label centered, all looking the same.
 * Place it inside the column's [Box]; it spans the column's width.
 *
 * A band says how long the salon is shut, so it is never stretched to fit its
 * label: the label takes the lines the band has room for, and a band too thin
 * for even one goes bare rather than showing half a word.
 */
@Composable
fun AgendaUnavailableBand(start: LocalTime, end: LocalTime, label: String?) {
    val minutes = (end.toSecondOfDay() - start.toSecondOfDay()) / 60
    val bandHeight = AgendaGrid.height(minutes)
    val labelLines = when {
        bandHeight >= 32.dp -> 2
        bandHeight >= 16.dp -> 1
        else -> 0
    }
    Box(
        modifier = Modifier
            .offset(y = AgendaGrid.y(start))
            .fillMaxWidth()
            .height(bandHeight)
            .background(StoneSoft)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null && labelLines > 0) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                color = TextMuted,
                textAlign = TextAlign.Center,
                maxLines = labelLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Hour rules (and fainter half-hour ones) behind a column of the timetable. */
@Composable
fun AgendaHourLines(modifier: Modifier = Modifier) {
    Column(modifier) {
        repeat(AgendaGrid.DAY_END_HOUR - AgendaGrid.DAY_START_HOUR) {
            Box(Modifier.fillMaxWidth().height(AgendaGrid.HourHeight)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Stone))
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Stone.copy(alpha = 0.5f)),
                )
            }
        }
    }
}
