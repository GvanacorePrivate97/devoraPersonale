package com.devora.mencare.feature.client.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.designsystem.component.BarAction
import com.devora.mencare.core.designsystem.component.BrandTopBar
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.ErrorState
import com.devora.mencare.core.designsystem.component.InlineErrorBanner
import com.devora.mencare.core.designsystem.component.LoadingState
import com.devora.mencare.core.designsystem.component.WizardSteps
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.feature.client.R

@Composable
fun BookingWizardScreen(
    onCancel: () -> Unit,
    onConfirmed: () -> Unit,
    viewModel: BookingViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.step == WizardStep.CONFIRMED) {
        ConfirmationScreen(state = state, onDone = onConfirmed, viewModel = viewModel)
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Bone)) {
        DarkHeader(
            contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 0.dp, bottom = 22.dp),
        ) {
            BrandTopBar(
                title = stringResource(
                    when {
                        state.step == WizardStep.SUMMARY -> R.string.wizard_summary_title
                        state.isEditing -> R.string.wizard_edit_title
                        else -> R.string.wizard_title
                    },
                ),
                onBack = when (state.step) {
                    WizardStep.SERVICES -> { { viewModel.goToStep(WizardStep.OPERATOR) } }
                    WizardStep.DATETIME -> { { viewModel.goToStep(WizardStep.SERVICES) } }
                    WizardStep.SUMMARY -> { { viewModel.goToStep(WizardStep.DATETIME) } }
                    else -> null
                },
                trailing = {
                    if (state.step != WizardStep.SUMMARY) {
                        BarAction(stringResource(R.string.wizard_cancel), onCancel)
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            WizardSteps(
                labels = listOf(
                    stringResource(R.string.wizard_step_operator),
                    stringResource(R.string.wizard_step_services),
                    stringResource(R.string.wizard_step_datetime),
                    stringResource(R.string.wizard_step_summary),
                ),
                currentIndex = state.step.ordinal,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            if (state.step == WizardStep.SERVICES) {
                Spacer(Modifier.height(14.dp))
                ChosenOperatorRow(state, viewModel)
            }
        }

        val failure = state.loadError
        // Senza listino e squadra il primo passo non esiste: si aspetta, e se la
        // lettura è andata male si propone di riprovare invece di mostrare una
        // lista di operatori vuota che sembra un salone senza dipendenti.
        when {
            state.loadingCatalog -> LoadingState(modifier = Modifier.weight(1f))
            state.operatorOptions.isEmpty() && failure != null ->
                ErrorState(error = failure, onRetry = viewModel::retry, modifier = Modifier.weight(1f))
            else -> {
                if (failure != null) {
                    InlineErrorBanner(error = failure, onRetry = viewModel::retry)
                }
                when (state.step) {
                    WizardStep.OPERATOR -> StepOperator(state, viewModel)
                    WizardStep.SERVICES -> StepServices(state, viewModel)
                    WizardStep.DATETIME -> StepDatetime(state, viewModel)
                    WizardStep.SUMMARY -> StepSummary(state, viewModel)
                    WizardStep.CONFIRMED -> Unit
                }
            }
        }
    }
}

/** Recap of step 1 shown while picking services, with a shortcut back. */
@Composable
private fun ChosenOperatorRow(state: BookingUiState, viewModel: BookingViewModel) {
    val operator = state.selectedOperator
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Bone.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                operator?.initials ?: "·",
                fontFamily = Cormorant,
                fontSize = 12.sp,
                color = Bone,
            )
        }
        Text(
            operator?.name ?: stringResource(R.string.wizard_any_operator),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, letterSpacing = 0.sp),
            color = Bone,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        Text(
            stringResource(R.string.wizard_change),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.sp),
            color = OliveWood,
            modifier = Modifier
                .clickable { viewModel.goToStep(WizardStep.OPERATOR) }
                .padding(vertical = 6.dp),
        )
    }
}
