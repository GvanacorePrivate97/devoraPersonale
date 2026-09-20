package com.devora.mencare.feature.client.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.common.formatDateLong
import com.devora.mencare.core.common.formatDateShort
import com.devora.mencare.core.common.formatDuration
import com.devora.mencare.core.common.formatTime
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.ErrorState
import com.devora.mencare.core.designsystem.component.InlineErrorBanner
import com.devora.mencare.core.designsystem.component.LoadingState
import com.devora.mencare.core.designsystem.component.LogoBadge
import com.devora.mencare.core.designsystem.component.NotificationBell
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.feature.client.R
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

private val CardShape = RoundedCornerShape(22.dp)
private val ButtonShape = RoundedCornerShape(16.dp)
private val TileShape = RoundedCornerShape(18.dp)

@Composable
fun ClientHomeScreen(
    onBook: () -> Unit,
    onRebook: (String) -> Unit,
    onQuickSlot: (QuickSlot) -> Unit,
    onHistory: () -> Unit,
    onNotifications: () -> Unit,
    viewModel: ClientHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bone)
            .verticalScroll(rememberScrollState()),
    ) {
        HomeHeader(state, onNotifications)

        val error = state.error
        // Tre casi, in quest'ordine: non è ancora arrivato niente e si aspetta;
        // non è arrivato niente ed è andata male, quindi "riprova" a tutta
        // pagina; oppure i dati ci sono e un eventuale guasto resta una riga
        // sopra, senza portare via la home a chi la stava guardando.
        if (state.isEmpty && error != null) {
            ErrorState(error = error, onRetry = viewModel::retry)
            return@Column
        }
        if (state.isEmpty) {
            LoadingState()
            return@Column
        }
        if (error != null) {
            InlineErrorBanner(
                error = error,
                onRetry = viewModel::retry,
                modifier = Modifier.readableWidth(),
            )
        }

        Column(
            modifier = Modifier.readableWidth().padding(horizontal = 20.dp).padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            val next = state.nextAppointment
            if (next != null) {
                NextAppointmentCard(state, next)
            } else {
                NoAppointmentCard()
            }

            // Stessa larghezza di quando divideva la riga con la lista d'attesa.
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AccentButton(
                    text = stringResource(R.string.client_home_book_now),
                    onClick = onBook,
                    modifier = Modifier.fillMaxWidth(0.58f),
                    height = 54.dp,
                    shape = ButtonShape,
                    leadingIcon = Icons.Outlined.Add,
                )
            }

            if (state.quickSlots.isNotEmpty()) {
                QuickSlotsSection(state, onQuickSlot)
            }

            val last = state.lastCompleted
            if (last != null) {
                RebookSection(state, last, onRebook, onHistory)
            }

        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HomeHeader(state: HomeUiState, onNotifications: () -> Unit) {
    DarkHeader(
        roundedBottom = true,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 26.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LogoBadge(size = 42.dp, corner = 13.dp)
            NotificationBell(hasUnread = state.unreadCount > 0, onClick = onNotifications)
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    formatDateLong(LocalDate.now()).uppercase(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, letterSpacing = 0.18.em),
                    color = OliveWood,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.client_home_greeting, state.user?.firstName.orEmpty()),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 38.sp),
                    color = Bone,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${state.user?.visitCount ?: 0}",
                    fontFamily = Cormorant,
                    fontSize = 30.sp,
                    lineHeight = 32.sp,
                    color = OliveWood,
                )
                Text(
                    stringResource(R.string.client_home_visits_label).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.12.em),
                    color = Bone,
                )
            }
        }
    }
}

private val CountdownHaloRadius = 56.dp

