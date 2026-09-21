package com.devora.mencare.feature.admin.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.common.formatTime
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.BarAction
import com.devora.mencare.core.designsystem.component.BottomActionBar
import com.devora.mencare.core.designsystem.component.BrandChip
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.BrandTimePickerDialog
import com.devora.mencare.core.designsystem.component.BrandTopBar
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.EmailField
import com.devora.mencare.core.designsystem.component.FilledTextField
import com.devora.mencare.core.designsystem.component.NameField
import com.devora.mencare.core.designsystem.component.PhoneField
import com.devora.mencare.core.designsystem.component.PickerTile
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.ErrorRed
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveLight
import com.devora.mencare.core.designsystem.theme.OnDarkMuted
import com.devora.mencare.feature.admin.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun OperatorEditScreen(
    onBack: () -> Unit,
    viewModel: OperatorEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // true = opening time, false = closing time
    var editingFrom by rememberSaveable { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Column(modifier = Modifier.fillMaxSize().background(Bone)) {
        DarkHeader(
            contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 0.dp, bottom = 8.dp),
        ) {
            BrandTopBar(
                title = stringResource(R.string.ops_new),
                onBack = onBack,
                backLabel = stringResource(R.string.manage_tab_operators),
                trailing = {
                    BarAction(stringResource(R.string.svc_save_short), viewModel::save, color = OliveLight)
                },
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NameField(
                value = state.name,
                onValueChange = { viewModel.onFieldChange(OperatorField.NAME, it) },
                label = stringResource(R.string.ops_field_name),
                error = state.errors[OperatorField.NAME],
            )
            FilledTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = stringResource(R.string.ops_field_role),
                placeholder = stringResource(R.string.ops_field_role_hint),
            )
            EmailField(
                value = state.email,
                onValueChange = { viewModel.onFieldChange(OperatorField.EMAIL, it) },
                label = stringResource(R.string.ops_field_email),
                error = state.errors[OperatorField.EMAIL],
                errorText = if (state.emailTaken) stringResource(R.string.ops_error_email_taken) else null,
            )
            PhoneField(
                value = state.phone,
                onValueChange = { viewModel.onFieldChange(OperatorField.PHONE, it) },
                label = stringResource(R.string.ops_field_phone),
                error = state.errors[OperatorField.PHONE],
            )

            BrandSectionLabel(stringResource(R.string.ops_working_days))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach { day ->
                    BrandChip(
                        text = day.getDisplayName(TextStyle.SHORT, Locale.ITALIAN)
                            .trimEnd('.')
                            .replaceFirstChar { it.uppercase() },
                        selected = day in state.workingDays,
                        onClick = { viewModel.toggleDay(day) },
                        modifier = Modifier.weight(1f),
                        fill = true,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                PickerTile(
                    label = stringResource(R.string.business_from),
                    value = formatTime(state.from),
                    onClick = { editingFrom = true },
                    modifier = Modifier.weight(1f),
                )
                PickerTile(
                    label = stringResource(R.string.business_to),
                    value = formatTime(state.to),
                    onClick = { editingFrom = false },
                    modifier = Modifier.weight(1f),
                )
            }

            BrandSectionLabel(stringResource(R.string.svc_operators_services))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.services.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { service ->
                            BrandChip(
                                text = service.name,
                                selected = service.id in state.selectedServiceIds,
                                onClick = { viewModel.toggleService(service.id) },
                                modifier = Modifier.weight(1f),
                                fill = true,
                                maxLines = 2,
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (state.selectedServiceIds.isEmpty()) {
                Text(
                    stringResource(R.string.ops_error_services),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                )
            }
            if (state.saveFailed) {
                Text(
                    stringResource(R.string.ops_error_save),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ink)
                    .padding(14.dp),
            ) {
                Text(
                    stringResource(R.string.ops_account_note),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    // Su fondo nero il grigio chiaro, non quello da fondo chiaro.
                    color = OnDarkMuted,
                )
            }
        }

        BottomActionBar(modifier = Modifier.navigationBarsPadding()) {
            AccentButton(
                modifier = Modifier.readableWidth(),
                text = stringResource(R.string.ops_create),
                onClick = viewModel::save,
                enabled = state.canSave,
                loading = state.saving,
                height = 56.dp,
                shape = RoundedCornerShape(16.dp),
            )
        }
    }

    val editing = editingFrom
    if (editing != null) {
        BrandTimePickerDialog(
            initial = if (editing) state.from else state.to,
            onDismiss = { editingFrom = null },
            onConfirm = { time ->
                if (editing) viewModel.setFrom(time) else viewModel.setTo(time)
                editingFrom = null
            },
        )
    }
}
