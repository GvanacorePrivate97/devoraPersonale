package com.devora.mencare.feature.staff

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
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
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.ProfileAvatar
import com.devora.mencare.core.designsystem.component.StoneKeyValueRow
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.ErrorRed
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Radii
import com.devora.mencare.core.designsystem.theme.StoneBorder

/**
 * "Profilo" dell'operatore: chi è loggato, i suoi contatti, la foto e l'uscita
 * dall'account.
 */
@Composable
fun StaffProfileScreen(
    onLoggedOut: () -> Unit,
    viewModel: StaffProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onPhotoPicked(uri.toString())
    }

    Column(modifier = Modifier.fillMaxSize().background(Bone)) {
        DarkHeader(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(
                    initials = state.user?.initials.orEmpty(),
                    photoPath = state.user?.avatarPath,
                    contentDescription = stringResource(R.string.staff_profile_photo_action),
                    editable = true,
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
                Column(Modifier.padding(start = 14.dp)) {
                    Text(
                        state.user?.fullName.orEmpty(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
                        color = Bone,
                    )
                    Text(
                        state.operator?.title.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Bone,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                BrandSectionLabel(
                    stringResource(R.string.staff_profile_account),
                    modifier = Modifier.weight(1f),
                )
                if (state.user?.avatarPath != null) {
                    Text(
                        stringResource(R.string.staff_profile_photo_remove),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = OliveWood,
                        modifier = Modifier.clickable { viewModel.onPhotoRemoved() },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Radii.Md)
                    .background(Bone)
                    .border(1.5.dp, StoneBorder, Radii.Md),
            ) {
                StoneKeyValueRow(stringResource(R.string.staff_profile_email), state.user?.email.orEmpty())
                HorizontalDivider(color = StoneBorder)
                StoneKeyValueRow(stringResource(R.string.staff_profile_phone), state.user?.phone.orEmpty())
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(Radii.Md)
                    .border(1.5.dp, ErrorRed, Radii.Md)
                    .clickable { viewModel.logout(onLoggedOut) },
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
                    stringResource(R.string.staff_profile_logout),
                    style = MaterialTheme.typography.titleMedium,
                    color = ErrorRed,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}
