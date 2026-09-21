package com.devora.mencare.feature.client.appointments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.common.formatDateShort
import com.devora.mencare.core.common.formatDuration
import com.devora.mencare.core.common.formatTime
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.SegmentedTabs
import com.devora.mencare.core.designsystem.component.ErrorState
import com.devora.mencare.core.designsystem.component.InlineErrorBanner
import com.devora.mencare.core.designsystem.component.LoadingState
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.StoneBorder
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.AppointmentStatus
import com.devora.mencare.core.model.CancellationActor
import com.devora.mencare.core.model.WaitlistEntry
import com.devora.mencare.feature.client.R
import java.time.LocalDate

@Composable
fun AppointmentsScreen(
    onBook: () -> Unit,
    onRebook: (String) -> Unit,
    onEdit: (String) -> Unit,
    viewModel: AppointmentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pendingCancel by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(Bone)) {
        DarkHeader(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.apts_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp),
                color = Bone,
            )
            Spacer(Modifier.height(14.dp))
            SegmentedTabs(
                options = listOf(
                    stringResource(R.string.apts_tab_upcoming, state.upcoming.size),
                    stringResource(R.string.apts_tab_past, state.past.size),
                ),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
        }

        val failure = state.error
        // Finché non è arrivato niente, un guasto è tutta la schermata; quando
        // gli appuntamenti ci sono già, resta una riga in cima: meglio una lista
        // un po' vecchia che una schermata vuota per un timeout.
        if (state.isEmpty && failure != null) {
            ErrorState(error = failure, onRetry = viewModel::retry, modifier = Modifier.weight(1f))
            return@Column
        }
        if (state.isEmpty && state.loading) {
            LoadingState(modifier = Modifier.weight(1f))
            return@Column
        }
        if (failure != null) {
            InlineErrorBanner(error = failure, onRetry = viewModel::retry, modifier = Modifier.readableWidth())
        }

        val isEmpty = if (tab == 0) state.upcoming.isEmpty() && state.waitlist.isEmpty() else state.past.isEmpty()
        if (isEmpty && tab == 0) {
            EmptyUpcoming(state, onBook, onRebook)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).readableWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                if (tab == 0) {
                    items(state.upcoming, key = { it.id }) { appointment ->
                        UpcomingCard(
                            appointment = appointment,
                            state = state,
                            onEdit = { onEdit(appointment.id) },
                            onCancel = { pendingCancel = appointment.id },
                        )
                    }
                    if (state.waitlist.isNotEmpty()) {
                        item(key = "waitlist-header") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BrandSectionLabel(stringResource(R.string.apts_waitlist_title))
                                Spacer(Modifier.width(10.dp))
                                Box(Modifier.weight(1f).height(1.dp).background(Stone))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    stringResource(R.string.apts_waitlist_slots, state.waitlist.size),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = TextMuted,
                                )
                            }
                        }
                        items(state.waitlist, key = { it.id }) { entry ->
                            WaitlistRow(entry, state) { viewModel.leaveWaitlist(entry.id) }
                        }
                    }
                } else {
                    items(state.past, key = { it.id }) { appointment ->
                        PastRow(appointment, state) { onRebook(appointment.id) }
                    }
                        }
            }
        }
    }

    val cancelId = pendingCancel
    if (cancelId != null) {
        val appointment = state.upcoming.firstOrNull { it.id == cancelId }
        AlertDialog(
            onDismissRequest = { pendingCancel = null },
            containerColor = Bone,
            title = { Text(stringResource(R.string.apts_cancel_confirm_title)) },
            text = {
                if (appointment != null) {
                    Text(
                        stringResource(
                            R.string.apts_cancel_confirm_body,
                            formatDateShort(appointment.date),
                            formatTime(appointment.time),
                            state.operators[appointment.operatorId]?.name.orEmpty(),
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancel(cancelId)
                    pendingCancel = null
                }) {
                    Text(stringResource(R.string.apts_cancel_confirm_yes), color = OliveWood)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancel = null }) {
                    Text(stringResource(R.string.apts_cancel_confirm_no), color = Ink)
                }
            },
        )
    }
}

@Composable
private fun UpcomingCard(
    appointment: Appointment,
    state: AppointmentsUiState,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Bone)
            .border(1.5.dp, OliveWood, RoundedCornerShape(16.dp)),
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            DateBlock(appointment.date, muted = false)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Ogni prenotazione è confermata appena fatta (§6.2): un badge
                    // che dice sempre la stessa cosa non informa, occupa e basta.
                    Text(
                        formatTime(appointment.time),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                        color = Ink,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    appointment.serviceIds.mapNotNull { state.services[it]?.name }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(Stone),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            state.operators[appointment.operatorId]?.initials.orEmpty(),
                            fontFamily = Cormorant,
                            fontSize = 11.sp,
                            color = Ink,
                        )
                    }
                    Text(
                        listOfNotNull(
                            state.operators[appointment.operatorId]?.name,
                            formatDuration(appointment.durationMinutes),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = StoneBorder)
        Row(modifier = Modifier.fillMaxWidth().height(46.dp)) {
            CardAction(stringResource(R.string.apts_edit), Modifier.weight(1f), onEdit)
            Box(Modifier.width(1.dp).fillMaxSize().background(StoneBorder))
            CardAction(stringResource(R.string.apts_cancel), Modifier.weight(1f), onCancel)
        }
    }
}

