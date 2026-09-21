package com.devora.mencare.feature.admin.manage

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.BarAction
import com.devora.mencare.core.designsystem.component.BottomActionBar
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.BrandTopBar
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.DurationField
import com.devora.mencare.core.designsystem.component.FilledTextField
import com.devora.mencare.core.designsystem.component.PriceField
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.component.validationMessageOrNull
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.ErrorRed
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveLight
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Radii
import com.devora.mencare.core.designsystem.theme.StoneBorder
import com.devora.mencare.feature.admin.R

@Composable
fun ServiceEditScreen(
    onBack: () -> Unit,
    viewModel: ServiceEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Column(modifier = Modifier.fillMaxSize().background(Bone)) {
        DarkHeader(
            contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 0.dp, bottom = 8.dp),
        ) {
            BrandTopBar(
                title = stringResource(
                    if (state.isNew) R.string.svc_new_title else R.string.svc_edit_title,
                ),
                onBack = onBack,
                backLabel = stringResource(R.string.manage_tab_services),
                trailing = { BarAction(stringResource(R.string.svc_save_short), viewModel::save, color = OliveLight) },
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FilledTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = stringResource(R.string.svc_name),
                error = validationMessageOrNull(state.nameError),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    BrandSectionLabel(stringResource(R.string.svc_duration))
                    Spacer(Modifier.height(8.dp))
                    DurationField(
                        minutes = state.durationMinutes,
                        onMinutesChange = viewModel::setDuration,
                        error = state.durationError,
                    )
                }
                Column(Modifier.weight(1f)) {
                    BrandSectionLabel(stringResource(R.string.svc_price))
                    Spacer(Modifier.height(8.dp))
                    PriceField(
                        value = state.priceEuros,
                        onValueChange = viewModel::setPrice,
                        label = "",
                        error = state.priceError,
                        height = 56.dp,
                    )
                }
            }

            if (state.saveFailed) {
                Text(
                    stringResource(R.string.svc_error_save),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                )
            }

            Column {
                BrandSectionLabel(stringResource(R.string.svc_operators))
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().clip(Radii.Md).background(Bone).border(1.5.dp, StoneBorder, Radii.Md),
                ) {
                    state.operators.forEachIndexed { index, operator ->
                        if (index > 0) HorizontalDivider(color = StoneBorder)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleOperator(operator.id) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(32.dp).clip(CircleShape).background(Bone),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(operator.initials, fontFamily = Cormorant, fontSize = 12.sp, color = Ink)
                            }
                            Text(
                                operator.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Ink,
                                modifier = Modifier.weight(1f).padding(start = 11.dp),
                            )
                            val enabled = operator.id in state.enabledOperatorIds
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (enabled) OliveWood else Bone),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (enabled) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = Bone,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        BottomActionBar(modifier = Modifier.navigationBarsPadding()) {
            AccentButton(
                modifier = Modifier.readableWidth(),
                text = stringResource(
                    if (state.isNew) R.string.svc_save else R.string.svc_save_changes,
                ),
                onClick = viewModel::save,
                loading = state.saving,
                height = 56.dp,
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}
