package com.devora.mencare.feature.client.booking

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.devora.mencare.core.common.NOTE_MAX
import com.devora.mencare.core.common.formatDateLong
import com.devora.mencare.core.common.formatDuration
import com.devora.mencare.core.common.formatPrice
import com.devora.mencare.core.common.formatPriceCompact
import com.devora.mencare.core.common.formatTime
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.ErrorRed
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveLight
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.feature.client.R

@Composable
internal fun StepSummary(state: BookingUiState, viewModel: BookingViewModel) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppointmentRecapCard(state)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BrandSectionLabel(stringResource(R.string.wizard_summary_services))
                state.selectedServices.forEach { service ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Stone)
                            .padding(horizontal = 15.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${service.name} · ${formatDuration(service.durationMinutes)}",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                            color = Ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatPriceCompact(service.priceCents),
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.wizard_summary_total),
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatPrice(state.totalPriceCents),
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                    )
                }
            }

            NoteBlock(state, viewModel)

            val error = state.error
            if (error != null) {
                Text(
                    stringResource(
                        when (error) {
                            BookingError.SLOT_TAKEN -> R.string.wizard_error_slot_taken
                            else -> R.string.wizard_error_generic
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                )
            }
        }
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .readableWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 14.dp),
        ) {
            AccentButton(
                text = stringResource(R.string.wizard_confirm_cta),
                onClick = viewModel::confirm,
                loading = state.submitting,
                height = 56.dp,
                shape = RoundedCornerShape(16.dp),
            )
        }
    }
}

@Composable
private fun AppointmentRecapCard(state: BookingUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Ink)
            .padding(18.dp),
    ) {
        Text(
            state.selectedDate?.let { formatDateLong(it).uppercase() }.orEmpty(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.16.em),
            color = OliveLight,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.selectedSlot?.let { formatTime(it) }.orEmpty(),
                fontFamily = Cormorant,
                fontSize = 38.sp,
                color = Bone,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatDuration(state.totalDurationMinutes),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                color = Bone,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Bone.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Bone.copy(alpha = 0.14f))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(Bone.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.selectedOperator?.initials ?: "·",
                    fontFamily = Cormorant,
                    fontSize = 12.sp,
                    color = Bone,
                )
            }
            Text(
                state.selectedOperator?.name ?: stringResource(R.string.wizard_any_operator),
                style = MaterialTheme.typography.titleSmall,
                color = Bone,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun NoteBlock(state: BookingUiState, viewModel: BookingViewModel) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BrandSectionLabel(stringResource(R.string.wizard_notes_label), modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.wizard_note_counter, state.note.length, NOTE_MAX),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextMuted,
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Bone)
                .border(1.5.dp, OliveWood, RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            BasicTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink, fontSize = 14.sp),
                cursorBrush = SolidColor(OliveWood),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { field ->
                Box {
                    if (state.note.isEmpty()) {
                        Text(
                            stringResource(R.string.wizard_notes_hint),
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                            color = TextMuted,
                        )
                    }
                    field()
                }
            }
        }
    }
}

