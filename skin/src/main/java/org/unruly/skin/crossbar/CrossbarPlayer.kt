/*
 * Copyright (c) 2026 Unruly Project
 * CrossbarPlayer.kt is part of Unruly.
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import org.unruly.skin.SkinLyricsState
import org.unruly.skin.SkinPlaybackState
import org.unruly.skin.SkinRepeatMode
import org.unruly.skin.SkinSession
import org.unruly.skin.support.SkinAlbumArt
import org.unruly.skin.support.activeLyricIndexAt
import org.unruly.skin.support.rememberInterpolatedPosition

/**
 * The playback screen's visualisers. The contract carries no audio samples, so each is driven by a
 * made-up signal that moves only while music plays (as the Amp skin's analyser is).
 */
internal enum class Visualiser(val label: String, val onBlack: Boolean) {
    OFF("Off", false),
    WAVE("Wave", false),
    BARS("Bars", true),
    CLOUD("Cloud", true),
    SCOPE("Scope", true),
}

private class Control(
    val glyph: Glyph?,
    val label: String,
    val text: String? = null,
    val run: () -> Unit,
)

@Composable
internal fun PlayerScreen(
    skin: CrossbarSkin,
    session: SkinSession,
    visualiserIndex: Int,
    setVisualiser: (Int) -> Unit,
) {
    val state by session.playback.collectAsState()
    val position by rememberInterpolatedPosition(state)
    val visualiser = Visualiser.entries.getOrElse(visualiserIndex) { Visualiser.OFF }
    var panel by remember { mutableStateOf(false) }
    var strip by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf(false) }
    // The last control touched, by position, so its label is read after it has acted.
    var hint by remember { mutableIntStateOf(-1) }
    val clock = rememberClockSeconds()

    // The wave visualiser swells the backdrop's own wave with the "music".
    LaunchedEffect(visualiser, state.isPlaying) {
        if (visualiser == Visualiser.WAVE && state.isPlaying) {
            while (true) withFrameNanos {
                skin.playerWaveEnergy = 1.35f + 0.45f * signal(0, clock.value)
            }
        } else {
            skin.playerWaveEnergy = 1f
        }
    }
    DisposableEffect(Unit) { onDispose { skin.playerWaveEnergy = 1f } }

    val song = state.song
    BoxWithConstraints(
        modifier =
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { if (strip) strip = false else panel = !panel }
            }
    ) {
        val wPx = maxWidth / LocalPx.current
        val hPx = maxHeight / LocalPx.current
        val barY = 70f + max(0f, hPx - 272f) * 0.25f
        val raised = visualiser != Visualiser.OFF && visualiser != Visualiser.WAVE || lyrics

        if (
            visualiser == Visualiser.BARS ||
                visualiser == Visualiser.CLOUD ||
                visualiser == Visualiser.SCOPE
        ) {
            val scopeColour = LocalTint.current.topRight
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(Color.Black)
                val t = if (state.isPlaying) clock.value else 0f
                val live = if (state.isPlaying) 1f else 0.15f
                when (visualiser) {
                    Visualiser.BARS -> drawBars(t, live)
                    Visualiser.CLOUD -> drawCloud(t, live)
                    else -> drawScope(t, live, scopeColour)
                }
                // Lyrics read over the visualiser, so it steps back while they show.
                if (lyrics) drawRect(Color.Black.copy(alpha = 0.6f))
            }
        }

        // The band across the top: the list it came from, and where in it.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.fillMaxWidth()
                    .height(px(20f))
                    .background(Ink.Band)
                    .padding(horizontal = px(8f)),
        ) {
            GlyphIcon(Glyph.MUSIC, px(12f))
            Spacer(Modifier.width(px(6f)))
            BasicText(
                skin.group.ifEmpty { song?.album ?: "" },
                style = crossText(Geo.TITLE - 1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (state.queue.isNotEmpty()) {
                BasicText(
                    "(${state.queueIndex + 1}/${state.queue.size})",
                    style = crossText(Geo.TITLE - 1f),
                )
            }
        }

        if (song == null) {
            BasicText(
                "Nothing is playing.",
                style = crossText(Geo.TITLE),
                modifier = Modifier.offset(px(75f), px(barY + Geo.FIRST_ROW - 10f)),
            )
        } else {
            val infoY = if (raised) 56f else barY + Geo.FIRST_ROW
            TrackInfo(state, infoY, wPx)
            if (lyrics) {
                LyricsView(
                    session,
                    song.id,
                    position,
                    top = infoY + 44f,
                    bottom = hPx - (if (panel) 110f else 46f),
                    wPx = wPx,
                )
            }
        }

        // Bottom: play state, time and progress.
        PlayState(state, Modifier.offset(px(12f), px(hPx - 21f)))
        TimeAndProgress(session, state, position, wPx, hPx)

        if (panel) {
            val commands = session.commands
            val row1 =
                listOf(
                    Control(Glyph.PREVIOUS, "Previous Track") { commands.previous() },
                    Control(Glyph.NEXT, "Next Track") { commands.next() },
                    Control(Glyph.REWIND, "Fast Reverse") {
                        commands.seekTo((position - 10_000L).coerceAtLeast(0L))
                    },
                    Control(Glyph.FORWARD, "Fast Forward") {
                        commands.seekTo(min(state.durationMs, position + 10_000L))
                    },
                    Control(Glyph.PLAY, "Play") { if (!state.isPlaying) commands.playPause() },
                    Control(Glyph.PAUSE, "Pause") { if (state.isPlaying) commands.playPause() },
                    Control(Glyph.STOP, "Stop") {
                        if (state.isPlaying) commands.playPause()
                        commands.seekTo(0L)
                    },
                )
            val row2 =
                listOf(
                    Control(
                        if (state.repeatMode == SkinRepeatMode.TRACK) Glyph.REPEAT_ONE
                        else Glyph.REPEAT,
                        when (state.repeatMode) {
                            SkinRepeatMode.NONE -> "Repeat: Off"
                            SkinRepeatMode.ALL -> "Repeat: All"
                            SkinRepeatMode.TRACK -> "Repeat: One"
                        },
                    ) {
                        commands.cycleRepeatMode()
                    },
                    Control(
                        null,
                        if (state.isShuffled) "Shuffle: On" else "Shuffle: Off",
                        text = "SHUF",
                    ) {
                        commands.toggleShuffle()
                    },
                    Control(Glyph.VISUAL, "Visual Player") { strip = !strip },
                    Control(Glyph.LYRICS, if (lyrics) "Lyrics: On" else "Lyrics: Off") {
                        lyrics = !lyrics
                    },
                )
            ControlPanel(
                row1,
                row2,
                wPx,
                hPx,
                (row1 + row2).getOrNull(hint)?.label ?: if (state.isPlaying) "Pause" else "Play",
                lit = { c ->
                    when (c.text) {
                        "SHUF" -> state.isShuffled
                        else ->
                            c.glyph == Glyph.REPEAT && state.repeatMode != SkinRepeatMode.NONE ||
                                c.glyph == Glyph.REPEAT_ONE ||
                                c.glyph == Glyph.LYRICS && lyrics ||
                                c.glyph == Glyph.PLAY && state.isPlaying ||
                                c.glyph == Glyph.PAUSE && !state.isPlaying && position > 0L
                    }
                },
            ) { c ->
                c.run()
                hint = (row1 + row2).indexOf(c)
            }
        }

        if (strip) {
            VisualiserStrip(visualiser, wPx, hPx) {
                setVisualiser(it.ordinal)
                strip = false
            }
        }
    }
}

