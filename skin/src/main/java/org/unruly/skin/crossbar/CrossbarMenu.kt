/*
 * Copyright (c) 2026 Unruly Project
 * CrossbarMenu.kt is part of Unruly.
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

import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.unruly.skin.LibraryLoadState
import org.unruly.skin.SkinPlaylist
import org.unruly.skin.SkinSession
import org.unruly.skin.SkinSong
import org.unruly.skin.support.SkinAlbumArt

/** One line in a list. */
internal class Entry(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val glyph: Glyph = Glyph.NOTE_TILE,
    val art: Uri? = null,
    /** Shown boxed at the right of the selected entry; the device showed the codec here. */
    val trailing: String? = null,
    val options: List<Opt>? = null,
    val open: () -> Unit,
)

/** A list's contents: possibly paged, so entries are fetched by index as they come into view. */
internal class Feed(val count: Int, val message: String?, private val at: (Int) -> Entry?) {
    operator fun get(i: Int): Entry? = if (i in 0 until count) at(i) else null
}

// -------------------------------------------------------------------------------------------
// The menu
// -------------------------------------------------------------------------------------------

@Composable
internal fun MenuScreen(
    skin: CrossbarSkin,
    session: SkinSession,
    now: Calendar,
    colour: Int,
    setColour: (Int) -> Unit,
) {
    val unit = LocalPx.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wPx = maxWidth / unit
        val hPx = maxHeight / unit
        // The bar sits where the device had it in landscape; in portrait it drops to about a
        // third of the way down, leaving room above for the entries before the selection.
        val barY = 70f + max(0f, hPx - 272f) * 0.25f

        val depth by animateFloatAsState(skin.stack.size.toFloat(), tween(320), label = "depth")
        val catPos by
            animateFloatAsState(skin.category.ordinal.toFloat(), tween(280), label = "category")

        val levels = listOf<Level>(Level.Root(skin.category)) + skin.stack
        val activeIndex = levels.lastIndex
        val active = levels[activeIndex]

        // Feeds for the active list and the one it was opened from; deeper ancestors are off
        // screen. Keyed on the level so each list keeps its own paging state.
        val feeds = HashMap<Int, Feed>()
        for (k in max(0, activeIndex - 1)..activeIndex) {
            key(levels[k]) { feeds[k] = rememberFeed(levels[k], skin, session, colour, setColour) }
        }
        val activeFeed = feeds.getValue(activeIndex)

        // The active list scrolls continuously under a drag; the selection is its rounding.
        val scroll = remember(active) { Animatable(skin.selected(active).toFloat()) }
        // A list that shrinks under the selection (a playlist deleted, a track removed) would
        // otherwise leave it past the end, with nothing in the focus slot.
        if (activeFeed.count > 0 && skin.selected(active) >= activeFeed.count) {
            skin.select(active, activeFeed.count - 1)
        }
        val selectedNow = skin.selected(active)
        LaunchedEffect(active, selectedNow) {
            if (scroll.targetValue.roundToInt() != selectedNow)
                scroll.animateTo(selectedNow.toFloat(), spring(0.9f, 400f))
        }
        val latestFeed by rememberUpdatedState(activeFeed)
        val latestActive by rememberUpdatedState(active)
        val rowPx = with(density) { (unit * Geo.ROW).toPx() }
        val catPx = with(density) { (unit * Geo.CATEGORY_GAP).toPx() }

        Box(
            modifier =
                Modifier.fillMaxSize().clipToBounds().pointerInput(scroll) {
                    var dx = 0f
                    var dy = 0f
                    var axis = 0 // 0 undecided, 1 vertical, 2 horizontal
                    var lastTime = 0L
                    var velocity = 0f
                    detectDragGestures(
                        onDragStart = {
                            dx = 0f
                            dy = 0f
                            axis = 0
                            velocity = 0f
                            lastTime = System.nanoTime()
                        },
                        onDragEnd = {
                            if (axis == 1) {
                                val count = latestFeed.count
                                val target =
                                    (scroll.value - velocity / rowPx * 0.12f)
                                        .roundToInt()
                                        .coerceIn(0, max(0, count - 1))
                                skin.select(latestActive, target)
                                scope.launch {
                                    scroll.animateTo(target.toFloat(), spring(0.85f, 300f))
                                }
                            } else if (axis == 2) {
                                if (skin.stack.isEmpty()) {
                                    // One category a swipe, as one press of the pad;
                                    // a long sweep moves further.
                                    val steps =
                                        -dx.signStep() * max(1, (abs(dx) / (catPx * 1.6f)).toInt())
                                    val next =
                                        (skin.category.ordinal + steps).coerceIn(
                                            0,
                                            Category.entries.size - 1,
                                        )
                                    skin.category = Category.entries[next]
                                } else if (dx > catPx * 0.5f) {
                                    skin.onBackPressed()
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { scroll.animateTo(scroll.value.roundToInt().toFloat()) }
                        },
                    ) { change, amount ->
                        change.consume()
                        dx += amount.x
                        dy += amount.y
                        if (axis == 0) axis = if (abs(dy) >= abs(dx)) 1 else 2
                        if (axis == 1) {
                            val t = System.nanoTime()
                            val dt = (t - lastTime).coerceAtLeast(1L) / 1e9f
                            velocity = 0.7f * velocity + 0.3f * (amount.y / dt)
                            lastTime = t
                            val count = latestFeed.count
                            val v =
                                (scroll.value - amount.y / rowPx).coerceIn(
                                    0f,
                                    max(0, count - 1).toFloat(),
                                )
                            scope.launch { scroll.snapTo(v) }
                            skin.select(latestActive, v.roundToInt())
                        }
                    }
                }
        ) {
            // Columns, oldest first so the active list draws on top.
            // The Information page stands in for the lists, as on the device.
            for (k in
                if (skin.info != null) IntRange.EMPTY else max(0, activeIndex - 1)..activeIndex) {
                val d = depth - k
                val colX = columnX(k, d)
                val level = levels[k]
                val feed = feeds.getValue(k)
                val pos = if (k == activeIndex) scroll.value else animatedSelection(skin, level)
                val textAlpha = (1f - d * 2f).coerceIn(0f, 1f)
                val columnAlpha = if (d >= 1f) (2f - d).coerceIn(0f, 1f) else 1f
                if (k == 0) {
                    CategoryBar(skin, colX, barY, catPos, depth)
                }
                if (k >= 1 && columnAlpha > 0f) {
                    LeftArrow(colX - 39f, barY + Geo.FIRST_ROW, columnAlpha)
                }
                ListColumn(
                    skin = skin,
                    level = level,
                    feed = feed,
                    x = colX,
                    rowY = barY + Geo.FIRST_ROW,
                    position = pos,
                    aboveBar = k == 0,
                    barY = barY,
                    hPx = hPx,
                    wPx = wPx,
                    textAlpha = textAlpha,
                    alpha = columnAlpha,
                    isActive = k == activeIndex,
                    onTapParent = { i ->
                        while (skin.stack.size > k) skin.stack.removeAt(skin.stack.lastIndex)
                        skin.select(level, i)
                    },
                )
            }

            Clock(now, Modifier.align(Alignment.TopEnd).padding(top = unit * 6f, end = unit * 10f))

            val selectedEntry = activeFeed[skin.selected(active)]
            if (
                selectedEntry?.options != null &&
                    skin.options == null &&
                    skin.info == null &&
                    skin.naming == null
            ) {
                OptionsHint(
                    Modifier.align(Alignment.BottomEnd)
                        .padding(end = unit * 6f, bottom = unit * 10f)
                ) {
                    skin.options = OptionsMenu(selectedEntry.options)
                }
            }

            skin.options?.let { menu -> SideMenu(skin, menu, barY + Geo.FIRST_ROW, hPx) }
            skin.info?.let { song -> InfoPage(song) { skin.info = null } }
            skin.naming?.let { prompt -> NameEntry(prompt) { skin.naming = null } }
        }
    }
}

private fun Float.signStep(): Int = if (this > 0f) 1 else if (this < 0f) -1 else 0

/** A column's icon centre, [d] levels behind the active one (negative: still arriving). */
private fun columnX(k: Int, d: Float): Float {
    val home = if (k == 0) Geo.COLUMN_X else Geo.LIST_X
    return when {
        d <= 0f -> home + 80f * -d
        d <= 1f -> home + (48f - home) * d
        else -> 48f - Geo.LEVEL_STEP * (d - 1f)
    }
}

@Composable
private fun animatedSelection(skin: CrossbarSkin, level: Level): Float =
    animateFloatAsState(skin.selected(level).toFloat(), tween(220), label = "sel").value

/**
 * Where entry [delta] places from the selection sits, relative to the selected row. Before the
 * selection the top level's entries jump above the bar, as on the device.
 */
private fun rowOffset(delta: Float, aboveBar: Boolean): Float =
    when {
        delta >= 0f ->
            if (delta <= 1f) Geo.NEAR_ROW * delta else Geo.NEAR_ROW + Geo.ROW * (delta - 1f)
        aboveBar ->
            if (delta >= -1f) -Geo.ABOVE_BAR * -delta else -Geo.ABOVE_BAR - Geo.ROW * (-delta - 1f)
        else ->
            if (delta >= -1f) -Geo.NEAR_ROW * -delta else -Geo.NEAR_ROW - Geo.ROW * (-delta - 1f)
    }

// -------------------------------------------------------------------------------------------
// The bar
// -------------------------------------------------------------------------------------------

@Composable
private fun CategoryBar(skin: CrossbarSkin, colX: Float, barY: Float, catPos: Float, depth: Float) {
    val others = (1f - depth).coerceIn(0f, 1f)
    for (c in Category.entries) {
        val rel = c.ordinal - catPos
        val near = (1f - abs(rel)).coerceIn(0f, 1f)
        val alpha = if (c == skin.category) 1f else 0.78f * others
        if (alpha <= 0f) continue
        val x = colX + Geo.CATEGORY_GAP * rel
        val box = Geo.CATEGORY_ICON * (1f + (Geo.CATEGORY_GROW - 1f) * near)
        Box(
            modifier =
                Modifier.offset(px(x - box / 2f), px(barY - box / 2f)).size(px(box)).pointerInput(
                    c
                ) {
                    detectTapGestures {
                        skin.stack.clear()
                        skin.category = c
                    }
                }
        ) {
            GlyphIcon(
                c.glyph,
                px(box),
                alpha = alpha,
                glow = if (c == skin.category && depth < 0.5f) 0.6f else 0f,
            )
        }
        if (near > 0.01f) {
            CenteredText(
                c.label,
                crossText(Geo.LABEL),
                centreX = x,
                top = barY + 20f,
                alpha = near * if (c == skin.category) 1f else others,
            )
        }
    }
}

/** Text centred on [centreX] (px), its top at [top]. */
@Composable
private fun CenteredText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    centreX: Float,
    top: Float,
    alpha: Float,
) {
    val width = 120f
    BasicText(
        text,
        style = style.copy(textAlign = TextAlign.Center),
        maxLines = 1,
        modifier = Modifier.offset(px(centreX - width / 2f), px(top)).width(px(width)).alpha(alpha),
    )
}

