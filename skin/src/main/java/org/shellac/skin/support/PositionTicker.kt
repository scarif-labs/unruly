package org.shellac.skin.support

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import org.shellac.skin.SkinPlaybackState

private const val SEEK_RESYNC_THRESHOLD_MS = 500L

/**
 * Returns a frame-clock-driven position between the playback engine's coarse updates.
 *
 * The engine position remains the source of truth: interpolation is reset on every
 * meaningful engine update and is clamped to the current track duration.
 */
@Composable
fun rememberInterpolatedPosition(state: SkinPlaybackState): State<Long> {
    val latestState = rememberUpdatedState(state)
    val position =
        remember(state.song?.id) {
            mutableStateOf(clampPosition(state.positionMs, state.durationMs))
        }

    LaunchedEffect(state.song?.id) {
        var lastEnginePositionMs = clampPosition(latestState.value.positionMs, latestState.value.durationMs)
        var lastEngineUpdateUptimeMs = Long.MIN_VALUE
        var lastObservedEnginePositionMs = lastEnginePositionMs
        var lastDurationMs = latestState.value.durationMs.coerceAtLeast(0L)
        var lastSongId = latestState.value.song?.id
        var wasPlaying = latestState.value.isPlaying
        var displayedPositionMs = position.value

        while (true) {
            withFrameMillis { nowUptimeMs ->
                val current = latestState.value
                val durationMs = current.durationMs.coerceAtLeast(0L)
                val enginePositionMs = clampPosition(current.positionMs, durationMs)
                val songChanged = current.song?.id != lastSongId
                val durationChanged = durationMs != lastDurationMs

                if (lastEngineUpdateUptimeMs == Long.MIN_VALUE) {
                    lastEngineUpdateUptimeMs = nowUptimeMs
                }

                if (songChanged || durationChanged) {
                    lastEnginePositionMs = enginePositionMs
                    lastEngineUpdateUptimeMs = nowUptimeMs
                    lastObservedEnginePositionMs = enginePositionMs
                    lastDurationMs = durationMs
                    lastSongId = current.song?.id
                    wasPlaying = current.isPlaying
                    displayedPositionMs = enginePositionMs
                } else if (!current.isPlaying) {
                    // A paused engine value is authoritative and must be held exactly.
                    lastEnginePositionMs = enginePositionMs
                    lastEngineUpdateUptimeMs = nowUptimeMs
                    lastObservedEnginePositionMs = enginePositionMs
                    wasPlaying = false
                    displayedPositionMs = enginePositionMs
                } else if (!wasPlaying) {
                    // Resume from the latest engine value without carrying paused time.
                    lastEnginePositionMs = enginePositionMs
                    lastEngineUpdateUptimeMs = nowUptimeMs
                    lastObservedEnginePositionMs = enginePositionMs
                    wasPlaying = true
                    displayedPositionMs = enginePositionMs
                } else {
                    if (enginePositionMs != lastObservedEnginePositionMs) {
                        val interpolatedBeforeUpdate =
                            interpolate(
                                lastEnginePositionMs,
                                lastEngineUpdateUptimeMs,
                                nowUptimeMs,
                                isPlaying = true,
                                durationMs = durationMs)

                        if (distanceExceedsThreshold(
                                enginePositionMs,
                                interpolatedBeforeUpdate,
                                SEEK_RESYNC_THRESHOLD_MS)) {
                            // A seek is allowed to move the display in either direction.
                            displayedPositionMs = enginePositionMs
                        } else {
                            // Do not let small polling jitter move normal playback backwards.
                            displayedPositionMs =
                                maxOf(displayedPositionMs, enginePositionMs)
                        }

                        lastEnginePositionMs = enginePositionMs
                        lastEngineUpdateUptimeMs = nowUptimeMs
                        lastObservedEnginePositionMs = enginePositionMs
                    }

                    val interpolatedPositionMs =
                        interpolate(
                            lastEnginePositionMs,
                            lastEngineUpdateUptimeMs,
                            nowUptimeMs,
                            isPlaying = true,
                            durationMs = durationMs)
                    displayedPositionMs = maxOf(displayedPositionMs, interpolatedPositionMs)
                }

                position.value = clampPosition(displayedPositionMs, durationMs)
            }
        }
    }

    return position
}

/**
 * Interpolates a single engine sample to [nowUptimeMs]. The result is always in the
 * range 0..[durationMs], and paused playback returns the engine sample unchanged.
 */
internal fun interpolate(
    lastEnginePositionMs: Long,
    lastEngineUpdateUptimeMs: Long,
    nowUptimeMs: Long,
    isPlaying: Boolean,
    durationMs: Long
): Long {
    val duration = durationMs.coerceAtLeast(0L)
    if (duration == 0L) return 0L

    val basePosition = lastEnginePositionMs.coerceIn(0L, duration)
    if (!isPlaying) return basePosition

    val elapsedMs =
        if (nowUptimeMs > lastEngineUpdateUptimeMs) {
            nowUptimeMs - lastEngineUpdateUptimeMs
        } else {
            0L
        }

    // Add without overflowing if a malformed/very long clock interval is supplied.
    val remainingMs = duration - basePosition
    return if (elapsedMs >= remainingMs) duration else basePosition + elapsedMs
}

private fun clampPosition(positionMs: Long, durationMs: Long): Long {
    val duration = durationMs.coerceAtLeast(0L)
    return if (duration == 0L) 0L else positionMs.coerceIn(0L, duration)
}

private fun distanceExceedsThreshold(first: Long, second: Long, threshold: Long): Boolean =
    if (first >= second) first - second > threshold else second - first > threshold
