package com.devora.mencare.feature.staff

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Search
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
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.core.ui.crm.FixedClientRow
import java.time.LocalDate
import java.time.LocalTime

/** Staff self-service booking sheet: same flow as the admin manual booking, but the
 * operator is always "me" — no operator picker, services limited to what this
 * operator performs. Lets staff take a phone/walk-in booking without the titolare.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffBookingScreen(
    onDismiss: () -> Unit,
    date: LocalDate = LocalDate.now(),
    time: LocalTime? = null,
    // "Modifica" dal dettaglio appuntamento: modulo precompilato con questo appuntamento.
    editAppointment: com.devora.mencare.core.model.Appointment? = null,
    editClient: com.devora.mencare.core.model.ClientRecord? = null,
    // Una chiave per apertura: ogni sheet riparte da un modulo vuoto.
    sessionKey: String = "staff-booking",
    viewModel: StaffBookingViewModel = hiltViewModel(key = sessionKey),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        if (editAppointment != null) {
            viewModel.applyEdit(editAppointment, editClient)
        } else {
            viewModel.applyPreset(date, time)
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
                    BarAction(stringResource(R.string.staff_booking_cancel), onDismiss, color = Ink)
                }
                Text(
                    stringResource(
                        if (state.editingId != null) R.string.staff_booking_title_edit else R.string.staff_booking_title,
                    ),
                    fontFamily = Cormorant,
                    fontSize = 21.sp,
                    color = Ink,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterEnd) {
                    BarAction(stringResource(R.string.staff_booking_save), viewModel::save, color = OliveLight)
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
                BrandSectionLabel(stringResource(R.string.staff_booking_client))
                // In modifica il cliente è quello dell'appuntamento: si mostra e basta.
                if (state.editingId != null) {
                    FixedClientRow(state.selectedClient)
                } else {
                FilledTextField(
                    value = state.query,
                    onValueChange = viewModel::search,
                    label = "",
                    placeholder = stringResource(R.string.staff_booking_search_hint),
                    leadingIcon = Icons.Outlined.Search,
                )
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Stone),
                ) {
                    state.results.take(3).forEach { client ->
                        val selected = state.selectedClient?.id == client.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selected) OliveWood.copy(alpha = 0.18f) else Stone)
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
                                    "${client.phone} · ${
                                        stringResource(R.string.staff_booking_visits, client.visitCount)
                                    }",
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
                            stringResource(R.string.staff_booking_new_client),
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
                            label = stringResource(R.string.staff_booking_new_client_first),
                            error = state.newFirstError,
                            modifier = Modifier.weight(1f),
                        )
                        NameField(
                            value = state.newLast,
                            onValueChange = { viewModel.setNewClientField("last", it) },
                            label = stringResource(R.string.staff_booking_new_client_last),
                            error = state.newLastError,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    PhoneField(
                        value = state.newPhone,
                        onValueChange = { viewModel.setNewClientField("phone", it) },
                        label = stringResource(R.string.staff_booking_new_client_phone),
                        error = state.newPhoneError,
                    )
                    if (state.newClientFailed) {
                        Text(
                            stringResource(R.string.staff_booking_error_new_client),
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                        )
                    }
                    AccentButton(
                        text = stringResource(R.string.staff_booking_new_client),
                        onClick = viewModel::createClient,
                        loading = state.creatingClientBusy,
                        height = 48.dp,
                        shape = RoundedCornerShape(16.dp),
                    )
                }

                Column {
                    BrandSectionLabel(stringResource(R.string.staff_booking_date))
                    Spacer(Modifier.height(8.dp))
                    DateField(value = formatDateShort(state.date)) {
                        showDatePicker = true
                    }
                }

                BrandSectionLabel(stringResource(R.string.staff_booking_services))
                // Stessa tendina multi-selezione della prenotazione del titolare.
                MultiSelectDropdown(
                    options = state.services.map { DropdownOption(it.id, it.name) },
                    selectedIds = state.selectedServiceIds,
                    onToggle = viewModel::toggleService,
                    placeholder = stringResource(R.string.manual_services_placeholder_staff),
                )

                BrandSectionLabel(
                    stringResource(R.string.staff_booking_slots, formatDuration(state.totalMinutes)),
                )
                SlotChipRow(
                    slots = state.slots,
                    selected = state.selectedSlot,
                    onSelect = viewModel::selectSlot,
                    label = ::formatTime,
                    emptyLabel = state.preferredSlot
                        ?.let { stringResource(R.string.staff_booking_slots_empty_preferred, formatTime(it)) }
                        ?: stringResource(R.string.staff_booking_slots_empty),
                )
                val preferred = state.preferredSlot
                if (state.preferredUnavailable && preferred != null) {
                    Text(
                        stringResource(R.string.staff_booking_slot_unavailable, formatTime(preferred)),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextMuted,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Stone)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.staff_booking_sms),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            color = Ink,
                        )
                        Text(
                            stringResource(R.string.staff_booking_sms_hint),
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
                                StaffBookingError.SLOT_TAKEN -> R.string.staff_booking_error_slot_taken
                                StaffBookingError.GENERIC -> R.string.staff_booking_error_save
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }

                AccentButton(
                    text = stringResource(
                        if (state.editingId != null) R.string.staff_booking_cta_edit else R.string.staff_booking_cta,
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

/** Stone field that reads as a picker: value plus a chevron, as in the manual-booking sheet. */
@Composable
private fun DateField(value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Stone)
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
