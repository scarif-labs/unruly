/*
 * Copyright (c) 2026 Unruly Project
 * TouchWheel.kt is part of Unruly.
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
 
package org.unruly.skin.wheel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import org.unruly.skin.support.SkinTheme
import org.unruly.skin.support.rotaryInput

private const val OUTER_RADIUS_FRACTION = 1f

/** Returns the largest circular wheel that fits within the supplied bounds. */
fun wheelDiameterFor(maxWidth: Dp, maxHeight: Dp): Dp = minOf(maxWidth, maxHeight)

/**
 * Draws and handles geometry for the Touch Wheel.
 *
 * Taps and drags use separate pointer-input blocks. The tap block is applied before [rotaryInput];
 * crossing the drag slop cancels the tap, while a tap never reaches rotaryInput's drag callback.
 */
@Composable
fun TouchWheel(
    modifier: Modifier = Modifier,
    onZonePressed: (WheelZone) -> Unit = {},
    onRotate: (Int) -> Unit = {},
    innerRadiusFraction: Float = 0.42f,
) {
    val textMeasurer = rememberTextMeasurer()
    val clampedInnerFraction = innerRadiusFraction.coerceIn(0f, OUTER_RADIUS_FRACTION)

    val gestureModifier =
        modifier
            .pointerInput(clampedInnerFraction, onZonePressed) {
                detectTapGestures { position ->
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    val outerRadius = min(width, height) / 2f
                    val center = Offset(width / 2f, height / 2f)
                    zoneForPoint(
                            dx = position.x - center.x,
                            dy = position.y - center.y,
                            outerRadius = outerRadius,
                            innerRadius = clampedInnerFraction * outerRadius,
                        )
                        ?.let(onZonePressed)
                }
            }
            .rotaryInput(
                innerRadiusFraction = clampedInnerFraction,
                outerRadiusFraction = OUTER_RADIUS_FRACTION,
                onClick = onRotate,
            )

    // SkinTheme.typography is a @Composable @ReadOnlyComposable getter, so it must be read
    // here and not inside the Canvas draw block - a DrawScope is not composable scope.
    val labelStyle =
        SkinTheme.typography.label.copy(
            color = wheelLabel,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )

    Canvas(gestureModifier) {
        val outerRadius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val innerRadius = clampedInnerFraction * outerRadius

        // Use one radius from the smaller dimension so the wheel remains circular.
        // The ring is satin white plastic, lit from above: a soft vertical falloff, a darker
        // recessed edge where it meets the body, and a faint inner shade around the button.
        drawCircle(color = wheelSeat, radius = outerRadius, center = center)
        drawCircle(
            brush =
                Brush.verticalGradient(
                    colors = listOf(wheelRingTop, wheelRingBottom),
                    startY = center.y - outerRadius,
                    endY = center.y + outerRadius,
                ),
            radius = outerRadius - 1.5f,
            center = center,
        )
        drawCircle(
            brush =
                Brush.radialGradient(
                    0f to Color.Transparent,
                    0.86f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.07f),
                    center = center,
                    radius = outerRadius,
                ),
            radius = outerRadius,
            center = center,
        )

        // The centre button is the body's aluminium, sitting slightly proud of the ring: a
        // shadow just outside it, a top-lit gradient, and a bright rim on its upper edge.
        drawCircle(
            color = Color.Black.copy(alpha = 0.10f),
            radius = innerRadius + 2.5f,
            center = center + Offset(0f, 1.5f),
        )
        drawCircle(
            brush =
                Brush.verticalGradient(
                    colors = listOf(wheelCentreTop, wheelCentreBottom),
                    startY = center.y - innerRadius,
                    endY = center.y + innerRadius,
                ),
            radius = innerRadius,
            center = center,
        )
        drawCircle(
            brush =
                Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.9f), wheelHairline),
                    startY = center.y - innerRadius,
                    endY = center.y + innerRadius,
                ),
            radius = innerRadius,
            center = center,
            style = Stroke(width = 1.2f),
        )

        // MENU stays a word; the transport controls are drawn glyphs, as on the hardware
        // this is modelled on - text arrows never sit at the right weight or spacing.
        val labelRadius = (outerRadius + innerRadius) / 2f
        val glyph = outerRadius * 0.075f
        drawWheelLabel("MENU", center, Offset(0f, -labelRadius), labelStyle, textMeasurer)
        drawSkipGlyph(center + Offset(labelRadius, 0f), glyph, forward = true)
        drawSkipGlyph(center + Offset(-labelRadius, 0f), glyph, forward = false)
        drawPlayPauseGlyph(center + Offset(0f, labelRadius), glyph)
    }
}

// The white wheel and silver button of the light-cased models.
private val wheelSeat = Color(0xFFB9BABE)
private val wheelRingTop = Color(0xFFFDFDFD)
private val wheelRingBottom = Color(0xFFE7E8EA)
private val wheelCentreTop = Color(0xFFE9EAEC)
private val wheelCentreBottom = Color(0xFFC6C8CC)
private val wheelHairline = Color(0xFF9EA0A4)
private val wheelLabel = Color(0xFFA9ACB1)

private fun DrawScope.drawWheelLabel(
    text: String,
    center: Offset,
    displacement: Offset,
    style: TextStyle,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    val layout = textMeasurer.measure(text, style)
    val point = center + displacement
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(point.x - layout.size.width / 2f, point.y - layout.size.height / 2f),
    )
}

/** Right-pointing triangle of height `2 * half`, its left edge at [left]. */
private fun triangle(left: Float, cy: Float, half: Float, pointsRight: Boolean): Path =
    Path().apply {
        val width = half * 1.25f
        if (pointsRight) {
            moveTo(left, cy - half)
            lineTo(left + width, cy)
            lineTo(left, cy + half)
        } else {
            moveTo(left + width, cy - half)
            lineTo(left, cy)
            lineTo(left + width, cy + half)
        }
        close()
    }

/** Two triangles and an end bar: `>>|` forwards, `|<<` backwards. */
private fun DrawScope.drawSkipGlyph(at: Offset, half: Float, forward: Boolean) {
    val width = half * 1.25f
    val bar = half * 0.42f
    val total = width * 2f + bar
    val left = at.x - total / 2f
    if (forward) {
        drawPath(triangle(left, at.y, half, pointsRight = true), wheelLabel)
        drawPath(triangle(left + width, at.y, half, pointsRight = true), wheelLabel)
        drawRect(wheelLabel, Offset(left + width * 2f, at.y - half), Size(bar, half * 2f))
    } else {
        drawRect(wheelLabel, Offset(left, at.y - half), Size(bar, half * 2f))
        drawPath(triangle(left + bar, at.y, half, pointsRight = false), wheelLabel)
        drawPath(triangle(left + bar + width, at.y, half, pointsRight = false), wheelLabel)
    }
}

/** A play triangle followed by pause bars: `>||`. */
private fun DrawScope.drawPlayPauseGlyph(at: Offset, half: Float) {
    val width = half * 1.25f
    val bar = half * 0.42f
    val gap = half * 0.45f
    val total = width + gap + bar * 2f + bar * 0.9f
    val left = at.x - total / 2f
    drawPath(triangle(left, at.y, half, pointsRight = true), wheelLabel)
    val barsLeft = left + width + gap
    drawRect(wheelLabel, Offset(barsLeft, at.y - half), Size(bar, half * 2f))
    drawRect(wheelLabel, Offset(barsLeft + bar * 1.9f, at.y - half), Size(bar, half * 2f))
}
