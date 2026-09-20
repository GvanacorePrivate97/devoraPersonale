package com.devora.mencare.feature.admin.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.OnDarkMuted
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.StoneBorder
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.feature.admin.R
import com.devora.mencare.feature.admin.manual.ManualBookingScreen
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MOVE_FEEDBACK_MILLIS = 2200L
private val HourHeight = AgendaGrid.HourHeight
private val ColumnWidth = 108.dp

/** One opening of the booking sheet: its own ViewModel key, and what the tap already chose. */
private data class BookingSheetRequest(
    val key: String,
    val operatorId: String?,
    val time: LocalTime?,
    /** Set when the sheet edits this appointment instead of creating one. */
    val editingId: String? = null,
)

/** An appointment being dragged: where it started, and how far the finger moved. */
private data class DragState(
    val appointmentId: String,
    val operatorIndex: Int,
    val startMinutes: Int,
    val durationMinutes: Int,
    val offset: Offset = Offset.Zero,
)

@Composable
fun WeeklyAgendaScreen(
    onNotifications: () -> Unit,
    viewModel: WeeklyAgendaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var drag by remember { mutableStateOf<DragState?>(null) }
    var bookingSheet by remember { mutableStateOf<BookingSheetRequest?>(null) }
    var sheetCount by rememberSaveable { mutableIntStateOf(0) }
    var blockSheetOpen by rememberSaveable { mutableStateOf(false) }
    var detailFor by rememberSaveable { mutableStateOf<String?>(null) }

    val density = LocalDensity.current
    val hourPx = with(density) { HourHeight.toPx() }

    fun openBooking(operatorId: String?, time: LocalTime?, editingId: String? = null) {
        sheetCount++
        bookingSheet = BookingSheetRequest("manual-booking-$sheetCount", operatorId, time, editingId)
    }

    LaunchedEffect(state.moveResult) {
        if (state.moveResult != null) {
            delay(MOVE_FEEDBACK_MILLIS)
            viewModel.consumeMoveResult()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Bone)) {
        // L'agenda usa tutta la larghezza del tablet: l'intestazione la segue.
        DarkHeader(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            fullWidthContent = true,
        ) {
            // Stessa impaginazione dell'agenda operatore: chi sono / cosa
            // guardo in alto, la navigazione del giorno sotto.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.week_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Bone,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(
                            R.string.week_subtitle,
                            state.operators.size,
                            state.appointments.count { it.date == state.selectedDay && it.isActive },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkMuted,
                    )
                }
                NotificationBell(hasUnread = state.hasUnreadNotifications, onClick = onNotifications)
            }
            Spacer(Modifier.height(16.dp))
            AgendaDayBar(
                selected = state.selectedDay,
                onSelect = viewModel::selectDay,
            )
        }

        BoxWithConstraints(Modifier.weight(1f)) {
            val horizontal = rememberScrollState()
            val scope = rememberCoroutineScope()
            // Le corsie riempiono lo schermo quando ci stanno tutte (tablet, pochi
            // operatori); altrimenti larghezza fissa e si scorre in orizzontale.
            val columnWidth = if (state.operators.isEmpty()) {
                ColumnWidth
            } else {
                max(ColumnWidth, (maxWidth - AgendaGrid.GutterWidth) / state.operators.size)
            }
            val columnPx = with(density) { columnWidth.toPx() }
            Column(Modifier.fillMaxSize()) {
                Box {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Bone).padding(vertical = 10.dp),
                    ) {
                        Spacer(Modifier.width(AgendaGrid.GutterWidth))
                        Row(Modifier.horizontalScroll(horizontal)) {
                            state.operators.forEach { operator ->
                                Column(
                                    modifier = Modifier.width(columnWidth),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                Box(
                                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Stone),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        operator.initials,
                                        fontFamily = Cormorant,
                                        fontSize = 12.sp,
                                        color = Ink,
                                    )
                                }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        operator.name.substringBefore(' '),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = Ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    // Altri operatori fuori dallo schermo: la freccia lo dice e ci porta.
                    if (horizontal.canScrollBackward) {
                        ScrollHint(
                            icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp),
                        ) { scope.launch { horizontal.animateScrollBy(-columnPx) } }
                    }
                    if (horizontal.canScrollForward) {
                        ScrollHint(
                            icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
                        ) { scope.launch { horizontal.animateScrollBy(columnPx) } }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(StoneBorder))

                Row(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    AgendaHourLabels()
                    Row(Modifier.horizontalScroll(horizontal)) {
                        state.operators.forEachIndexed { index, operator ->
                            OperatorColumn(
                                state = state,
                                operatorId = operator.id,
                                columnWidth = columnWidth,
                                drag = drag,
                                onDragStart = { appointment ->
                                    drag = DragState(
                                        appointmentId = appointment.id,
                                        operatorIndex = index,
                                        startMinutes = minutesFromStart(appointment.time),
                                        durationMinutes = appointment.durationMinutes,
                                    )
                                },
                                onDragAmount = { amount ->
                                    drag = drag?.let { it.copy(offset = it.offset + amount) }
                                },
                                onDragEnd = {
                                    val current = drag
                                    drag = null
                                    if (current != null) {
                                        val target = dropTarget(current, hourPx, columnPx, state.operators.size)
                                        if (target != null) {
                                            viewModel.move(
                                                current.appointmentId,
                                                state.operators[target.first].id,
                                                target.second,
                                            )
                                        }
                                    }
                                },
                                onDragCancel = { drag = null },
                                onTap = { detailFor = it },
                                onEmptyTap = { time ->
                                    // Niente prenotazioni su blocchi o fuori turno.
                                    if (!state.isBlocked(operator.id, time) && state.onShift(operator.id, time)) {
                                        openBooking(operator.id, time)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // Durante il trascinamento i bottoni si tolgono di mezzo: la card
            // deve poter atterrare anche nell'angolo che occupano.
            if (drag == null) {
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Ink)
                            .clickable { blockSheetOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.EventBusy,
                            contentDescription = stringResource(R.string.week_new_block),
                            tint = Bone,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(OliveWood)
                            .clickable { openBooking(null, null) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.week_new_booking),
                            tint = Bone,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            bookingSheet?.let { request ->
                val editing = request.editingId?.let { id -> state.appointments.firstOrNull { it.id == id } }
                ManualBookingScreen(
                    onDismiss = { bookingSheet = null },
                    operatorId = request.operatorId,
                    date = state.selectedDay,
                    time = request.time,
                    editAppointment = editing,
                    editClient = editing?.let { state.clients[it.clientId] },
                    sessionKey = request.key,
                )
            }
            if (blockSheetOpen) {
                AdminBlockSheet(onDismiss = { blockSheetOpen = false })
            }
            val detailId = detailFor
            if (detailId != null) {
                AppointmentActionsSheet(
                    state = state,
                    appointmentId = detailId,
                    onEditAppointment = {
                        detailFor = null
                        openBooking(operatorId = null, time = null, editingId = detailId)
                    },
                    onCancelAppointment = {
                        viewModel.cancel(detailId)
                        detailFor = null
                    },
                    onMarkNoShow = { viewModel.markNoShow(detailId) },
                    onMarkCompleted = { viewModel.markCompleted(detailId) },
                    onDismiss = { detailFor = null },
                )
            }

            val move = state.moveResult
            if (drag == null && move != null) {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    containerColor = Ink,
                    contentColor = Bone,
                ) {
                    Text(
                        stringResource(
                            if (move == MoveResult.MOVED) R.string.week_moved else R.string.week_move_error,
                        ),
                    )
                }
            }

            // While dragging, spell out where the card would land.
            val current = drag
            if (current != null) {
                val target = dropTarget(current, hourPx, columnPx, state.operators.size)
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    containerColor = Ink,
                    contentColor = Bone,
                ) {
                    Text(
                        if (target == null) {
                            stringResource(R.string.week_drag_out)
                        } else {
                            val operator = state.operators[target.first]
                            val firstName = operator.name.substringBefore(' ')
                            // Fuori turno lo si dice subito, senza aspettare il rifiuto.
                            if (state.worksAt(operator, target.second, current.durationMinutes)) {
                                stringResource(R.string.week_drag_target, firstName, formatTime(target.second))
                            } else {
                                stringResource(R.string.week_drag_target_off, firstName, formatTime(target.second))
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Turns the finger travel into a column and a time, snapped to the grid.
 * Null when the drop would fall outside the day or the operator strip.
 */
private fun dropTarget(
    drag: DragState,
    hourPx: Float,
    columnPx: Float,
    operatorCount: Int,
): Pair<Int, LocalTime>? {
    val columnDelta = (drag.offset.x / columnPx).roundToInt()
    val targetIndex = drag.operatorIndex + columnDelta
    if (targetIndex !in 0 until operatorCount) return null

    val minutesDelta = (drag.offset.y / hourPx * 60f).roundToInt()
    val raw = drag.startMinutes + minutesDelta
    val snapped = (raw.toFloat() / AgendaGrid.SNAP_MINUTES).roundToInt() * AgendaGrid.SNAP_MINUTES
    val lastStart = (AgendaGrid.DAY_END_HOUR - AgendaGrid.DAY_START_HOUR) * 60 - drag.durationMinutes
    if (snapped < 0 || snapped > lastStart) return null
    return targetIndex to LocalTime.of(AgendaGrid.DAY_START_HOUR, 0).plusMinutes(snapped.toLong())
}

/** Round nudge over the operator strip: more columns that way, tap to go. */
@Composable
private fun ScrollHint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Ink.copy(alpha = 0.85f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Bone, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun OperatorColumn(
    state: WeeklyAgendaUiState,
    operatorId: String,
    columnWidth: Dp,
    drag: DragState?,
    onDragStart: (com.devora.mencare.core.model.Appointment) -> Unit,
    onDragAmount: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onTap: (String) -> Unit,
    onEmptyTap: (LocalTime) -> Unit,
) {
    val currentOnEmptyTap by rememberUpdatedState(onEmptyTap)
    Box(
        modifier = Modifier
            .width(columnWidth)
            .height(AgendaGrid.TotalHeight)
            // Tap su uno spazio libero: nuova prenotazione con operatore e ora già scelti.
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    AgendaGrid.timeAt(offset.y, HourHeight.toPx())?.let(currentOnEmptyTap)
                }
            },
    ) {
        // Hour rules, so the grid reads as a timetable.
        AgendaHourLines(Modifier.fillMaxWidth())
        // Fuori turno e blocchi (pausa, ferie…): stessa fascia grigia, etichetta
        // centrata. Una colonna vuota non deve sembrare libera.
        val offRanges = state.offDutyRanges(operatorId)
        val wholeDayOff = offRanges.size == 1 &&
            offRanges.first().start == LocalTime.of(AgendaGrid.DAY_START_HOUR, 0) &&
            offRanges.first().end == LocalTime.of(AgendaGrid.DAY_END_HOUR, 0)
        offRanges.forEach { range ->
            AgendaUnavailableBand(
                start = range.start,
                end = range.end,
                label = if (wholeDayOff) stringResource(R.string.week_off_duty) else null,
            )
        }
        state.blocksFor(operatorId).forEach { block ->
            AgendaUnavailableBand(
                start = block.range.start,
                end = block.range.end,
                label = block.label ?: stringResource(blockReasonLabel(block.reason)),
            )
        }
        // In ordine di orario: se una card corta sborda di qualche dp finisce
        // sotto quella dopo, non sopra il suo testo.
        state.appointmentsFor(operatorId).sortedBy { it.time }.forEach { appointment ->
            val top = minutesFromStart(appointment.time)
            val dragging = drag?.appointmentId == appointment.id
            val dragOffset = if (dragging) drag.offset else Offset.Zero
            // Nero solo mentre si trascina; per il resto olive attivo, stone
            // completato, spento con il filo se il cliente non si è presentato.
            val completed = appointment.status == com.devora.mencare.core.model.AppointmentStatus.COMPLETED
            val noShow = appointment.status == com.devora.mencare.core.model.AppointmentStatus.NO_SHOW
            val background = when {
                dragging -> Ink
                noShow -> Bone
                completed -> Stone
                else -> OliveWood
            }
            val foreground = when {
                noShow -> TextMuted
                completed -> Ink
                else -> Bone
            }
            // La durata detta l'altezza, ma una card corta si allarga nel tempo
            // libero che ha davanti: anche dieci minuti restano leggibili per intero.
            val cardHeight = AgendaGrid.cardHeight(
                appointment.durationMinutes,
                state.freeMinutesAfter(operatorId, appointment),
            )
            val serviceLines = AgendaGrid.serviceLines(cardHeight)
            Column(
                verticalArrangement = if (serviceLines == 0) Arrangement.Center else Arrangement.Top,
                modifier = Modifier
                    .offset(y = HourHeight * (top / 60f))
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationX = dragOffset.x
                        translationY = dragOffset.y
                        alpha = if (dragging) 0.9f else 1f
                    }
                    .padding(horizontal = 3.dp, vertical = 2.dp)
                    .fillMaxWidth()
                    .heightIn(min = cardHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(background)
                    .border(1.dp, if (noShow) StoneBorder else Color.Transparent, RoundedCornerShape(10.dp))
                    .pointerInput(appointment.id, appointment.isActive) {
                        // Un appuntamento concluso non si sposta più.
                        if (appointment.isActive) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart(appointment) },
                                onDrag = { _, amount -> onDragAmount(amount) },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragCancel,
                            )
                        }
                    }
                    .clickable { onTap(appointment.id) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                val client = state.clients[appointment.clientId]
                Text(
                    listOfNotNull(client?.firstName?.first()?.plus("."), client?.lastName).joinToString(" "),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        letterSpacing = 0.sp,
                    ),
                    color = foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (serviceLines > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (noShow) {
                            stringResource(R.string.week_status_no_show)
                        } else {
                            appointment.serviceIds.mapNotNull { state.services[it]?.name }.joinToString(" + ")
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            letterSpacing = 0.02.em,
                        ),
                        color = foreground,
                        maxLines = serviceLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun minutesFromStart(time: LocalTime): Int = AgendaGrid.minutesFromStart(time)

/**
 * Minuti liberi fra la fine di [appointment] e il prossimo impegno della
 * colonna: e' lo spazio che una card corta puo' prendersi per restare intera.
 */
private fun WeeklyAgendaUiState.freeMinutesAfter(
    operatorId: String,
    appointment: com.devora.mencare.core.model.Appointment,
): Int {
    val end = minutesFromStart(appointment.time) + appointment.durationMinutes
    val busyStarts = appointmentsFor(operatorId).filter { it.id != appointment.id }
        .map { minutesFromStart(it.time) } +
        blocksFor(operatorId).map { minutesFromStart(it.range.start) } +
        offDutyRanges(operatorId).map { minutesFromStart(it.start) }
    return AgendaGrid.freeMinutesAfter(end, busyStarts)
}

private fun blockReasonLabel(reason: com.devora.mencare.core.model.BlockReason): Int = when (reason) {
    com.devora.mencare.core.model.BlockReason.PERMESSO -> R.string.block_reason_permesso_admin
    com.devora.mencare.core.model.BlockReason.PAUSA -> R.string.block_reason_pausa_admin
    com.devora.mencare.core.model.BlockReason.FERIE -> R.string.block_reason_ferie_admin
    com.devora.mencare.core.model.BlockReason.CORSO -> R.string.block_reason_corso_admin
}
