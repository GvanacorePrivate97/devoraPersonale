package com.devora.mencare.core.ui.crm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.Radii
import com.devora.mencare.core.designsystem.theme.StoneBorder
import com.devora.mencare.core.designsystem.theme.TextMuted
import com.devora.mencare.core.model.ClientRecord

/**
 * Il cliente di un appuntamento in modifica: si mostra e basta, niente ricerca
 * né "Crea nuovo cliente". Lo usano il foglio del titolare e quello dell'operatore.
 */
@Composable
fun FixedClientRow(client: ClientRecord?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radii.Md)
            .background(Bone)
            .border(1.5.dp, StoneBorder, Radii.Md)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(Bone),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                listOfNotNull(client?.firstName?.firstOrNull(), client?.lastName?.firstOrNull()).joinToString(""),
                fontFamily = Cormorant,
                fontSize = 12.sp,
                color = Ink,
            )
        }
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(
                listOfNotNull(client?.firstName, client?.lastName).joinToString(" "),
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            client?.phone?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextMuted,
                )
            }
        }
    }
}