@Composable
private fun LeftArrow(x: Float, y: Float, alpha: Float) {
    Canvas(modifier = Modifier.offset(px(x - 4f), px(y - 5f)).size(px(8f), px(10f))) {
        val path =
            Path().apply {
                moveTo(size.width * 0.9f, 0f)
                lineTo(size.width * 0.9f, size.height)
                lineTo(size.width * 0.1f, size.height / 2f)
                close()
            }
        drawPath(path, Color.White.copy(alpha = 0.9f * alpha))
    }
}

// -------------------------------------------------------------------------------------------
// Lists
// -------------------------------------------------------------------------------------------

@Composable
private fun ListColumn(
    skin: CrossbarSkin,
    level: Level,
    feed: Feed,
    x: Float,
    rowY: Float,
    position: Float,
    aboveBar: Boolean,
    barY: Float,
    hPx: Float,
    wPx: Float,
    textAlpha: Float,
    alpha: Float,
    isActive: Boolean,
    onTapParent: (Int) -> Unit,
) {
    if (alpha <= 0f) return
    if (feed.count == 0) {
        feed.message?.let { msg ->
            BasicText(
                msg,
                style = crossText(Geo.TITLE),
                modifier =
                    Modifier.offset(px(x - Geo.ITEM_ICON / 2f), px(rowY - 9f))
                        .alpha(alpha * max(textAlpha, 0f)),
            )
        }
        return
    }
    val selected = position.roundToInt().coerceIn(0, feed.count - 1)
    val first = max(0, selected - 8)
    val last = min(feed.count - 1, selected + 14)
    for (i in first..last) {
        val delta = i - position
        val y = rowY + rowOffset(delta, aboveBar)
        if (y < -40f || y > hPx + 40f) continue
        // Entries before the selection must not run over the bar.
        if (aboveBar && delta < 0f && y > barY - 30f) continue
        val entry = feed[i] ?: continue
        val focus = (1f - abs(delta)).coerceIn(0f, 1f)
        val isSelected = i == selected
        key(entry.key) {
            EntryRow(
                entry = entry,
                x = x,
                y = y,
                wPx = wPx,
                focus = focus,
                selected = isSelected && isActive,
                textAlpha = textAlpha,
                alpha =
                    alpha *
                        if (isActive) (0.42f + 0.58f * focus) else (if (isSelected) 1f else 0.35f),
                onTap = {
                    if (isActive) {
                        skin.select(level, i)
                        entry.open()
                    } else {
                        onTapParent(i)
                    }
                },
                onHold = {
                    if (isActive && entry.options != null) {
                        skin.select(level, i)
                        skin.options = OptionsMenu(entry.options)
                    }
                },
            )
        }
    }
}

