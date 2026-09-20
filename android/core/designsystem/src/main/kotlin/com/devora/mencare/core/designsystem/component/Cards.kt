package com.devora.mencare.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.StoneBorder

@Composable
fun OutlineCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = Bone)
    val border = BorderStroke(1.dp, StoneBorder)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            colors = colors,
            border = border,
            shape = MaterialTheme.shapes.medium,
            content = content,
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = colors,
            border = border,
            shape = MaterialTheme.shapes.medium,
            content = content,
        )
    }
}

@Composable

/** "label — value" row used in summaries. */
@Composable
