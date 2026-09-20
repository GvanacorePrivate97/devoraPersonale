package com.devora.mencare.feature.admin.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.common.formatDateShort
import com.devora.mencare.core.common.formatDurationLong
import com.devora.mencare.core.common.formatTime
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.BarAction
import com.devora.mencare.core.designsystem.component.BrandChip
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.BrandDatePickerDialog
import com.devora.mencare.core.designsystem.component.BrandTimePickerDialog
import com.devora.mencare.core.designsystem.component.PickerTile
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.ErrorRed
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.core.model.BlockReason
import com.devora.mencare.feature.admin.R

private enum class Editing { DATE, FROM, TO }

private const val OPERATOR_COLUMNS = 4

/** Owner-side personal block: pick the operator, the day and the window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminBlockSheet(
    onDismiss: () -> Unit,
    viewModel: AdminBlockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editing by rememberSaveable { mutableStateOf<Editing?>(null) }

    LaunchedEffect(state.saved) {
        if (state.saved) onDismiss()
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
                    stringResource(R.string.week_new_block),
                    fontFamily = Cormorant,
                    fontSize = 21.sp,
                    color = Ink,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(72.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 12.dp),
                // Blocchi un po' più distanziati: la tendina resta piena fino al bottone.
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                BrandSectionLabel(stringResource(R.string.manual_operator))
                state.operators.chunked(OPERATOR_COLUMNS).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { operator ->
                            BrandChip(
                                text = operator.name.substringBefore(' '),
                                selected = operator.id == state.selectedOperatorId,
                                onClick = { viewModel.selectOperator(operator.id) },
                                modifier = Modifier.weight(1f),
                                fill = true,
                            )
                        }
                        repeat(OPERATOR_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                BrandSectionLabel(stringResource(R.string.block_reason_label))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BlockReason.values().forEach { reason ->
                        BrandChip(
                            text = stringResource(reasonLabel(reason)),
                            selected = state.reason == reason,
                            onClick = { viewModel.setReason(reason) },
                            modifier = Modifier.weight(1f),
                            fill = true,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    PickerTile(
                        label = stringResource(R.string.manual_date),
                        value = formatDateShort(state.date),
                        onClick = { editing = Editing.DATE },
                        modifier = Modifier.weight(1f),
                    )
                    PickerTile(
                        label = stringResource(R.string.business_from),
                        value = formatTime(state.from),
                        onClick = { editing = Editing.FROM },
                        modifier = Modifier.weight(1f),
                    )
                    PickerTile(
                        label = stringResource(R.string.business_to),
                        value = formatTime(state.to),
                        onClick = { editing = Editing.TO },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    BrandChip(
                        text = stringResource(R.string.block_half_day_label),
                        selected = false,
                        onClick = viewModel::halfDay,
                        modifier = Modifier.weight(1f),
                        fill = true,
                    )
                    BrandChip(
                        text = stringResource(R.string.block_full_day_label),
                        selected = false,
                        onClick = viewModel::fullDay,
                        modifier = Modifier.weight(1f),
                        fill = true,
                    )
                }

                if (state.conflicts.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Bone)
                            .border(1.5.dp, ErrorRed, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                    ) {
                        Text(
                            stringResource(R.string.block_conflicts_admin, state.conflicts.size),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            color = ErrorRed,
                        )
                        Spacer(Modifier.height(6.dp))
                        state.conflicts.forEach { conflict ->
                            Text(
                                "${formatTime(conflict.time)} · ${formatDurationLong(conflict.durationMinutes)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = TextMuted,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.block_conflicts_admin_hint),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                            color = TextMuted,
                        )
                    }
                }
            }

            // Fuori dallo scroll: il bottone resta sempre a vista in fondo.
            AccentButton(
                text = stringResource(
                    R.string.block_cta_admin,
                    formatTime(state.from),
                    formatTime(state.to),
                ),
                onClick = viewModel::save,
                enabled = state.canSave,
                loading = state.saving,
                height = 56.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
            )
        }
    }

    when (editing) {
        Editing.DATE -> BrandDatePickerDialog(
            initial = state.date,
            onDismiss = { editing = null },
            onConfirm = {
                viewModel.setDate(it)
                editing = null
            },
        )
        Editing.FROM -> BrandTimePickerDialog(
            initial = state.from,
            onDismiss = { editing = null },
            onConfirm = {
                viewModel.setFrom(it)
                editing = null
            },
        )
        Editing.TO -> BrandTimePickerDialog(
            initial = state.to,
            onDismiss = { editing = null },
            onConfirm = {
                viewModel.setTo(it)
                editing = null
            },
        )
        null -> Unit
    }
}

private fun reasonLabel(reason: BlockReason): Int = when (reason) {
    BlockReason.PERMESSO -> R.string.block_reason_permesso_admin
    BlockReason.PAUSA -> R.string.block_reason_pausa_admin
    BlockReason.FERIE -> R.string.block_reason_ferie_admin
    BlockReason.CORSO -> R.string.block_reason_corso_admin
}
