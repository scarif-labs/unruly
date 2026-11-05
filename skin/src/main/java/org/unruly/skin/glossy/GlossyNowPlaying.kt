/*
 * Copyright (c) 2026 Unruly Project
 * GlossyNowPlaying.kt is part of Unruly.
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

package org.unruly.skin.glossy

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.unruly.skin.SkinLyricsState
import org.unruly.skin.SkinRepeatMode
import org.unruly.skin.SkinSession
import org.unruly.skin.SkinSong
import org.unruly.skin.support.SkinAlbumArt
import org.unruly.skin.support.activeLyricIndexAt
import org.unruly.skin.support.rememberInterpolatedPosition
import org.unruly.skin.support.rememberMediaVolume

/**
 * The player: black glossy bars, the cover full width between them. Tapping the cover slides the
 * control band over its top - track count, scrubber, repeat, lyrics and shuffle. The list button
 * flips the cover over to the album's track list.
 */
@Composable
internal fun NowPlayingScreen(skin: GlossySkin, session: SkinSession, startWithLyrics: Boolean) {
    val playback by session.playback.collectAsState()
    val position by rememberInterpolatedPosition(playback)
    val song = playback.song
    var lyrics by remember { mutableStateOf(startWithLyrics) }
    var flipped by remember { mutableStateOf(false) }
    val flip by animateFloatAsState(if (flipped) 180f else 0f, tween(600), label = "flip")

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        PlayerNavBar(skin, song, flipped) { flipped = !flipped }
        // Between the bars: the artwork stage. The control band and the transport are translucent
        // and float over it at top and bottom; the cover sits centred in the space between them,
        // and a blurred, enlarged copy of it fills the rest, so the band and transport take on the
        // art's colour as on the original instead of sitting on flat black.
        // Clipped: a blur spreads past its own bounds and would smear over the title bar.
        Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            if (flip <= 90f) {
                SkinAlbumArt(
                    uri = song?.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { renderEffect = StageBlur }.drawWithContent {
                        drawContent()
                        drawRect(Color.Black.copy(alpha = 0.18f))
                    },
                    contentScale = ContentScale.Crop,
                    fallback = { Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2A2A2C))) },
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Glossy.TrackRowBottom))
            }
            Column(modifier = Modifier.fillMaxSize()) {
                if (song != null) ControlBand(session, position, lyrics) { lyrics = !lyrics }
                Box(
                    modifier =
                        Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                            rotationY = flip
                            cameraDistance = 12f * density
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (flip <= 90f) {
                        // Square, as large as the space allows in either direction.
                        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(minOf(maxWidth, maxHeight))) {
                            SkinAlbumArt(
                                uri = song?.albumArtUri,
                                contentDescription = song?.album,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                fallback = {
                                    Box(
                                        modifier =
                                            Modifier.fillMaxSize()
                                                .background(Brush.verticalGradient(listOf(Color(0xFF3A3A3C), Color(0xFF1A1A1C)))),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        BasicText("♪", style = heros(120f, Color(0xFF555557)))
                                    }
                                },
                            )
                            if (lyrics && song != null) LyricsOverlay(session, song, position, under = false)
                        }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) { TrackList(session, song) }
                    }
                }
                TransportBar(session, playback.isPlaying)
            }
        }
        VolumeBar()
    }
}

/** Blur for the stage behind the cover. Render effects need API 31; older devices get it sharp. */
private val StageBlur =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        android.graphics.RenderEffect.createBlurEffect(60f, 60f, android.graphics.Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    } else {
        null
    }