/** The data the stand-in visualisers run on: a smooth pseudo-random level, 0..1, per band. */
private fun signal(band: Int, t: Float): Float {
    val b = band * 1.37f
    val beat = (sin(t * 2f * PI.toFloat() * 1.9f) * 0.5f + 0.5f).let { it * it }
    val v =
        0.45f +
            0.25f * sin(t * (1.3f + b * 0.11f) + b) +
            0.15f * sin(t * (3.7f + b * 0.07f) + b * 2.1f) +
            0.15f * beat * (1f - band / 24f)
    return v.coerceIn(0f, 1f)
}

@Composable
private fun TrackInfo(state: SkinPlaybackState, infoY: Float, wPx: Float) {
    val song = state.song ?: return
    val cover = 64f
    // Shuffle and repeat marks above the cover's corner, where the device kept its status icons.
    Row(modifier = Modifier.offset(px(8f), px(infoY - cover / 2f - 14f))) {
        if (state.isShuffled) GlyphIcon(Glyph.SHUFFLE, px(11f))
        if (state.repeatMode != SkinRepeatMode.NONE) {
            Spacer(Modifier.width(px(3f)))
            GlyphIcon(
                if (state.repeatMode == SkinRepeatMode.TRACK) Glyph.REPEAT_ONE else Glyph.REPEAT,
                px(11f),
            )
        }
    }
    Box(
        modifier =
            Modifier.offset(px(5f), px(infoY - cover / 2f))
                .size(px(cover))
                .border(px(0.8f), Color.White.copy(alpha = 0.6f))
    ) {
        SkinAlbumArt(
            song.albumArtUri,
            null,
            Modifier.fillMaxSize(),
            fallback = {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.NOTE_TILE, px(40f))
                }
            },
        )
    }
    val textX = 5f + cover + 6f
    BasicText(
        song.title,
        style = crossText(Geo.TITLE),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.offset(px(textX), px(infoY - 19f)).width(px(wPx - textX - 8f)),
    )
    Box(
        modifier =
            Modifier.offset(px(textX), px(infoY))
                .width(px(wPx - textX - 6f))
                .height(px(0.9f))
                .background(Color.White.copy(alpha = 0.85f))
    )
    BasicText(
        song.artist,
        style = crossText(Geo.SUBTITLE + 0.5f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.offset(px(textX), px(infoY + 3f)).width(px(wPx - textX - 70f)),
    )
    val tint = LocalTint.current
    Box(
        modifier =
            Modifier.offset(px(wPx - 62f), px(infoY + 4f))
                .size(px(54f), px(11f))
                .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(px(3f))),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            formatTime(song.durationMs),
            style = crossText(8.5f, FontWeight.Bold, tint.midLeft).copy(shadow = null),
        )
    }
}

