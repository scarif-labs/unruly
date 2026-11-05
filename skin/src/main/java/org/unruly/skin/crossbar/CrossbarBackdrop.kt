/*
 * Copyright (c) 2026 Unruly Project
 * CrossbarBackdrop.kt is part of Unruly.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.unruly.skin.crossbar

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Seconds since the backdrop started, advancing every frame. */
@Composable
internal fun rememberClockSeconds(): State<Float> =
    produceState(0f) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1_000_000_000f }
    }

/**
 * The month-tinted gradient with the slow wave across it.
 *
 * On the device the wave is a soft, translucent ribbon: a lighter band with a few fine bright lines
 * running through it, drifting continuously. (The emulator the frames came from fills under it
 * solidly; the real hardware does not, and this follows the hardware.)
 *
 * [energy] scales the wave's height; the playback screen's wave visualiser raises it.
 */
@Composable
internal fun CrossbarBackdrop(
    tint: Tint,
    modifier: Modifier = Modifier,
    energy: () -> Float = { 1f },
) {
    val clock = rememberClockSeconds()
    Canvas(modifier = modifier) {
        drawGradient(tint)
        drawWave(tint, clock.value, energy())
    }
}

private fun DrawScope.drawGradient(tint: Tint) {
    // Bilinear across the screen: top edge topLeft..topRight, middle midLeft..midRight, the
    // bottom a little deeper than the middle. Drawn as horizontal bands, each a left-right ramp.
    val bands = 32
    val bandHeight = size.height / bands
    for (i in 0 until bands) {
        val f = (i + 0.5f) / bands
        val left: Color
        val right: Color
        if (f < 0.5f) {
            val g = f / 0.5f
            left = lerp(tint.topLeft, tint.midLeft, g)
            right = lerp(tint.topRight, tint.midRight, g)
        } else {
            val g = (f - 0.5f) / 0.5f
            left = lerp(tint.midLeft, darker(tint.midLeft), g)
            right = lerp(tint.midRight, darker(tint.midRight), g * 0.6f)
        }
        drawRect(
            brush = Brush.horizontalGradient(listOf(left, right), startX = 0f, endX = size.width),
            topLeft = Offset(0f, i * bandHeight),
            size = Size(size.width, bandHeight + 1f),
        )
    }
}

private fun darker(c: Color) = Color(c.red * 0.86f, c.green * 0.86f, c.blue * 0.86f, c.alpha)

/** One undulating line: a slow swell plus a shorter ripple, both drifting at their own speeds. */
private class Strand(
    val offset: Float,
    val swell: Float,
    val swellSpeed: Float,
    val ripple: Float,
    val rippleSpeed: Float,
    val phase: Float,
) {
    fun y(xFrac: Float, t: Float, centre: Float, amp: Float): Float {
        val a = sin(2f * PI.toFloat() * (0.55f * xFrac) + swellSpeed * t + phase)
        val b = sin(2f * PI.toFloat() * (1.35f * xFrac) - rippleSpeed * t + phase * 1.7f)
        // Rising towards the right, as the device's wave does.
        val lean = (0.5f - xFrac) * 0.55f
        return centre + amp * (offset + swell * a + ripple * b + lean)
    }
}

private val ribbon =
    listOf(
        Strand(0.00f, 0.80f, 0.11f, 0.22f, 0.17f, 0.0f),
        Strand(0.10f, 0.78f, 0.11f, 0.26f, 0.15f, 0.25f),
        Strand(0.22f, 0.74f, 0.10f, 0.30f, 0.13f, 0.55f),
        Strand(0.34f, 0.70f, 0.10f, 0.34f, 0.12f, 0.85f),
    )

private val crest = Strand(-0.05f, 0.85f, 0.11f, 0.20f, 0.17f, -0.15f)

private fun DrawScope.drawWave(tint: Tint, t: Float, energy: Float) {
    val w = size.width
    val h = size.height
    val centre = h * if (h > w) 0.62f else 0.58f
    val amp = min(w, h) * 0.16f * energy
    val steps = 48

    fun path(strand: Strand, closeBelow: Boolean): Path {
        val p = Path()
        for (i in 0..steps) {
            val xf = i / steps.toFloat()
            val y = strand.y(xf, t, centre, amp)
            if (i == 0) p.moveTo(0f, y) else p.lineTo(xf * w, y)
        }
        if (closeBelow) {
            p.lineTo(w, h)
            p.lineTo(0f, h)
            p.close()
        }
        return p
    }

    // The broad lit swell under the crest, fading out downward.
    drawPath(
        path(crest, closeBelow = true),
        brush =
            Brush.verticalGradient(
                listOf(
                    tint.glow.copy(alpha = 0.30f),
                    tint.glow.copy(alpha = 0.10f),
                    Color.Transparent,
                ),
                startY = centre - amp,
                endY = h,
            ),
    )

    // The ribbon: translucent bands between neighbouring strands, then the fine lines.
    for (i in 0 until ribbon.size - 1) {
        val top = ribbon[i]
        val bottom = ribbon[i + 1]
        val band = Path()
        for (s in 0..steps) {
            val xf = s / steps.toFloat()
            val y = top.y(xf, t, centre, amp)
            if (s == 0) band.moveTo(0f, y) else band.lineTo(xf * w, y)
        }
        for (s in steps downTo 0) {
            val xf = s / steps.toFloat()
            band.lineTo(xf * w, bottom.y(xf, t, centre, amp))
        }
        band.close()
        drawPath(band, color = Color.White.copy(alpha = 0.07f + 0.03f * i))
    }
    val line = Stroke(width = (min(w, h) / 272f) * 1.1f, cap = StrokeCap.Round)
    for ((i, strand) in ribbon.withIndex()) {
        drawPath(
            path(strand, closeBelow = false),
            color = Color.White.copy(alpha = 0.42f - 0.07f * i),
            style = line,
        )
    }
}
