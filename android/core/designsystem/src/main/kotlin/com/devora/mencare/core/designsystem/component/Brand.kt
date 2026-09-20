package com.devora.mencare.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devora.mencare.core.designsystem.R
import com.devora.mencare.core.designsystem.theme.Bone

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
