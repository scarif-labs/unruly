package org.shellac.skin.wheel

import kotlin.math.atan2
import kotlin.math.sqrt

/** The regions exposed by the Touch Wheel. */
enum class WheelZone {
    MENU,
    NEXT,
    PREVIOUS,
    PLAY_PAUSE,
    CENTER,
}

/**
 * Resolves an angle measured clockwise from the top of the wheel.
 *
 * The intervals are half-open: MENU owns [315, 360) and [0, 45), NEXT owns
 * [45, 135), PLAY_PAUSE owns [135, 225), and PREVIOUS owns [225, 315).
 */
internal fun zoneForAngle(degreesClockwiseFromTop: Float): WheelZone {
    val degrees = ((degreesClockwiseFromTop % DEGREES_PER_TURN) + DEGREES_PER_TURN) % DEGREES_PER_TURN
    return when {
        degrees < 45f -> WheelZone.MENU
        degrees < 135f -> WheelZone.NEXT
        degrees < 225f -> WheelZone.PLAY_PAUSE
        degrees < 315f -> WheelZone.PREVIOUS
        else -> WheelZone.MENU
    }
}

/**
 * Resolves a point relative to the wheel centre, or returns null outside the wheel.
 * Screen coordinates are used: positive X is right and positive Y is down.
 */
internal fun zoneForPoint(
    dx: Float,
    dy: Float,
    outerRadius: Float,
    innerRadius: Float,
): WheelZone? {
    if (!dx.isFinite() || !dy.isFinite() || !outerRadius.isFinite() || !innerRadius.isFinite()) {
        return null
    }
    if (outerRadius <= 0f || innerRadius < 0f || innerRadius > outerRadius) {
        return null
    }

    val radius = sqrt(dx * dx + dy * dy)
    if (radius > outerRadius) return null
    if (radius <= innerRadius) return WheelZone.CENTER

    // atan2(dx, -dy) makes 0° top, 90° right, 180° bottom, and 270° left.
    val degreesClockwiseFromTop = atan2(dx, -dy) * DEGREES_PER_RADIAN
    return zoneForAngle(degreesClockwiseFromTop)
}

private const val DEGREES_PER_TURN = 360f
private const val DEGREES_PER_RADIAN = 57.29578f
