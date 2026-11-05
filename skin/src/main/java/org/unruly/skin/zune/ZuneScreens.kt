/*
 * Copyright (c) 2026 Unruly Project
 * ZuneScreens.kt is part of Unruly.
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

package org.unruly.skin.zune

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import android.media.AudioManager
import android.os.Build
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.paging.compose.collectAsLazyPagingItems
import kotlin.math.floor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.unruly.skin.SkinAlbum
import org.unruly.skin.SkinRepeatMode
import org.unruly.skin.SkinSearchResults
import org.unruly.skin.SkinSession
import org.unruly.skin.SkinSong
import org.unruly.skin.SkinSort
import org.unruly.skin.support.SkinAlbumArt
import org.unruly.skin.support.SkinLibraryGate
import org.unruly.skin.support.SkinNoResults
import org.unruly.skin.support.SkinTheme
import org.unruly.skin.support.rememberInterpolatedPosition
import org.unruly.skin.support.rememberMediaVolume

// ---------------------------------------------------------------------------------------------
// Home: the menu, with quickplay to its left
// ---------------------------------------------------------------------------------------------

/** How many covers quickplay's "New" section shows. */
private const val NEW_ALBUM_COUNT = 8

private val MenuItemStyle =
    TextStyle(
        fontFamily = Selawik,
        fontWeight = FontWeight.Normal,
        fontSize = 60.sp,
        lineHeight = 64.sp,
        color = Color.White,
    )

private val QuickplayHeadingStyle =
    TextStyle(
        fontFamily = Selawik,
        fontWeight = FontWeight.Light,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        color = Color(0xFFD2D2D2),
    )

private data class MenuEntry(val label: String, val sub: String, val onClick: () -> Unit)

/**
 * The start screen, as two layers that trade places. On the menu, the big lower-case list fills
 * the screen and quickplay sits shrunk into the left gutter; swipe right and quickplay grows to
 * full size while the menu shrinks into a small column at the right. Everything between is driven
 * by one 0..1 [Animatable], so the drag tracks the finger and settles to whichever side is nearer.
 */
