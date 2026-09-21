package com.devora.mencare.feature.staff

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.common.formatDateShort
import com.devora.mencare.core.common.formatDuration
import com.devora.mencare.core.common.formatTime
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.BarAction
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.BrandTopBar
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.StatTile
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.ErrorRed
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveLight
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.util.dialPhone
import com.devora.mencare.core.model.AppointmentStatus

@Composable
fun AppointmentDetailScreen(
    onBack: () -> Unit,
    viewModel: AppointmentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val apt = state.appointment ?: return
    val client = state.client
    val completed = apt.status == AppointmentStatus.COMPLETED
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    var confirmNoShow by rememberSaveable { mutableStateOf(false) }
    var editOpen by rememberSaveable { mutableStateOf(false) }
    var edited by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // Salvata la modifica, questo appuntamento è stato sostituito (e annullato):
    // si torna all'agenda, dove c'è quello nuovo.
    LaunchedEffect(apt.status, edited) {
        if (edited && apt.status == AppointmentStatus.CANCELLED) onBack()
    }

    Column(modifier = Modifier.fillMaxSize().background(Bone)) {
        DarkHeader(
            contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 0.dp, bottom = 18.dp),
        ) {
            BrandTopBar(
                title = stringResource(R.string.apt_detail_title_short),
                onBack = onBack,
                backLabel = stringResource(R.string.staff_tab_agenda),
                // Si modifica solo ciò che è ancora in programma.
                trailing = {
                    if (apt.isActive) {
                        BarAction(
                            stringResource(R.string.apt_detail_edit),
                            {
                                edited = true
                                editOpen = true
                            },
                            color = OliveWood,
                        )
                    }
                },
            )
            Row(
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Iniziali oro su nero, come nelle schede del titolare; il filo
                // chiaro stacca il riquadro dalla banda scura.
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Ink)
                        .border(1.dp, Bone.copy(alpha = 0.14f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        listOfNotNull(client?.firstName?.firstOrNull(), client?.lastName?.firstOrNull())
                            .joinToString(""),
                        fontFamily = Cormorant,
                        fontSize = 17.sp,
                        // Oro, non oliva: sul nero l'oliva si ferma a 3.7:1.
                        color = OliveLight,
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        listOfNotNull(client?.firstName, client?.lastName).joinToString(" "),
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                        color = Bone,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        HeaderPill(
                            text = stringResource(
                                when (apt.status) {
                                    AppointmentStatus.IN_PROGRESS -> R.string.staff_in_progress
                                    AppointmentStatus.COMPLETED -> R.string.apt_detail_completed
                                    AppointmentStatus.NO_SHOW -> R.string.apt_detail_no_show
                                    else -> R.string.apts_status_confirmed_staff
                                },
                            ),
                            accent = true,
                        )
                        HeaderPill(
                            text = stringResource(R.string.apt_detail_visits, client?.visitCount ?: 0),
                            accent = false,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = stringResource(R.string.apt_detail_time),
                    value = "${formatTime(apt.time)} — ${formatTime(apt.end.toLocalTime())}",
                    modifier = Modifier.weight(1f),
                )
                // La durata al posto del totale: le cifre le vede solo il titolare.
                StatTile(
                    label = stringResource(R.string.apt_detail_duration),
                    value = formatDuration(apt.durationMinutes),
                    modifier = Modifier.weight(1f),
                )
            }

            Column {
                BrandSectionLabel(stringResource(R.string.apt_detail_services))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    apt.serviceIds.mapNotNull { state.services[it] }.forEach { service ->
                        Text(
                            "${service.name} · ${service.durationMinutes}'",
                            style = MaterialTheme.typography.titleSmall,
                            color = Bone,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Ink)
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                        )
                    }
                }
            }

            // La nota scritta dal cliente nel riepilogo della prenotazione.
            apt.noteForOperator?.takeIf { it.isNotBlank() }?.let { note ->
                Column {
                    BrandSectionLabel(stringResource(R.string.apt_detail_client_note))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Stone)
                            .padding(horizontal = 15.dp, vertical = 14.dp),
                    )
                }
            }

            if (state.history.isNotEmpty()) {
                Column {
                    BrandSectionLabel(stringResource(R.string.apt_detail_history))
                    Spacer(Modifier.height(8.dp))
                    state.history.forEach { past ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Stone)
                                .padding(horizontal = 15.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${formatDateShort(past.date)} · " +
                                    past.serviceIds.mapNotNull { state.services[it]?.name }.joinToString(" + "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        // Stato a mano: il no-show, da orario d'inizio passato.
        if (apt.canMarkNoShow()) {
            Text(
                stringResource(R.string.apt_detail_mark_no_show),
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { confirmNoShow = true }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (apt.isActive) {
            Text(
                stringResource(R.string.apt_detail_cancel),
                style = MaterialTheme.typography.titleSmall,
                color = ErrorRed,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { confirmCancel = true }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .readableWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Stone)
                    .clickable(enabled = client?.phone?.isNotBlank() == true) { context.dialPhone(client?.phone) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.apt_detail_call),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
            }
            // Lo stato si chiude da solo a fine servizio (§6.2): il pulsante
            // resta solo dove serve davvero, cioè per rimettere a posto un
            // no-show segnato per sbaglio.
            if (apt.canRevertNoShow) {
                AccentButton(
                    text = stringResource(R.string.apt_detail_complete),
                    onClick = viewModel::markCompleted,
                    height = 54.dp,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1.2f),
                )
            }
        }
    }

    if (confirmNoShow) {
        AlertDialog(
            onDismissRequest = { confirmNoShow = false },
            containerColor = Bone,
            title = { Text(stringResource(R.string.apt_detail_no_show_title)) },
            text = { Text(stringResource(R.string.apt_detail_no_show_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmNoShow = false
                    viewModel.markNoShow()
                }) {
                    Text(stringResource(R.string.apt_detail_no_show_yes), color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmNoShow = false }) {
                    Text(stringResource(R.string.apt_detail_cancel_no), color = Ink)
                }
            },
        )
    }

    if (editOpen) {
        StaffBookingScreen(
            onDismiss = { editOpen = false },
            editAppointment = apt,
            editClient = client,
            sessionKey = "staff-edit-${apt.id}",
        )
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            containerColor = Bone,
            title = { Text(stringResource(R.string.apt_detail_cancel_title)) },
            text = { Text(stringResource(R.string.apt_detail_cancel_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    // L'annullo torna indietro da sé: non deve scattare anche il ritorno della modifica.
                    edited = false
                    viewModel.cancel(onBack)
                }) {
                    Text(stringResource(R.string.apt_detail_cancel_yes), color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) {
                    Text(stringResource(R.string.apt_detail_cancel_no), color = Ink)
                }
            },
        )
    }
}

@Composable
private fun HeaderPill(text: String, accent: Boolean) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.14.em),
        color = Bone,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (accent) OliveWood else Bone.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
