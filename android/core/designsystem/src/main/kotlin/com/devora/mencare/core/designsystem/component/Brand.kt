package com.devora.mencare.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devora.mencare.core.designsystem.R
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.Stone

/** The brand mark in its rounded-square outline, as it appears on dark bands. */
@Composable
fun LogoBadge(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    corner: Dp = 14.dp,
    borderWidth: Dp = 1.5.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .border(borderWidth, Bone, RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_mark),
            contentDescription = null,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

/**
 * Hatched stand-in for a service photo — the mockup ships no imagery yet, and a
 * labelled placeholder reads as deliberate where a grey box reads as broken.
 */
@Composable
fun PhotoPlaceholder(
    label: String,
    modifier: Modifier = Modifier,
    height: Dp = 54.dp,
) {
    val hatch = Ink.copy(alpha = 0.07f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(Stone)
            .drawBehind {
                val step = 9.dp.toPx()
                val line = 1.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(
                        color = hatch,
                        start = Offset(x, size.height),
                        end = Offset(x + size.height, 0f),
                        strokeWidth = line,
                    )
                    x += step
                }
            },
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.sp),
            color = Ink.copy(alpha = 0.6f),
            modifier = Modifier.padding(6.dp),
        )
    }
}