@Composable
private fun PlayState(state: SkinPlaybackState, modifier: Modifier) {
    val glyph =
        when {
            state.song == null -> Glyph.STOP
            state.isPlaying -> Glyph.PLAY
            else -> Glyph.PAUSE
        }
    GlyphIcon(glyph, px(16f), modifier)
}

@Composable
private fun TimeAndProgress(
    session: SkinSession,
    state: SkinPlaybackState,
    position: Long,
    wPx: Float,
    hPx: Float,
) {
    val duration = state.durationMs.coerceAtLeast(1L)
    val barLeft = wPx * 0.5f
    val barRight = wPx - 6f
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.offset(0.dp, px(hPx - 36f)).width(px(wPx - 7f)),
    ) {
        Spacer(Modifier.weight(1f))
        BasicText(
            formatTime(position),
            style =
                crossText(Geo.TITLE + 2f, color = Ink.Blue).let {
                    // A faint light halo keeps the blue legible on the darker months.
                    it.copy(
                        shadow =
                            it.shadow?.copy(
                                color = Color.White.copy(alpha = 0.75f),
                                offset = Offset.Zero,
                            )
                    )
                },
        )
        BasicText(
            " / ${formatTime(state.durationMs)}",
            style = crossText(Geo.TITLE - 1f),
            modifier = Modifier.padding(bottom = px(0.5f)),
        )
    }
    // The bar; drag or tap along it to seek.
    Box(
        modifier =
            Modifier.offset(px(barLeft), px(hPx - 20f))
                .size(px(barRight - barLeft), px(14f))
                .pointerInput(duration) {
                    fun seekAt(x: Float) {
                        session.commands.seekTo(
                            ((x / size.width).coerceIn(0f, 1f) * duration).toLong()
                        )
                    }
                    detectTapGestures { seekAt(it.x) }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        session.commands.seekTo(
                            ((change.position.x / size.width).coerceIn(0f, 1f) * duration).toLong()
                        )
                    }
                }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val y = size.height * 0.55f
            val h = size.height * 0.24f
            drawRect(
                Color.Black.copy(alpha = 0.18f),
                Offset(0f, y - h / 2f + h * 0.4f),
                Size(size.width, h),
            )
            drawRect(Color.White, Offset(0f, y - h / 2f), Size(size.width, h))
            val f = (position.toFloat() / duration).coerceIn(0f, 1f)
            drawRect(Ink.Blue, Offset(0f, y - h / 2f - h * 0.15f), Size(size.width * f, h * 1.3f))
        }
    }
}

