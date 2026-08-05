package org.shellac.skin.wheel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.shellac.skin.SkinSession
import org.shellac.skin.support.SkinAlbumArt
import org.shellac.skin.support.SkinTheme
import org.shellac.skin.support.rememberInterpolatedPosition

private const val SCRUB_IDLE_MS = 300L
private const val SEEK_HANDOVER_TOLERANCE_MS = 100L
private const val SEEK_HANDOVER_TIMEOUT_MS = 1_000L
private const val MIN_SCRUB_STEP_MS = 1_000L
private const val SCRUB_PERCENT_DENOMINATOR = 100L

/** State shared by the now-playing body and the wheel's rotation callback. */
internal class WheelNowPlayingState {
    private var songId: String? = null
    private var durationMs: Long = 0L
    private var livePositionMs: Long = 0L

    var scrubTargetMs by mutableStateOf<Long?>(null)
        private set

    var seekHoldMs by mutableStateOf<Long?>(null)
        private set

    fun update(
        songId: String?,
        durationMs: Long,
        livePositionMs: Long,
    ) {
        if (this.songId != songId) {
            scrubTargetMs = null
            seekHoldMs = null
        }
        this.songId = songId
        this.durationMs = durationMs.coerceAtLeast(0L)
        this.livePositionMs = livePositionMs.coerceAtLeast(0L)
    }

    fun onRotate(delta: Int) {
        if (delta == 0 || durationMs <= 0L) return

        val basePositionMs = scrubTargetMs ?: seekHoldMs ?: livePositionMs
        val stepMs = scrubStepMs(durationMs)
        scrubTargetMs = moveScrubTarget(
            basePositionMs = basePositionMs,
            delta = delta,
            stepMs = stepMs,
            durationMs = durationMs,
        )
    }

    fun commitScrub(targetMs: Long) {
        // Hold the target after the command: the engine's next 10 Hz sample can still be
        // the pre-seek position, and immediately following it would visibly jump backwards.
        seekHoldMs = targetMs
        scrubTargetMs = null
    }

    fun clearSeekHold(expectedTargetMs: Long) {
        if (seekHoldMs == expectedTargetMs) seekHoldMs = null
    }
}

@Composable
internal fun rememberWheelNowPlayingState(): WheelNowPlayingState = remember {
    WheelNowPlayingState()
}

