/*
 * Copyright (c) 2026 Unruly Project
 * MediaVolume.kt is part of Unruly.
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

package org.unruly.skin.support

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

// Not in the public SDK, but sent by the system on every stream volume change.
private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"

/**
 * The phone's media volume as Compose state. It follows changes made anywhere else (hardware
 * keys, the system panel, another app) as well as the skin's own.
 */
@Stable
class MediaVolume internal constructor(private val audio: AudioManager) {
    /** The stream's step count. */
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    /**
     * The volume from 0 to 1. Kept as a fraction rather than a step so a slider moves smoothly
     * under the finger; it only snaps to a step when the volume changes elsewhere.
     */
    var fraction by mutableFloatStateOf(stream() / max.toFloat())
        private set

    /** The volume in steps, `0..max`. */
    val level: Int
        get() = (fraction * max).roundToInt()

    fun set(fraction: Float) {
        this.fraction = fraction.coerceIn(0f, 1f)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
    }

    /** Steps the volume, as the hardware keys do. [direction] is an `AudioManager.ADJUST_*` value. */
    fun adjust(direction: Int) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        fraction = stream() / max.toFloat()
    }

    internal fun refresh() {
        // Our own set also triggers the broadcast; leave the in-between fraction alone.
        val now = stream()
        if (now != level) fraction = now / max.toFloat()
    }

    private fun stream() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
}

@Composable
fun rememberMediaVolume(): MediaVolume {
    val context = LocalContext.current
    val volume = remember(context) { MediaVolume(context.getSystemService(AudioManager::class.java)) }
    DisposableEffect(context, volume) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = volume.refresh()
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(VOLUME_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Catch anything that changed between composition and registration.
        volume.refresh()
        onDispose { context.unregisterReceiver(receiver) }
    }
    return volume
}
