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
 */
object AgendaGrid {
    const val DAY_START_HOUR = 9
    const val DAY_END_HOUR = 20
    const val SNAP_MINUTES = 15
    val HourHeight: Dp = 64.dp
    val GutterWidth: Dp = 46.dp

    val hours: IntRange get() = DAY_START_HOUR until DAY_END_HOUR
    val TotalHeight: Dp get() = HourHeight * (DAY_END_HOUR - DAY_START_HOUR)

    fun minutesFromStart(time: LocalTime): Int = time.toSecondOfDay() / 60 - DAY_START_HOUR * 60

    fun y(time: LocalTime): Dp = HourHeight * (minutesFromStart(time) / 60f)

    fun height(minutes: Int): Dp = HourHeight * (minutes / 60f)

    /** Start of the quarter hour at [yPx], or null outside the day. */
    fun timeAt(yPx: Float, hourPx: Float): LocalTime? {
        val minutes = (yPx / hourPx * 60f).toInt()
        val snapped = minutes / SNAP_MINUTES * SNAP_MINUTES
        if (yPx < 0 || snapped >= (DAY_END_HOUR - DAY_START_HOUR) * 60) return null
        return LocalTime.of(DAY_START_HOUR, 0).plusMinutes(snapped.toLong())
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
 */
@Composable
fun AgendaUnavailableBand(start: LocalTime, end: LocalTime, label: String?) {
    val minutes = (end.toSecondOfDay() - start.toSecondOfDay()) / 60
    Box(
        modifier = Modifier
            .offset(y = AgendaGrid.y(start))
            .fillMaxWidth()
            .height(AgendaGrid.height(minutes))
            .background(StoneSoft)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 2,
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