@Composable
internal fun WheelNowPlaying(
    session: SkinSession,
    state: WheelNowPlayingState,
) {
    val playback by session.playback.collectAsState()
    val song = playback.song
    val livePositionMs by rememberInterpolatedPosition(playback)
    val durationMs = playback.durationMs.coerceAtLeast(0L)
    state.update(
        songId = song?.id,
        durationMs = durationMs,
        livePositionMs = livePositionMs,
    )

    val scrubTargetMs = state.scrubTargetMs?.coerceIn(0L, durationMs)

    LaunchedEffect(scrubTargetMs, song?.id, durationMs) {
        val targetMs = scrubTargetMs ?: return@LaunchedEffect
        if (song == null || durationMs <= 0L) return@LaunchedEffect

        delay(SCRUB_IDLE_MS)
        session.commands.seekTo(positionMs = targetMs)
        state.commitScrub(targetMs)
    }

    // The raw engine position is used only as the seek acknowledgement; rendering continues
    // to use the interpolated position (or the held target) so the handover cannot jump back.
    LaunchedEffect(state.seekHoldMs, playback.positionMs, song?.id, durationMs) {
        val heldTargetMs = state.seekHoldMs ?: return@LaunchedEffect
        if (hasReachedSeekTarget(playback.positionMs, heldTargetMs)) {
            state.clearSeekHold(expectedTargetMs = heldTargetMs)
        }
    }

    // Safety net for the acknowledgement above. It only fires when a sample lands within
    // one decisecond of the target, and nothing guarantees one does: a seek can settle
    // slightly off, or the 10 Hz sampling can step straight over the window while playing.
    // Without this the hold would never clear and the progress bar would sit frozen at the
    // target while the audio carried on - a worse artefact than the backwards jump the hold
    // exists to prevent. Keyed on the hold alone so engine samples do not restart the timer.
    LaunchedEffect(state.seekHoldMs) {
        val heldTargetMs = state.seekHoldMs ?: return@LaunchedEffect
        delay(SEEK_HANDOVER_TIMEOUT_MS)
        state.clearSeekHold(expectedTargetMs = heldTargetMs)
    }

    val displayedPositionMs =
        (scrubTargetMs ?: state.seekHoldMs ?: livePositionMs).coerceIn(0L, durationMs)
    val progress = progressFraction(displayedPositionMs, durationMs)
    val colors = SkinTheme.colors
    val typography = SkinTheme.typography

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).padding(20.dp),
    ) {
        BasicText(
            "Now Playing",
            style = typography.title.copy(color = colors.onBackground),
        )
        if (song == null) {
            BasicText(
                "Nothing playing",
                modifier = Modifier.padding(top = 20.dp),
                style = typography.body.copy(color = colors.onBackground),
            )
        } else {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                val artSize = minOf(maxWidth, maxHeight * 0.43f)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.size(artSize),
                    ) {
                        SkinAlbumArt(
                            uri = song.albumArtUri,
                            contentDescription = song.album,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            fallback = {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(colors.surface),
                                )
                            },
                        )
                    }
                    BasicText(
                        song.title,
                        modifier = Modifier.padding(top = 10.dp),
                        style = typography.title.copy(color = colors.onBackground),
                    )
                    BasicText(
                        song.artist,
                        modifier = Modifier.padding(top = 3.dp),
                        style = typography.body.copy(color = colors.onSurface),
                    )
                    BasicText(
                        song.album,
                        modifier = Modifier.padding(top = 2.dp),
                        style = typography.body.copy(color = colors.onSurface),
                    )
                    BasicText(
                        queuePositionText(playback.queueIndex, playback.queue.size),
                        modifier = Modifier.padding(top = 5.dp),
                        style = typography.label.copy(color = colors.onSurface),
                    )
                    ProgressBar(
                        fraction = progress,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        BasicText(
                            formatTime(displayedPositionMs),
                            style = typography.label.copy(
                                color = if (scrubTargetMs != null) colors.accent else colors.onSurface,
                                fontWeight = if (scrubTargetMs != null) FontWeight.Bold else null,
                            ),
                        )
                        BasicText(
                            "-${formatTime(durationMs - displayedPositionMs)}",
                            style = typography.label.copy(color = colors.onSurface),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(
    fraction: Float,
    modifier: Modifier,
) {
    val colors = SkinTheme.colors
    Canvas(modifier = modifier.height(8.dp)) {
        val safeFraction = fraction.coerceIn(0f, 1f)
        drawRect(
            color = colors.surface,
            topLeft = androidx.compose.ui.geometry.Offset.Zero,
            size = size,
        )
        drawRect(
            color = colors.accent,
            topLeft = androidx.compose.ui.geometry.Offset.Zero,
            size = Size(size.width * safeFraction, size.height),
        )
    }
}

internal fun formatTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1_000L
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = totalSeconds / 3_600L
    return if (hours > 0L) {
        "$hours:${twoDigits(minutes)}:${twoDigits(seconds)}"
    } else {
        "${minutes}:${twoDigits(seconds)}"
    }
}

internal fun progressFraction(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toDouble().coerceIn(0.0, durationMs.toDouble()) / durationMs.toDouble())
        .toFloat()
        .coerceIn(0f, 1f)
}

internal fun scrubStepMs(durationMs: Long): Long =
    if (durationMs <= 0L) 0L
    else maxOf(MIN_SCRUB_STEP_MS, durationMs / SCRUB_PERCENT_DENOMINATOR)

private fun twoDigits(value: Long): String = value.toString().padStart(2, '0')

private fun moveScrubTarget(
    basePositionMs: Long,
    delta: Int,
    stepMs: Long,
    durationMs: Long,
): Long {
    val base = basePositionMs.coerceIn(0L, durationMs)
    return if (delta > 0) {
        val clicks = delta.toLong()
        if (clicks > (durationMs - base) / stepMs) durationMs
        else base + clicks * stepMs
    } else {
        val clicks = -delta.toLong()
        if (clicks > base / stepMs) 0L
        else base - clicks * stepMs
    }
}

private fun queuePositionText(queueIndex: Int, queueSize: Int): String {
    val position = if (queueIndex in 0 until queueSize) queueIndex + 1 else 1
    return "$position of $queueSize"
}

private fun hasReachedSeekTarget(enginePositionMs: Long, targetMs: Long): Boolean {
    val distance = if (enginePositionMs >= targetMs) {
        enginePositionMs - targetMs
    } else {
        targetMs - enginePositionMs
    }
    return distance <= SEEK_HANDOVER_TOLERANCE_MS
}