@Composable
private fun EntryRow(
    entry: Entry,
    x: Float,
    y: Float,
    wPx: Float,
    focus: Float,
    selected: Boolean,
    textAlpha: Float,
    alpha: Float,
    onTap: () -> Unit,
    onHold: () -> Unit,
) {
    val icon = Geo.ITEM_ICON
    val textX = x + Geo.TEXT_FROM_ICON
    val rowTop = y - 22f
    // The whole row is the touch target.
    Box(
        modifier =
            Modifier.offset(px(x - icon / 2f - 6f), px(rowTop))
                .size(px(max(10f, wPx - (x - icon / 2f - 6f))), px(44f))
                .pointerInput(entry.key) {
                    detectTapGestures(onTap = { onTap() }, onLongPress = { onHold() })
                }
    )
    Box(
        modifier = Modifier.offset(px(x - icon / 2f), px(y - icon / 2f)).size(px(icon)).alpha(alpha)
    ) {
        if (entry.art != null) {
            val shape = RoundedCornerShape(px(1.5f))
            SkinAlbumArt(
                uri = entry.art,
                contentDescription = null,
                modifier =
                    Modifier.fillMaxSize()
                        .clip(shape)
                        .border(px(0.8f), Color.White.copy(alpha = 0.5f), shape),
                fallback = { GlyphIcon(entry.glyph, px(icon)) },
            )
        } else {
            GlyphIcon(entry.glyph, px(icon), glow = if (selected) 0.8f else 0f)
        }
    }
    if (textAlpha <= 0f) return
    val textWidth = wPx - textX - 8f
    val titleTop = if (entry.subtitle != null) y - 19f else y - 10f
    BasicText(
        entry.title,
        style = crossText(Geo.TITLE),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier.offset(px(textX), px(titleTop)).width(px(textWidth)).alpha(alpha * textAlpha),
    )
    if (selected) {
        Box(
            modifier =
                // Between title and subtitle; under a lone title, which is centred on the row.
                Modifier.offset(px(textX), px(if (entry.subtitle != null) y else y + 10f))
                    .width(px(wPx - textX - 6f))
                    .height(px(0.9f))
                    .alpha(textAlpha)
                    .background(Color.White.copy(alpha = 0.85f))
        )
    }
    entry.subtitle?.let { sub ->
        BasicText(
            sub,
            style = crossText(Geo.SUBTITLE),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier.offset(px(textX), px(y + 3f))
                    .width(px(textWidth - if (selected && entry.trailing != null) 60f else 0f))
                    .alpha(alpha * textAlpha),
        )
    }
    if (selected && entry.trailing != null) {
        val tint = LocalTint.current
        Box(
            modifier =
                Modifier.offset(px(wPx - 62f), px(y + 4f))
                    .size(px(54f), px(11f))
                    .alpha(textAlpha)
                    .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(px(3f))),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                entry.trailing,
                style = crossText(8.5f, FontWeight.Bold, tint.midLeft).copy(shadow = null),
            )
        }
    }
}

