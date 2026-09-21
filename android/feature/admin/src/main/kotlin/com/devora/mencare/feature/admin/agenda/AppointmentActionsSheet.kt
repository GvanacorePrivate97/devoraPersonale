package com.devora.mencare.feature.admin.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.devora.mencare.core.common.formatDuration
import com.devora.mencare.core.common.formatPrice
import com.devora.mencare.core.common.formatTime
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.ErrorRed
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveLight
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.StoneBorder
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.core.designsystem.util.dialPhone
import com.devora.mencare.core.model.AppointmentStatus
import com.devora.mencare.feature.admin.R

/**
 * Tapping a card in the agenda opens this: the appointment at a glance plus the
 * one destructive action the owner needs. Moving is done by dragging the card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppointmentActionsSheet(
    state: WeeklyAgendaUiState,
    appointmentId: String,
    onEditAppointment: () -> Unit,
    onCancelAppointment: () -> Unit,
    onMarkNoShow: () -> Unit,
    onMarkCompleted: () -> Unit,
    onDismiss: () -> Unit,
) {
    val appointment = state.appointments.firstOrNull { it.id == appointmentId } ?: return
    val client = state.clients[appointment.clientId]
    val operator = state.operators.firstOrNull { it.id == appointment.operatorId }
    // Si apre subito per intero: niente stato a metà con spazio vuoto sotto.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirming by rememberSaveable { mutableStateOf(false) }
    var confirmingNoShow by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Bone,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        listOfNotNull(client?.firstName?.firstOrNull(), client?.lastName?.firstOrNull())
                            .joinToString(""),
                        fontFamily = Cormorant,
                        fontSize = 15.sp,
                        // Oro, non oliva: sul nero l'oliva si ferma a 3.7:1.
                        color = OliveLight,
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        listOfNotNull(client?.firstName, client?.lastName).joinToString(" "),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            "${formatTime(appointment.time)} · ${formatDuration(appointment.durationMinutes)}",
                            operator?.name?.let { stringResource(R.string.week_with_operator, it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextMuted,
                    )
                }
                Text(
                    formatPrice(appointment.totalPriceCents),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(statusLabel(appointment.status)).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.12.em),
                color = if (appointment.status == AppointmentStatus.NO_SHOW) ErrorRed else OliveWood,
            )
            Spacer(Modifier.height(12.dp))
            BrandSectionLabel(stringResource(R.string.manual_services))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                appointment.serviceIds.mapNotNull { state.services[it] }.forEach { service ->
                    Text(
                        "${service.name} · ${service.durationMinutes}'",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, letterSpacing = 0.02.em),
                        color = Bone,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Ink)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.week_move_hint),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = TextMuted,
            )

            // "Chiama" sempre, in oro; un appuntamento concluso invece non si
            // modifica né si annulla.
            val context = LocalContext.current
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .then(if (appointment.isActive) Modifier.width(124.dp) else Modifier.weight(1f))
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(OliveWood)
                        .clickable(enabled = client?.phone?.isNotBlank() == true) { context.dialPhone(client?.phone) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Call,
                        contentDescription = null,
                        tint = Bone,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.week_call),
                        style = MaterialTheme.typography.titleMedium,
                        color = Bone,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (appointment.isActive) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Ink)
                            .clickable(onClick = onEditAppointment),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = Bone,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            stringResource(R.string.week_edit),
                            style = MaterialTheme.typography.titleMedium,
                            color = Bone,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
            }
            // Stato a mano: il no-show (da orario d'inizio passato) e la sua correzione.
            if (appointment.canMarkNoShow()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (confirmingNoShow) Ink else Bone)
                        .border(1.dp, StoneBorder, RoundedCornerShape(16.dp))
                        .clickable {
                            if (confirmingNoShow) {
                                confirmingNoShow = false
                                onMarkNoShow()
                            } else {
                                confirmingNoShow = true
                            }
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.EventBusy,
                        contentDescription = null,
                        tint = if (confirmingNoShow) Bone else Ink,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(
                            if (confirmingNoShow) R.string.week_no_show_confirm else R.string.week_mark_no_show,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (confirmingNoShow) Bone else Ink,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
            if (appointment.canRevertNoShow) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(OliveWood)
                        .clickable(onClick = onMarkCompleted),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Bone,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.week_mark_completed),
                        style = MaterialTheme.typography.titleMedium,
                        color = Bone,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
            if (appointment.isActive) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (confirming) ErrorRed else Stone)
                        .clickable {
                            if (confirming) onCancelAppointment() else confirming = true
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = if (confirming) Bone else ErrorRed,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(
                            if (confirming) R.string.week_cancel_confirm else R.string.week_cancel,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (confirming) Bone else ErrorRed,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }
    }
}

private fun statusLabel(status: AppointmentStatus): Int = when (status) {
    AppointmentStatus.CONFIRMED -> R.string.week_status_confirmed
    AppointmentStatus.IN_PROGRESS -> R.string.week_status_in_progress
    AppointmentStatus.COMPLETED -> R.string.week_status_completed
    AppointmentStatus.NO_SHOW -> R.string.week_status_no_show
    AppointmentStatus.CANCELLED -> R.string.week_status_cancelled
}
