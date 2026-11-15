// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.liquid

// Adapted from Kyant0/AndroidLiquidGlass — https://github.com/Kyant0/AndroidLiquidGlass (Apache 2.0).

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Lightweight stand-in for miuix's `drawEdgeStroke` (which lives in the unpublished
 * `miuix-material` module): a 1dp specular rim painted along [shape] with a vertical
 * white gradient, brighter on the top edge and softer at the bottom — the same read as
 * the glass edge highlight used by the official iOS-like navigation bar.
 *
 * @param alpha overall opacity multiplier; `0` skips drawing entirely.
 */
internal fun DrawScope.drawGlassRim(shape: Shape, alpha: Float) {
    if (alpha <= 0.01f) return
    val outline = shape.createOutline(size, layoutDirection, this)
    val brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.55f * alpha),
            Color.White.copy(alpha = 0.10f * alpha),
            Color.White.copy(alpha = 0.28f * alpha),
        ),
        startY = 0f,
        endY = size.height.coerceAtLeast(1f),
    )
    drawOutline(
        outline = outline,
        brush = brush,
        style = Stroke(width = 1.dp.toPx()),
    )
}