// -------------------------------------------------------------------------------------------
// What each list holds
// -------------------------------------------------------------------------------------------

@Composable
private fun rememberFeed(
    level: Level,
    skin: CrossbarSkin,
    session: SkinSession,
    colour: Int,
    setColour: (Int) -> Unit,
): Feed {
    val library = session.library
    val commands = session.commands
    val loadState by library.loadState.collectAsState()
    val nothing = emptyMessage(loadState)
    val scope = rememberCoroutineScope()
    val playlists = remember { library.playlists() }.collectAsLazyPagingItems()
    val targets = PlaylistTargets(scope) { playlists.itemSnapshotList.items }

    return when (level) {
        is Level.Root ->
            when (level.category) {
                Category.SETTINGS -> {
                    val entries =
                        listOf(
                            Entry(
                                "colour",
                                "Color",
                                subtitle =
                                    if (colour == 0) "Original" else Tints.monthNames[colour - 1],
                                glyph = Glyph.COLOUR,
                            ) {
                                val choices =
                                    listOf(Opt("Original") { setColour(0) }) +
                                        Tints.monthNames.mapIndexed { i, name ->
                                            Opt(name, Tints.months[i]) { setColour(i + 1) }
                                        }
                                skin.options = OptionsMenu(choices, colour)
                            },
                            Entry("app", "Unruly Settings", glyph = Glyph.GEAR) {
                                session.openAppSettings()
                            },
                        )
                    Feed(entries.size, null) { entries[it] }
                }
                Category.MUSIC -> {
                    val playing by session.playback.collectAsState()
                    val song = playing.song
                    val entries = buildList {
                        if (song != null) {
                            add(
                                Entry(
                                    "now",
                                    "Now Playing",
                                    subtitle = song.title,
                                    glyph = Glyph.NOW_PLAYING,
                                ) {
                                    skin.openPlayer(null)
                                }
                            )
                        }
                        add(
                            Entry("shuffle", "Shuffle All", glyph = Glyph.SHUFFLE) {
                                commands.shuffleAll()
                                skin.openPlayer("All Songs")
                            }
                        )
                    }
                    Feed(entries.size, null) { entries[it] }
                }
                Category.PLAYLISTS -> {
                    // Offered once there is music to put in one; until then the loading or
                    // no-music message stands alone.
                    val lead = if (nothing == null) 1 else 0
                    val create =
                        Entry("new-playlist", "New Playlist", glyph = Glyph.PLAYLISTS) {
                            skin.naming = NamePrompt("New Playlist") { commands.createPlaylist(it) }
                        }
                    Feed(playlists.itemCount + lead, nothing) { i ->
                        if (i < lead) {
                            create
                        } else {
                            playlists[i - lead]?.let { p ->
                                Entry(
                                    p.id,
                                    p.name,
                                    subtitle = tracks(p.songCount),
                                    glyph = Glyph.PLAYLISTS,
                                    options = playlistOptions(skin, session, p),
                                ) {
                                    skin.drill(Level.PlaylistSongs(p.id, p.name))
                                }
                            }
                        }
                    }
                }
                Category.ARTISTS ->
                    pagedFeed(remember { library.artists() }, nothing ?: "There are no artists.") {
                        a ->
                        Entry(
                            a.id,
                            a.name,
                            subtitle = plural(a.albumCount, "Album"),
                            glyph = Glyph.ARTISTS,
                            options =
                                listOf(
                                    Opt("Play") {
                                        commands.playArtist(a.id)
                                        skin.openPlayer(a.name)
                                    }
                                ),
                        ) {
                            skin.drill(Level.ArtistAlbums(a.id, a.name))
                        }
                    }
                Category.ALBUMS ->
                    pagedFeed(remember { library.albums() }, nothing ?: "There are no albums.") { a
                        ->
                        Entry(
                            a.id,
                            a.title,
                            subtitle = a.artist,
                            glyph = Glyph.ALBUMS,
                            art = a.albumArtUri,
                            options = albumOptions(skin, session, targets, a.id, a.title),
                        ) {
                            skin.drill(Level.AlbumSongs(a.id, a.title))
                        }
                    }
                Category.SONGS ->
                    pagedFeed(remember { library.songs() }, nothing ?: "There are no songs.") { s ->
                        songEntry(skin, session, targets, s, "All Songs") {
                            commands.playSongFromAll(s.id)
                        }
                    }
                Category.GENRES ->
                    pagedFeed(remember { library.genres() }, nothing ?: "There are no genres.") { g
                        ->
                        Entry(
                            g.id,
                            g.name,
                            subtitle = tracks(g.songCount),
                            glyph = Glyph.GENRES,
                            options =
                                listOf(
                                    Opt("Play") {
                                        commands.playGenre(g.id)
                                        skin.openPlayer(g.name)
                                    }
                                ),
                        ) {
                            skin.drill(Level.GenreSongs(g.id, g.name))
                        }
                    }
            }
        is Level.ArtistAlbums ->
            pagedFeed(remember { library.albumsByArtist(level.id) }, "There are no albums.") { a ->
                Entry(
                    a.id,
                    a.title,
                    subtitle = tracks(a.songCount),
                    glyph = Glyph.ALBUMS,
                    art = a.albumArtUri,
                    options = albumOptions(skin, session, targets, a.id, a.title),
                ) {
                    skin.drill(Level.AlbumSongs(a.id, a.title))
                }
            }
        is Level.AlbumSongs -> {
            val songs by remember { library.songsByAlbum(level.id) }.collectAsState(initial = null)
            val ordered = remember(songs) { songs?.let(::inAlbumOrder) }
            if (ordered == null) {
                Feed(0, null) { null }
            } else {
                Feed(ordered.size, "There are no tracks.") { i ->
                    val s = ordered[i]
                    songEntry(skin, session, targets, s, level.title) {
                        commands.playSongFromAlbum(s.id, level.id)
                    }
                }
            }
        }
        is Level.GenreSongs ->
            pagedFeed(remember { library.songsByGenre(level.id) }, "There are no tracks.") { s ->
                songEntry(skin, session, targets, s, level.name) {
                    commands.playSongFromGenre(s.id, level.id)
                }
            }
        is Level.PlaylistSongs -> {
            val songs by
                remember { library.songsByPlaylist(level.id) }.collectAsState(initial = null)
            val list = songs
            if (list == null) {
                Feed(0, null) { null }
            } else {
                Feed(list.size, "There are no tracks.") { i ->
                    val s = list[i]
                    songEntry(
                        skin,
                        session,
                        targets.except(level.id),
                        s,
                        level.name,
                        extra =
                            listOf(
                                Opt("Remove from Playlist") {
                                    commands.removeFromPlaylist(level.id, s.id, i)
                                }
                            ),
                    ) {
                        commands.playSongFromPlaylist(s.id, level.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T : Any> pagedFeed(flow: Flow<PagingData<T>>, empty: String, map: (T) -> Entry): Feed {
    val items = flow.collectAsLazyPagingItems()
    return Feed(items.itemCount, empty) { i -> items[i]?.let(map) }
}

private fun emptyMessage(state: LibraryLoadState): String? =
    when (state) {
        LibraryLoadState.Idle -> "Loading..."
        is LibraryLoadState.Scanning ->
            state.loaded?.let { "Scanning... ${plural(it, "track")}" } ?: "Scanning..."
        is LibraryLoadState.Failed -> "The music could not be read."
        is LibraryLoadState.Ready -> if (state.isEmpty) "There is no music." else null
    }

private fun songEntry(
    skin: CrossbarSkin,
    session: SkinSession,
    targets: PlaylistTargets,
    s: SkinSong,
    group: String,
    extra: List<Opt> = emptyList(),
    play: () -> Unit,
): Entry {
    val start = {
        play()
        skin.openPlayer(group)
    }
    return Entry(
        s.id,
        s.title,
        subtitle = s.artist,
        glyph = Glyph.NOTE_TILE,
        art = s.albumArtUri,
        trailing = formatTime(s.durationMs),
        options =
            listOf(
                Opt("Play", run = start),
                Opt("Play Next") { session.commands.playNext(listOf(s)) },
                Opt("Add to Queue") { session.commands.addToQueue(listOf(s)) },
                addToPlaylist(skin, session, targets) { listOf(s) },
            ) + extra + Opt("Information") { skin.info = s },
        open = start,
    )
}

private fun albumOptions(
    skin: CrossbarSkin,
    session: SkinSession,
    targets: PlaylistTargets,
    albumId: String,
    title: String,
): List<Opt> =
    listOf(
        Opt("Play") {
            session.commands.playAlbum(albumId)
            skin.openPlayer(title)
        },
        addToPlaylist(skin, session, targets) {
            inAlbumOrder(session.library.songsByAlbum(albumId).first())
        },
    )

private fun playlistOptions(skin: CrossbarSkin, session: SkinSession, p: SkinPlaylist): List<Opt> {
    val commands = session.commands
    return listOf(
        Opt("Play") {
            commands.playPlaylist(p.id)
            skin.openPlayer(p.name)
        },
        Opt("Rename") {
            skin.naming = NamePrompt("Rename", p.name) { commands.renamePlaylist(p.id, it) }
        },
        // A second menu to confirm, its cursor resting on Cancel.
        Opt("Delete") {
            skin.options =
                OptionsMenu(
                    listOf(
                        Opt("Delete \"${p.name}\"") { commands.deletePlaylist(p.id) },
                        Opt("Cancel") {},
                    ),
                    1,
                )
        },
    )
}

/** The playlists a song can be added to, read when the option is chosen rather than when shown. */
private class PlaylistTargets(
    val scope: CoroutineScope,
    private val except: String? = null,
    private val all: () -> List<SkinPlaylist>,
) {
    fun playlists(): List<SkinPlaylist> = all().filter { it.id != except }

    /** The same, without the playlist being browsed: adding its own tracks back is never meant. */
    fun except(id: String) = PlaylistTargets(scope, id, all)
}

/**
 * "Add to Playlist", opening a second menu: a new playlist (named first), then each existing one.
 * [songs] is suspending so an album can fetch its tracks only once one is chosen.
 */
private fun addToPlaylist(
    skin: CrossbarSkin,
    session: SkinSession,
    targets: PlaylistTargets,
    songs: suspend () -> List<SkinSong>,
): Opt =
    Opt("Add to Playlist") {
        val commands = session.commands
        val create =
            Opt("New Playlist") {
                skin.naming =
                    NamePrompt("New Playlist") { name ->
                        targets.scope.launch { commands.createPlaylist(name, songs()) }
                    }
            }
        skin.options =
            OptionsMenu(
                listOf(create) +
                    targets.playlists().map { p ->
                        Opt(p.name) {
                            targets.scope.launch { commands.addToPlaylist(p.id, songs()) }
                        }
                    }
            )
    }

internal fun inAlbumOrder(songs: List<SkinSong>): List<SkinSong> =
    songs.sortedWith(
        compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: Int.MAX_VALUE }, { it.title })
    )

private fun tracks(n: Int) = plural(n, "Track")

private fun plural(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

internal fun formatTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%02d:%02d", m, s)
}

// -------------------------------------------------------------------------------------------
// Side menu, information page, clock
// -------------------------------------------------------------------------------------------

@Composable
private fun SideMenu(skin: CrossbarSkin, menu: OptionsMenu, rowY: Float, hPx: Float) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wPx = maxWidth / LocalPx.current
        val panelW = max(150f, wPx * 0.36f)
        val lineH = 20f
        // The list starts level with the selected entry, moving up only if it would run off. A
        // list too long for the screen (a playlist per line) starts at the top and scrolls.
        val top = min(rowY - lineH / 2f, hPx - 16f - lineH * menu.options.size).coerceAtLeast(8f)
        val scroll = rememberScrollState()
        val linePx = with(LocalDensity.current) { px(lineH).toPx() }
        LaunchedEffect(menu) { scroll.scrollTo((menu.selected * linePx).roundToInt()) }
        // Tapping outside closes it.
        Box(
            modifier =
                Modifier.fillMaxSize().pointerInput(menu) {
                    detectTapGestures { skin.options = null }
                }
        )
        Box(
            modifier =
                Modifier.offset(px(wPx - panelW), 0.dp)
                    .width(px(panelW))
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.10f), Ink.Panel))
                    )
        )
        Column(
            modifier =
                Modifier.offset(px(wPx - panelW), px(top))
                    .width(px(panelW))
                    .heightIn(max = px(hPx - top - 8f))
                    .verticalScroll(scroll)
        ) {
            for ((i, opt) in menu.options.withIndex()) {
                val chosen = i == menu.selected
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(px(lineH))
                            .then(
                                if (chosen) {
                                    Modifier.background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.45f),
                                                Color.White.copy(alpha = 0.18f),
                                            )
                                        )
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .pointerInput(opt) {
                                detectTapGestures {
                                    menu.selected = i
                                    skin.options = null
                                    opt.run()
                                }
                            },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = px(10f)),
                    ) {
                        opt.swatch?.let { tint ->
                            Box(
                                modifier =
                                    Modifier.size(px(11f))
                                        .border(px(0.8f), Color.White.copy(alpha = 0.8f))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(tint.topLeft, tint.midRight)
                                            )
                                        )
                            )
                            Spacer(Modifier.width(px(7f)))
                        }
                        BasicText(
                            opt.label,
                            style = crossText(Geo.SUBTITLE + 1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A name typed over the dimmed menu, for a new or renamed playlist. Done on the keyboard or OK
 * hands it on; tapping outside or Cancel drops it. Left blank, a new playlist takes a default name.
 */
@Composable
private fun NameEntry(prompt: NamePrompt, close: () -> Unit) {
    var text by
        remember(prompt) {
            mutableStateOf(TextFieldValue(prompt.initial, TextRange(prompt.initial.length)))
        }
    val focus = remember { FocusRequester() }
    LaunchedEffect(prompt) { focus.requestFocus() }
    val finish = {
        close()
        prompt.done(text.text)
    }
    val shape = RoundedCornerShape(px(3f))
    Box(
        modifier =
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).pointerInput(prompt) {
                detectTapGestures { close() }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // Taps on the dialog itself must not fall through and close it.
            modifier = Modifier.width(px(240f)).pointerInput(Unit) { detectTapGestures {} },
        ) {
            BasicText(prompt.title, style = crossText(Geo.TITLE))
            Spacer(Modifier.height(px(10f)))
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = crossText(Geo.TITLE).copy(shadow = null),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { finish() }),
                modifier =
                    Modifier.fillMaxWidth()
                        .focusRequester(focus)
                        .background(Color.Black.copy(alpha = 0.6f), shape)
                        .border(px(0.8f), Color.White.copy(alpha = 0.6f), shape)
                        .padding(horizontal = px(8f), vertical = px(5f)),
            )
            Spacer(Modifier.height(px(12f)))
            Row {
                PromptButton("Cancel", close)
                Spacer(Modifier.width(px(12f)))
                PromptButton("OK", finish)
            }
        }
    }
}

