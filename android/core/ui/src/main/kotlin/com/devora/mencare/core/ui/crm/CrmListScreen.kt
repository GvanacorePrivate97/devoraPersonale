package com.devora.mencare.core.ui.crm

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devora.mencare.core.common.formatDateShort
import com.devora.mencare.core.designsystem.component.BrandChip
import com.devora.mencare.core.designsystem.component.DarkHeader
import com.devora.mencare.core.designsystem.component.FilledTextField
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.OnDarkMuted
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.core.model.ClientRecord
import com.devora.mencare.core.model.ClientSegment
import com.devora.mencare.core.ui.R

@Composable
fun CrmListScreen(
    onClient: (String) -> Unit,
    viewModel: CrmListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Bone)) {
        DarkHeader(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.crm_title),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp),
                color = Bone,
            )
            Spacer(Modifier.height(14.dp))
            FilledTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = "",
                placeholder = stringResource(R.string.crm_search_hint),
                leadingIcon = Icons.Outlined.Search,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ClientSegment.TUTTI to R.string.crm_seg_all,
                    ClientSegment.INATTIVI_60 to R.string.crm_seg_inactive,
                ).forEach { (segment, label) ->
                    BrandChip(
                        text = stringResource(label),
                        selected = state.segment == segment,
                        onClick = { viewModel.setSegment(segment) },
                        modifier = Modifier.weight(1f),
                        onDark = true,
                        fill = true,
                    )
                }
            }
        }

        if (state.clients.isEmpty()) {
            Text(
                stringResource(R.string.crm_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.readableWidth().padding(top = 48.dp),
            )
        } else {
            val grouped = state.clients.groupBy { it.lastName.first().uppercaseChar() }.toSortedMap()
            LazyColumn(
                modifier = Modifier.readableWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            ) {
                grouped.forEach { (letter, clients) ->
                    item(key = "letter-$letter") {
                        Text(
                            "$letter",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.14.em),
                            color = TextMuted,
                            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                        )
                    }
                    items(clients.size, key = { clients[it].id }) { index ->
                        val client = clients[index]
                        ClientRow(client = client, onClick = { onClient(client.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientRow(client: ClientRecord, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Ink)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(OliveWood),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${client.firstName.first()}${client.lastName.first()}",
                fontFamily = Cormorant,
                fontSize = 13.sp,
                color = Bone,
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                "${client.firstName} ${client.lastName}",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = Bone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    "${client.visitCount} ${stringResource(R.string.crm_visits_suffix)}",
                    client.lastVisit?.let {
                        "${stringResource(R.string.crm_last_visit)} ${formatDateShort(it)}"
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = OnDarkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = OliveWood,
            modifier = Modifier.size(18.dp),
        )
    }
}
