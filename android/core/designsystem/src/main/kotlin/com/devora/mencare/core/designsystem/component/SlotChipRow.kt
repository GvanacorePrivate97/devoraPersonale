package com.devora.mencare.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.TextMuted
import java.time.LocalTime

private val ChipShape = RoundedCornerShape(10.dp)
private val ChipHeight = 46.dp
private val ChipMinWidth = 84.dp

/**
 * Gli orari liberi di una giornata, tutti, scorrendo in orizzontale: con la
 * riga a larghezza fissa se ne vedevano solo i primi.
 *
 * [label] formatta l'ora — come ogni componente qui, la formattazione resta a
 * chi chiama — e [emptyLabel] è la riga che prende il posto delle chip quando
 * non c'è niente di prenotabile.
 */
@Composable
fun SlotChipRow(
    slots: List<LocalTime>,
    selected: LocalTime?,
    onSelect: (LocalTime) -> Unit,
    label: (LocalTime) -> String,
    emptyLabel: String,
    modifier: Modifier = Modifier,
) {
    if (slots.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(ChipHeight)
                .clip(ChipShape)
                .background(Stone),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                emptyLabel,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        slots.forEach { slot ->
            val isSelected = slot == selected
            Box(
                modifier = Modifier
                    .widthIn(min = ChipMinWidth)
                    .height(ChipHeight)
                    .clip(ChipShape)
                    .background(if (isSelected) Ink else Stone)
                    .clickable { onSelect(slot) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(slot),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSelected) Bone else Ink,
                )
            }
        }
    }
}
