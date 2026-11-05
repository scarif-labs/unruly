/*
 * Copyright (c) 2026 Unruly Project
 * AmpSkin.kt is part of Unruly.
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

package org.unruly.skin.amp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.paging.compose.collectAsLazyPagingItems
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay
import org.unruly.skin.AppSkin
import org.unruly.skin.SkinLyricsState
import org.unruly.skin.SkinPlaybackState
import org.unruly.skin.SkinSession
import org.unruly.skin.SkinSong
import org.unruly.skin.support.SkinColors
import org.unruly.skin.support.SkinTheme
import org.unruly.skin.support.SkinTypography
import org.unruly.skin.support.activeLyricIndexAt
import org.unruly.skin.support.rememberInterpolatedPosition
import org.unruly.skin.support.rememberMediaVolume

/** What the list window is showing. */
private enum class ListMode {
    PLAYLIST,
    LIBRARY,
    LYRICS,
}

/** A step into the library from the list window. */
private sealed interface LibNode {
    data class Category(val name: String) : LibNode

    data class Artist(val id: String, val name: String) : LibNode

    data class Album(val id: String, val title: String) : LibNode

    data class Genre(val id: String, val name: String) : LibNode

    data class Playlist(val id: String, val name: String) : LibNode
}

private val CATEGORIES = listOf("Artists", "Albums", "Songs", "Genres", "Playlists")

/**
 * A skin after the classic late-90s desktop player's default look: the main window, the equaliser
 * and the playlist editor, stacked down the phone as the original's windows docked.
 *
 * Each window is drawn as pixel art at its native size (see [PixelPainter]) and scaled up with
 * nearest-neighbour filtering, so the result is crisp at any width. Touch is mapped back into the
 * native coordinates, so the controls sit exactly where they are drawn.
 *
 * Gaps the contract leaves, and what this skin does about them: the analyser has no audio samples,
 * so it is animated while playing and falls when paused; the equaliser has no engine behind it, so
 * its sliders are a model only; there is no bitrate or sample rate, so those boxes stay dark; the
 * original had no library browser, so eject opens one in the list window's own style.
 */
class AmpSkin : AppSkin {
    override val id: String = "amp"
    override val displayName: String = "Winamp 2"
    override val supportsEdgeToEdge: Boolean = false

    // Held on the skin rather than in composition so they survive leaving and returning.
    private var eqShown by mutableStateOf(true)
    private var playlistShown by mutableStateOf(true)
    private var showRemaining by mutableStateOf(false)
    private var eqOn by mutableStateOf(true)
    private var eqAuto by mutableStateOf(false)
    private val eqValues = mutableStateListOf(*Array(EQ_SLIDER_X.size) { 0.5f })
    private var listMode by mutableStateOf(ListMode.PLAYLIST)
    private val libraryPath = mutableStateListOf<LibNode>()
    private var selected by mutableIntStateOf(-1)
    private var stopped by mutableStateOf(false)
    private var balance by mutableFloatStateOf(0.5f)

    override fun onBackPressed(): Boolean {
        if (listMode == ListMode.LIBRARY && libraryPath.isNotEmpty()) {
            libraryPath.removeAt(libraryPath.lastIndex)
            return true
        }
        if (listMode != ListMode.PLAYLIST) {
            listMode = ListMode.PLAYLIST
            return true
        }
        return false
    }

