package com.devora.mencare.feature.admin.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.common.formatTime
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.BrandSwitch
import com.devora.mencare.core.designsystem.component.BrandTimePickerDialog
import com.devora.mencare.core.designsystem.component.PickerTile
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.ErrorRed
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.Radii
import com.devora.mencare.core.designsystem.theme.StoneBorder
import com.devora.mencare.core.designsystem.theme.StoneSoft
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.feature.admin.R
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * "Orari" tab: the salon's own opening hours, which bound every operator's
 * agenda.
 */
@Composable
internal fun BusinessSettingsTab(viewModel: BusinessSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // day to edit, and whether the opening or the closing time was tapped
    var editing by remember { mutableStateOf<Pair<DayOfWeek, Boolean>?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(Bone)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BrandSectionLabel(stringResource(R.string.business_hours))
            Text(
                stringResource(R.string.business_hours_hint),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = TextMuted,
            )
            DayOfWeek.entries.forEach { day ->
                val range = state.hours[day]
                // Il giorno chiuso si spegne: fondo tenue invece di bianco,
                // come nel mockup. Prima aperto e chiuso avevano la stessa card
                // e cambiava solo la parolina a destra.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(Radii.Md)
                        .background(if (range == null) StoneSoft else Bone)
                        .border(1.5.dp, StoneBorder, Radii.Md)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            day.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            color = Ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(
                                if (range == null) R.string.business_closed else R.string.business_open,
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = TextMuted,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                        BrandSwitch(
                            checked = range != null,
                            onCheckedChange = { viewModel.setOpen(day, it) },
                        )
                    }
                    if (range != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            PickerTile(
                                label = stringResource(R.string.business_from),
                                value = formatTime(range.start),
                                onClick = { editing = day to true },
                                modifier = Modifier.weight(1f),
                            )
                            PickerTile(
                                label = stringResource(R.string.business_to),
                                value = formatTime(range.end),
                                onClick = { editing = day to false },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (state.saveFailed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.business_error_save),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                )
            }

            // In tab there is no nav bar to hold the save action.
            if (state.dirty) {
                Spacer(Modifier.height(6.dp))
                AccentButton(
                    text = stringResource(R.string.business_save_hours),
                    onClick = viewModel::save,
                    loading = state.saving,
                    height = 54.dp,
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }
    }

    editing?.let { (day, isStart) ->
        val range = state.hours[day]
        BrandTimePickerDialog(
            initial = (if (isStart) range?.start else range?.end) ?: LocalTime.of(9, 0),
            onDismiss = { editing = null },
            onConfirm = { time ->
                if (isStart) viewModel.setFrom(day, time) else viewModel.setTo(day, time)
                editing = null
            },
        )
    }
}
