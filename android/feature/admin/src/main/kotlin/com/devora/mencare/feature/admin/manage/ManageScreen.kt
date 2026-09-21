package com.devora.mencare.feature.admin.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.common.formatDayGroup
import com.devora.mencare.core.common.formatPriceCompact
import com.devora.mencare.core.common.formatTime
import com.devora.mencare.core.designsystem.component.SecondaryButton
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.SegmentedTabs
import com.devora.mencare.core.designsystem.component.StoneKeyValueRow
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Radii
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.StoneBorder
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.core.model.BlockReason
import com.devora.mencare.core.model.Operator
import com.devora.mencare.core.model.Service
import com.devora.mencare.core.model.TimeBlock
import com.devora.mencare.core.model.groupConsecutiveDays
import com.devora.mencare.feature.admin.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ManageScreen(
    onEditService: (String?) -> Unit,
    onNewOperator: () -> Unit,
    viewModel: ManageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val titles = listOf(
        stringResource(R.string.manage_tab_services),
        stringResource(R.string.manage_tab_operators),
        stringResource(R.string.manage_tab_notifications),
        stringResource(R.string.manage_tab_business),
    )

    Column(modifier = Modifier.fillMaxSize().background(Bone)) {
        DarkHeader(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.manage_title).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = TextMuted,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                titles[tab],
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp),
                color = Bone,
            )
            Spacer(Modifier.height(14.dp))
            // Le stesse tab a vetro oro della dashboard e degli appuntamenti:
            // quattro schede dello stesso oggetto vogliono un controllo solo.
            SegmentedTabs(
                options = titles,
                selectedIndex = tab,
                onSelect = { tab = it },
            )
        }

        when (tab) {
            0 -> ServicesTab(state, onEditService)
            1 -> OperatorsTab(state, onNewOperator)
            2 -> NotificationSettingsTab()
            else -> BusinessSettingsTab()
        }
    }
}

@Composable
private fun ServicesTab(state: ManageUiState, onEditService: (String?) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandSectionLabel(stringResource(R.string.manage_services_all, state.services.size))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(1.dp).background(Stone))
            }
            state.services.forEach { service ->
                ServiceRow(service, state.operators) { onEditService(service.id) }
            }
        }
        Column(
            modifier = Modifier.readableWidth().padding(horizontal = 20.dp).padding(bottom = 14.dp),
        ) {
            // Affianca la navigazione, non conclude niente: filo nero, non
            // riempimento d'accento.
            SecondaryButton(
                text = stringResource(R.string.manage_new_service),
                onClick = { onEditService(null) },
                height = 54.dp,
                leadingIcon = Icons.Outlined.Add,
            )
        }
    }
}

@Composable
private fun ServiceRow(service: Service, operators: List<Operator>, onClick: () -> Unit) {
    val eligible = operators.count { service.id in it.serviceIds }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .clip(Radii.Md)
            .background(Bone)
            .border(1.5.dp, StoneBorder, Radii.Md)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                service.name,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.manage_service_meta, service.durationMinutes, eligible),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = TextMuted,
            )
        }
        Text(
            formatPriceCompact(service.priceCents),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            color = Ink,
        )
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.padding(start = 8.dp).size(18.dp),
        )
    }
}

@Composable
private fun OperatorsTab(state: ManageUiState, onNewOperator: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        OperatorList(state, Modifier.weight(1f))
        // Stesso pulsante in fondo della tab Servizi.
        Column(
            modifier = Modifier.readableWidth().padding(horizontal = 20.dp).padding(bottom = 14.dp),
        ) {
            SecondaryButton(
                text = stringResource(R.string.ops_new),
                onClick = onNewOperator,
                height = 54.dp,
                leadingIcon = Icons.Outlined.Add,
            )
        }
    }
}

@Composable
private fun OperatorList(state: ManageUiState, modifier: Modifier = Modifier) {
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .readableWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.operators.forEach { operator ->
            val expanded = expandedId == operator.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (expanded) Bone else Stone)
                    .then(
                        if (expanded) {
                            Modifier.border(1.5.dp, OliveWood, RoundedCornerShape(16.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable { expandedId = if (expanded) null else operator.id }
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(Bone),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(operator.initials, fontFamily = Cormorant, fontSize = 13.sp, color = Ink)
                    }
                    Column(Modifier.weight(1f).padding(start = 11.dp)) {
                        Text(
                            operator.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            color = Ink,
                        )
                        Text(
                            "${operator.title} · ${stringResource(
                                R.string.ops_services_count,
                                operator.serviceIds.size,
                            )}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    when {
                        operator.isOwner -> Text(
                            stringResource(R.string.ops_owner_badge).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Bone,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(OliveWood)
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                        )
                        expanded -> Text(
                            stringResource(R.string.ops_detail),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                            color = OliveWood,
                        )
                        else -> Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (expanded) {
                    Spacer(Modifier.height(14.dp))
                    BrandSectionLabel(stringResource(R.string.ops_hours))
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        operator.weeklyHours.groupConsecutiveDays().forEach { (days, range) ->
                            val closed = range == null
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .then(
                                        if (closed) {
                                            Modifier.border(1.dp, StoneBorder, RoundedCornerShape(10.dp))
                                        } else {
                                            Modifier.background(Stone)
                                        },
                                    ),
                            ) {
                                StoneKeyValueRow(
                                    label = formatDayGroup(days),
                                    value = range?.let { "${formatTime(it.start)} — ${formatTime(it.end)}" }
                                        ?: stringResource(R.string.ops_closed).lowercase(),
                                    valueColor = if (closed) TextMuted else Ink,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    BrandSectionLabel(stringResource(R.string.ops_holidays))
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        blockPeriods(state.blocksByOperator[operator.id].orEmpty()).forEach { period ->
                            Text(
                                periodLabel(period),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = Bone,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Ink)
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                            )
                        }
                        Text(
                            stringResource(R.string.ops_add_holiday),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = TextMuted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Stone)
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    BrandSectionLabel(
                        "${stringResource(R.string.ops_services)} · ${operator.serviceIds.size}",
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.services.filter { it.id in operator.serviceIds }.forEach { service ->
                            Text(
                                service.name,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = Bone,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OliveWood)
                                    .padding(horizontal = 11.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Consecutive days blocked for the same reason read as one period. */
private fun blockPeriods(blocks: List<TimeBlock>): List<Pair<ClosedRange<LocalDate>, BlockReason>> {
    val sorted = blocks.sortedBy { it.date }
    val periods = mutableListOf<Pair<MutableList<LocalDate>, BlockReason>>()
    sorted.forEach { block ->
        val last = periods.lastOrNull()
        if (last != null && last.second == block.reason && last.first.last().plusDays(1) == block.date) {
            last.first.add(block.date)
        } else if (last != null && last.second == block.reason && last.first.last() == block.date) {
            // same day, another slot: nothing to add
        } else {
            periods.add(mutableListOf(block.date) to block.reason)
        }
    }
    return periods.map { (days, reason) -> (days.first()..days.last()) to reason }
}

private fun periodLabel(period: Pair<ClosedRange<LocalDate>, BlockReason>): String {
    val (range, reason) = period
    val label = reason.name.lowercase()
    val day = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)
    return if (range.start == range.endInclusive) {
        "${range.start.format(day)} · $label"
    } else {
        "${range.start.dayOfMonth}–${range.endInclusive.format(day)} · $label"
    }
}
