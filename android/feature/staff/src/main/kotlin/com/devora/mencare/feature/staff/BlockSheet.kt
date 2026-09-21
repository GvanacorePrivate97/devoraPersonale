package com.devora.mencare.feature.staff

import androidx.compose.foundation.background
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
import com.devora.mencare.core.designsystem.component.BrandDatePickerDialog
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.BrandTimePickerDialog
import com.devora.mencare.core.designsystem.component.PickerTile
import com.devora.mencare.core.designsystem.component.WarningCard
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.Radii
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.core.model.BlockReason

private enum class Editing { DATE, FROM, TO }

/**
 * Ferie e permessi dell'operatore: la stessa tendina del titolare, senza la
 * scelta dell'operatore (è chi ha fatto l'accesso) e con i conflitti che si
 * risolvono qui.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlockSheet(
    onDismiss: () -> Unit,
    viewModel: BlockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editing by rememberSaveable { mutableStateOf<Editing?>(null) }

    // Il ViewModel vive con l'agenda: ogni apertura riparte da capo, come su iOS.
    val close = {
        viewModel.reset()
        onDismiss()
    }

    LaunchedEffect(state.saved) {
        if (state.saved) close()
    }

    ModalBottomSheet(
        onDismissRequest = close,
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
                    BarAction(stringResource(R.string.block_cancel), close, color = Ink)
                }
                Text(
                    stringResource(R.string.block_title),
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
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                BrandSectionLabel(stringResource(R.string.block_reason))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BlockReason.entries.forEach { reason ->
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
                        label = stringResource(R.string.block_date),
                        value = formatDateShort(state.date),
                        onClick = { editing = Editing.DATE },
                        modifier = Modifier.weight(1f),
                    )
                    PickerTile(
                        label = stringResource(R.string.block_from),
                        value = formatTime(state.from),
                        onClick = { editing = Editing.FROM },
                        modifier = Modifier.weight(1f),
                    )
                    PickerTile(
                        label = stringResource(R.string.block_to),
                        value = formatTime(state.to),
                        onClick = { editing = Editing.TO },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    BrandChip(
                        text = stringResource(R.string.block_half_day),
                        selected = false,
                        onClick = viewModel::halfDay,
                        modifier = Modifier.weight(1f),
                        fill = true,
                    )
                    BrandChip(
                        text = stringResource(R.string.block_full_day),
                        selected = false,
                        onClick = viewModel::fullDay,
                        modifier = Modifier.weight(1f),
                        fill = true,
                    )
                }

                if (state.unresolvedConflicts.isNotEmpty()) {
                    ConflictsCard(state, viewModel)
                }
            }

            // Fuori dallo scroll: il bottone resta sempre a vista in fondo.
            AccentButton(
                text = stringResource(
                    R.string.block_cta,
                    formatTime(state.from),
                    formatTime(state.to),
                ),
                onClick = viewModel::save,
                enabled = state.canSave,
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

@Composable
private fun ConflictsCard(state: BlockUiState, viewModel: BlockViewModel) {
    val conflicts = state.unresolvedConflicts
    WarningCard(title = stringResource(R.string.block_conflicts_count, conflicts.size)) {
        Column {
            conflicts.forEach { conflict ->
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        R.string.block_conflict_desc,
                        formatTime(conflict.time),
                        formatDurationLong(conflict.durationMinutes),
                        formatDateShort(conflict.date),
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    color = TextMuted,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    val colleague = state.colleagues.firstOrNull()
                    if (colleague != null) {
                        Text(
                            stringResource(R.string.block_reassign, colleague.name.substringBefore(' ')),
                            style = MaterialTheme.typography.titleSmall,
                            color = Bone,
                            modifier = Modifier
                                .clip(Radii.Sm)
                                .background(Ink)
                                .clickable { viewModel.reassign(conflict.id, colleague.id) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.block_propose),
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                        modifier = Modifier
                            .clip(Radii.Sm)
                            .background(Bone)
                            .clickable { viewModel.proposeNewTime(conflict.id) }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    )
                }
            }
        }
    }
}

private fun reasonLabel(reason: BlockReason): Int = when (reason) {
    BlockReason.PERMESSO -> R.string.block_reason_permesso
    BlockReason.PAUSA -> R.string.block_reason_pausa
    BlockReason.FERIE -> R.string.block_reason_ferie
    BlockReason.CORSO -> R.string.block_reason_corso
}
