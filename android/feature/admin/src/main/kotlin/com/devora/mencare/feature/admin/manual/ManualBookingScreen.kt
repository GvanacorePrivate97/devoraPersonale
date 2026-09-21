package com.devora.mencare.feature.admin.manual

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.common.formatDateShort
import com.devora.mencare.core.common.formatDuration
import com.devora.mencare.core.common.formatTime
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.BarAction
import com.devora.mencare.core.designsystem.component.BrandDatePickerDialog
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.BrandSwitch
import com.devora.mencare.core.designsystem.component.DropdownOption
import com.devora.mencare.core.designsystem.component.FilledTextField
import com.devora.mencare.core.designsystem.component.MultiSelectDropdown
import com.devora.mencare.core.designsystem.component.NameField
import com.devora.mencare.core.designsystem.component.PhoneField
import com.devora.mencare.core.designsystem.component.SlotChipRow
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.ErrorRed
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveLight
import com.devora.mencare.core.designsystem.theme.OliveTint
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Radii
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.StoneBorder
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.core.ui.crm.FixedClientRow
import com.devora.mencare.feature.admin.R
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualBookingScreen(
    onDismiss: () -> Unit,
    operatorId: String? = null,
    date: LocalDate = LocalDate.now(),
    time: LocalTime? = null,
    // "Modifica" dall'agenda: modulo precompilato con questo appuntamento.
    editAppointment: com.devora.mencare.core.model.Appointment? = null,
    editClient: com.devora.mencare.core.model.ClientRecord? = null,
    // Una chiave per apertura: ogni sheet riparte da un modulo vuoto.
    sessionKey: String = "manual-booking",
    viewModel: ManualBookingViewModel = hiltViewModel(key = sessionKey),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var operatorMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        if (editAppointment != null) {
            viewModel.applyEdit(editAppointment, editClient)
        } else {
            viewModel.applyPreset(operatorId, date, time)
        }
    }

    LaunchedEffect(state.done) {
        if (state.done) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Bone,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterStart) {
                    BarAction(stringResource(R.string.manual_cancel), onDismiss, color = Ink)
                }
                Text(
                    stringResource(
                        if (state.editingId != null) R.string.manual_title_edit else R.string.manual_title_short,
                    ),
                    fontFamily = Cormorant,
                    fontSize = 21.sp,
                    color = Ink,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterEnd) {
                    BarAction(stringResource(R.string.manual_save), viewModel::save, color = OliveLight)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BrandSectionLabel(stringResource(R.string.manual_client))
                // In modifica il cliente è quello dell'appuntamento: si mostra e basta.
                if (state.editingId != null) {
                    FixedClientRow(state.selectedClient)
                } else {
                FilledTextField(
                    value = state.query,
                    onValueChange = viewModel::search,
                    label = "",
                    placeholder = stringResource(R.string.manual_search_hint),
                    leadingIcon = Icons.Outlined.Search,
                )
                Column(
                    modifier = Modifier.fillMaxWidth().clip(Radii.Md).background(Bone).border(1.5.dp, StoneBorder, Radii.Md),
                ) {
                    state.results.take(3).forEach { client ->
                        val selected = state.selectedClient?.id == client.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selected) OliveTint else Bone)
                                .clickable { viewModel.selectClient(client) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(34.dp).clip(CircleShape).background(Bone),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${client.firstName.first()}${client.lastName.first()}",
                                    fontFamily = Cormorant,
                                    fontSize = 12.sp,
                                    color = Ink,
                                )
                            }
                            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                Text(
                                    "${client.firstName} ${client.lastName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${client.phone} · ${stringResource(R.string.manual_visits, client.visitCount)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = viewModel::startCreateClient)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(34.dp).clip(CircleShape).background(Bone),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = null,
                                tint = OliveWood,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            stringResource(R.string.manual_new_client),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            modifier = Modifier.padding(start = 11.dp),
                        )
                    }
                }
                }

                if (state.creatingClient) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NameField(
                            value = state.newFirst,
                            onValueChange = { viewModel.setNewClientField("first", it) },
                            label = stringResource(R.string.manual_new_client_first),
                            error = state.newFirstError,
                            modifier = Modifier.weight(1f),
                        )
                        NameField(
                            value = state.newLast,
                            onValueChange = { viewModel.setNewClientField("last", it) },
                            label = stringResource(R.string.manual_new_client_last),
                            error = state.newLastError,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    PhoneField(
                        value = state.newPhone,
                        onValueChange = { viewModel.setNewClientField("phone", it) },
                        label = stringResource(R.string.manual_new_client_phone),
                        error = state.newPhoneError,
                    )
                    if (state.newClientFailed) {
                        Text(
                            stringResource(R.string.manual_error_new_client),
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                        )
                    }
                    AccentButton(
                        text = stringResource(R.string.manual_new_client),
                        onClick = viewModel::createClient,
                        loading = state.creatingClientBusy,
                        height = 48.dp,
                        shape = RoundedCornerShape(16.dp),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        BrandSectionLabel(stringResource(R.string.manual_operator))
                        Spacer(Modifier.height(8.dp))
                        val current = state.operators.firstOrNull { it.id == state.selectedOperatorId }
                        // Menu a tendina: tutti gli operatori, spunta su quello scelto.
                        Box {
                            DropdownField(
                                value = current?.let { shortOperatorName(it.name) } ?: "—",
                            ) { operatorMenuOpen = true }
                            DropdownMenu(
                                expanded = operatorMenuOpen,
                                onDismissRequest = { operatorMenuOpen = false },
                                containerColor = Bone,
                            ) {
                                state.operators.forEach { operator ->
                                    DropdownMenuItem(
                                        text = { Text(operator.name, color = Ink) },
                                        onClick = {
                                            operatorMenuOpen = false
                                            viewModel.selectOperator(operator.id)
                                        },
                                        trailingIcon = {
                                            if (operator.id == state.selectedOperatorId) {
                                                Icon(Icons.Outlined.Check, contentDescription = null, tint = OliveWood)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        BrandSectionLabel(stringResource(R.string.manual_date))
                        Spacer(Modifier.height(8.dp))
                        DropdownField(value = formatDateShort(state.date)) {
                            showDatePicker = true
                        }
                    }
                }

                BrandSectionLabel(stringResource(R.string.manual_services))
                // Tendina multi-selezione, gemella di quella dell'operatore.
                MultiSelectDropdown(
                    options = state.services.map { DropdownOption(it.id, it.name) },
                    selectedIds = state.selectedServiceIds,
                    onToggle = viewModel::toggleService,
                    placeholder = stringResource(R.string.manual_services_placeholder),
                )

                BrandSectionLabel(
                    stringResource(R.string.manual_slots, formatDuration(state.totalMinutes)),
                )
                // Colonna vuota non vuol dire libera: se l'operatore non è in turno
                // o non esegue i servizi scelti, lo si dice.
                val emptyReason = when {
                    state.selectedOperatorId == null || state.selectedServiceIds.isEmpty() -> null
                    state.operatorIneligible -> stringResource(R.string.manual_slots_ineligible)
                    state.operatorOffDuty -> stringResource(R.string.manual_slots_off_duty)
                    else -> null
                }
                SlotChipRow(
                    slots = state.slots,
                    selected = state.selectedSlot,
                    onSelect = viewModel::selectSlot,
                    label = ::formatTime,
                    emptyLabel = emptyReason
                        ?: state.preferredSlot
                            ?.let { stringResource(R.string.manual_slots_empty_preferred, formatTime(it)) }
                        ?: stringResource(R.string.manual_slots_empty),
                )
                val preferred = state.preferredSlot
                if (state.preferredUnavailable && preferred != null) {
                    Text(
                        stringResource(R.string.manual_slot_unavailable, formatTime(preferred)),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextMuted,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(Radii.Md)
                        .background(Bone)
                        .border(1.5.dp, StoneBorder, Radii.Md)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.manual_sms),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            color = Ink,
                        )
                        Text(
                            stringResource(R.string.manual_sms_hint),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = TextMuted,
                        )
                    }
                    BrandSwitch(checked = state.sendSms, onCheckedChange = viewModel::setSendSms)
                }

                state.bookingError?.let { error ->
                    Text(
                        stringResource(
                            when (error) {
                                ManualBookingError.SLOT_TAKEN -> R.string.manual_error_slot_taken
                                ManualBookingError.GENERIC -> R.string.manual_error_save
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }

                AccentButton(
                    text = stringResource(
                        if (state.editingId != null) R.string.manual_cta_edit else R.string.manual_cta,
                    ),
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    loading = state.saving,
                    height = 56.dp,
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }
    }

    if (showDatePicker) {
        BrandDatePickerDialog(
            initial = state.date,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                viewModel.selectDate(it)
                showDatePicker = false
            },
        )
    }
}

/** Stone field that reads as a picker: value plus a chevron, as in the mockup. */
@Composable
private fun DropdownField(value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(Radii.Md)
            .background(Bone)
            .border(1.5.dp, StoneBorder, Radii.Md)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Outlined.ArrowDropDown,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** "Luca Ferrante" -> "Luca F." */
private fun shortOperatorName(name: String): String {
    val parts = name.split(' ').filter { it.isNotBlank() }
    return if (parts.size > 1) "${parts.first()} ${parts[1].first()}." else name
}
