/*
 * Copyright (c) 2026 Unruly Project
 * WheelHaptics.kt is part of Unruly.
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

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Haptics for the wheel.
 *
 * **Fired per click, never per touch event.** `Modifier.rotaryInput` already accumulates angle and
 * emits one callback per click of travel, so hooking these to its callbacks gives the "one tick per
 * detent" feel of a physical wheel. Driving them from raw pointer movement instead would fire
 * dozens of times a second and feel like a buzz.
 *
 * These deliberately do **not** pass `FLAG_IGNORE_VIEW_SETTING` or `FLAG_IGNORE_GLOBAL_SETTING`: a
 * user who has turned haptics off means it.
 */

/** One detent of wheel rotation. */
internal fun View.performWheelTick() {
    // SEGMENT_FREQUENT_TICK exists precisely for this - a continuous series of ticks as a
    // control passes through discrete positions - and is tuned to stay comfortable when
    // repeated quickly. It arrived in API 34; CLOCK_TICK is the closest older equivalent.
    val constant =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        } else {
            @Suppress("DEPRECATION") HapticFeedbackConstants.CLOCK_TICK
        }
    performHapticFeedback(constant)
}

/** A press on one of the wheel's zones - firmer than a rotation tick. */
internal fun View.performWheelPress() {
    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}
