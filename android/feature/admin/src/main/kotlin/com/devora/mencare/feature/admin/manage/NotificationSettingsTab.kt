package com.devora.mencare.feature.admin.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.BrandSwitch
import com.devora.mencare.core.designsystem.component.SegmentedTabs
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Radii
import com.devora.mencare.core.designsystem.theme.StoneBorder
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.feature.admin.R

@Composable
internal fun NotificationSettingsTab(viewModel: NotificationSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .readableWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandSectionLabel(stringResource(R.string.ntf_reminders))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Radii.Md)
                .background(Bone)
                .border(1.5.dp, StoneBorder, Radii.Md)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            settings.reminders.forEachIndexed { index, rule ->
                if (index > 0) HorizontalDivider(color = StoneBorder)
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.ntf_nth_reminder, index + 1),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Ink,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.removeReminder(rule.id) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.ntf_remove_reminder),
                                tint = OliveWood,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val options = reminderOptions(index)
                    SegmentedTabs(
                        options = options.map { hours -> reminderLabel(hours) },
                        selectedIndex = options.indexOf(rule.hoursBefore),
                        onSelect = { viewModel.setReminderHours(rule.id, options[it]) },
                        onDark = false,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .height(48.dp)
                    .clip(Radii.Md)
                    .border(1.5.dp, Ink, Radii.Md)
                    .clickable(onClick = viewModel::addReminder),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = Ink, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.ntf_add_reminder),
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        BrandSectionLabel(stringResource(R.string.ntf_types))
        Column(
            modifier = Modifier.fillMaxWidth().clip(Radii.Md).background(Bone).border(1.5.dp, StoneBorder, Radii.Md),
        ) {
            ToggleRow(
                title = stringResource(R.string.ntf_confirmation),
                hint = stringResource(R.string.ntf_confirmation_hint),
                checked = settings.bookingConfirmation,
            ) { checked -> viewModel.update { it.copy(bookingConfirmation = checked) } }
            HorizontalDivider(color = StoneBorder)
            ToggleRow(
                title = stringResource(R.string.ntf_cancellation),
                hint = null,
                checked = settings.cancellationAlert,
            ) { checked -> viewModel.update { it.copy(cancellationAlert = checked) } }
            HorizontalDivider(color = StoneBorder)
            ToggleRow(
                title = stringResource(R.string.ntf_late),
                hint = stringResource(R.string.ntf_late_hint),
                checked = settings.lateOperatorAlert,
            ) { checked -> viewModel.update { it.copy(lateOperatorAlert = checked) } }
            HorizontalDivider(color = StoneBorder)
            ToggleRow(
                title = stringResource(R.string.ntf_promo_empty),
                hint = stringResource(R.string.ntf_promo_empty_hint),
                checked = settings.emptyDayPromos,
            ) { checked -> viewModel.update { it.copy(emptyDayPromos = checked) } }
        }

        Spacer(Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, OliveWood, RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            Text(
                stringResource(R.string.ntf_preview).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.14.em),
                color = OliveWood,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Ink),
                )
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(
                        stringResource(R.string.ntf_preview_sender),
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                    )
                    Text(
                        stringResource(R.string.ntf_preview_body),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                        color = Ink,
                    )
                }
                Text(
                    stringResource(R.string.ntf_preview_now),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextMuted,
                )
            }
        }
    }
}

@Composable
private fun reminderLabel(hours: Int): String = when {
    hours == 24 -> stringResource(R.string.ntf_one_day_before)
    hours > 24 -> stringResource(R.string.ntf_days_before, hours / 24)
    else -> stringResource(R.string.ntf_hours_before, hours)
}

@Composable
private fun ToggleRow(
    title: String,
    hint: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextMuted,
                )
            }
        }
        BrandSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
