/*
 * Copyright (c) 2026 Shellac Project
 * RotaryGesture.kt is part of Shellac.
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
 
package org.shellac.skin.support

// detectDragGestures lives in foundation.gestures, NOT ui.input.pointer.
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Detects clockwise/counter-clockwise rotation in a ring around the composable's centre. Clockwise
 * movement emits positive clicks.
 */
fun Modifier.rotaryInput(
    degreesPerClick: Float = 13f,
    innerRadiusFraction: Float = 0.30f,
    outerRadiusFraction: Float = 1.0f,
    onClick: (delta: Int) -> Unit,
): Modifier =
    pointerInput(degreesPerClick, innerRadiusFraction, outerRadiusFraction, onClick) {
        val clickSize = degreesPerClick
        val innerFraction = innerRadiusFraction.coerceAtLeast(0f)
        val outerFraction = outerRadiusFraction.coerceAtLeast(0f)

        fun isInRing(position: Offset): Boolean {
            val width = size.width.toFloat()
            val height = size.height.toFloat()
            val halfSize = min(width, height) / 2f
            if (halfSize <= 0f || innerFraction > outerFraction) return false

            val center = Offset(width / 2f, height / 2f)
            val dx = position.x - center.x
            val dy = position.y - center.y
            val radius = sqrt(dx * dx + dy * dy)
            return radius >= innerFraction * halfSize && radius <= outerFraction * halfSize
        }

        fun angleAt(position: Offset): Float {
            val center = Offset(size.width / 2f, size.height / 2f)
            // In screen coordinates +Y points down, so atan2's increasing angle is clockwise.
            return atan2(position.y - center.y, position.x - center.x) * (180f / PI.toFloat())
        }

        var rotationStartedInRing = false
        var previousAngle: Float? = null
        var accumulatedDegrees = 0f

        detectDragGestures(
            onDragStart = { position ->
                accumulatedDegrees = 0f
                rotationStartedInRing = isInRing(position)
                previousAngle = if (rotationStartedInRing) angleAt(position) else null
            },
            onDragEnd = {
                rotationStartedInRing = false
                previousAngle = null
                accumulatedDegrees = 0f
            },
            onDragCancel = {
                rotationStartedInRing = false
                previousAngle = null
                accumulatedDegrees = 0f
            },
            onDrag = { change, _ ->
                if (!rotationStartedInRing || !clickSize.isFinite() || clickSize <= 0f) {
                    return@detectDragGestures
                }

                val position = change.position
                if (!isInRing(position)) {
                    // Re-entry is re-anchored below; the invalid segment contributes no angle.
                    previousAngle = null
                    return@detectDragGestures
                }

                val currentAngle = angleAt(position)
                val oldAngle = previousAngle
                if (oldAngle == null) {
                    previousAngle = currentAngle
                    return@detectDragGestures
                }

                accumulatedDegrees += angleDeltaDegrees(oldAngle, currentAngle)
                previousAngle = currentAngle

                while (accumulatedDegrees >= clickSize) {
                    onClick(+1)
                    accumulatedDegrees -= clickSize
                }
                while (accumulatedDegrees <= -clickSize) {
                    onClick(-1)
                    accumulatedDegrees += clickSize
                }
            },
        )
    }

/** Returns the shortest signed distance from [fromDegrees] to [toDegrees] in (-180, 180]. */
internal fun angleDeltaDegrees(fromDegrees: Float, toDegrees: Float): Float {
    var delta = (toDegrees - fromDegrees) % 360f
    if (delta <= -180f) delta += 360f
    if (delta > 180f) delta -= 360f
    return delta
}
