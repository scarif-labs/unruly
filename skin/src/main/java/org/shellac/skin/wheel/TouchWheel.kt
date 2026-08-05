package org.shellac.skin.wheel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import org.shellac.skin.support.SkinTheme
import org.shellac.skin.support.rotaryInput
import kotlin.math.min

private const val OUTER_RADIUS_FRACTION = 1f

/** Returns the largest circular wheel that fits within the supplied bounds. */
fun wheelDiameterFor(maxWidth: Dp, maxHeight: Dp): Dp = minOf(maxWidth, maxHeight)

/**
 * Draws and handles geometry for the Touch Wheel.
 *
 * Taps and drags use separate pointer-input blocks. The tap block is applied
 * before [rotaryInput]; crossing the drag slop cancels the tap, while a tap
 * never reaches rotaryInput's drag callback.
 */
@Composable
fun TouchWheel(
    modifier: Modifier = Modifier,
    onZonePressed: (WheelZone) -> Unit = {},
    onRotate: (Int) -> Unit = {},
    innerRadiusFraction: Float = 0.42f,
) {
    val colors = SkinTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val clampedInnerFraction = innerRadiusFraction.coerceIn(0f, OUTER_RADIUS_FRACTION)

    val gestureModifier = modifier
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
                )?.let(onZonePressed)
            }
        }
        .rotaryInput(
            innerRadiusFraction = clampedInnerFraction,
            outerRadiusFraction = OUTER_RADIUS_FRACTION,
            onClick = onRotate,
        )

    // SkinTheme.typography is a @Composable @ReadOnlyComposable getter, so it must be read
    // here and not inside the Canvas draw block - a DrawScope is not composable scope.
    val labelStyle = SkinTheme.typography.label.copy(color = colors.onSurface)

    Canvas(gestureModifier) {
        val outerRadius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val innerRadius = clampedInnerFraction * outerRadius

        // Use one radius from the smaller dimension so the wheel remains circular.
        drawCircle(color = colors.surface, radius = outerRadius, center = center)
        drawCircle(color = colors.accent, radius = innerRadius, center = center)
        val labelRadius = (outerRadius + innerRadius) / 2f
        drawWheelLabel("MENU", center, Offset(0f, -labelRadius), labelStyle, textMeasurer)
        drawWheelLabel("NEXT", center, Offset(labelRadius, 0f), labelStyle, textMeasurer)
        drawWheelLabel("PLAY", center, Offset(0f, labelRadius), labelStyle, textMeasurer)
        drawWheelLabel("PREV", center, Offset(-labelRadius, 0f), labelStyle, textMeasurer)
    }
}

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
        topLeft = Offset(
            point.x - layout.size.width / 2f,
            point.y - layout.size.height / 2f,
        ),
    )
}
