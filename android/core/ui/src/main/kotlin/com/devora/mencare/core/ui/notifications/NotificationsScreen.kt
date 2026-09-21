package com.devora.mencare.core.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.common.formatDateTime
import com.devora.mencare.core.designsystem.R as DsR
import com.devora.mencare.core.designsystem.component.BrandSectionLabel
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.DarkHeaderTitle
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.Meta
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Radii
import com.devora.mencare.core.designsystem.theme.StoneBorder
import com.devora.mencare.core.designsystem.theme.StoneSoft
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.core.ui.R
import java.time.LocalDate

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
                // Le due letture della pagina — "da leggere" e "già viste" —
                // hanno due superfici, non due tonalità dello stesso pallino:
                // la card con il filo per le nuove, il grigio tenue per le
                // altre, con la data a separarle come nel mockup.
                val today = LocalDate.now()
                val groups = state.notifications.groupBy { it.at.toLocalDate() == today }
                listOf(true to R.string.notifications_today, false to R.string.notifications_earlier)
                    .forEach { (isToday, labelRes) ->
                        val rows = groups[isToday].orEmpty()
                        if (rows.isEmpty()) return@forEach
                        item(key = "header_$isToday") {
                            BrandSectionLabel(
                                stringResource(labelRes),
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                            )
                        }
                        items(rows, key = { it.id }) { notification ->
                            val isNew = notification.id in state.newIds
                            NotificationRow(notification.title, notification.body, formatDateTime(notification.at), isNew)
                        }
                    }
            }
        }
    }
}

/**
 * Una riga della campanella. Non letta: card chiara col filo e il punto
 * d'accento. Già letta: fondo tenue, nessun filo e il punto spento — la
 * differenza si vede dalla superficie, non solo da un pallino più chiaro.
 */
@Composable
private fun NotificationRow(title: String, body: String, at: String, isNew: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radii.Md)
            .background(if (isNew) Bone else StoneSoft)
            .then(if (isNew) Modifier.border(1.5.dp, StoneBorder, Radii.Md) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(end = 12.dp)
                .size(9.dp)
                .background(if (isNew) OliveWood else StoneBorder, CircleShape),
        )
        Column {
            Text(
                title,
                style = if (isNew) {
                    MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp)
                } else {
                    MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)
                },
                color = Ink,
            )
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Text(at, style = Meta, color = TextMuted)
        }
    }
}
