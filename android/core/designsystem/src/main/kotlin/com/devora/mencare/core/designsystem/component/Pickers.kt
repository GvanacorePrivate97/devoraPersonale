package com.devora.mencare.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.devora.mencare.core.designsystem.R
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.TextMuted
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Material time picker dressed in brand colours. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandTimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bone,
        shape = RoundedCornerShape(22.dp),
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
                Text(stringResource(R.string.ds_confirm), color = OliveWood)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ds_cancel), color = Ink)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(
                    state = pickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = Stone,
                        selectorColor = OliveWood,
                        containerColor = Bone,
                        periodSelectorSelectedContainerColor = OliveWood,
                        timeSelectorSelectedContainerColor = OliveWood,
                        timeSelectorSelectedContentColor = Bone,
                        timeSelectorUnselectedContainerColor = Stone,
                        timeSelectorUnselectedContentColor = Ink,
                    ),
                )
            }
        },
    )
}

/** Material date picker dressed in brand colours. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bone,
        shape = RoundedCornerShape(22.dp),
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis
                    onConfirm(
                        millis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        } ?: initial,
                    )
                },
            ) {
                Text(stringResource(R.string.ds_confirm), color = OliveWood)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ds_cancel), color = Ink)
            }
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                DatePicker(
                    state = pickerState,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    colors = DatePickerDefaults.colors(
                        containerColor = Bone,
                        selectedDayContainerColor = OliveWood,
                        selectedDayContentColor = Bone,
                        todayContentColor = OliveWood,
                        todayDateBorderColor = OliveWood,
                        dayContentColor = Ink,
                        weekdayContentColor = Ink,
                        navigationContentColor = Ink,
                        yearContentColor = Ink,
                        currentYearContentColor = OliveWood,
                        selectedYearContainerColor = OliveWood,
                        selectedYearContentColor = Bone,
                        dividerColor = Color.Transparent,
                        titleContentColor = Ink,
                        headlineContentColor = Ink,
                    ),
                )
            }
        },
    )
}

/**
 * Label above a tappable value: the date/time tiles used across the editors.
 * Give tiles in a row `Modifier.weight(1f)` so they come out equal.
 */
@Composable
fun PickerTile(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Stone)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.1.em),
            color = TextMuted,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            color = Ink,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