/** Black nav bar: back arrow, artist / title / album in three lines, list or cover button. */
@Composable
private fun PlayerNavBar(skin: GlossySkin, song: SkinSong?, flipped: Boolean, onList: () -> Unit) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(pt(44))
                .background(Brush.verticalGradient(listOf(Glossy.NpNavTop, Glossy.NpNavBottom)))
                .drawBehind {
                    drawLine(Color(0xFF404040), Offset(0f, 0f), Offset(size.width, 0f), 2f)
                }
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = pt(56)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Three 12-point lines in a 44-point bar: the line height has to be tight and the
            // font's own padding trimmed, or the album line falls off the bottom.
            val line =
                androidx.compose.ui.text.TextStyle(
                    textAlign = TextAlign.Center,
                    lineHeight = ptSp(12.5f),
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle =
                        androidx.compose.ui.text.style.LineHeightStyle(
                            androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                        ),
                )
            // Each line gets a fixed 13-point slot and draws centred in it, whatever the
            // font's own metrics ask for.
            val lineBox = Modifier.height(pt(13f)).wrapContentHeight(unbounded = true)
            BasicText(song?.artist.orEmpty(), modifier = lineBox, style = heros(12f, Glossy.NpArtist, bold = false).merge(line), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(song?.title.orEmpty(), modifier = lineBox, style = heros(12f, Color.White).merge(line), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(song?.album.orEmpty(), modifier = lineBox, style = heros(12f, Glossy.NpAlbum, bold = false).merge(line), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DarkSquareButton(Modifier.align(Alignment.CenterStart).padding(start = pt(5)), arrow = true, onClick = { skin.pop() }) {
            // Back arrow.
            val w = size.width
            val h = size.height
            drawLine(Color.White, Offset(w * 0.3f, h * 0.5f), Offset(w * 0.75f, h * 0.5f), h * 0.16f)
            drawPath(
                Path().apply {
                    moveTo(w * 0.18f, h * 0.5f)
                    lineTo(w * 0.45f, h * 0.22f)
                    lineTo(w * 0.45f, h * 0.78f)
                    close()
                },
                Color.White,
            )
        }
        if (flipped && song != null) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterEnd)
                        .padding(end = pt(8))
                        .size(pt(30))
                        .border(1.dp, Color(0xFF555555))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onList)
            ) {
                SkinAlbumArt(uri = song.albumArtUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        } else {
            DarkSquareButton(Modifier.align(Alignment.CenterEnd).padding(end = pt(5)), onClick = onList) {
                // List icon: three bullets and lines.
                val w = size.width
                val h = size.height
                for (i in 0..2) {
                    val y = h * (0.32f + i * 0.18f)
                    drawCircle(Color.White, h * 0.04f, Offset(w * 0.3f, y))
                    drawLine(Color.White, Offset(w * 0.38f, y), Offset(w * 0.72f, y), h * 0.07f)
                }
            }
        }
    }
}

/** The player's bar buttons: a dark glossy rounded square, or arrow-ended for back. */
@Composable
private fun DarkSquareButton(modifier: Modifier, arrow: Boolean = false, onClick: () -> Unit, glyph: DrawScope.() -> Unit) {
    Box(
        modifier =
            modifier
                .size(pt(if (arrow) 52 else 36), pt(30))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.height * 0.17f
            val tip = if (arrow) size.height * 0.36f else 0f
            val path =
                Path().apply {
                    if (arrow) {
                        moveTo(tip, 0f)
                        lineTo(size.width - r, 0f)
                        quadraticTo(size.width, 0f, size.width, r)
                        lineTo(size.width, size.height - r)
                        quadraticTo(size.width, size.height, size.width - r, size.height)
                        lineTo(tip, size.height)
                        lineTo(0f, size.height / 2)
                        close()
                    } else {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
                    }
                }
            drawPath(path, Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF1C1C1C), Color(0xFF111111))))
            drawPath(path, Color(0xFF000000), style = Stroke(2f))
            drawLine(Color(0x33FFFFFF), Offset(tip + r, 2f), Offset(size.width - r, 2f), 1.5f)
            translateGlyph(if (arrow) tip * 0.35f else 0f, glyph)
        }
    }
}

private fun DrawScope.translateGlyph(dx: Float, glyph: DrawScope.() -> Unit) {
    drawContext.transform.translate(dx, 0f)
    glyph()
    drawContext.transform.translate(-dx, 0f)
}

