package com.devora.mencare.feature.client.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.devora.mencare.core.common.formatDateShort
import com.devora.mencare.core.common.formatDuration
import com.devora.mencare.core.common.formatMonthYear
import com.devora.mencare.core.common.formatPriceCompact
import com.devora.mencare.core.common.formatTime
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.BottomBarReveal
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.DarkTotalBar
import com.devora.mencare.core.designsystem.component.FullyBookedIllustration
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.StoneBorder
import com.devora.mencare.core.designsystem.theme.TextMuted
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import com.devora.mencare.feature.client.R

@Composable
internal fun StepDatetime(state: BookingUiState, viewModel: BookingViewModel) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 16.dp),
        ) {
            MonthCalendar(state, viewModel)
            Spacer(Modifier.height(18.dp))
            val date = state.selectedDate
            if (date != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrandSectionLabel(
                        "${formatDateShort(date)} · ${formatDuration(state.totalDurationMinutes)}",
                        modifier = Modifier.weight(1f),
                    )
                    if (!state.selectedDayFull) {
                        LegendDot(Stone, stringResource(R.string.wizard_slot_free))
                        Spacer(Modifier.width(10.dp))
                        LegendDot(OliveWood, stringResource(R.string.wizard_slot_chosen))
                    }
                }
                Spacer(Modifier.height(10.dp))
                when {
                    state.slotsLoading -> Box(
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = OliveWood, strokeWidth = 2.dp)
                    }
                    state.selectedDayFull -> FullyBookedPanel()
                    state.slots.isEmpty() -> Text(
                        stringResource(R.string.wizard_no_slots),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                    )
                    else -> SlotGrid(state.slots, state.selectedSlot, viewModel::selectSlot)
                }
                if (state.takenSlot != null && !state.selectedDayFull) {
                    Spacer(Modifier.height(11.dp))
                    WaitlistPrompt(state, viewModel)
                }
            }
        }
        // Sul giorno al completo "Avvisami" sta fisso in fondo, come la barra
        // degli altri step: non finisce mai sotto il bordo dello schermo.
        if (state.selectedDayFull) {
            FullyBookedActions(state, viewModel)
        }
        BottomBarReveal(visible = state.selectedSlot != null) {
            DarkTotalBar(
                caption = state.selectedDate?.let { date ->
                    "${formatDateShort(date)} · ${state.selectedSlot?.let { formatTime(it) }.orEmpty()}"
                }.orEmpty(),
                value = "${formatDuration(state.totalDurationMinutes)} · ${formatPriceCompact(state.totalPriceCents)}",
                ctaLabel = stringResource(R.string.wizard_continue),
                onClick = viewModel::continueFromDatetime,
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = TextMuted,
        )
    }
}

@Composable
private fun SlotGrid(slots: List<LocalTime>, selected: LocalTime?, onSelect: (LocalTime) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        slots.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { slot ->
                    val isSelected = slot == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) OliveWood else Stone)
                            .clickable { onSelect(slot) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            formatTime(slot),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isSelected) Bone else Ink,
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The chosen day is fully booked: instead of an empty grid, an illustration and
 * "Avvisami", which queues the client for any time of that day.
 */
@Composable
private fun FullyBookedPanel() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FullyBookedIllustration(Modifier.widthIn(max = 210.dp).fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.wizard_full_title),
            fontFamily = Cormorant,
            fontSize = 26.sp,
            color = Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.wizard_full_body),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

/** "Avvisami", or the joined state once the client is in line. */
@Composable
private fun FullyBookedActions(state: BookingUiState, viewModel: BookingViewModel) {
    val joined = state.dayWaitlistEntry
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bone)
            .navigationBarsPadding()
            .readableWidth()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (joined == null) {
            AccentButton(
                text = stringResource(R.string.wizard_full_cta),
                onClick = viewModel::joinDayWaitlist,
                loading = state.joiningWaitlist,
                height = 54.dp,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = Icons.Outlined.NotificationsNone,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, OliveWood, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.wizard_full_joined, joined.position),
                    style = MaterialTheme.typography.titleMedium,
                    color = OliveWood,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.wizard_full_joined_hint),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = TextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Offered only when the chosen slot was taken while confirming. */
@Composable
private fun WaitlistPrompt(state: BookingUiState, viewModel: BookingViewModel) {
    val slot = state.takenSlot ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Stone)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.wizard_waitlist_prompt, formatTime(slot)),
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.wizard_waitlist_push_hint),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextMuted,
            )
        }
        Text(
            state.waitlistJoinedPosition?.let { stringResource(R.string.wizard_waitlist_joined, it) }
                ?: stringResource(R.string.wizard_waitlist_cta),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, letterSpacing = 0.sp),
            color = OliveWood,
            modifier = Modifier.clickable(
                enabled = state.waitlistJoinedPosition == null,
                onClick = viewModel::joinWaitlist,
            ),
        )
    }
}

@Composable
private fun MonthCalendar(state: BookingUiState, viewModel: BookingViewModel) {
    val month = state.month
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatMonthYear(month.atDay(1)).replaceFirstChar { it.uppercase() },
                fontFamily = Cormorant,
                fontSize = 26.sp,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            RoundArrow(
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                enabled = month > YearMonth.now(),
                onClick = { viewModel.loadMonth(month.minusMonths(1)) },
            )
            Spacer(Modifier.width(8.dp))
            RoundArrow(
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                enabled = true,
                dark = true,
                onClick = { viewModel.loadMonth(month.plusMonths(1)) },
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("L", "M", "M", "G", "V", "S", "D").forEach { day ->
                Text(
                    day,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.1.em),
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val firstDay = month.atDay(1)
        val leadingEmpty = firstDay.dayOfWeek.value - DayOfWeek.MONDAY.value
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val cells: List<LocalDate?> = List(leadingEmpty) { null } + days
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    DayCell(day, state, viewModel, modifier = Modifier.weight(1f))
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RoundArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    dark: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (dark) Ink else Stone)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (dark) Bone else Ink.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun DayCell(day: LocalDate?, state: BookingUiState, viewModel: BookingViewModel, modifier: Modifier) {
    if (day == null) {
        Spacer(modifier.aspectRatio(1f))
        return
    }
    val available = day in state.availableDays
    // Fully booked: still tappable (it offers the waitlist), outlined instead of filled.
    val full = day in state.fullyBookedDays
    val selectable = available || full
    val isSelected = day == state.selectedDate
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isSelected -> OliveWood
                    available -> Stone
                    else -> Color.Transparent
                },
            )
            .then(
                if (full && !isSelected) {
                    Modifier.border(1.dp, StoneBorder, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .clickable(enabled = selectable) { viewModel.selectDate(day) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${day.dayOfMonth}",
            style = MaterialTheme.typography.titleSmall,
            color = when {
                isSelected -> Bone
                selectable -> Ink
                else -> TextMuted.copy(alpha = 0.5f)
            },
        )
        if (available) {
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Bone else OliveWood),
            )
        }
    }
}
