package com.devora.mencare.feature.client.profile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.common.formatDayGroup
import com.devora.mencare.core.common.formatMonthYear
import com.devora.mencare.core.common.formatTime
import com.devora.mencare.core.designsystem.component.AccentButton
import com.devora.mencare.core.designsystem.component.BarAction
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.BrandSwitch
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.EmailField
import com.devora.mencare.core.designsystem.component.InlineErrorBanner
import com.devora.mencare.core.designsystem.component.NameField
import com.devora.mencare.core.designsystem.component.PhoneField
import com.devora.mencare.core.designsystem.component.StoneKeyValueRow
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.ErrorRed
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.StoneBorder
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.core.model.groupConsecutiveDays
import com.devora.mencare.feature.client.R

@Composable
fun ProfileScreen(
    onLoggedOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var changingPassword by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    // Si esce dalla modifica solo quando il salvataggio è andato a buon fine.
    LaunchedEffect(state.saved) {
        if (state.saved) editing = false
    }

    Column(modifier = Modifier.fillMaxSize().background(Bone)) {
        DarkHeader(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(OliveWood),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        state.user?.initials.orEmpty().uppercase(),
                        fontFamily = Cormorant,
                        fontSize = 19.sp,
                        color = Bone,
                    )
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        state.user?.fullName.orEmpty(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
                        color = Bone,
                    )
                    Text(
                        stringResource(
                            R.string.profile_since,
                            state.user?.memberSince?.let { formatMonthYear(it) }.orEmpty(),
                            state.user?.visitCount ?: 0,
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Bone,
                    )
                }
                BarAction(
                    text = stringResource(if (editing) R.string.profile_save else R.string.profile_edit),
                    onClick = { if (editing) viewModel.save() else editing = true },
                    color = OliveWood,
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
                .padding(top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Il profilo ha sempre qualcosa da mostrare (i dati della sessione),
            // quindi un guasto di lettura è una riga e non una schermata intera.
            state.loadError?.let { failure ->
                InlineErrorBanner(error = failure, onRetry = viewModel::retry)
            }
            BrandSectionLabel(stringResource(R.string.profile_personal_data))
            if (editing) {
                NameField(
                    value = state.firstName,
                    onValueChange = { viewModel.onFieldChange(ProfileField.FIRST_NAME, it) },
                    label = stringResource(R.string.profile_first_name),
                    error = state.fieldErrors[ProfileField.FIRST_NAME],
                )
                NameField(
                    value = state.lastName,
                    onValueChange = { viewModel.onFieldChange(ProfileField.LAST_NAME, it) },
                    label = stringResource(R.string.profile_last_name),
                    error = state.fieldErrors[ProfileField.LAST_NAME],
                )
                EmailField(
                    value = state.email,
                    onValueChange = { viewModel.onFieldChange(ProfileField.EMAIL, it) },
                    label = stringResource(R.string.profile_email),
                    error = state.fieldErrors[ProfileField.EMAIL],
                )
                PhoneField(
                    value = state.phone,
                    onValueChange = { viewModel.onFieldChange(ProfileField.PHONE, it) },
                    label = stringResource(R.string.profile_phone),
                    error = state.fieldErrors[ProfileField.PHONE],
                )
                if (state.saveFailed) {
                    Text(
                        stringResource(R.string.profile_error_save),
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Stone),
                ) {
                    StoneKeyValueRow(
                        stringResource(R.string.profile_first_name),
                        "${state.firstName} ${state.lastName}".trim(),
                    )
                    HorizontalDivider(color = StoneBorder)
                    StoneKeyValueRow(stringResource(R.string.profile_email), state.email)
                    HorizontalDivider(color = StoneBorder)
                    StoneKeyValueRow(stringResource(R.string.profile_phone), state.phone)
                }
            }

            // The salon's own opening hours, as the owner sets them in Gestione.
            state.salon?.let { salon ->
                val groups = salon.weeklyHours.groupConsecutiveDays()
                if (groups.any { it.second != null }) {
                    Spacer(Modifier.height(6.dp))
                    BrandSectionLabel(stringResource(R.string.profile_salon_hours))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        groups.forEach { (days, range) ->
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
                                        ?: stringResource(R.string.profile_salon_closed),
                                    valueColor = if (closed) TextMuted else Ink,
                                )
                            }
                        }
                    }
                    Text(
                        salon.address,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextMuted,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            BrandSectionLabel(stringResource(R.string.profile_account_management))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Stone),
            ) {
                StoneKeyValueRow(
                    label = stringResource(R.string.profile_change_password),
                    value = "••••••••",
                    valueColor = OliveWood,
                    onClick = { changingPassword = true },
                )
            }

            Spacer(Modifier.height(6.dp))
            BrandSectionLabel(stringResource(R.string.profile_notifications))
            PrefRow(
                title = stringResource(R.string.profile_pref_reminder),
                hint = stringResource(R.string.profile_pref_reminder_hint),
                checked = state.prefs.appointmentReminder,
                onCheckedChange = { viewModel.onPrefChange(state.prefs.copy(appointmentReminder = it)) },
            )
            PrefRow(
                title = stringResource(R.string.profile_pref_waitlist),
                hint = null,
                checked = state.prefs.waitlistAlerts,
                onCheckedChange = { viewModel.onPrefChange(state.prefs.copy(waitlistAlerts = it)) },
            )
            PrefRow(
                title = stringResource(R.string.profile_pref_marketing),
                hint = null,
                checked = state.prefs.marketing,
                onCheckedChange = { viewModel.onPrefChange(state.prefs.copy(marketing = it)) },
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, StoneBorder, RoundedCornerShape(16.dp))
                    .clickable(onClick = viewModel::logout),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(R.string.profile_logout),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }

            if (editing && state.dirty) {
                AccentButton(
                    text = stringResource(R.string.profile_save),
                    onClick = viewModel::save,
                    loading = state.saving,
                    height = 54.dp,
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }
    }

    if (changingPassword) {
        ChangePasswordScreen(onDismiss = { changingPassword = false })
    }
}

@Composable
private fun PrefRow(
    title: String,
    hint: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Stone)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextMuted,
                )
            }
        }
        BrandSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
