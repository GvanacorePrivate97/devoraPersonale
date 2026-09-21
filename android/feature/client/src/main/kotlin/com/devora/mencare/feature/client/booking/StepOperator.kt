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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
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
import com.devora.mencare.core.designsystem.component.BottomBarReveal
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.DarkContinueBar
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveTint
import com.devora.mencare.core.designsystem.theme.OliveLight
import com.devora.mencare.core.designsystem.theme.StoneBorder
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.core.designsystem.theme.Radii
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.feature.client.R

@Composable
internal fun StepOperator(state: BookingUiState, viewModel: BookingViewModel) {
    Column(Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f).readableWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 20.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        stringResource(R.string.wizard_operator_title),
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                        color = Ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.wizard_step_counter, 1, 4),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Ink,
                    )
                }
            }
            item { AnyOperatorCard(state.anyOperator) { viewModel.selectOperator(null) } }
            item {
                BrandSectionLabel(
                    stringResource(R.string.wizard_team_count, state.operatorOptions.size),
                )
            }
            items(state.operatorOptions, key = { it.operator.id }) { option ->
                OperatorRow(
                    option = option,
                    selected = state.selectedOperatorId == option.operator.id,
                    onClick = { viewModel.selectOperator(option.operator.id) },
                )
            }
        }
        BottomBarReveal(visible = state.operatorChosen) {
            DarkContinueBar(
                label = stringResource(R.string.wizard_continue_services),
                onClick = viewModel::continueFromOperator,
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun AnyOperatorCard(selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radii.Md)
            .background(if (selected) OliveTint else Bone)
            .border(1.5.dp, if (selected) OliveWood else StoneBorder, Radii.Md)
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Ink),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = null,
                tint = OliveLight,
                modifier = Modifier.size(21.dp),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(
                stringResource(R.string.wizard_any_operator),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                color = Ink,
            )
            Text(
                stringResource(R.string.wizard_any_operator_hint),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                color = TextMuted,
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = OliveWood,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun OperatorRow(option: OperatorOption, selected: Boolean, onClick: () -> Unit) {
    val operator = option.operator
    val enabled = option.availableSoon
    val contentAlpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radii.Md)
            .background(if (selected) OliveTint else Bone)
            .border(1.5.dp, if (selected) OliveWood else StoneBorder, Radii.Md)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HatchedAvatar(operator.initials, selected)
        // Solo nome e mansione: niente tag di specialità né prossimo orario.
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                operator.name,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = Ink.copy(alpha = contentAlpha),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                operator.title,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = TextMuted.copy(alpha = contentAlpha),
            )
        }
    }
}

/** Tondo scuro con l'iniziale in oro: lo stesso avatar della scheda cliente. */
@Composable
private fun HatchedAvatar(initials: String, onAccent: Boolean) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Ink),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            fontFamily = Cormorant,
            fontSize = 16.sp,
            color = OliveLight,
        )
    }
}