@Composable
private fun PromptButton(label: String, onTap: () -> Unit) {
    val shape = RoundedCornerShape(px(3f))
    BasicText(
        label,
        style = crossText(Geo.SUBTITLE + 1f).copy(textAlign = TextAlign.Center),
        modifier =
            Modifier.width(px(72f))
                .background(Color.White.copy(alpha = 0.18f), shape)
                .border(px(0.8f), Color.White.copy(alpha = 0.5f), shape)
                .pointerInput(onTap) { detectTapGestures { onTap() } }
                .padding(vertical = px(4f)),
    )
}

/** The track's details over a dimmed menu, as the device's Information page. Tap to close. */
@Composable
private fun InfoPage(song: SkinSong, close: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().pointerInput(song) { detectTapGestures { close() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(px(64f))) {
                SkinAlbumArt(
                    song.albumArtUri,
                    null,
                    Modifier.fillMaxSize(),
                    fallback = { GlyphIcon(Glyph.NOTE_TILE, px(64f)) },
                )
            }
            Spacer(Modifier.height(px(10f)))
            val rows =
                listOf(
                    "Title" to song.title,
                    "Artist" to song.artist,
                    "Album" to song.album,
                    "Track" to (song.trackNumber?.toString() ?: "-"),
                    "Year" to (song.year?.toString() ?: "-"),
                    "Length" to formatTime(song.durationMs),
                )
            for ((label, value) in rows) {
                Row(modifier = Modifier.padding(vertical = px(1.5f))) {
                    BasicText(
                        label,
                        style = crossText(Geo.SUBTITLE + 1f).copy(textAlign = TextAlign.End),
                        modifier = Modifier.width(px(90f)),
                    )
                    Spacer(Modifier.width(px(8f)))
                    BasicText(
                        value,
                        style = crossText(Geo.SUBTITLE + 1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(px(200f)),
                    )
                }
            }
            Spacer(Modifier.height(px(14f)))
            BasicText(
                "Back",
                style = crossText(Geo.SUBTITLE).copy(color = Color.White.copy(alpha = 0.8f)),
            )
        }
    }
}

