package com.devora.mencare.feature.client.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devora.mencare.core.common.formatDuration
import com.devora.mencare.core.common.formatPrice
import com.devora.mencare.core.common.formatPriceCompact
import com.devora.mencare.core.designsystem.component.BottomBarReveal
import com.devora.mencare.core.designsystem.component.DarkTotalBar
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.model.Service
import com.devora.mencare.feature.client.R

@Composable
internal fun StepServices(state: BookingUiState, viewModel: BookingViewModel) {
    val eligible = state.eligibleServices
    Column(Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f).readableWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.wizard_services_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 27.sp),
                    color = Ink,
                )
            }
            items(eligible.size, key = { eligible[it].id }) { index ->
                val service = eligible[index]
                ServiceRow(
                    service = service,
                    selected = service.id in state.selectedServiceIds,
                    onClick = { viewModel.toggleService(service.id) },
                )
            }
        }
        BottomBarReveal(visible = state.selectedServiceIds.isNotEmpty()) {
            DarkTotalBar(
                caption = if (state.selectedServiceIds.size == 1) {
                    stringResource(R.string.wizard_cart_caption_one, formatDuration(state.totalDurationMinutes))
                } else {
                    stringResource(
                        R.string.wizard_cart_caption,
                        state.selectedServiceIds.size,
                        formatDuration(state.totalDurationMinutes),
                    )
                },
                value = formatPrice(state.totalPriceCents),
                ctaLabel = stringResource(R.string.wizard_continue),
                onClick = viewModel::continueFromServices,
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun ServiceRow(service: Service, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) OliveWood else Stone)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Bone),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = OliveWood,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                service.name,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = if (selected) Bone else Ink,
            )
            Text(
                formatDuration(service.durationMinutes),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = if (selected) Bone else Ink,
            )
        }
        Text(
            formatPriceCompact(service.priceCents),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            color = if (selected) Bone else Ink,
        )
    }
}
