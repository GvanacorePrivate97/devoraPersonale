package com.devora.mencare.feature.client.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devora.mencare.core.common.formatDateLong
import com.devora.mencare.core.common.formatDuration
import com.devora.mencare.core.common.formatPrice
import com.devora.mencare.core.common.formatTime
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.Radii
import com.devora.mencare.core.designsystem.theme.OliveLight
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.feature.client.R

@Composable
internal fun ConfirmationScreen(
    state: BookingUiState,
    onDone: () -> Unit,
    viewModel: BookingViewModel,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // At least as tall as the viewport, so short content sits
                // centered instead of stuck to the top; taller content scrolls.
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ConfirmationContent(state, onDone, viewModel)
        }
    }
}

@Composable
private fun ConfirmationContent(
    state: BookingUiState,
    onDone: () -> Unit,
    viewModel: BookingViewModel,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier.size(96.dp).clip(CircleShape).background(OliveWood),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = Bone,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.confirm_title),
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 34.sp, lineHeight = 38.sp),
            color = Bone,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(
                R.string.confirm_when,
                state.selectedDate?.let { formatDateLong(it) }.orEmpty(),
                state.selectedSlot?.let { formatTime(it) }.orEmpty(),
            ),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 23.sp),
            color = Bone,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Bone.copy(alpha = 0.06f))
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            // Con "Qualsiasi operatore" la prenotazione ne ha già assegnato uno: si mostra quello.
            DarkRecapRow(
                stringResource(R.string.wizard_summary_operator),
                state.confirmed
                    ?.let { apt -> state.operatorOptions.firstOrNull { it.operator.id == apt.operatorId } }
                    ?.operator?.name
                    ?: state.selectedOperator?.name
                    ?: stringResource(R.string.wizard_any_operator),
            )
            HorizontalDivider(color = Bone.copy(alpha = 0.1f))
            DarkRecapRow(
                stringResource(R.string.wizard_summary_services),
                state.selectedServices.joinToString(" + ") { it.name },
            )
            HorizontalDivider(color = Bone.copy(alpha = 0.1f))
            DarkRecapRow(
                stringResource(R.string.wizard_summary_duration),
                formatDuration(state.totalDurationMinutes),
            )
            HorizontalDivider(color = Bone.copy(alpha = 0.1f))
            DarkRecapRow(
                stringResource(R.string.wizard_summary_total),
                formatPrice(state.totalPriceCents),
            )
            // Le note compaiono solo se il cliente le ha scritte.
            if (state.note.isNotBlank()) {
                HorizontalDivider(color = Bone.copy(alpha = 0.1f))
                DarkRecapRow(stringResource(R.string.confirm_note), state.note.trim())
            }
        }

        Spacer(Modifier.height(28.dp))
        // Oro con testo scuro: la schermata è tutta nera, l'oliva ci sparisce.
        AccentButton(
            text = stringResource(R.string.client_confirm_add_calendar),
            onClick = {},
            height = 56.dp,
            shape = Radii.Md,
            leadingIcon = Icons.Outlined.CalendarMonth,
            container = OliveLight,
            contentColor = Ink,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Bone.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .clickable {
                    viewModel.reset()
                    onDone()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.confirm_back_home),
                style = MaterialTheme.typography.titleMedium,
                color = Bone,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DarkRecapRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Bone.copy(alpha = 0.65f),
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = Bone,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