@Composable
private fun ControlPanel(
    row1: List<Control>,
    row2: List<Control>,
    wPx: Float,
    hPx: Float,
    label: String,
    lit: (Control) -> Boolean,
    onTap: (Control) -> Unit,
) {
    val gap = min(32f, (wPx - 20f) / 8f)
    val right = wPx - 22f
    @Composable
    fun place(controls: List<Control>, y: Float) {
        controls.forEachIndexed { i, c ->
            val x = right - gap * (controls.size - 1 - i)
            val on = lit(c)
            val w = if (c.text != null) 30f else 18f
            Box(
                modifier =
                    Modifier.offset(px(x - w / 2f - 4f), px(y - 13f))
                        .size(px(w + 8f), px(26f))
                        .pointerInput(c.label) { detectTapGestures { onTap(c) } },
                contentAlignment = Alignment.Center,
            ) {
                if (c.text != null) {
                    Box(
                        modifier =
                            Modifier.size(px(w), px(11f))
                                .background(
                                    Color.White.copy(alpha = if (on) 0.95f else 0.45f),
                                    RoundedCornerShape(px(6f)),
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            c.text,
                            style =
                                crossText(7.5f, FontWeight.Bold, LocalTint.current.midLeft)
                                    .copy(shadow = null),
                        )
                    }
                } else {
                    GlyphIcon(
                        c.glyph!!,
                        px(w),
                        alpha = if (on) 1f else 0.55f,
                        glow = if (on) 0.5f else 0f,
                    )
                }
            }
        }
    }
    place(row1, hPx - 97f)
    place(row2, hPx - 69f)
    BasicText(
        label,
        style = crossText(Geo.TITLE - 1f).copy(textAlign = TextAlign.Center),
        modifier =
            Modifier.offset(px(right - gap * 6.5f - 20f), px(hPx - 56f)).width(px(gap * 6.5f + 40f)),
    )
}

@Composable
private fun VisualiserStrip(
    current: Visualiser,
    wPx: Float,
    hPx: Float,
    choose: (Visualiser) -> Unit,
) {
    val w = 56f
    val h = 32f
    val gap = 4f
    val n = Visualiser.entries.size
    val left = wPx - 6f - n * w - (n - 1) * gap
    val scopeColour = LocalTint.current.topRight
    Visualiser.entries.forEachIndexed { i, v ->
        val x = left + i * (w + gap)
        Box(
            modifier =
                Modifier.offset(px(x), px(hPx - h - 4f))
                    .size(px(w), px(h))
                    .border(
                        if (v == current) px(1.5f) else px(0.6f),
                        Color.White.copy(alpha = if (v == current) 1f else 0.5f),
                    )
                    .pointerInput(v) { detectTapGestures { choose(v) } },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                when (v) {
                    Visualiser.OFF -> drawRect(Color(0xFF202020))
                    Visualiser.WAVE -> {
                        drawRect(
                            Brush.verticalGradient(listOf(Color(0xFF1C6FD6), Color(0xFF7ED3F5)))
                        )
                        val p = Path()
                        for (s in 0..20) {
                            val xf = s / 20f
                            val y = size.height * (0.55f + 0.18f * sin(xf * 5f))
                            if (s == 0) p.moveTo(0f, y) else p.lineTo(xf * size.width, y)
                        }
                        drawPath(p, Color.White.copy(alpha = 0.8f), style = Stroke(1.5f))
                    }
                    Visualiser.BARS -> {
                        drawRect(Color.Black)
                        drawBars(1.7f, 1f)
                    }
                    Visualiser.CLOUD -> {
                        drawRect(Color.Black)
                        drawCloud(3f, 1f)
                    }
                    Visualiser.SCOPE -> {
                        drawRect(Color.Black)
                        drawScope(2f, 1f, scopeColour)
                    }
                }
            }
            if (v == Visualiser.OFF) BasicText("OFF", style = crossText(9f, FontWeight.Bold))
        }
    }
}

// -------------------------------------------------------------------------------------------
// The stand-in visualisers
// -------------------------------------------------------------------------------------------

