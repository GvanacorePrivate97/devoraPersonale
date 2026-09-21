package com.devora.mencare.feature.admin.campaign

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.common.formatDateShort
import com.devora.mencare.core.common.formatTime
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.BarAction
import com.devora.mencare.core.designsystem.component.BrandChip
import com.devora.mencare.core.designsystem.component.BrandDatePickerDialog
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.BrandSwitch
import com.devora.mencare.core.designsystem.component.BrandTimePickerDialog
import com.devora.mencare.core.designsystem.component.BrandTopBar
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.FieldMessage
import com.devora.mencare.core.designsystem.component.FilledTextField
import com.devora.mencare.core.designsystem.component.PickerTile
import com.devora.mencare.core.designsystem.component.SegmentedTabs
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.component.validationMessageOrNull
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.ErrorRed
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveLight
import com.devora.mencare.core.designsystem.theme.OliveSoft
import com.devora.mencare.core.designsystem.theme.OliveTint
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Radii
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.StoneBorder
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.core.model.CampaignSegment
import com.devora.mencare.feature.admin.R

@Composable
fun CampaignScreen(
    onBack: () -> Unit,
    viewModel: CampaignViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf<Picking?>(null) }

    LaunchedEffect(state.done) {
        if (state.done) onBack()
    }

    Column(modifier = Modifier.fillMaxSize().background(Bone)) {
        DarkHeader(
            contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 0.dp, bottom = 18.dp),
        ) {
            BrandTopBar(
                title = stringResource(R.string.camp_title),
                onBack = onBack,
                trailing = { BarAction(stringResource(R.string.camp_draft), onBack, color = OliveLight) },
            )
            Row(
                modifier = Modifier.padding(horizontal = 22.dp).padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.name.ifBlank { stringResource(R.string.camp_name) },
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp),
                    color = Bone,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.camp_reach, state.reachable, state.segmentSize),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    color = Bone,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Bone.copy(alpha = 0.12f))
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = stringResource(R.string.camp_name),
                error = validationMessageOrNull(state.nameError),
            )

            BrandSectionLabel(stringResource(R.string.camp_segment))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CampaignSegment.entries.forEach { segment ->
                    BrandChip(
                        text = stringResource(segmentLabel(segment)),
                        selected = state.segment == segment,
                        onClick = { viewModel.setSegment(segment) },
                        modifier = Modifier.weight(1f),
                        fill = true,
                    )
                }
            }

            FilledTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = stringResource(R.string.camp_msg_title),
            )

            Column {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BrandSectionLabel(stringResource(R.string.camp_msg_body), modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.camp_chars, state.body.length),
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
                        .border(1.5.dp, OliveSoft, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                ) {
                    BasicTextField(
                        value = state.body,
                        onValueChange = viewModel::setBody,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink, fontSize = 14.sp),
                        cursorBrush = SolidColor(OliveWood),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TokenChip(stringResource(R.string.camp_token_name)) { viewModel.appendToken("{{nome}}") }
                        TokenChip(stringResource(R.string.camp_token_link)) { viewModel.appendToken("{{link}}") }
                    }
                }
                FieldMessage(validationMessageOrNull(state.bodyError))
            }
            if (state.saveFailed) {
                Text(
                    stringResource(R.string.camp_error_save),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                )
            }

            BrandSectionLabel(stringResource(R.string.camp_preview))
            // L'anteprima è la notifica come la vede il cliente: sul telefono
            // arriva su fondo chiaro, non su una banda scura. L'icona scura è
            // il posto del marchio, come nel mockup.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Radii.Md)
                    .background(Stone)
                    .padding(14.dp),
            ) {
                Box(
                    modifier = Modifier.size(38.dp).clip(Radii.Sm).background(Ink),
                )
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(
                        state.title.ifBlank { stringResource(R.string.camp_msg_title) },
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                    )
                    Text(
                        state.previewBody,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        color = TextMuted,
                    )
                }
                Text(
                    stringResource(R.string.camp_now),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    color = TextMuted,
                )
            }

            BrandSectionLabel(stringResource(R.string.camp_scheduling))
            SegmentedTabs(
                options = listOf(
                    stringResource(R.string.camp_send_now),
                    stringResource(R.string.camp_schedule),
                ),
                selectedIndex = if (state.scheduleLater) 1 else 0,
                onSelect = { viewModel.setScheduleLater(it == 1) },
                onDark = false,
            )
            if (state.scheduleLater) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerTile(
                        label = stringResource(R.string.camp_date),
                        value = formatDateShort(state.scheduledDate),
                        onClick = { picking = Picking.DATE },
                        modifier = Modifier.weight(1f),
                    )
                    PickerTile(
                        label = stringResource(R.string.camp_time),
                        value = formatTime(state.scheduledTime),
                        onClick = { picking = Picking.TIME },
                        modifier = Modifier.weight(1f),
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
                            stringResource(R.string.camp_repeat_weekly),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            color = Ink,
                        )
                        Text(
                            stringResource(R.string.camp_stop_after, state.sendCap),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = TextMuted,
                        )
                    }
                    BrandSwitch(
                        checked = state.repeatWeekly,
                        onCheckedChange = viewModel::setRepeatWeekly,
                    )
                }
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
                text = stringResource(
                    if (state.scheduleLater) R.string.camp_schedule_cta else R.string.camp_send_now,
                ),
                onClick = viewModel::send,
                loading = state.saving,
                height = 56.dp,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = Icons.AutoMirrored.Outlined.Send,
            )
        }
    }

    when (picking) {
        Picking.DATE -> BrandDatePickerDialog(
            initial = state.scheduledDate,
            onDismiss = { picking = null },
            onConfirm = {
                viewModel.setScheduledDate(it)
                picking = null
            },
        )
        Picking.TIME -> BrandTimePickerDialog(
            initial = state.scheduledTime,
            onDismiss = { picking = null },
            onConfirm = {
                viewModel.setScheduledTime(it)
                picking = null
            },
        )
        null -> Unit
    }
}

private enum class Picking { DATE, TIME }

@Composable
private fun TokenChip(text: String, onClick: () -> Unit) {
    // Pill d'accento come le altre: fondo OliveTint pieno, testo oliva.
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
        color = OliveWood,
        modifier = Modifier
            .clip(Radii.Pill)
            .background(OliveTint)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
    )
}

private fun segmentLabel(segment: CampaignSegment): Int = when (segment) {
    CampaignSegment.INATTIVI_60 -> R.string.camp_seg_inactive
    CampaignSegment.TUTTI -> R.string.camp_seg_all
    CampaignSegment.TOP_SPESA -> R.string.camp_seg_top
}