@Composable
private fun CardAction(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = Ink)
    }
}

/** Riquadro giorno/data/mese delle card appuntamento, uguale nei due tab; spento se la visita non c'è stata. */
@Composable
private fun DateBlock(date: LocalDate, muted: Boolean) {
    val parts = formatDateShort(date).split(" ")
    val content = if (muted) TextMuted else Bone
    Column(
        modifier = Modifier
            .size(width = 54.dp, height = 62.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (muted) Bone else OliveWood),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            parts.getOrElse(0) { "" }.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.1.em),
            color = content,
        )
        Text(
            parts.getOrElse(1) { "" },
            fontFamily = Cormorant,
            fontSize = 20.sp,
            color = content,
        )
        Text(
            parts.getOrElse(2) { "" }.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.1.em),
            color = content,
        )
    }
}

@Composable
private fun StatusPill(text: String, accent: Boolean) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.14.em),
        color = if (accent) Bone else Ink,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (accent) OliveWood else Stone)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun WaitlistRow(entry: WaitlistEntry, state: AppointmentsUiState, onLeave: () -> Unit) {
    val first = entry.position == 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (first) Ink else Stone)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(6.dp).clip(CircleShape).background(if (first) OliveWood else TextMuted),
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                "${formatDateShort(entry.date)} · " +
                    (entry.time?.let { formatTime(it) } ?: stringResource(R.string.apts_waitlist_any_time)),
                style = MaterialTheme.typography.titleSmall,
                color = if (first) Bone else Ink,
            )
            Text(
                listOfNotNull(
                    entry.operatorId?.let { state.operators[it]?.name }
                        ?: stringResource(R.string.apts_waitlist_any),
                    stringResource(R.string.apts_waitlist_status),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = if (first) Bone else TextMuted,
            )
        }
        // La posizione si vede sempre e tutti possono lasciare la coda.
        StatusPill(stringResource(R.string.apts_waitlist_position, entry.position), accent = true)
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.apts_waitlist_leave),
            style = MaterialTheme.typography.titleSmall,
            color = OliveWood,
            modifier = Modifier.clickable(onClick = onLeave).padding(6.dp),
        )
    }
}

@Composable
private fun PastRow(appointment: Appointment, state: AppointmentsUiState, onRebook: () -> Unit) {
    val cancelled = appointment.status == AppointmentStatus.CANCELLED ||
        appointment.status == AppointmentStatus.NO_SHOW
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Stone)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateBlock(appointment.date, muted = cancelled)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                appointment.serviceIds.mapNotNull { state.services[it]?.name }.joinToString(" + "),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                color = if (cancelled) TextMuted else Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(
                    formatTime(appointment.time),
                    if (cancelled) {
                        stringResource(
                            when {
                                appointment.status == AppointmentStatus.NO_SHOW -> R.string.apts_no_show
                                appointment.cancelledBy == CancellationActor.SALON -> R.string.apts_cancelled_by_salon
                                else -> R.string.apts_cancelled_by_client
                            },
                        )
                    } else {
                        state.operators[appointment.operatorId]?.name
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!cancelled) {
            Text(
                stringResource(R.string.apts_rebook),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                color = Bone,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Ink)
                    .clickable(onClick = onRebook)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = Cormorant, fontSize = 22.sp, color = Bone)
        Spacer(Modifier.height(2.dp))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.12.em),
            color = Bone,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyUpcoming(
    state: AppointmentsUiState,
    onBook: () -> Unit,
    onRebook: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .readableWidth()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Box(
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(Stone),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(26.dp))
        Text(
            stringResource(R.string.apts_empty_title),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp, lineHeight = 32.sp),
            color = Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.apts_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        AccentButton(
            text = stringResource(R.string.apts_empty_book),
            onClick = onBook,
            height = 54.dp,
            shape = RoundedCornerShape(16.dp),
        )
        val last = state.lastCompleted
        if (last != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Stone)
                    .clickable { onRebook(last.id) }
                    .padding(horizontal = 15.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.apts_empty_rebook),
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                    )
                    Text(
                        listOfNotNull(
                            last.serviceIds.mapNotNull { state.services[it]?.name }.joinToString(" + "),
                            state.operators[last.operatorId]?.name,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
