package com.devora.mencare.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.BrandTopBar
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.EmailField
import com.devora.mencare.core.designsystem.component.NavigationRow
import com.devora.mencare.core.designsystem.component.StoneCard
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.ErrorRed
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveLight
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Stone

@Composable
fun RecoverScreen(
    onBack: () -> Unit,
    viewModel: RecoverViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Bone)) {
        DarkHeader(
            contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 0.dp, bottom = 22.dp),
        ) {
            BrandTopBar(
                title = stringResource(R.string.auth_recover_topbar),
                onBack = onBack,
            )
            Column(Modifier.padding(start = 26.dp, end = 26.dp, top = 18.dp)) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(OliveLight.copy(alpha = 0.22f))
                        .border(1.dp, OliveLight.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        // Sulla banda nera l'accento è l'oro, non l'oliva.
                        tint = OliveLight,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.auth_recover_title),
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 38.sp),
                    color = Bone,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    stringResource(R.string.auth_recover_subtitle),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp, lineHeight = 21.sp),
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
                .padding(horizontal = 24.dp)
                .padding(top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EmailField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = stringResource(R.string.auth_email_account),
                leadingIcon = Icons.Outlined.MailOutline,
                outlined = true,
                error = state.emailError,
            )
            AccentButton(
                text = stringResource(R.string.auth_recover_cta),
                onClick = viewModel::send,
                loading = state.loading,
                height = 56.dp,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = Icons.Outlined.Send,
            )
            if (state.genericError) {
                Text(
                    stringResource(R.string.auth_error_generic),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                )
            }
            if (state.sent) {
                StoneCard(corner = 16.dp, container = OliveWood.copy(alpha = 0.14f)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            stringResource(R.string.auth_recover_sent_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink,
                        )
                        Text(
                            stringResource(R.string.auth_recover_sent_body, state.email),
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.auth_recover_note),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 19.sp),
                color = if (state.emailError != null) ErrorRed else Ink,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            HorizontalDivider(color = Stone, modifier = Modifier.padding(vertical = 4.dp))
            BrandSectionLabel(stringResource(R.string.auth_recover_other_ways))
            NavigationRow(
                text = stringResource(R.string.auth_recover_sms, "+39 347 •• 4490"),
                onClick = viewModel::send,
                leadingIcon = Icons.Outlined.Phone,
            )
            Spacer(Modifier.height(1.dp))
            NavigationRow(
                text = stringResource(R.string.auth_recover_whatsapp),
                onClick = viewModel::send,
                leadingIcon = Icons.Outlined.Send,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.auth_back_to_login),
                style = MaterialTheme.typography.titleSmall,
                color = OliveWood,
                modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