@Composable
internal fun ZuneSkin.homeScreen(session: SkinSession) {
    val playback by session.playback.collectAsState()
    val history = rememberHistory()
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    fun settle(target: Float) {
        scope.launch { progress.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 300f)) }
    }

    val entries =
        listOf(
            MenuEntry("music", "ARTISTS PLAYLISTS SONGS GENRES ALBUMS") { navigateToLibrary() },
            MenuEntry(
                "queue",
                if (playback.queue.isEmpty()) "" else plural(playback.queue.size, "song").uppercase(),
            ) {
                navigateToQueue()
            },
            MenuEntry("lyrics", playback.song?.title?.uppercase().orEmpty()) { navigateToLyrics() },
            MenuEntry("search", "ARTISTS ALBUMS SONGS") { navigateToSearch() },
            MenuEntry("settings", "SKINS LIBRARY LYRICS", session.openAppSettings),
        )

    ZuneSurface {
        BoxWithConstraints(
            modifier =
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { settle(if (progress.value > 0.5f) 1f else 0f) },
                        onDragCancel = { settle(if (progress.value > 0.5f) 1f else 0f) },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            scope.launch {
                                progress.snapTo(
                                    (progress.value + dx / (size.width * 0.7f)).coerceIn(0f, 1f)
                                )
                            }
                        },
                    )
                }
        ) {
            val width = maxWidth
            val density = LocalDensity.current
            val p = progress.value

            // Quickplay. Full size it fills all but the menu's column; shrunk, its right edge
            // sits in the gutter left of the menu words.
            val panelWidth = width - 74.dp
            Box(
                modifier =
                    Modifier.width(panelWidth).fillMaxHeight().graphicsLayer {
                        transformOrigin = TransformOrigin(1f, 0f)
                        val scale = lerp(0.3f, 1f, p)
                        scaleX = scale
                        scaleY = scale
                        translationX = lerp((84.dp - panelWidth).toPx(), 0f, p)
                        translationY = lerp(150.dp.toPx(), 0f, p)
                    }
            ) {
                QuickplayPanel(
                    session = session,
                    historyIds = history.albumIds,
                    interactive = p > 0.5f,
                    onOpenWhenSmall = { settle(1f) },
                )
            }

            // The menu. The ring beside "music" is now playing, as on the hardware.
            Column(
                modifier =
                    Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        val scale = lerp(1f, 0.36f, p)
                        scaleX = scale
                        scaleY = scale
                        translationX = with(density) { lerp(16.dp.toPx(), (width - 116.dp).toPx(), p) }
                        translationY = with(density) { lerp(96.dp.toPx(), 210.dp.toPx(), p) }
                    }
            ) {
                entries.forEachIndexed { index, entry ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(modifier = Modifier.width(62.dp).height(72.dp), contentAlignment = Alignment.CenterStart) {
                            if (index == 0 && playback.song != null) {
                                RingButton(
                                    glyph = Glyph.PLAY,
                                    description = "now playing",
                                    onClick = { navigateToNowPlaying() },
                                    diameter = 46.dp,
                                )
                            }
                        }
                        Column(modifier = Modifier.pressable(entry.onClick).padding(end = 12.dp)) {
                            BasicText(text = entry.label, style = MenuItemStyle, maxLines = 1, softWrap = false)
                            // The sub-label tucks up under the word and fades as the menu shrinks.
                            BasicText(
                                text = entry.sub,
                                modifier =
                                    Modifier.offset(x = 16.dp, y = (-7).dp).graphicsLayer {
                                        alpha = 1f - p
                                    },
                                style = CapsSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

/** Quickplay: Playing, History and New, each a heading over a two-column wall of covers. */
@Composable
private fun ZuneSkin.QuickplayPanel(
    session: SkinSession,
    historyIds: List<String>,
    interactive: Boolean,
    onOpenWhenSmall: () -> Unit,
) {
    val playback by session.playback.collectAsState()
    val recent =
        remember(session.library) {
                session.library.albums(SkinSort(SkinSort.By.DATE_ADDED, ascending = false))
            }
            .collectAsLazyPagingItems()
            .itemSnapshotList
            .items
            .take(NEW_ALBUM_COUNT)
    val historyAlbums by
        produceState(initialValue = emptyList<SkinAlbum>(), historyIds, session.library) {
            value = historyIds.mapNotNull { session.library.findAlbum(it) }
        }
    val current = playback.song
    // The playing album already has its own section.
    val pastAlbums = historyAlbums.filter { it.id != current?.albumId }
    val openAlbum: (SkinAlbum) -> Unit = { open(SkinDestination.AlbumSongs(it.id, it.title)) }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .then(
                    if (interactive) Modifier.verticalScroll(rememberScrollState())
                    else Modifier.pressable(onOpenWhenSmall)
                )
                .padding(start = 36.dp, top = 28.dp, bottom = 24.dp)
    ) {
        if (current != null) {
            BasicText("Playing", style = QuickplayHeadingStyle)
            CoverTile(
                uri = current.albumArtUri,
                label = current.album,
                onClick = { if (interactive) navigateToNowPlaying() else onOpenWhenSmall() },
            )
            Spacer(modifier = Modifier.height(28.dp))
        }
        if (pastAlbums.isNotEmpty()) {
            BasicText("History", style = QuickplayHeadingStyle)
            CoverWall(pastAlbums, interactive, openAlbum, onOpenWhenSmall)
            Spacer(modifier = Modifier.height(28.dp))
        }
        if (recent.isNotEmpty()) {
            BasicText("New", style = QuickplayHeadingStyle)
            CoverWall(recent, interactive, openAlbum, onOpenWhenSmall)
        }
    }
}

private val TileSize = 120.dp

@Composable
private fun CoverWall(
    albums: List<SkinAlbum>,
    interactive: Boolean,
    onOpen: (SkinAlbum) -> Unit,
    onOpenWhenSmall: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        albums.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pair.forEach { album ->
                    CoverTile(
                        uri = album.albumArtUri,
                        label = album.title,
                        onClick = { if (interactive) onOpen(album) else onOpenWhenSmall() },
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverTile(uri: Uri?, label: String, onClick: () -> Unit) {
    Box(modifier = Modifier.size(TileSize).pressable(onClick)) {
        SkinAlbumArt(
            uri = uri,
            contentDescription = label,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            fallback = { NamedTile(label) },
        )
    }
}

/** Stand-in for a missing cover: the name in small capitals on a grey square, as on the hardware. */
@Composable
private fun NamedTile(label: String, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier = modifier.background(SkinPaletteColors.surface), contentAlignment = Alignment.BottomStart) {
        BasicText(
            text = label.uppercase(),
            modifier = Modifier.padding(6.dp),
            style = CapsBold.copy(fontSize = 11.sp, lineHeight = 12.sp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The music hub
// ---------------------------------------------------------------------------------------------

/** The hardware's pivot order. The row is a loop: whichever is active comes first. */
private enum class Pivot(val title: String) {
    ARTISTS("ARTISTS"),
    PLAYLISTS("PLAYLISTS"),
    SONGS("SONGS"),
    GENRES("GENRES"),
    ALBUMS("ALBUMS"),
}

/** A pager this long never runs out in practice; it starts mid-way so both directions loop. */
private const val PIVOT_PAGES = 5_000

private val PivotStyle =
    TextStyle(fontFamily = Selawik, fontWeight = FontWeight.SemiBold, fontSize = 27.sp, lineHeight = 30.sp)

private val PivotGap = 10.dp

@Composable
internal fun ZuneSkin.libraryScreen(session: SkinSession) {
    val loadState by session.library.loadState.collectAsState()
    SkinLibraryGate(
        loadState = loadState,
        scanning = { loaded, explored ->
            BlackFrame(title = "music") {
                SectionLabel("indexing")
                BasicText(
                    text = if (loaded == null || explored == null) "scanning" else "$loaded / $explored",
                    style = SkinTheme.typography.body,
                )
            }
        },
        failed = { message ->
            BlackFrame(title = "music") {
                SectionLabel("library error")
                BasicText(text = message, style = SkinTheme.typography.body)
            }
        },
        empty = {
            BlackFrame(title = "music") {
                EmptyMessage("nothing here yet")
                AccentLink(text = "settings", onClick = session.openAppSettings)
            }
        },
        ready = { musicHub(session) },
    )
}

/**
 * The hub: the cropped giant "music", the pivot row, and the lists. Swiping a list moves all
 * three - the lists a page at a time, the pivot row by exactly the width of the name leaving it,
 * and the heading by a fraction of that, which is the screen's depth.
 */
@Composable
private fun ZuneSkin.musicHub(session: SkinSession) {
    val start = PIVOT_PAGES / 2 - (PIVOT_PAGES / 2) % Pivot.entries.size
    val pager = rememberPagerState(initialPage = start, pageCount = { PIVOT_PAGES })
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelWidths =
        remember(measurer, density) {
            Pivot.entries.map { measurer.measure(it.title, PivotStyle).size.width.toFloat() }
        }
    val gapPx = with(density) { PivotGap.toPx() }

    ZuneSurface {
        Box(modifier = Modifier.fillMaxSize()) {
            val position = pager.currentPage + pager.currentPageOffsetFraction
            val base = floor(position).toInt()
            val fraction = position - base
            val cycle = ((position % 5f) + 5f) % 5f
            GiantHeading("music", shift = (-cycle * 22f).dp)

            // Pivot row: the active name first, the rest following in loop order, slid left by
            // the progress through the current page.
            Row(
                modifier =
                    Modifier.padding(top = GiantHeadingVisible + 4.dp)
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .graphicsLayer {
                            translationX =
                                PageMargin.toPx() - fraction * (labelWidths[Math.floorMod(base, 5)] + gapPx)
                        },
                horizontalArrangement = Arrangement.spacedBy(PivotGap),
            ) {
                for (i in 0..5) {
                    val pivot = Pivot.entries[Math.floorMod(base + i, 5)]
                    val emphasis =
                        when (i) {
                            0 -> 1f - fraction
                            1 -> fraction
                            else -> 0f
                        }
                    BasicText(
                        text = pivot.title,
                        modifier =
                            Modifier.pressable {
                                scope.launch { pager.animateScrollToPage(base + i) }
                            },
                        style = PivotStyle.copy(color = lerp(ZuneLavender, Color.White, emphasis)),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().padding(top = GiantHeadingVisible + 50.dp),
                verticalAlignment = Alignment.Top,
                beyondViewportPageCount = 0,
            ) { page ->
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = PageMargin)) {
                    when (Pivot.entries[Math.floorMod(page, 5)]) {
                        Pivot.ARTISTS -> artistsPivot(session)
                        Pivot.PLAYLISTS -> playlistsPivot(session)
                        Pivot.SONGS -> songsPivot(session)
                        Pivot.GENRES -> genresPivot(session)
                        Pivot.ALBUMS -> albumsPivot(session)
                    }
                }
            }
        }
    }
}

/**
 * A name row: the ring that plays it, then the name in bold capitals with [detail] beneath. Tapping
 * the name opens it; tapping the ring plays it without leaving.
 */
@Composable
private fun NameRow(
    name: String,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    detail: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RingButton(glyph = Glyph.PLAY, description = "play $name", onClick = onPlay, diameter = 40.dp)
        Column(modifier = Modifier.weight(1f).pressable(onOpen).padding(start = 8.dp, top = 10.dp)) {
            BasicText(
                text = name.uppercase(),
                style = CapsBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            detail()
        }
    }
}

@Composable
private fun ZuneSkin.artistsPivot(session: SkinSession) {
    val artists =
        remember(session.library) { session.library.artists() }.collectAsLazyPagingItems()
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(count = artists.itemCount, key = { artists.peek(it)?.id ?: it }) { index ->
            artists[index]?.let { artist ->
                val name = displayArtist(artist.name)
                NameRow(
                    name = name,
                    onPlay = {
                        session.commands.playArtist(artist.id)
                        navigateToNowPlaying()
                    },
                    onOpen = { open(SkinDestination.ArtistAlbums(artist.id, name)) },
                ) {
                    // A small cover under the name, as the hardware shows the artist's albums.
                    SkinAlbumArt(
                        uri = artist.albumArtUri,
                        contentDescription = null,
                        modifier = Modifier.padding(top = 4.dp).size(20.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

@Composable
private fun ZuneSkin.playlistsPivot(session: SkinSession) {
    val playlists =
        remember(session.library) { session.library.playlists() }.collectAsLazyPagingItems()
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (playlists.itemCount == 0) item { EmptyMessage("no playlists") }
        items(count = playlists.itemCount, key = { playlists.peek(it)?.id ?: it }) { index ->
            playlists[index]?.let { playlist ->
                NameRow(
                    name = playlist.name,
                    onPlay = {
                        session.commands.playPlaylist(playlist.id)
                        navigateToNowPlaying()
                    },
                    onOpen = { open(SkinDestination.PlaylistSongs(playlist.id, playlist.name)) },
                ) {
                    BasicText(plural(playlist.songCount, "song").uppercase(), style = CapsSmall)
                }
            }
        }
    }
}

@Composable
private fun ZuneSkin.songsPivot(session: SkinSession) {
    val songs = remember(session.library) { session.library.songs() }.collectAsLazyPagingItems()
    val playback by session.playback.collectAsState()
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(count = songs.itemCount, key = { songs.peek(it)?.id ?: it }) { index ->
            songs[index]?.let { song ->
                SongRow(
                    song = song,
                    isCurrent = song.id == playback.song?.id,
                    onClick = {
                        session.commands.playSongFromAll(song.id)
                        navigateToNowPlaying()
                    },
                )
            }
        }
    }
}

@Composable
private fun ZuneSkin.genresPivot(session: SkinSession) {
    val genres = remember(session.library) { session.library.genres() }.collectAsLazyPagingItems()
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(count = genres.itemCount, key = { genres.peek(it)?.id ?: it }) { index ->
            genres[index]?.let { genre ->
                val name = genre.name.ifBlank { "unknown genre" }
                NameRow(
                    name = name,
                    onPlay = {
                        session.commands.playGenre(genre.id)
                        navigateToNowPlaying()
                    },
                    onOpen = { open(SkinDestination.GenreSongs(genre.id, name)) },
                ) {
                    BasicText(plural(genre.songCount, "song").uppercase(), style = CapsSmall)
                }
            }
        }
    }
}

@Composable
private fun ZuneSkin.albumsPivot(session: SkinSession) {
    val albums = remember(session.library) { session.library.albums() }.collectAsLazyPagingItems()
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(count = albums.itemCount, key = { albums.peek(it)?.id ?: it }) { index ->
            albums[index]?.let { album -> AlbumRow(session, album) }
        }
    }
}

/**
 * An album row: a large cover with a play ring over its right side, the title in bold capitals
 * beside it, the artist under that, and the first few track names in small dim type.
 */
@Composable
private fun ZuneSkin.AlbumRow(session: SkinSession, album: SkinAlbum) {
    val tracks by
        remember(session.library, album.id) { session.library.songsByAlbum(album.id) }
            .collectAsState(initial = emptyList())
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(modifier = Modifier.size(150.dp).pressable { open(SkinDestination.AlbumSongs(album.id, album.title)) }) {
            SkinAlbumArt(
                uri = album.albumArtUri,
                contentDescription = album.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                fallback = { NamedTile(album.title) },
            )
            RingButton(
                glyph = Glyph.PLAY,
                description = "play ${album.title}",
                onClick = {
                    session.commands.playAlbum(album.id)
                    navigateToNowPlaying()
                },
                diameter = 46.dp,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
            )
        }
        Column(
            modifier =
                Modifier.weight(1f)
                    .pressable { open(SkinDestination.AlbumSongs(album.id, album.title)) }
                    .padding(start = 12.dp, top = 22.dp)
        ) {
            BasicText(
                text = album.title.uppercase(),
                style = CapsBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = displayArtist(album.artist).uppercase(),
                style = CapsSmall.copy(fontSize = 15.sp, lineHeight = 17.sp, color = Color(0xFFD9D7F2)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            tracks.take(6).forEach { song ->
                BasicText(
                    text = song.title,
                    modifier = Modifier.padding(top = 3.dp),
                    style = SkinTheme.typography.label.copy(fontSize = 9.sp, lineHeight = 10.sp, color = ZuneDim),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Detail pages
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ZuneSkin.artistAlbumsScreen(session: SkinSession, destination: SkinDestination.ArtistAlbums) {
    val albums =
        remember(session.library, destination.artistId) {
                session.library.albumsByArtist(destination.artistId)
            }
            .collectAsLazyPagingItems()
    BlackFrame(title = destination.artistName) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(count = albums.itemCount, key = { albums.peek(it)?.id ?: it }) { index ->
                albums[index]?.let { album -> AlbumRow(session, album) }
            }
        }
    }
}

@Composable
internal fun ZuneSkin.albumSongsScreen(session: SkinSession, destination: SkinDestination.AlbumSongs) {
    val songs by
        remember(session.library, destination.albumId) {
                session.library.songsByAlbum(destination.albumId)
            }
            .collectAsState(initial = emptyList())
    val playback by session.playback.collectAsState()
    BlackFrame(title = destination.albumTitle) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(items = songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isCurrent = song.id == playback.song?.id,
                    onClick = {
                        session.commands.playSongFromAlbum(song.id, destination.albumId)
                        navigateToNowPlaying()
                    },
                )
            }
        }
    }
}

@Composable
internal fun ZuneSkin.genreSongsScreen(session: SkinSession, destination: SkinDestination.GenreSongs) {
    val songs =
        remember(session.library, destination.genreId) {
                session.library.songsByGenre(destination.genreId)
            }
            .collectAsLazyPagingItems()
    val playback by session.playback.collectAsState()
    BlackFrame(title = destination.genreName) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(count = songs.itemCount, key = { songs.peek(it)?.id ?: it }) { index ->
                songs[index]?.let { song ->
                    SongRow(
                        song = song,
                        isCurrent = song.id == playback.song?.id,
                        onClick = {
                            session.commands.playSongFromGenre(song.id, destination.genreId)
                            navigateToNowPlaying()
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ZuneSkin.playlistSongsScreen(session: SkinSession, destination: SkinDestination.PlaylistSongs) {
    val songs by
        remember(session.library, destination.playlistId) {
                session.library.songsByPlaylist(destination.playlistId)
            }
            .collectAsState(initial = emptyList())
    val playback by session.playback.collectAsState()
    BlackFrame(title = destination.playlistName) {
        if (songs.isEmpty()) EmptyMessage("no songs")
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(items = songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isCurrent = song.id == playback.song?.id,
                    onClick = {
                        session.commands.playSongFromPlaylist(song.id, destination.playlistId)
                        navigateToNowPlaying()
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ZuneSkin.searchScreen(session: SkinSession) {
    var query by remember { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        delay(250L)
        debounced = query
    }
    val results by
        remember(session.library, debounced) { session.library.search(debounced) }
            .collectAsState(initial = SkinSearchResults())
    BlackFrame(title = "search") {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            textStyle = SkinTheme.typography.body.copy(fontSize = 26.sp, fontWeight = FontWeight.Light),
            cursorBrush = SolidColor(Color.White),
            singleLine = true,
            decorationBox = { inner ->
                Column {
                    if (query.isEmpty()) {
                        BasicText(
                            "type to search",
                            style =
                                SkinTheme.typography.body.copy(
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Light,
                                    color = ZuneDim,
                                ),
                        )
                    }
                    inner()
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(1.dp).background(Color.White))
                }
            },
        )
        when {
            debounced.isEmpty() -> Unit
            results.isEmpty -> SkinNoResults(query = debounced, content = { EmptyMessage("no results for $it") })
            else ->
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (results.songs.isNotEmpty()) {
                        item { SectionLabel("songs") }
                        items(results.songs, key = { "s-${it.id}" }) { song ->
                            SongRow(
                                song = song,
                                onClick = {
                                    session.commands.playSongs(results.songs, results.songs.indexOf(song))
                                    navigateToNowPlaying()
                                },
                            )
                        }
                    }
                    if (results.albums.isNotEmpty()) {
                        item { SectionLabel("albums") }
                        items(results.albums, key = { "a-${it.id}" }) { album -> AlbumRow(session, album) }
                    }
                    if (results.artists.isNotEmpty()) {
                        item { SectionLabel("artists") }
                        items(results.artists, key = { "r-${it.id}" }) { artist ->
                            val name = displayArtist(artist.name)
                            NameRow(
                                name = name,
                                onPlay = {
                                    session.commands.playArtist(artist.id)
                                    navigateToNowPlaying()
                                },
                                onOpen = { open(SkinDestination.ArtistAlbums(artist.id, name)) },
                            )
                        }
                    }
                }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Now playing
// ---------------------------------------------------------------------------------------------

/** How long the transport overlay stays after the last touch. */
private const val OVERLAY_MS = 4_000L

/** How long without a touch before now playing gives way to the ambient album-title display. */
private const val IDLE_MS = 20_000L

/**
 * Now playing, in the hardware's three states:
 * - **Details**: back ring, artist and album in capitals, the cover with its progress bar and
 *   times, the song title, what plays next, and the shuffle / repeat / words rings.
 * - **Overlay** (tap): everything dims and the transport appears over the cover - the volume as a
 *   big number with + and -, skip and play, and the track in the corner. It leaves on its own.
 * - **Ambient** (left alone): the details fade away, the cover fills the screen dimly, and the
 *   album title's words tumble across it one at a time. Any touch brings the details back.
 */
@Composable
internal fun ZuneSkin.nowPlayingScreen(session: SkinSession) {
    val playback by session.playback.collectAsState()
    val position by rememberInterpolatedPosition(playback)
    val song = playback.song
    var overlay by remember { mutableStateOf(false) }
    var ambient by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    fun touched() {
        lastTouch = System.currentTimeMillis()
        ambient = false
    }

    LaunchedEffect(lastTouch, overlay) {
        if (overlay) {
            delay(OVERLAY_MS)
            overlay = false
        } else {
            delay(IDLE_MS)
            ambient = true
        }
    }

    ZuneSurface {
        if (song == null) {
            Box(modifier = Modifier.fillMaxSize().padding(start = PageMargin), contentAlignment = Alignment.CenterStart) {
                EmptyMessage("nothing playing")
            }
            return@ZuneSurface
        }
        val detailsAlpha by
            animateFloatAsState(
                targetValue =
                    when {
                        ambient -> 0f
                        overlay -> 0.28f
                        else -> 1f
                    },
                animationSpec = tween(400),
                label = "details",
            )
        Box(
            modifier =
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures {
                        if (ambient) touched()
                        else {
                            touched()
                            overlay = !overlay
                        }
                    }
                }
        ) {
            AmbientBackdrop(uri = song.albumArtUri, strength = if (ambient) 0.36f else 0.22f)
            if (ambient) KineticTitle(text = song.album.ifBlank { song.title })

            NowPlayingDetails(
                session = session,
                song = song,
                positionMs = position,
                enabled = !overlay && !ambient,
                onTouch = ::touched,
                modifier = Modifier.graphicsLayer { alpha = detailsAlpha },
            )

            AnimatedVisibility(visible = overlay, enter = fadeIn(tween(200)), exit = fadeOut(tween(300))) {
                TransportOverlay(
                    session = session,
                    song = song,
                    isPlaying = playback.isPlaying,
                    onTouch = ::touched,
                    onExit = { overlay = false },
                )
            }
        }
    }
}

@Composable
private fun ZuneSkin.NowPlayingDetails(
    session: SkinSession,
    song: SkinSong,
    positionMs: Long,
    enabled: Boolean,
    onTouch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback by session.playback.collectAsState()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val artSize = maxWidth * 0.64f
        Column(modifier = Modifier.fillMaxSize().padding(start = PageMargin + 8.dp, end = PageMargin)) {
            RingButton(
                glyph = Glyph.BACK,
                description = "back",
                onClick = { if (enabled) navigateBack() },
                diameter = 50.dp,
                // The overlay puts EXIT here; the ring would show through it.
                modifier =
                    Modifier.offset(x = (-8).dp).padding(top = 4.dp).graphicsLayer {
                        alpha = if (enabled) 1f else 0f
                    },
            )
            BasicText(
                text = displayArtist(song.artist).uppercase(),
                modifier = Modifier.padding(top = 10.dp),
                style = CapsBold.copy(fontSize = 29.sp, lineHeight = 28.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = song.album.uppercase(),
                style = TextStyle(fontFamily = Selawik, fontWeight = FontWeight.Light, fontSize = 29.sp, lineHeight = 32.sp, color = Color.White),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
            SkinAlbumArt(
                uri = song.albumArtUri,
                contentDescription = song.album,
                modifier = Modifier.padding(top = 6.dp).size(artSize),
                contentScale = ContentScale.Crop,
                fallback = { NamedTile(song.album, Modifier.size(artSize)) },
            )
            ProgressBar(
                positionMs = positionMs,
                durationMs = playback.durationMs,
                onSeek = {
                    onTouch()
                    session.commands.seekTo(it)
                },
                modifier = Modifier.width(artSize),
            )
            Row(modifier = Modifier.width(artSize), horizontalArrangement = Arrangement.SpaceBetween) {
                val timeStyle = CapsSmall.copy(color = Color.White, fontSize = 14.sp)
                BasicText(formatDuration(positionMs), style = timeStyle)
                BasicText(formatSignedDuration(positionMs - playback.durationMs), style = timeStyle)
            }
            BasicText(
                text = song.title,
                modifier = Modifier.padding(top = 6.dp).pressable { if (enabled) navigateToQueue() },
                style = SkinTheme.typography.body.copy(fontSize = 26.sp, lineHeight = 30.sp),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
            // What plays next, small and dim, as on the hardware.
            playback.queue.drop(playback.queueIndex + 1).take(3).forEach { next ->
                BasicText(
                    text = next.title,
                    style = SkinTheme.typography.label.copy(fontSize = 12.sp, color = ZuneDim),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier =
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).graphicsLayer {
                        // The overlay's track caption takes this corner.
                        alpha = if (enabled) 1f else 0f
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RingButton(
                    glyph = Glyph.SHUFFLE,
                    description = "shuffle",
                    onClick = {
                        onTouch()
                        if (enabled) session.commands.toggleShuffle()
                    },
                    dimmed = !playback.isShuffled,
                )
                RingButton(
                    glyph = if (playback.repeatMode == SkinRepeatMode.TRACK) Glyph.REPEAT_ONE else Glyph.REPEAT,
                    description = "repeat",
                    onClick = {
                        onTouch()
                        if (enabled) session.commands.cycleRepeatMode()
                    },
                    dimmed = playback.repeatMode == SkinRepeatMode.NONE,
                )
                RingButton(
                    glyph = Glyph.LYRICS,
                    description = "lyrics",
                    onClick = { if (enabled) navigateToLyrics() },
                )
            }
        }
    }
}

/** A thin bar under the cover; drag along it to seek. */
@Composable
private fun ProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragMs by remember(durationMs) { mutableStateOf<Long?>(null) }
    val commit by rememberUpdatedState(onSeek)
    val duration = durationMs.coerceAtLeast(0L)
    val shown = dragMs ?: positionMs
    val fraction = if (duration == 0L) 0f else (shown.toFloat() / duration).coerceIn(0f, 1f)
    Box(
        modifier =
            modifier.height(16.dp).pointerInput(durationMs) {
                var target = 0L
                fun at(x: Float) =
                    if (duration == 0L || size.width == 0) 0L
                    else (duration * (x / size.width).coerceIn(0f, 1f)).toLong()
                detectDragGestures(
                    onDragStart = {
                        target = at(it.x)
                        dragMs = target
                    },
                    onDrag = { change, _ ->
                        target = at(change.position.x)
                        dragMs = target
                    },
                    onDragEnd = {
                        dragMs = null
                        commit(target)
                    },
                    onDragCancel = { dragMs = null },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(Color(0xFF3C3C44)))
        Box(modifier = Modifier.fillMaxWidth(fraction).height(3.dp).background(Color(0xFFE6E6F0)))
    }
}

/** The transport overlay: volume, skip and play over the dimmed details. */
@Composable
private fun TransportOverlay(
    session: SkinSession,
    song: SkinSong,
    isPlaying: Boolean,
    onTouch: () -> Unit,
    onExit: () -> Unit,
) {
    val volume = rememberMediaVolume()
    fun adjust(direction: Int) {
        onTouch()
        volume.adjust(direction)
    }

    Box(modifier = Modifier.fillMaxSize().padding(horizontal = PageMargin + 8.dp)) {
        BasicText(
            text = "EXIT",
            modifier = Modifier.align(Alignment.TopStart).padding(top = 14.dp).pressable(onExit),
            style = CapsBold.copy(fontSize = 14.sp),
        )
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(top = 52.dp),
            verticalAlignment = Alignment.Top,
        ) {
            BasicText(
                text = volume.level.toString(),
                style = TextStyle(fontFamily = Selawik, fontWeight = FontWeight.Light, fontSize = 88.sp, lineHeight = 88.sp, color = Color.White),
            )
            BasicText(
                text = "+",
                modifier =
                    Modifier.padding(start = 18.dp, top = 2.dp).pressable {
                        adjust(AudioManager.ADJUST_RAISE)
                    },
                style = CapsBold.copy(fontSize = 58.sp, lineHeight = 58.sp),
            )
        }
        Row(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RingButton(
                glyph = Glyph.PREVIOUS,
                description = "previous track",
                onClick = {
                    onTouch()
                    session.commands.previous()
                },
                diameter = 60.dp,
                ring = false,
            )
            RingButton(
                glyph = if (isPlaying) Glyph.PAUSE else Glyph.PLAY,
                description = if (isPlaying) "pause" else "play",
                onClick = {
                    onTouch()
                    session.commands.playPause()
                },
                diameter = 104.dp,
            )
            RingButton(
                glyph = Glyph.NEXT,
                description = "next track",
                onClick = {
                    onTouch()
                    session.commands.next()
                },
                diameter = 60.dp,
                ring = false,
            )
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp).pressable {
                adjust(AudioManager.ADJUST_LOWER)
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.width(30.dp).height(9.dp).background(Color.White))
            BasicText("VOLUME", modifier = Modifier.padding(top = 6.dp), style = CapsBold.copy(fontSize = 12.sp))
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 14.dp)) {
            BasicText(displayArtist(song.artist).uppercase(), style = CapsBold.copy(fontSize = 17.sp))
            BasicText(
                text = song.title,
                style = SkinTheme.typography.body.copy(fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/**
 * The cover, filling the screen behind everything, slowly drifting - blurred and drained of colour,
 * so it reads as atmosphere behind the type rather than as a second copy of the art. (The hardware
 * used artist photographs here; the library only has covers.)
 */
@Composable
private fun AmbientBackdrop(uri: Uri?, strength: Float) {
    val alpha by animateFloatAsState(strength, tween(800), label = "backdrop")
    val drift by
        rememberInfiniteTransition(label = "drift")
            .animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing), RepeatMode.Reverse),
                label = "drift-x",
            )
    SkinAlbumArt(
        uri = uri,
        contentDescription = null,
        modifier =
            Modifier.fillMaxSize().graphicsLayer {
                this.alpha = alpha
                scaleX = 1.25f
                scaleY = 1.25f
                translationX = (drift - 0.5f) * size.width * 0.15f
                renderEffect = BackdropEffect
            },
        contentScale = ContentScale.Crop,
    )
}

/** Greyscale and blur for the backdrop. Render effects need API 31; older devices get it sharp. */
private val BackdropEffect =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        android.graphics.RenderEffect.createChainEffect(
                android.graphics.RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP),
                android.graphics.RenderEffect.createColorFilterEffect(
                    ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                ),
            )
            .asComposeRenderEffect()
    } else {
        null
    }

/**
 * The ambient display's type: the album title's words, one at a time, each tumbling in from a
 * quarter turn, holding, then leaving - the hardware's idle animation, reduced to its gesture.
 */
@Composable
private fun KineticTitle(text: String) {
    val words = remember(text) { text.uppercase().split(" ").filter { it.isNotBlank() }.ifEmpty { listOf(text) } }
    var index by remember(text) { mutableIntStateOf(0) }
    val turn = remember { Animatable(0f) }
    LaunchedEffect(text) {
        while (true) {
            turn.snapTo(0f)
            turn.animateTo(1f, tween(900))
            delay(1_400)
            turn.animateTo(2f, tween(500))
            index = (index + 1) % words.size
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val t = turn.value
        BasicText(
            text = words[index % words.size],
            modifier =
                Modifier.align(Alignment.Center).graphicsLayer {
                    val enter = t.coerceAtMost(1f)
                    val exit = (t - 1f).coerceAtLeast(0f)
                    rotationZ = lerp(-90f, if (index % 2 == 0) -24f else 18f, enter)
                    val scale = lerp(2.6f, 1f, enter) * lerp(1f, 0.6f, exit)
                    scaleX = scale
                    scaleY = scale
                    alpha = enter * (1f - exit)
                    translationX = lerp(size.width * 0.4f, 0f, enter) - exit * size.width * 0.5f
                },
            style = TextStyle(fontFamily = Selawik, fontWeight = FontWeight.Bold, fontSize = 110.sp, lineHeight = 110.sp, color = Color.White),
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Queue
// ---------------------------------------------------------------------------------------------

/**
 * The queue, as the hardware shows the now-playing list: the playing song's title as the giant
 * heading, "n OF m", then the songs, the playing one with its length in the gutter.
 */
@Composable
internal fun ZuneSkin.queueScreen(session: SkinSession) {
    val playback by session.playback.collectAsState()
    val queue = playback.queue
    BlackFrame(title = playback.song?.title ?: "queue") {
        if (queue.isEmpty()) {
            EmptyMessage("queue is empty")
            return@BlackFrame
        }
        BasicText(
            text = "${playback.queueIndex + 1} OF ${queue.size}",
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
            style = CapsBold.copy(fontSize = 22.sp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(count = queue.size, key = { "${queue[it].id}-$it" }) { index ->
                val isCurrent = index == playback.queueIndex
                SongRow(
                    song = queue[index],
                    isCurrent = false,
                    showTime = true,
                    timeVisible = isCurrent,
                    onClick = { session.commands.playQueueItem(index) },
                )
            }
        }
    }
}
