package com.devora.mencare.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.data.repository.SocialProvider
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.EmailField
import com.devora.mencare.core.designsystem.component.PasswordField
import com.devora.mencare.core.designsystem.component.SocialButton
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.model.UserRole

@Composable
fun LoginScreen(
    onLoggedIn: (UserRole) -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.loggedInRole) {
        state.loggedInRole?.let { role ->
            viewModel.consumeLogin()
            onLoggedIn(role)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Bone)) {
        DarkHeader(
            contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 18.dp, bottom = 28.dp),
        ) {
            // Il logo completo (monogramma, nome e "Men Care") al centro della banda:
            // porta già il nome del salone, e il resto dell'header lo segue centrato.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(com.devora.mencare.core.designsystem.R.drawable.logo_lockup),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.width(210.dp),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.auth_login_title),
                    style = MaterialTheme.typography.displayMedium,
                    color = Bone,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.auth_login_subtitle),
                    style = MaterialTheme.typography.titleSmall,
                    color = Bone,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp),
        ) {
            EmailField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = stringResource(R.string.auth_email),
                leadingIcon = Icons.Outlined.MailOutline,
                error = state.emailError,
                outlined = true,
            )
            Spacer(Modifier.height(12.dp))
            PasswordField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(R.string.auth_password),
                error = state.passwordError,
                errorText = when (state.error) {
                    LoginError.CREDENTIALS -> stringResource(R.string.auth_error_credentials)
                    LoginError.GENERIC -> stringResource(R.string.auth_error_generic)
                    LoginError.SOCIAL_UNAVAILABLE -> stringResource(R.string.auth_error_social_unavailable)
                    null -> null
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.auth_forgot_password),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = Ink,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(onClick = onForgotPassword)
                    .padding(vertical = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            AccentButton(
                text = stringResource(R.string.auth_login_cta),
                onClick = viewModel::login,
                loading = state.loading,
                height = 56.dp,
                shape = RoundedCornerShape(16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Stone)
                Text(
                    stringResource(R.string.auth_social_divider).uppercase(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, letterSpacing = 0.14.em),
                    color = Ink,
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Stone)
            }
            SocialButton(
                text = stringResource(R.string.auth_social_google),
                iconRes = com.devora.mencare.core.designsystem.R.drawable.ic_google,
                onClick = { viewModel.loginWithProvider(SocialProvider.GOOGLE) },
            )
            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.auth_no_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                )
                Text(
                    stringResource(R.string.auth_register_link),
                    style = MaterialTheme.typography.titleSmall,
                    color = OliveWood,
                    modifier = Modifier
                        .clickable(onClick = onRegister)
                        .padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            Text(
                stringResource(R.string.auth_demo_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
            )
        }
    }
}
