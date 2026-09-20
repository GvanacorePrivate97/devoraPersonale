package com.devora.mencare.feature.client.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.BarAction
import com.devora.mencare.core.designsystem.component.PasswordField
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.ErrorRed
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.feature.client.R

/** "Cambia password" sheet: current password + new password (with strength meter) + confirm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    onDismiss: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.done) {
        if (state.done) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Bone,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterStart) {
                    BarAction(stringResource(R.string.change_password_cancel), onDismiss, color = Ink)
                }
                Text(
                    stringResource(R.string.change_password_title),
                    fontFamily = Cormorant,
                    fontSize = 21.sp,
                    color = Ink,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.width(72.dp))
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PasswordField(
                    value = state.currentPassword,
                    onValueChange = { viewModel.onFieldChange(ChangePasswordField.CURRENT, it) },
                    label = stringResource(R.string.change_password_current),
                    error = state.fieldErrors[ChangePasswordField.CURRENT],
                    errorText = if (state.wrongCurrent) {
                        stringResource(R.string.change_password_error_wrong_current)
                    } else {
                        null
                    },
                )
                PasswordField(
                    value = state.newPassword,
                    onValueChange = { viewModel.onFieldChange(ChangePasswordField.NEW, it) },
                    label = stringResource(R.string.change_password_new),
                    error = state.fieldErrors[ChangePasswordField.NEW],
                    showStrength = true,
                )
                PasswordField(
                    value = state.confirmPassword,
                    onValueChange = { viewModel.onFieldChange(ChangePasswordField.CONFIRM, it) },
                    label = stringResource(R.string.change_password_confirm),
                    error = state.fieldErrors[ChangePasswordField.CONFIRM],
                )
                if (state.genericError) {
                    Text(
                        stringResource(R.string.change_password_error_generic),
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }
                Spacer(Modifier.height(4.dp))
                AccentButton(
                    text = stringResource(R.string.change_password_cta),
                    onClick = viewModel::save,
                    loading = state.loading,
                    height = 56.dp,
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }
    }
}
