package com.devora.mencare.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Stone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Drawing grid of the illustration: every coordinate below is in these units.
 * The artboard is cropped to the drawing (rings on top, shadow at the bottom)
 * so the picture sits centered in its frame.
 */
private const val ART_WIDTH = 240f
private const val ART_HEIGHT = 138f
private const val ART_TOP = 22f

/** One full turn of the minute hand. */
private const val TURN_MILLIS = 6000

/**
 * "Giornata al completo": a calendar page with every day crossed out and a
 * stopwatch whose hand keeps turning, sweeping an olive wedge behind it — the
 * wait for a slot to free up. Decorative only.
 */
@Composable
fun FullyBookedIllustration(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "stopwatch")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = TURN_MILLIS, easing = LinearEasing)),
        label = "hand",
    )
    Canvas(modifier.aspectRatio(ART_WIDTH / ART_HEIGHT)) {
        val u = size.width / ART_WIDTH
        translate(top = -ART_TOP * u) {
            drawOval(Stone, topLeft = Offset(28f * u, 146f * u), size = Size(190f * u, 12f * u))
            drawCalendar(u)
            drawStopwatch(u, angle)
        }
    }
}

private fun DrawScope.drawCalendar(u: Float) {
    val topLeft = Offset(18f * u, 34f * u)
    val page = Size(130f * u, 116f * u)
    val corner = CornerRadius(10f * u)

    drawRoundRect(Bone, topLeft, page, corner)
    // Olive header band, rounded on top only.
    drawRoundRect(OliveWood, topLeft, Size(page.width, 30f * u), corner)
    drawRect(OliveWood, topLeft + Offset(0f, 20f * u), Size(page.width, 10f * u))
    drawRoundRect(Ink, topLeft, page, corner, style = Stroke(2.5f * u))
    listOf(46f, 112f).forEach { x ->
        drawRoundRect(Ink, Offset(x * u, 26f * u), Size(8f * u, 16f * u), CornerRadius(4f * u))
    }

    // Every day taken: a cross on each cell.
    val cell = Size(22f * u, 16f * u)
    val gap = 8f * u
    val gridLeft = topLeft.x + (page.width - (4 * cell.width + 3 * gap)) / 2
    val gridTop = topLeft.y + 40f * u
    val cross = 4f * u
    for (row in 0 until 3) {
        for (col in 0 until 4) {
            val origin = Offset(gridLeft + col * (cell.width + gap), gridTop + row * (cell.height + gap))
            drawRoundRect(Stone, origin, cell, CornerRadius(4f * u))
            val center = origin + Offset(cell.width / 2, cell.height / 2)
            val mark = Ink.copy(alpha = 0.55f)
            drawLine(mark, center + Offset(-cross, -cross), center + Offset(cross, cross), 1.8f * u, StrokeCap.Round)
            drawLine(mark, center + Offset(-cross, cross), center + Offset(cross, -cross), 1.8f * u, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawStopwatch(u: Float, angle: Float) {
    val center = Offset(182f * u, 98f * u)
    val radius = 40f * u

    // Crown and side button sit behind the face.
    drawRoundRect(Ink, Offset(177f * u, 50f * u), Size(10f * u, 10f * u), CornerRadius(2f * u))
    drawRoundRect(Ink, Offset(171f * u, 44f * u), Size(22f * u, 7f * u), CornerRadius(3.5f * u))
    drawCircle(Ink, 5.5f * u, center + polar(radius + 2f * u, 45f))

    drawCircle(Bone, radius, center)
    val wedge = radius * 0.84f
    drawArc(
        color = OliveWood.copy(alpha = 0.22f),
        startAngle = -90f,
        sweepAngle = angle,
        useCenter = true,
        topLeft = center - Offset(wedge, wedge),
        size = Size(wedge * 2, wedge * 2),
    )
    drawCircle(Ink, radius, center, style = Stroke(3f * u))

    for (i in 0 until 12) {
        val length = if (i % 3 == 0) 7f * u else 4f * u
        val outer = radius - 6f * u
        drawLine(
            Ink,
            center + polar(outer - length, i * 30f),
            center + polar(outer, i * 30f),
            strokeWidth = 1.6f * u,
            cap = StrokeCap.Round,
        )
    }

    drawLine(Ink, center, center + polar(radius * 0.42f, angle / 12f), 3.5f * u, StrokeCap.Round)
    drawLine(OliveWood, center, center + polar(radius * 0.7f, angle), 3f * u, StrokeCap.Round)
    drawCircle(Ink, 4f * u, center)
    drawCircle(OliveWood, 1.8f * u, center)
}

/** Point at [distance] from the origin, [degrees] clockwise from twelve o'clock. */
private fun polar(distance: Float, degrees: Float): Offset {
    val radians = (degrees - 90f) * PI.toFloat() / 180f
    return Offset(distance * cos(radians), distance * sin(radians))
}
