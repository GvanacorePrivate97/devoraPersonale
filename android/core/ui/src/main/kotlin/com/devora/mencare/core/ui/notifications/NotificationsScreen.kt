package com.devora.mencare.core.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.common.formatDateTime
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.DarkHeaderTitle
import com.devora.mencare.core.designsystem.component.OutlineCard
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.ui.R
import com.devora.mencare.core.designsystem.R as DsR

/** Notification list: the same page for client, staff and owner. */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Bone)) {
        DarkHeader {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(DsR.string.ds_back),
                        tint = Bone,
                    )
                }
                DarkHeaderTitle(title = stringResource(R.string.notifications_title))
            }
        }
        if (state.notifications.isEmpty()) {
            Text(
                stringResource(R.string.notifications_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.readableWidth().padding(20.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.readableWidth(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.notifications, key = { it.id }) { notification ->
                    val isNew = notification.id in state.newIds
                    OutlineCard {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Oliva piena se nuova, oliva tenue se già letta.
                            Box(
                                Modifier
                                    .padding(end = 12.dp)
                                    .size(9.dp)
                                    .background(
                                        if (isNew) OliveWood else OliveWood.copy(alpha = 0.3f),
                                        CircleShape,
                                    ),
                            )
                            Column {
                                Text(notification.title, style = MaterialTheme.typography.titleSmall)
                                Text(notification.body, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    formatDateTime(notification.at),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