/** Columns of lit dots, cool at the foot and hot at the top. */
private fun DrawScope.drawBars(t: Float, live: Float) {
    val cols = 16
    val rows = 12
    val cw = size.width / cols
    val ch = size.height * 0.6f / rows
    val base = size.height * 0.85f
    val r = min(cw, ch) * 0.28f
    for (c in 0 until cols) {
        val level = (signal(c, t) * live * rows).toInt()
        for (rI in 0 until rows) {
            val on = rI < level
            val f = rI / (rows - 1f)
            val colour =
                when {
                    f < 0.6f -> Color(0xFF3FE6F2)
                    else -> Color(0xFFF2A23A)
                }
            drawCircle(
                if (on) colour else colour.copy(alpha = 0.08f),
                r,
                Offset(cw * (c + 0.5f), base - ch * rI),
            )
        }
    }
}

/** Soft coloured clouds drifting and swelling. */
private fun DrawScope.drawCloud(t: Float, live: Float) {
    val colours =
        listOf(
            Color(0xFFE0418A),
            Color(0xFF6A3BE0),
            Color(0xFFF29B2E),
            Color(0xFF2E9BE0),
            Color(0xFFE0E040),
        )
    for ((i, c) in colours.withIndex()) {
        val cx = size.width * (0.5f + 0.35f * sin(t * 0.21f * (i + 1) + i))
        val cy = size.height * (0.5f + 0.3f * sin(t * 0.17f * (i + 2) + i * 2f))
        val radius = min(size.width, size.height) * (0.25f + 0.25f * signal(i * 3, t) * live)
        drawCircle(
            Brush.radialGradient(
                listOf(c.copy(alpha = 0.55f), Color.Transparent),
                Offset(cx, cy),
                radius,
            ),
            radius,
            Offset(cx, cy),
        )
    }
}

/** An oscilloscope: a few traces of one bright line, ringing with the level. */
private fun DrawScope.drawScope(t: Float, live: Float, colour: Color) {
    val mid = size.height * 0.55f
    for (trace in 0 until 3) {
        val p = Path()
        val steps = 96
        for (s in 0..steps) {
            val xf = s / steps.toFloat()
            val env = sin(xf * PI.toFloat())
            val amp =
                size.height * 0.12f * live * env * (0.4f + signal(trace * 5, t - trace * 0.05f))
            val y =
                mid + amp * (sin(xf * 38f + t * 9f + trace) * 0.7f + sin(xf * 91f - t * 13f) * 0.3f)
            if (s == 0) p.moveTo(0f, y) else p.lineTo(xf * size.width, y)
        }
        drawPath(
            p,
            colour.copy(alpha = 0.75f - 0.22f * trace),
            style = Stroke(width = size.height * 0.004f + 1f, cap = StrokeCap.Round),
        )
    }
}

// -------------------------------------------------------------------------------------------
// Lyrics (on the control panel's Display button)
// -------------------------------------------------------------------------------------------

@Composable
private fun LyricsView(
    session: SkinSession,
    songId: String,
    position: Long,
    top: Float,
    bottom: Float,
    wPx: Float,
) {
    val state by
        remember(session.lyrics, songId) { session.lyrics.lyrics(songId) }
            .collectAsState(initial = SkinLyricsState.Loading)
    val lines: List<String>
    val active: Int
    when (val s = state) {
        is SkinLyricsState.Found -> {
            if (s.lyrics.isSynced) {
                lines = s.lyrics.synced.map { it.text }
                active = activeLyricIndexAt(s.lyrics.synced, position)
            } else {
                lines = s.lyrics.plain
                active = -1
            }
        }
        SkinLyricsState.Loading -> {
            lines = listOf("Loading lyrics...")
            active = 0
        }
        SkinLyricsState.Disabled -> {
            lines = listOf("Lyrics are turned off in Unruly Settings.")
            active = 0
        }
        is SkinLyricsState.Failed -> {
            lines = listOf("The lyrics could not be loaded.")
            active = 0
        }
        else -> {
            lines = listOf("No lyrics were found.")
            active = 0
        }
    }
    val lineH = 19f
    val visible = max(1, ((bottom - top) / lineH).toInt())
    val first =
        if (active < 0) 0 else (active - visible / 2).coerceIn(0, max(0, lines.size - visible))
    Column(modifier = Modifier.offset(px(14f), px(top)).width(px(wPx - 28f))) {
        for (i in first until min(lines.size, first + visible)) {
            val on = i == active || active < 0
            BasicText(
                lines[i].ifBlank { " " },
                style = crossText(if (i == active) Geo.TITLE else Geo.SUBTITLE + 1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(px(lineH)).alpha(if (on) 1f else 0.5f).fillMaxWidth(),
            )
        }
    }
}
