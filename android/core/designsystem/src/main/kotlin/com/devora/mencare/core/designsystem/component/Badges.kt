package com.devora.mencare.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveTint
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Stone

/** Circle with serif initials — stands in for people photos across the app. */
@Composable
fun InitialsAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    dark: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (dark) Ink else Stone),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            fontFamily = Cormorant,
            // Le iniziali erano in Cormorant Regular: a corpo piccolo il serif
            // ad alto contrasto si assottiglia fino a sparire.
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.42).sp,
            color = if (dark) Bone else Ink,
        )
    }
}

/** Small rounded status/category pill. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = Stone,
    content: Color = Ink,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/**
 * Pill d'accento. Il fondo è un tono pieno, non l'oliva al 14%: con l'alpha il
 * contrasto del testo cambiava a seconda di cosa c'era sotto (4.5:1 su Bone,
 * 3.3:1 su Stone). Così è 4.76:1 ovunque.
 */
@Composable
fun AccentPill(text: String, modifier: Modifier = Modifier) =
    Pill(text, modifier, container = OliveTint, content = OliveWood)

@Composable
fun DarkPill(text: String, modifier: Modifier = Modifier) =
    Pill(text, modifier, container = Ink, content = Bone)