/** The translucent band over the cover's top: count, scrubber, repeat / lyrics / shuffle. */
@Composable
private fun ControlBand(session: SkinSession, positionMs: Long, lyricsOn: Boolean, onLyrics: () -> Unit) {
    val playback by session.playback.collectAsState()
    Column(
        modifier = Modifier.fillMaxWidth().background(Glossy.NpBand).padding(horizontal = pt(10), vertical = pt(4)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (playback.queue.isNotEmpty()) {
            BasicText("${playback.queueIndex + 1} of ${playback.queue.size}", style = heros(11f, Color.White))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(formatTime(positionMs), style = heros(12f, Color.White), modifier = Modifier.width(pt(34)))
            Scrubber(
                fraction = if (playback.durationMs > 0) positionMs.toFloat() / playback.durationMs else 0f,
                onSeek = { session.commands.seekTo((it * playback.durationMs).toLong()) },
                modifier = Modifier.weight(1f),
            )
            BasicText(
                "-${formatTime(playback.durationMs - positionMs)}",
                style = heros(12f, Color.White).copy(textAlign = TextAlign.End),
                modifier = Modifier.width(pt(40)),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().height(pt(26)), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(onClick = session.commands::cycleRepeatMode) {
                drawRepeatGlyph(if (playback.repeatMode == SkinRepeatMode.NONE) InactiveGlyph else Glossy.Orange, playback.repeatMode == SkinRepeatMode.TRACK)
            }
            GlyphButton(onClick = onLyrics) { drawLyricsGlyph(if (lyricsOn) Glossy.Orange else InactiveGlyph) }
            GlyphButton(onClick = session.commands::toggleShuffle) {
                drawShuffleGlyph(if (playback.isShuffled) Glossy.Orange else InactiveGlyph)
            }
        }
    }
}

/** Repeat, lyrics and shuffle when off: a soft grey, so the lit ones stand out. */
private val InactiveGlyph = Color(0xFFC8C8C8)

@Composable
private fun GlyphButton(onClick: () -> Unit, glyph: DrawScope.() -> Unit) {
    Box(
        modifier =
            Modifier.size(pt(40), pt(26))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(pt(22), pt(15)), onDraw = glyph)
    }
}

private fun DrawScope.drawRepeatGlyph(color: Color, one: Boolean) {
    val w = size.width
    val h = size.height
    val stroke = h * 0.14f
    drawRoundRect(color, Offset(stroke, h * 0.2f), Size(w - stroke * 2, h * 0.6f), CornerRadius(h * 0.25f), style = Stroke(stroke))
    drawPath(Path().apply { moveTo(w * 0.62f, h * 0.02f); lineTo(w * 0.8f, h * 0.2f); lineTo(w * 0.62f, h * 0.38f); close() }, color)
    drawPath(Path().apply { moveTo(w * 0.38f, h * 0.62f); lineTo(w * 0.2f, h * 0.8f); lineTo(w * 0.38f, h * 0.98f); close() }, color)
    if (one) drawLine(color, Offset(w * 0.5f, h * 0.35f), Offset(w * 0.5f, h * 0.65f), stroke)
}

private fun DrawScope.drawLyricsGlyph(color: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(color, Offset(w * 0.12f, 0f), Size(w * 0.76f, h * 0.75f), CornerRadius(h * 0.2f), style = Stroke(h * 0.12f))
    drawPath(Path().apply { moveTo(w * 0.3f, h * 0.72f); lineTo(w * 0.3f, h); lineTo(w * 0.5f, h * 0.72f); close() }, color)
    for (i in 0..1) drawLine(color, Offset(w * 0.28f, h * (0.25f + i * 0.22f)), Offset(w * 0.72f, h * (0.25f + i * 0.22f)), h * 0.09f)
}

/** The scrubber: a dark recessed track with a lighter played part and a glossy white knob. */
@Composable
private fun Scrubber(fraction: Float, onSeek: (Float) -> Unit, modifier: Modifier) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val seek by rememberUpdatedState(onSeek)
    val shown = (drag ?: fraction).coerceIn(0f, 1f)
    Box(
        modifier =
            modifier.height(pt(24)).pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        drag?.let(seek)
                        drag = null
                    },
                    onDragCancel = { drag = null },
                    onHorizontalDrag = { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f) },
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackH = size.height * 0.34f
            val top = (size.height - trackH) / 2
            val r = CornerRadius(trackH / 2)
            blueTrack(shown, size.height * 0.26f)
            knob(Offset(size.width * shown, size.height / 2), size.height * 0.3f)
        }
    }
}

/**
 * A slider track: light grey glass, filled in glossy blue up to [fraction]. Used by the scrubber
 * and the volume slider alike.
 */
