package com.devora.mencare.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.designsystem.theme.TextMuted

/**
 * Multi-select twin of the operator picker: a stone field with a chevron that
 * opens a menu with a checkmark per selected row. The menu stays open while
 * picking, so choosing two services costs two taps, not four.
 */
@Composable
fun MultiSelectDropdown(
    options: List<DropdownOption>,
    selectedIds: Collection<String>,
    onToggle: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val summary = options.filter { it.id in selectedIds }.joinToString(", ") { it.label }
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Stone)
                .clickable { open = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                summary.ifEmpty { placeholder },
                style = MaterialTheme.typography.bodyLarge,
                color = if (summary.isEmpty()) TextMuted else Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Bone,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, color = Ink) },
                    onClick = { onToggle(option.id) },
                    trailingIcon = {
                        if (option.id in selectedIds) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = OliveWood)
                        }
                    },
                )
            }
        }
    }
}
