package com.devora.mencare.feature.auth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.BottomActionBar
import com.devora.mencare.core.designsystem.component.BrandCheckbox
import com.devora.mencare.core.designsystem.component.BrandTopBar
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.EmailField
import com.devora.mencare.core.designsystem.component.NameField
import com.devora.mencare.core.designsystem.component.PasswordField
import com.devora.mencare.core.designsystem.component.PhoneField
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.ErrorRed
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.model.UserRole

private val FieldHeight = 48.dp
private val CtaShape = RoundedCornerShape(16.dp)

@Composable
fun RegisterScreen(
    onRegistered: (UserRole) -> Unit,
    onBack: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.registered) {
        if (state.registered) onRegistered(UserRole.CLIENT)
    }

    Column(modifier = Modifier.fillMaxSize().background(Bone)) {
        DarkHeader(
            contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 0.dp, bottom = 20.dp),
        ) {
            BrandTopBar(
                title = stringResource(R.string.auth_register_topbar),
                onBack = onBack,
            )
            Column(Modifier.padding(start = 26.dp, end = 26.dp, top = 6.dp)) {
                Text(
                    stringResource(R.string.auth_register_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 36.sp),
                    color = Bone,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.auth_register_subtitle),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    color = Bone,
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
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NameField(
                    value = state.firstName,
                    onValueChange = { viewModel.onFieldChange(RegisterField.FIRST_NAME, it) },
                    label = stringResource(R.string.auth_first_name),
                    error = state.fieldErrors[RegisterField.FIRST_NAME],
                    height = FieldHeight,
                    modifier = Modifier.weight(1f),
                )
                NameField(
                    value = state.lastName,
                    onValueChange = { viewModel.onFieldChange(RegisterField.LAST_NAME, it) },
                    label = stringResource(R.string.auth_last_name),
                    error = state.fieldErrors[RegisterField.LAST_NAME],
                    height = FieldHeight,
                    modifier = Modifier.weight(1f),
                )
            }
            PhoneField(
                value = state.phone,
                onValueChange = { viewModel.onFieldChange(RegisterField.PHONE, it) },
                label = stringResource(R.string.auth_phone),
                error = state.fieldErrors[RegisterField.PHONE],
                height = FieldHeight,
                trailing = {
                    ValidMark(state.phone.isNotBlank() && state.fieldErrors[RegisterField.PHONE] == null)
                },
            )
            EmailField(
                value = state.email,
                onValueChange = { viewModel.onFieldChange(RegisterField.EMAIL, it) },
                label = stringResource(R.string.auth_email),
                error = state.fieldErrors[RegisterField.EMAIL],
                errorText = if (state.emailTaken) stringResource(R.string.auth_error_email_taken) else null,
                height = FieldHeight,
                trailing = {
                    ValidMark(state.email.contains('@') && state.fieldErrors[RegisterField.EMAIL] == null)
                },
            )
            PasswordField(
                value = state.password,
                onValueChange = { viewModel.onFieldChange(RegisterField.PASSWORD, it) },
                label = stringResource(R.string.auth_password),
                error = state.fieldErrors[RegisterField.PASSWORD],
                showStrength = true,
                height = FieldHeight,
            )
            Column {
                PasswordField(
                    value = state.passwordConfirm,
                    onValueChange = { viewModel.onFieldChange(RegisterField.PASSWORD_CONFIRM, it) },
                    label = stringResource(R.string.auth_password_confirm),
                    error = state.fieldErrors[RegisterField.PASSWORD_CONFIRM],
                    height = FieldHeight,
                )
                val matches = state.passwordConfirm.isNotEmpty() && state.passwordConfirm == state.password
                if (matches) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ValidMark(true)
                        Text(
                            stringResource(R.string.auth_password_match),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = OliveWood,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                BrandCheckbox(
                    checked = state.termsAccepted,
                    onCheckedChange = viewModel::onTermsChange,
                    modifier = Modifier.padding(top = 1.dp),
                )
                Column(Modifier.padding(start = 11.dp)) {
                    Row {
                        Text(
                            stringResource(R.string.auth_terms),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = Ink,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            " ${stringResource(R.string.auth_terms_link)}",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = OliveWood,
                        )
                    }
                    if (state.termsError) {
                        Text(
                            stringResource(R.string.auth_error_terms),
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            if (state.genericError) {
                Text(
                    stringResource(R.string.auth_error_generic),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                )
            }
        }

        BottomActionBar(modifier = Modifier.navigationBarsPadding()) {
            AccentButton(
                modifier = Modifier.readableWidth(),
                text = stringResource(R.string.auth_register_cta),
                onClick = viewModel::register,
                loading = state.loading,
                height = 56.dp,
                shape = CtaShape,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.auth_have_account),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = Ink,
                )
                Text(
                    stringResource(R.string.auth_login_link),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    color = OliveWood,
                    modifier = Modifier.clickable(onClick = onBack).padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ValidMark(valid: Boolean) {
    if (valid) {
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = OliveWood,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
