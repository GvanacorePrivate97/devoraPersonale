package com.devora.mencare.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * The brand commits to a single light look — dark full-bleed bands over a
 * light body are part of the design language, not a theme variant.
 */
private val MenCareColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Bone,
    primaryContainer = Ink,
    onPrimaryContainer = Bone,
    secondary = OliveWood,
    onSecondary = Bone,
    secondaryContainer = Stone,
    onSecondaryContainer = Ink,
    tertiary = OliveWood,
    onTertiary = Bone,
    background = Bone,
    onBackground = Ink,
    surface = Bone,
    onSurface = Ink,
    surfaceVariant = Stone,
    onSurfaceVariant = TextMuted,
    surfaceContainer = Stone,
    surfaceContainerHigh = Stone,
    surfaceContainerHighest = Stone,
    surfaceContainerLow = Bone,
    surfaceContainerLowest = Bone,
    outline = StoneBorder,
    outlineVariant = StoneBorder,
    error = ErrorRed,
    onError = Bone,
    inverseSurface = Ink,
    inverseOnSurface = Bone,
    inversePrimary = OliveWood,
)

private val MenCareShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MenCareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MenCareColorScheme,
        typography = MenCareTypography,
        shapes = MenCareShapes,
        content = content,
    )
}