private fun DrawScope.blueTrack(fraction: Float, trackH: Float) {
    val top = (size.height - trackH) / 2
    val r = CornerRadius(trackH / 2)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFBDBDBD), Color(0xFFF2F2F2)), startY = top, endY = top + trackH),
        Offset(0f, top),
        Size(size.width, trackH),
        r,
    )
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF8EC0F7), Color(0xFF3E86E6), Color(0xFF2667CF)), startY = top, endY = top + trackH),
        Offset(0f, top),
        Size(size.width * fraction, trackH),
        r,
    )
    drawRoundRect(Color(0xFF555555), Offset(0f, top), Size(size.width, trackH), r, style = Stroke(1f))
}

/** A glossy white knob: radial highlight, grey rim. */
private fun DrawScope.knob(center: Offset, radius: Float) {
    val c = Offset(center.x.coerceIn(radius, size.width - radius), center.y)
    drawCircle(Color.Black.copy(alpha = 0.35f), radius, c + Offset(0f, radius * 0.08f))
    drawCircle(
        Brush.radialGradient(listOf(Color.White, Color(0xFFE2E2E2), Color(0xFFA9A9A9)), center = c - Offset(0f, radius * 0.3f), radius = radius * 1.3f),
        radius,
        c,
    )
    drawCircle(Color(0xFF8A8A8A), radius, c, style = Stroke(1.2f))
}

// The scrim alone loses to white art (snow, skies); the shadow keeps each line legible on it.
private val LyricShadow = Shadow(Color.Black, Offset(0f, 1f), blurRadius = 4f)

/** Lyrics over the cover, below the band: white on translucent black, the sung line brightest. */
@Composable
private fun LyricsOverlay(session: SkinSession, song: SkinSong, positionMs: Long, under: Boolean = true) {
    val state by remember(session.lyrics, song.id) { session.lyrics.lyrics(song.id) }
        .collectAsState(initial = SkinLyricsState.Loading)
    val lines: List<String>
    var active = -1
    when (val s = state) {
        is SkinLyricsState.Found ->
            if (s.lyrics.isSynced) {
                lines = s.lyrics.synced.map { it.text }
                active = activeLyricIndexAt(s.lyrics.synced, positionMs)
            } else {
                lines = s.lyrics.plain
            }
        SkinLyricsState.Loading -> lines = listOf("Loading lyrics…")
        SkinLyricsState.NotFound -> lines = listOf("No lyrics for this song.")
        SkinLyricsState.Disabled -> lines = listOf("Lyrics lookup is off in Settings.")
        SkinLyricsState.NoTrack -> lines = emptyList()
        is SkinLyricsState.Failed -> lines = listOf("Lyrics unavailable.")
    }
    val listState = rememberLazyListState()
    LaunchedEffect(active) { if (active >= 0) listState.animateScrollToItem((active - 2).coerceAtLeast(0)) }
    Box(modifier = Modifier.fillMaxSize().padding(top = if (under) pt(70) else pt(0)).background(Color.Black.copy(alpha = 0.8f))) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = pt(16), vertical = pt(10))) {
            itemsIndexed(lines) { i, line ->
                BasicText(
                    line,
                    style = heros(14f, if (active < 0 || i == active) Color.White else Color(0xFF9A9A9A), bold = i == active)
                        .copy(textAlign = TextAlign.Center, shadow = LyricShadow),
                    modifier = Modifier.fillMaxWidth().padding(vertical = pt(2)),
                )
            }
        }
    }
}

/**
 * The flip side: the album's tracks in dark rows, a number column, then the title. The playing
 * track's number is a blue play arrow.
 */
