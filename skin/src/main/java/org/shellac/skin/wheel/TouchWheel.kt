/*
 * Copyright (c) 2026 Shellac Project
 * TouchWheel.kt is part of Shellac.
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
 
package org.shellac.skin.wheel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import kotlin.math.min
import org.shellac.skin.support.SkinTheme
import org.shellac.skin.support.rotaryInput

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
    val labelStyle = SkinTheme.typography.label.copy(color = wheelLabel)

    Canvas(gestureModifier) {
        val outerRadius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val innerRadius = clampedInnerFraction * outerRadius

        // Use one radius from the smaller dimension so the wheel remains circular.
        // The ring is a shallow radial gradient and the centre button is flat and lighter,
        // which is what reads as a dished physical wheel rather than two flat discs.
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(wheelRingInner, wheelRingOuter),
                    center = center,
                    radius = outerRadius,
                ),
            radius = outerRadius,
            center = center,
        )
        drawCircle(color = wheelCentre, radius = innerRadius, center = center)
        drawCircle(
            color = wheelHairline,
            radius = innerRadius,
            center = center,
            style = Stroke(width = 1f),
        )

        // MENU stays a word; the transport controls use the standard glyphs, as on the
        // hardware this is modelled on.
        val labelRadius = (outerRadius + innerRadius) / 2f
        drawWheelLabel("MENU", center, Offset(0f, -labelRadius), labelStyle, textMeasurer)
        drawWheelLabel("▶▶|", center, Offset(labelRadius, 0f), labelStyle, textMeasurer)
        drawWheelLabel("▶ ||", center, Offset(0f, labelRadius), labelStyle, textMeasurer)
        drawWheelLabel("|◀◀", center, Offset(-labelRadius, 0f), labelStyle, textMeasurer)
    }
}

// Silver wheel of the light-cased models: a light ring darkening slightly outward, a paler
// centre button, and a hairline separating them.
private val wheelRingInner = Color(0xFFE2E2E2)
private val wheelRingOuter = Color(0xFFC9C9C9)
private val wheelCentre = Color(0xFFF2F2F2)
private val wheelHairline = Color(0xFFA8A8A8)
private val wheelLabel = Color(0xFF5F5F5F)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWheelLabel(
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
