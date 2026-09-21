package com.devora.mencare.feature.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.common.formatTime
import com.devora.mencare.core.designsystem.component.AgendaDayBar
import com.devora.mencare.core.designsystem.component.AgendaGrid
import com.devora.mencare.core.designsystem.component.AgendaHourLabels
import com.devora.mencare.core.designsystem.component.AgendaHourLines
import com.devora.mencare.core.designsystem.component.AgendaUnavailableBand
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.NotificationBell
import com.devora.mencare.core.designsystem.component.ProfileAvatar
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.OnDarkMuted
import com.devora.mencare.core.designsystem.theme.Radii
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.StoneBorder
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.core.model.Appointment
import com.devora.mencare.core.model.AppointmentStatus
import com.devora.mencare.core.model.BlockReason
import com.devora.mencare.core.model.BookingChannel
import com.devora.mencare.core.model.TimeBlock
import java.time.LocalTime

/** One opening of the booking sheet: its own ViewModel key, and the tapped time if any. */
private data class BookingSheetRequest(val key: String, val time: LocalTime?)

@Composable
fun AgendaScreen(
    onAppointment: (String) -> Unit,
    onProfile: () -> Unit,
    onNotifications: () -> Unit,
    viewModel: AgendaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var bookingSheet by remember { mutableStateOf<BookingSheetRequest?>(null) }
    var sheetCount by rememberSaveable { mutableIntStateOf(0) }
    var blockSheetOpen by rememberSaveable { mutableStateOf(false) }

    fun openBooking(time: LocalTime?) {
        sheetCount++
        bookingSheet = BookingSheetRequest("staff-booking-$sheetCount", time)
    }

    Column(modifier = Modifier.fillMaxSize().background(Bone)) {
        DarkHeader(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Le iniziali (o la foto) portano al profilo, dove la foto si cambia.
                ProfileAvatar(
                    initials = state.operator?.initials.orEmpty(),
                    photoPath = state.user?.avatarPath,
                    size = 44.dp,
                    corner = 14.dp,
                    contentDescription = stringResource(R.string.staff_tab_profile),
                    onClick = onProfile,
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        state.operator?.name.orEmpty(),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                        color = Bone,
                    )
                    Text(
                        state.operator?.title.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkMuted,
                    )
                }
                NotificationBell(hasUnread = state.hasUnreadNotifications, onClick = onNotifications)
            }
            Spacer(Modifier.height(16.dp))
            // Stessa barra del titolare: frecce, data, striscia dei giorni e
            // "Oggi" quando si è altrove.
            AgendaDayBar(
                selected = state.selectedDate,
                onSelect = viewModel::selectDate,
            )
        }

        // La data sta nella barra dei giorni, qui sopra: ripeterla sarebbe
        // solo rumore. Resta la riga della giornata vuota.
        if (state.appointments.isEmpty()) {
            Text(
                stringResource(R.string.staff_no_appointments),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier
                    .readableWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 14.dp, bottom = 8.dp),
            )
        }

        Box(Modifier.weight(1f)) {
            // La griglia c'è sempre, anche a giornata vuota: un tap su un
            // orario libero apre la prenotazione a quell'ora.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .readableWidth()
                    .padding(start = 12.dp, end = 20.dp, top = 6.dp, bottom = 96.dp),
            ) {
                AgendaHourLabels()
                DayColumn(
                    state = state,
                    onAppointment = onAppointment,
                    onEmptyTap = { time -> openBooking(time) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .readableWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(Radii.Md)
                        .background(Bone)
                        .border(1.5.dp, Ink, Radii.Md)
                        .clickable { blockSheetOpen = true }
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.staff_block_fab),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                // Nero, non oliva: i due pulsanti galleggiano sopra le card
                // dell'agenda, che sono oliva — un'azione dello stesso colore
                // di ciò che copre sparisce. Il nero stacca su oliva, su stone
                // e sul fondo chiaro, e resta il colore dell'azione principale.
                Row(
                    modifier = Modifier
                        .weight(1.35f)
                        .height(52.dp)
                        .shadow(10.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Ink)
                        .clickable { openBooking(null) }
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = Bone, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.staff_booking_fab),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = Bone,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        bookingSheet?.let { request ->
            StaffBookingScreen(
                onDismiss = { bookingSheet = null },
                date = state.selectedDate,
                time = request.time,
                sessionKey = request.key,
            )
        }
        if (blockSheetOpen) {
            BlockSheet(onDismiss = { blockSheetOpen = false })
        }
    }
}