/** The "Options" chip at the bottom right, as the device's button hint. */
@Composable
private fun OptionsHint(modifier: Modifier, onTap: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(px(3f)))
                .border(px(0.8f), Color.White.copy(alpha = 0.35f), RoundedCornerShape(px(3f)))
                .pointerInput(Unit) { detectTapGestures { onTap() } }
                .padding(horizontal = px(5f), vertical = px(2f)),
    ) {
        Canvas(modifier = Modifier.size(px(8f))) {
            val p =
                Path().apply {
                    moveTo(size.width / 2f, size.height * 0.1f)
                    lineTo(size.width * 0.95f, size.height * 0.9f)
                    lineTo(size.width * 0.05f, size.height * 0.9f)
                    close()
                }
            drawPath(
                p,
                Color.White,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width * 0.12f),
            )
        }
        Spacer(Modifier.width(px(4f)))
        BasicText("Options", style = crossText(Geo.SUBTITLE).copy(shadow = null))
    }
}

@Composable
private fun Clock(now: Calendar, modifier: Modifier) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val date = DateFormat.format(DateFormat.getBestDateTimePattern(locale, "dM"), now).toString()
    val time =
        DateFormat.format(
                DateFormat.getBestDateTimePattern(
                    locale,
                    if (DateFormat.is24HourFormat(context)) "Hm" else "hm",
                ),
                now,
            )
            .toString()
    val battery by
        produceState(batteryLevel(context)) {
            while (true) {
                value = batteryLevel(context)
                delay(60_000L)
            }
        }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        BasicText("$date $time", style = crossText(Geo.SUBTITLE + 1f))
        Spacer(Modifier.width(px(5f)))
        BatteryGlyph(battery)
    }
}