@Composable
private fun TrackList(session: SkinSession, song: SkinSong?) {
    val unordered by remember(session.library, song?.albumId) {
            song?.albumId?.let { session.library.songsByAlbum(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
        }
        .collectAsState(initial = emptyList())
    val tracks = remember(unordered) { inAlbumOrder(unordered) }
    LazyColumn(modifier = Modifier.fillMaxSize().background(Glossy.TrackRowBottom)) {
        itemsIndexed(tracks, key = { _, s -> s.id }) { i, s ->
            val current = s.id == song?.id
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(pt(38))
                        .background(Color(0xFF404040))
                        .drawBehind {
                            drawLine(Color(0xFF555555), Offset(0f, 0f), Offset(size.width, 0f), 1f)
                            drawLine(Color(0xFF2A2A2A), Offset(0f, size.height - 1), Offset(size.width, size.height - 1), 2f)
                            val x = size.width * 0.15f
                            drawLine(Color(0xFF2A2A2A), Offset(x, 0f), Offset(x, size.height), 2f)
                            drawLine(Color(0xFF555555), Offset(x + 2, 0f), Offset(x + 2, size.height), 1f)
                        }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            session.commands.playSongFromAlbum(s.id, s.albumId)
                        },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(0.15f), contentAlignment = Alignment.Center) {
                    if (current) {
                        Canvas(modifier = Modifier.size(pt(12))) {
                            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close() }, Color(0xFF2F80ED))
                        }
                    } else {
                        BasicText("${s.trackNumber ?: (i + 1)}", style = heros(13f, Color.White, bold = false))
                    }
                }
                BasicText(
                    s.title,
                    style = heros(13f, Color.White, bold = false),
                    modifier = Modifier.weight(0.85f).padding(start = pt(10), end = pt(10)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The transport: rewind, play or pause, fast-forward, clustered at the centre of a translucent
 * band over the stage - no cells, no dividers.
 */
@Composable
private fun TransportBar(session: SkinSession, isPlaying: Boolean) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(pt(44))
                .background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0x99000000))))
                .drawBehind { drawLine(Color(0x55FFFFFF), Offset(0f, 0f), Offset(size.width, 0f), 1.5f) },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportKey(session.commands::previous) { drawSkip(forward = false) }
        TransportKey(session.commands::playPause, Modifier.padding(horizontal = pt(18))) {
            if (isPlaying) {
                val w = size.width
                val h = size.height
                drawRect(Color.White, Offset(w * 0.28f, 0f), Size(w * 0.16f, h))
                drawRect(Color.White, Offset(w * 0.56f, 0f), Size(w * 0.16f, h))
            } else {
                drawPath(Path().apply { moveTo(size.width * 0.22f, 0f); lineTo(size.width * 0.85f, size.height / 2); lineTo(size.width * 0.22f, size.height); close() }, Color.White)
            }
        }
        TransportKey(session.commands::next) { drawSkip(forward = true) }
    }
}

@Composable
private fun TransportKey(onClick: () -> Unit, modifier: Modifier = Modifier, glyph: DrawScope.() -> Unit) {
    Box(
        modifier =
            modifier.size(pt(52), pt(44))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(pt(24), pt(18))) {
            // A soft drop shadow under each glyph, as the original's were embossed.
            drawContext.transform.translate(0f, 1.5f)
            drawIntoGlyphShadow(glyph)
            drawContext.transform.translate(0f, -1.5f)
            glyph()
        }
    }
}

private fun DrawScope.drawIntoGlyphShadow(glyph: DrawScope.() -> Unit) {
    // Re-run the glyph under a dark layer: draw, then tint by drawing over in multiply would need a
    // layer; a simpler offset copy at low alpha reads the same at this size.
    drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), androidx.compose.ui.graphics.Paint().apply { alpha = 0.45f })
    glyph()
    drawRect(Color.Black, blendMode = androidx.compose.ui.graphics.BlendMode.SrcIn)
    drawContext.canvas.restore()
}

private fun DrawScope.drawSkip(forward: Boolean) {
    val w = size.width
    val h = size.height
    fun tri(x0: Float) =
        Path().apply {
            if (forward) {
                moveTo(x0, 0f); lineTo(x0 + w / 2, h / 2); lineTo(x0, h)
            } else {
                moveTo(x0 + w / 2, 0f); lineTo(x0, h / 2); lineTo(x0 + w / 2, h)
            }
            close()
        }
    drawPath(tri(0f), Color.White)
    drawPath(tri(w / 2), Color.White)
}

/** The bottom bar: the volume slider (the phone's media volume) across the width, filled blue. */
@Composable
private fun VolumeBar() {
    val volume = rememberMediaVolume()
    fun set(f: Float) = volume.set(f)
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(pt(44))
                .background(Brush.verticalGradient(listOf(Color(0xFF151515), Color(0xFF000000))))
                .padding(horizontal = pt(22)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth().height(pt(28)).pointerInput(Unit) {
                    detectTapGestures { set(it.x / size.width) }
                }.pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ -> set(change.position.x / size.width) }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                blueTrack(volume.fraction, size.height * 0.2f)
                knob(Offset(size.width * volume.fraction, size.height / 2), size.height * 0.3f)
            }
        }
    }
}