@Composable
private fun NextAppointmentCard(state: HomeUiState, next: Appointment) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Ink),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.client_home_next_title).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.16.em),
                    color = Bone,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(OliveWood)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
                Text(
                    countdownLabel(next.start).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.1.em),
                    color = Bone,
                    // Alone di luce centrato sul countdown: la card lo ritaglia ai bordi.
                    modifier = Modifier.drawBehind {
                        drawCircle(OliveWood.copy(alpha = 0.16f), radius = CountdownHaloRadius.toPx())
                    },
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatTime(next.time),
                    fontFamily = Cormorant,
                    fontSize = 40.sp,
                    color = Bone,
                )
                Text(
                    dayAndDuration(next),
                    style = MaterialTheme.typography.titleSmall,
                    color = Bone,
                    modifier = Modifier.padding(start = 10.dp, bottom = 6.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Bone.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        state.operators[next.operatorId]?.initials.orEmpty(),
                        fontFamily = Cormorant,
                        fontSize = 12.sp,
                        color = Bone,
                    )
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text(
                        state.operators[next.operatorId]?.name.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        color = Bone,
                    )
                    Text(
                        next.serviceIds.mapNotNull { state.services[it]?.name }.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                        color = Bone,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(color = Bone.copy(alpha = 0.14f))
            // Niente prezzo: le cifre le vede solo il titolare.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Bone.copy(alpha = 0.12f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = Bone,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    stringResource(R.string.client_home_add_calendar),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                    color = Bone,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun NoAppointmentCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Ink)
            .padding(18.dp),
    ) {
        Text(
            stringResource(R.string.client_home_no_upcoming),
            style = MaterialTheme.typography.titleSmall,
            color = Bone,
        )
    }
}

@Composable
private fun QuickSlotsSection(state: HomeUiState, onQuickSlot: (QuickSlot) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.client_home_quick_title).uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.16.em),
            color = Ink,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            state.quickSlots.forEach { slot ->
                QuickSlotChip(slot, onQuickSlot)
            }
        }
    }
}

@Composable
private fun QuickSlotChip(slot: QuickSlot, onClick: (QuickSlot) -> Unit) {
    Column(
        modifier = Modifier
            // Larghezza fissa: i chip restano tutti uguali qualunque sia l'etichetta.
            .width(92.dp)
            .clip(TileShape)
            .background(Stone)
            .clickable { onClick(slot) }
            .padding(vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            quickDayLabel(slot.date).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.12.em),
            color = TextMuted,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            formatTime(slot.time),
            fontFamily = Cormorant,
            fontSize = 20.sp,
            color = Ink,
        )
    }
}

@Composable
private fun quickDayLabel(date: LocalDate): String = when (date) {
    LocalDate.now() -> stringResource(R.string.client_home_today)
    LocalDate.now().plusDays(1) -> stringResource(R.string.client_home_tomorrow)
    else -> formatDateShort(date)
}

@Composable
private fun RebookSection(
    state: HomeUiState,
    last: Appointment,
    onRebook: (String) -> Unit,
    onHistory: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.client_home_rebook_title).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.16.em),
                color = Ink,
            )
            Text(
                stringResource(R.string.client_home_history_link),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                color = OliveWood,
                modifier = Modifier.clickable(onClick = onHistory),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TileShape)
                .background(Stone)
                .clickable { onRebook(last.id) }
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Bone),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.operators[last.operatorId]?.initials.orEmpty(),
                    fontFamily = Cormorant,
                    fontSize = 15.sp,
                    color = Ink,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    last.serviceIds.mapNotNull { state.services[it]?.name }.joinToString(" + "),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(
                        state.operators[last.operatorId]?.name,
                        formatDuration(last.durationMinutes),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(R.string.client_home_rebook_cta),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                color = Bone,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Ink)
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun dayAndDuration(next: Appointment): String {
    val day = if (next.date == LocalDate.now()) {
        stringResource(R.string.client_home_today)
    } else {
        formatDateShort(next.date)
    }
    return "$day · ${formatDuration(next.durationMinutes)}"
}

@Composable
private fun countdownLabel(start: LocalDateTime): String {
    val minutes = Duration.between(LocalDateTime.now(), start).toMinutes()
    return when {
        minutes < 60 -> stringResource(R.string.client_home_in_minutes, minutes.coerceAtLeast(0))
        minutes < 60 * 24 -> stringResource(R.string.client_home_in_hours, minutes / 60)
        else -> stringResource(R.string.client_home_in_days, minutes / (60 * 24))
    }
}