private fun batteryLevel(context: android.content.Context): Float {
    val intent: Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level < 0 || scale <= 0) 1f else level / scale.toFloat()
}

/** Three bars in a rounded case, as the device's battery mark. */
@Composable
private fun BatteryGlyph(level: Float) {
    Canvas(modifier = Modifier.size(px(16f), px(9f))) {
        val w = size.width
        val h = size.height
        val line = h * 0.12f
        drawRoundRect(
            Color.White,
            Offset(line / 2f, line / 2f),
            androidx.compose.ui.geometry.Size(w * 0.88f - line, h - line),
            androidx.compose.ui.geometry.CornerRadius(h * 0.2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(line),
        )
        drawRect(
            Color.White,
            Offset(w * 0.9f, h * 0.3f),
            androidx.compose.ui.geometry.Size(w * 0.1f, h * 0.4f),
        )
        val bars =
            when {
                level > 0.66f -> 3
                level > 0.33f -> 2
                level > 0.08f -> 1
                else -> 0
            }
        val gap = w * 0.05f
        val bw = (w * 0.88f - line * 2f - gap * 4f) / 3f
        for (b in 0 until bars) {
            drawRect(
                Color.White,
                Offset(line + gap + b * (bw + gap), line + gap),
                androidx.compose.ui.geometry.Size(bw, h - line * 2f - gap * 2f),
            )
        }
    }
}