@Composable
private fun DayColumn(
    state: AgendaUiState,
    onAppointment: (String) -> Unit,
    onEmptyTap: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentState by rememberUpdatedState(state)
    val currentOnEmptyTap by rememberUpdatedState(onEmptyTap)
    Box(
        modifier = modifier
            .height(AgendaGrid.TotalHeight)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val time = AgendaGrid.timeAt(offset.y, AgendaGrid.HourHeight.toPx())
                    // Niente prenotazioni su blocchi o fuori turno.
                    if (time != null && !currentState.isBlocked(time) && currentState.onShift(time)) {
                        currentOnEmptyTap(time)
                    }
                }
            },
    ) {
        AgendaHourLines(Modifier.fillMaxWidth())
        state.blocks.forEach { block -> BlockCard(block) }
        // In ordine di orario: se una card corta sborda di qualche dp finisce
        // sotto quella dopo, non sopra il suo testo.
        state.appointments.sortedBy { it.time }.forEach { appointment ->
            BookingCard(appointment, state, onAppointment)
        }
    }
}

/**
 * Minuti liberi fra la fine di [appointment] e il prossimo impegno della
 * giornata: e' lo spazio che una card corta puo' prendersi per restare intera.
 */
private fun AgendaUiState.freeMinutesAfter(appointment: Appointment): Int {
    val end = AgendaGrid.minutesFromStart(appointment.time) + appointment.durationMinutes
    val busyStarts = appointments.filter { it.id != appointment.id }
        .map { AgendaGrid.minutesFromStart(it.time) } +
        blocks.map { AgendaGrid.minutesFromStart(it.range.start) }
    return AgendaGrid.freeMinutesAfter(end, busyStarts)
}

@Composable
private fun BookingCard(
    appointment: Appointment,
    state: AgendaUiState,
    onAppointment: (String) -> Unit,
) {
    val inProgress = appointment.status == AppointmentStatus.IN_PROGRESS
    val completed = appointment.status == AppointmentStatus.COMPLETED
    val noShow = appointment.status == AppointmentStatus.NO_SHOW
    // Olive anche in corso: il nero in agenda e' solo il feedback del
    // trascinamento. Il no-show è spento, con il filo, come dal titolare.
    val background = when {
        noShow -> Bone
        completed -> Stone
        else -> OliveWood
    }
    val foreground = when {
        noShow -> TextMuted
        completed -> Ink
        else -> Bone
    }
    // La durata detta l'altezza, ma una card corta si allarga nel tempo libero
    // che ha davanti: anche dieci minuti restano leggibili per intero.
    val height = AgendaGrid.cardHeight(appointment.durationMinutes, state.freeMinutesAfter(appointment))
    val serviceLines = AgendaGrid.serviceLines(height)
    val client = state.clients[appointment.clientId]
    Column(
        modifier = Modifier
            .offset(y = AgendaGrid.y(appointment.time) + 2.dp)
            .padding(horizontal = 3.dp)
            .fillMaxWidth()
            .heightIn(min = height)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(1.dp, if (noShow) StoneBorder else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable { onAppointment(appointment.id) }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalArrangement = if (serviceLines == 0) Arrangement.Center else Arrangement.Top,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(client?.firstName, client?.lastName).joinToString(" "),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, lineHeight = 16.sp),
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (inProgress) {
                    stringResource(R.string.staff_in_progress)
                } else {
                    "${formatTime(appointment.time)} – " +
                        formatTime(appointment.time.plusMinutes(appointment.durationMinutes.toLong()))
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                color = foreground,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        if (serviceLines > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                if (noShow) {
                    stringResource(R.string.apt_detail_no_show)
                } else {
                    appointment.serviceIds.mapNotNull { state.services[it]?.name }.joinToString(" + ") +
                        if (appointment.channel == BookingChannel.WALK_IN) {
                            " · ${stringResource(R.string.staff_walk_in)}"
                        } else {
                            ""
                        }
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                color = foreground,
                maxLines = serviceLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Stessa fascia grigia del titolare: etichetta centrata, niente card a parte. */
@Composable
private fun BlockCard(block: TimeBlock) {
    AgendaUnavailableBand(
        start = block.range.start,
        end = block.range.end,
        label = block.label ?: stringResource(blockReasonLabel(block.reason)),
    )
}

private fun blockReasonLabel(reason: BlockReason): Int = when (reason) {
    BlockReason.PERMESSO -> R.string.block_reason_permesso
    BlockReason.PAUSA -> R.string.block_reason_pausa
    BlockReason.FERIE -> R.string.block_reason_ferie
    BlockReason.CORSO -> R.string.block_reason_corso
}