    @Composable
    override fun Render(session: SkinSession) {
        SkinTheme(
            colors = SkinColors.darkDefault(),
            typography = SkinTypography.default(),
            darkSystemBars = true,
        ) {
            val playback by session.playback.collectAsState()
            val position by rememberInterpolatedPosition(playback)
            val anim = remember { AmpAnimation() }
            val playing by rememberUpdatedState(playback.isPlaying)
            LaunchedEffect(Unit) {
                while (true) {
                    delay(40)
                    anim.step(playing)
                }
            }
            LaunchedEffect(playback.isPlaying) { if (playback.isPlaying) stopped = false }

            BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                val density = LocalDensity.current
                val widthPx = constraints.maxWidth
                val scale = widthPx / MAIN_W.toFloat()
                val mainPx = (MAIN_H * scale).roundToInt()
                val eqPx = if (eqShown) (EQ_H * scale).roundToInt() else 0
                val listUnits =
                    floor((constraints.maxHeight - mainPx - eqPx) / scale).toInt().coerceAtLeast(PL_MIN_H)
                Column {
                    MainWindow(session, playback, position, anim, Modifier.height(with(density) { mainPx.toDp() }))
                    if (eqShown) EqualizerWindow(Modifier.height(with(density) { eqPx.toDp() }))
                    if (playlistShown) {
                        ListWindow(
                            session = session,
                            playback = playback,
                            positionMs = position,
                            units = listUnits,
                            modifier = Modifier.height(with(density) { (listUnits * scale).roundToInt().toDp() }),
                        )
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Main window
    // ---------------------------------------------------------------------------------------

    @Composable
    private fun MainWindow(
        session: SkinSession,
        playback: SkinPlaybackState,
        positionMs: Long,
        anim: AmpAnimation,
        modifier: Modifier,
    ) {
        val painter = remember { PixelPainter(MAIN_W, MAIN_H) }
        val image = remember { painter.bitmap.asImageBitmap() }
        val mediaVolume = rememberMediaVolume()
        var pressed by remember { mutableStateOf<MainHit?>(null) }
        var seekPreview by remember { mutableStateOf<Long?>(null) }
        val state by rememberUpdatedState(playback)

        fun act(hit: MainHit) {
            val p = state
            when (hit) {
                MainHit.OPTIONS, MainHit.CLUTTER_O -> session.openAppSettings()
                MainHit.CLUTTER_I -> {
                    listMode = if (listMode == ListMode.LYRICS) ListMode.PLAYLIST else ListMode.LYRICS
                    playlistShown = true
                }
                MainHit.TIME -> showRemaining = !showRemaining
                MainHit.EQ -> eqShown = !eqShown
                MainHit.PL -> playlistShown = !playlistShown
                MainHit.PREVIOUS -> session.commands.previous()
                MainHit.PLAY -> {
                    if (p.queue.isEmpty()) openLibrary()
                    else if (stopped) {
                        session.commands.seekTo(0)
                        if (!p.isPlaying) session.commands.playPause()
                    } else if (!p.isPlaying) session.commands.playPause()
                }
                MainHit.PAUSE -> if (p.song != null && !stopped) session.commands.playPause()
                MainHit.STOP -> {
                    if (p.isPlaying) session.commands.playPause()
                    session.commands.seekTo(0)
                    stopped = true
                }
                MainHit.NEXT -> session.commands.next()
                MainHit.EJECT -> openLibrary()
                MainHit.SHUFFLE -> session.commands.toggleShuffle()
                MainHit.REPEAT -> session.commands.cycleRepeatMode()
                MainHit.VOLUME, MainHit.BALANCE, MainHit.SEEK -> Unit
            }
        }

        val input: suspend PointerInputScope.() -> Unit = {
            awaitEachGesture {
                val down = awaitFirstDown()
                val s = size.width / MAIN_W.toFloat()
                val hit = mainHitAt(down.position.x / s, down.position.y / s) ?: return@awaitEachGesture
                pressed = hit
                fun drag(x: Float) {
                    when (hit) {
                        MainHit.VOLUME -> {
                            mediaVolume.set(sliderFraction(x, 107, 68, 14))
                        }
                        MainHit.BALANCE -> {
                            // Snaps back to the centre near it, as the original's did.
                            val b = sliderFraction(x, 177, 38, 14)
                            balance = if (abs(b - 0.5f) < 0.08f) 0.5f else b
                        }
                        MainHit.SEEK ->
                            if (state.durationMs > 0) {
                                seekPreview = (sliderFraction(x, 16, 248, 29) * state.durationMs).toLong()
                            }
                        else -> Unit
                    }
                }
                drag(down.position.x / s)
                var upX = down.position.x
                var upY = down.position.y
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    upX = change.position.x
                    upY = change.position.y
                    if (!change.pressed) break
                    drag(upX / s)
                    change.consume()
                }
                pressed = null
                when (hit) {
                    MainHit.SEEK ->
                        seekPreview?.let {
                            session.commands.seekTo(it)
                            seekPreview = null
                        }
                    MainHit.VOLUME, MainHit.BALANCE -> Unit
                    else -> if (mainHitAt(upX / s, upY / s) == hit) act(hit)
                }
            }
        }

        PixelWindow(image, modifier.pointerInput(Unit, input)) {
            anim.frame
            val song = playback.song
            painter.drawMainWindow(
                MainModel(
                    hasSong = song != null && !stopped,
                    isPlaying = playback.isPlaying,
                    positionMs = seekPreview ?: positionMs,
                    durationMs = playback.durationMs,
                    marquee = marqueeText(playback),
                    marqueeOffset = anim.marquee,
                    bars = anim.bars,
                    peaks = anim.peaks,
                    volume = mediaVolume.fraction,
                    balance = balance,
                    shuffle = playback.isShuffled,
                    repeat = playback.repeatMode,
                    eqShown = eqShown,
                    playlistShown = playlistShown,
                    showRemaining = showRemaining,
                    pressed = pressed,
                )
            )
        }
    }

    private fun openLibrary() {
        listMode = ListMode.LIBRARY
        libraryPath.clear()
        playlistShown = true
    }

    /** "3. Artist - Title (4:12)  ***  ", the original's scrolling title format. */
    private fun marqueeText(p: SkinPlaybackState): String {
        val song = p.song ?: return ""
        val index = if (p.queueIndex >= 0) "${p.queueIndex + 1}. " else ""
        return "$index${song.artist} - ${song.title} (${formatTime(song.durationMs)})  ***  "
    }

    // ---------------------------------------------------------------------------------------
    // Equaliser
    // ---------------------------------------------------------------------------------------

    @Composable
    private fun EqualizerWindow(modifier: Modifier) {
        val painter = remember { PixelPainter(MAIN_W, EQ_H) }
        val image = remember { painter.bitmap.asImageBitmap() }
        val input: suspend PointerInputScope.() -> Unit = {
            awaitEachGesture {
                val down = awaitFirstDown()
                val s = size.width / MAIN_W.toFloat()
                val x = down.position.x / s
                val y = down.position.y / s
                val slider = eqSliderAt(x, y)
                if (slider != null) {
                    eqValues[slider] = eqValueAt(y)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        eqValues[slider] = eqValueAt(change.position.y / s)
                        change.consume()
                    }
                    return@awaitEachGesture
                }
                when (eqHitAt(x, y)) {
                    EqHit.CLOSE -> eqShown = false
                    EqHit.ON -> eqOn = !eqOn
                    EqHit.AUTO -> eqAuto = !eqAuto
                    EqHit.RESET -> eqValues.indices.forEach { eqValues[it] = 0.5f }
                    null -> Unit
                }
            }
        }
        PixelWindow(image, modifier.pointerInput(Unit, input)) {
            painter.drawEqualizerWindow(EqModel(eqOn, eqAuto, eqValues.toFloatArray()))
        }
    }

    // ---------------------------------------------------------------------------------------
    // List window: playlist, library, lyrics
    // ---------------------------------------------------------------------------------------

    @Composable
    private fun ListWindow(
        session: SkinSession,
        playback: SkinPlaybackState,
        positionMs: Long,
        units: Int,
        modifier: Modifier,
    ) {
        val painter = remember(units) { PixelPainter(MAIN_W, units) }
        val image = remember(painter) { painter.bitmap.asImageBitmap() }
        // One holder for the window's life: the touch handler is created once and must keep
        // writing to the same state, so a mode change resets the value, not the holder.
        val scrollState = remember { mutableFloatStateOf(0f) }
        var scroll by scrollState
        LaunchedEffect(listMode, libraryPath.size) { scroll = 0f }
        var pressed by remember { mutableStateOf<PlHit?>(null) }
        val current by rememberUpdatedState(playback)

        val content: ListContent =
            when (listMode) {
                ListMode.PLAYLIST -> playlistContent(session, playback)
                ListMode.LIBRARY -> libraryContent(session)
                ListMode.LYRICS -> lyricsContent(session, playback, positionMs)
            }
        val listH = listHeight(units)
        val maxScroll = (content.rows.size * ROW_H - listH).coerceAtLeast(0).toFloat()
        if (scroll > maxScroll) scroll = maxScroll
        // Lyrics follow the sung line, keeping it a third of the way down.
        LaunchedEffect(content.follow, listMode) {
            if (listMode == ListMode.LYRICS && content.follow >= 0) {
                scroll = (content.follow * ROW_H - listH / 3f).coerceIn(0f, maxScroll)
            }
        }
        val latest by rememberUpdatedState(content)
        val latestMax by rememberUpdatedState(maxScroll)

        fun act(hit: PlHit) {
            val playback = current
            when (hit) {
                PlHit.CLOSE ->
                    if (listMode != ListMode.PLAYLIST) listMode = ListMode.PLAYLIST else playlistShown = false
                PlHit.SEL -> selected = if (selected == playback.queueIndex) -1 else playback.queueIndex
                PlHit.LYRICS ->
                    listMode = if (listMode == ListMode.LYRICS) ListMode.PLAYLIST else ListMode.LYRICS
                PlHit.SETTINGS -> session.openAppSettings()
                PlHit.MINI_PREVIOUS -> session.commands.previous()
                PlHit.MINI_PLAY -> if (!playback.isPlaying) session.commands.playPause()
                PlHit.MINI_PAUSE -> session.commands.playPause()
                PlHit.MINI_STOP -> {
                    if (playback.isPlaying) session.commands.playPause()
                    session.commands.seekTo(0)
                    stopped = true
                }
                PlHit.MINI_NEXT -> session.commands.next()
                PlHit.MINI_EJECT -> openLibrary()
                PlHit.SCROLLBAR, PlHit.LIST_AREA -> Unit
            }
        }

        val input: suspend PointerInputScope.() -> Unit = {
            awaitEachGesture {
                val down = awaitFirstDown()
                val s = size.width / MAIN_W.toFloat()
                val hit = plHitAt(down.position.x / s, down.position.y / s, MAIN_W, units) ?: return@awaitEachGesture
                pressed = hit
                var moved = 0f
                var lastY = down.position.y
                var upX = down.position.x
                var upY = down.position.y
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull() ?: break
                    upX = change.position.x
                    upY = change.position.y
                    if (!change.pressed) break
                    val dy = (change.position.y - lastY) / s
                    lastY = change.position.y
                    moved += abs(dy)
                    when (hit) {
                        PlHit.LIST_AREA -> scroll = (scroll - dy).coerceIn(0f, latestMax)
                        PlHit.SCROLLBAR -> {
                            val travel = (listH - 18).coerceAtLeast(1)
                            val f = ((change.position.y / s - PL_TOP - 9) / travel).coerceIn(0f, 1f)
                            scroll = f * latestMax
                        }
                        else -> Unit
                    }
                    change.consume()
                }
                pressed = null
                when (hit) {
                    PlHit.LIST_AREA ->
                        if (moved < 4f) {
                            val row = ((upY / s - PL_TOP + scroll) / ROW_H).toInt()
                            if (row in latest.rows.indices) latest.onTap(row)
                        }
                    PlHit.SCROLLBAR -> Unit
                    else -> if (plHitAt(upX / s, upY / s, MAIN_W, units) == hit) act(hit)
                }
            }
        }

        PixelWindow(image, modifier.fillMaxWidth().pointerInput(units, input)) {
            painter.drawPlaylistWindow(
                PlaylistModel(
                    title = content.title,
                    rows = content.rows,
                    scrollPx = scroll.roundToInt(),
                    selected = content.selected,
                    current = content.current,
                    runningTime = content.runningTime,
                    miniTime = formatTime(positionMs),
                    pressed = pressed,
                )
            )
        }
    }

    /** What the list window draws and does in its current mode. */
    private class ListContent(
        val title: String,
        val rows: List<PlRow>,
        val selected: Int = -1,
        val current: Int = -1,
        val runningTime: String = "",
        /** A row the window should keep in view, or -1. */
        val follow: Int = -1,
        val onTap: (Int) -> Unit = {},
    )

    @Composable
    private fun playlistContent(session: SkinSession, playback: SkinPlaybackState): ListContent {
        val queue = playback.queue
        val total = queue.sumOf { it.durationMs }
        val selectedMs = queue.getOrNull(selected)?.durationMs ?: 0L
        return ListContent(
            title = "UNRULY PLAYLIST",
            rows =
                queue.mapIndexed { i, song ->
                    PlRow("${i + 1}. ${song.artist} - ${song.title}", formatTime(song.durationMs))
                },
            selected = selected,
            current = playback.queueIndex,
            runningTime = "${formatTime(selectedMs)}/${formatTime(total)}",
            // First tap selects, a tap on the selected line plays it - touch's double-click.
            onTap = { row ->
                if (row == selected) {
                    stopped = false
                    session.commands.playQueueItem(row)
                } else {
                    selected = row
                }
            },
        )
    }

    @Composable
    private fun lyricsContent(session: SkinSession, playback: SkinPlaybackState, positionMs: Long): ListContent {
        val songId = playback.song?.id
        val state =
            if (songId == null) {
                SkinLyricsState.NoTrack
            } else {
                remember(session.lyrics, songId) { session.lyrics.lyrics(songId) }
                    .collectAsState(initial = SkinLyricsState.Loading)
                    .value
            }
        val rows: List<PlRow>
        var current = -1
        when (state) {
            is SkinLyricsState.Found ->
                if (state.lyrics.isSynced) {
                    rows = state.lyrics.synced.map { PlRow(it.text) }
                    current = activeLyricIndexAt(state.lyrics.synced, positionMs)
                } else {
                    rows = state.lyrics.plain.map { PlRow(it) }
                }
            SkinLyricsState.NoTrack -> rows = listOf(PlRow("Nothing playing."))
            SkinLyricsState.Loading -> rows = listOf(PlRow("Looking up lyrics..."))
            SkinLyricsState.NotFound -> rows = listOf(PlRow("No lyrics on file for this track."))
            SkinLyricsState.Disabled -> rows = listOf(PlRow("Lyrics lookup is off. O opens settings."))
            is SkinLyricsState.Failed -> rows = listOf(PlRow("Lyrics unavailable: ${state.message}"))
        }
        return ListContent(
            title = "UNRULY LYRICS",
            rows = rows,
            current = current,
            runningTime = playback.song?.let { "${formatTime(positionMs)}/${formatTime(it.durationMs)}" } ?: "",
            follow = current,
        )
    }

    @Composable
    private fun libraryContent(session: SkinSession): ListContent {
        val node = libraryPath.lastOrNull()
        val up = if (node == null) emptyList() else listOf(PlRow("[..]"))
        fun withUp(rows: List<PlRow>, onItem: (Int) -> Unit) =
            ListContent(
                title = "UNRULY LIBRARY",
                rows = up + rows,
                runningTime = "${rows.size} ITEMS",
                onTap = { row ->
                    if (up.isNotEmpty() && row == 0) libraryPath.removeAt(libraryPath.lastIndex)
                    else onItem(row - up.size)
                },
            )
        fun playAndReturn(play: () -> Unit) {
            play()
            stopped = false
            listMode = ListMode.PLAYLIST
        }
        val library = session.library
        return when (node) {
            null -> withUp(CATEGORIES.map { PlRow(it) }) { libraryPath.add(LibNode.Category(CATEGORIES[it])) }
            is LibNode.Category ->
                when (node.name) {
                    "Artists" -> {
                        val items = remember(library) { library.artists() }.collectAsLazyPagingItems().itemSnapshotList.items
                        withUp(items.map { PlRow(it.name.ifBlank { "Unknown artist" }) }) {
                            items.getOrNull(it)?.let { a -> libraryPath.add(LibNode.Artist(a.id, a.name)) }
                        }
                    }
                    "Albums" -> {
                        val items = remember(library) { library.albums() }.collectAsLazyPagingItems().itemSnapshotList.items
                        withUp(items.map { PlRow("${it.artist} - ${it.title}") }) {
                            items.getOrNull(it)?.let { a -> libraryPath.add(LibNode.Album(a.id, a.title)) }
                        }
                    }
                    "Songs" -> {
                        val items = remember(library) { library.songs() }.collectAsLazyPagingItems().itemSnapshotList.items
                        withUp(items.map { songRow(it) }) {
                            items.getOrNull(it)?.let { s -> playAndReturn { session.commands.playSongFromAll(s.id) } }
                        }
                    }
                    "Genres" -> {
                        val items = remember(library) { library.genres() }.collectAsLazyPagingItems().itemSnapshotList.items
                        withUp(items.map { PlRow(it.name.ifBlank { "Unknown genre" }) }) {
                            items.getOrNull(it)?.let { g -> libraryPath.add(LibNode.Genre(g.id, g.name)) }
                        }
                    }
                    else -> {
                        val items = remember(library) { library.playlists() }.collectAsLazyPagingItems().itemSnapshotList.items
                        withUp(items.map { PlRow(it.name) }) {
                            items.getOrNull(it)?.let { p -> libraryPath.add(LibNode.Playlist(p.id, p.name)) }
                        }
                    }
                }
            is LibNode.Artist -> {
                val items =
                    remember(library, node.id) { library.albumsByArtist(node.id) }
                        .collectAsLazyPagingItems()
                        .itemSnapshotList
                        .items
                withUp(items.map { PlRow(it.title) }) {
                    items.getOrNull(it)?.let { a -> libraryPath.add(LibNode.Album(a.id, a.title)) }
                }
            }
            is LibNode.Album -> {
                val items by remember(library, node.id) { library.songsByAlbum(node.id) }.collectAsState(emptyList())
                withUp(items.mapIndexed { i, s -> PlRow("${s.trackNumber ?: (i + 1)}. ${s.title}", formatTime(s.durationMs)) }) {
                    items.getOrNull(it)?.let { s -> playAndReturn { session.commands.playSongFromAlbum(s.id, node.id) } }
                }
            }
            is LibNode.Genre -> {
                val items =
                    remember(library, node.id) { library.songsByGenre(node.id) }
                        .collectAsLazyPagingItems()
                        .itemSnapshotList
                        .items
                withUp(items.map { songRow(it) }) {
                    items.getOrNull(it)?.let { s -> playAndReturn { session.commands.playSongFromGenre(s.id, node.id) } }
                }
            }
            is LibNode.Playlist -> {
                val items by remember(library, node.id) { library.songsByPlaylist(node.id) }.collectAsState(emptyList())
                withUp(items.map { songRow(it) }) {
                    items.getOrNull(it)?.let { s -> playAndReturn { session.commands.playSongFromPlaylist(s.id, node.id) } }
                }
            }
        }
    }

    private fun songRow(song: SkinSong) = PlRow("${song.artist} - ${song.title}", formatTime(song.durationMs))
}

/**
 * Draws a pixel window: [paint] fills the native bitmap, which is then scaled to the composable's
 * size with nearest-neighbour filtering.
 */
@Composable
private fun PixelWindow(image: ImageBitmap, modifier: Modifier, paint: DrawScope.() -> Unit) {
    Canvas(modifier = modifier.fillMaxWidth()) {
        paint()
        drawImage(
            image = image,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.None,
        )
    }
}

/**
 * The analyser and marquee clock. There are no audio samples to analyse, so while playing the bars
 * follow a shaped random walk - heavier in the low bands, as music is - and when paused they fall
 * away, peaks last.
 */
private class AmpAnimation {
    var frame by mutableIntStateOf(0)
    val bars = FloatArray(BAR_COUNT)
    val peaks = FloatArray(BAR_COUNT)
    private val hold = IntArray(BAR_COUNT)
    private val targets = FloatArray(BAR_COUNT)
    private val random = Random(7)
    val marquee: Int
        get() = frame / 2

    fun step(playing: Boolean) {
        for (i in 0 until BAR_COUNT) {
            if (playing) {
                if (frame % 3 == 0) {
                    val tilt = 0.95f - i * 0.028f
                    targets[i] = (tilt * (0.35f + random.nextFloat() * 0.65f)).coerceIn(0.05f, 1f)
                    if (random.nextFloat() < 0.06f) targets[i] = (targets[i] + 0.3f).coerceAtMost(1f)
                }
            } else {
                targets[i] = 0f
            }
            bars[i] = if (targets[i] > bars[i]) targets[i] else (bars[i] - 0.045f).coerceAtLeast(targets[i])
            if (bars[i] >= peaks[i]) {
                peaks[i] = bars[i]
                hold[i] = 8
            } else if (hold[i] > 0) {
                hold[i]--
            } else {
                peaks[i] = (peaks[i] - 0.02f).coerceAtLeast(0f)
            }
        }
        frame++
    }
}

/** m:ss, or h:mm:ss past an hour. */
internal fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total / 60) % 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}
