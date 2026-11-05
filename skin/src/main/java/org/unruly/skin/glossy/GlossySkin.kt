/*
 * Copyright (c) 2026 Unruly Project
 * GlossySkin.kt is part of Unruly.
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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.unruly.skin.AppSkin
import org.unruly.skin.SkinAlbum
import org.unruly.skin.SkinSearchResults
import org.unruly.skin.SkinSession
import org.unruly.skin.SkinSong
import org.unruly.skin.support.SkinColors
import org.unruly.skin.support.SkinTheme
import org.unruly.skin.support.SkinTypography

/** A page in the skin's navigation. */
internal sealed interface GDest {
    val title: String

    data object Root : GDest {
        override val title = ""
    }

    data class ArtistAlbums(val id: String, override val title: String) : GDest

    data class AlbumDetail(val id: String, override val title: String) : GDest

    data class Songs(val kind: SongSource, val id: String, override val title: String) : GDest

    data object Genres : GDest {
        override val title = "Genres"
    }

    data class NowPlaying(val lyrics: Boolean = false) : GDest {
        override val title = "Now Playing"
    }

    /**
     * The song picker. With a [playlistId] the picked songs are appended to that playlist; without
     * one, Done creates a playlist called [name] (blank takes the default name) holding them.
     */
    data class PickSongs(val playlistId: String?, val name: String) : GDest {
        override val title = "Songs"
    }
}

internal enum class SongSource {
    ALL,
    GENRE,
    PLAYLIST,
}

/**
 * A skin after the phone music app of 2012: silver navigation bars and tab bar, bold grey lists with
 * etched separators and A-Z indexes, and the black, glossy now-playing screen whose controls slide
 * over the cover. Built from a screen recording; see [GlossyTheme] for the measurements.
 *
 * Deviations, all from what the contract lacks: no Store (the left button opens settings), no Genius
 * or ratings (the rating row shows empty dots), no Cover Flow (not in the reference recording), and
 * the player's centre control shows lyrics instead of Genius.
 */
class GlossySkin : AppSkin {
    override val id: String = "glossy"
    override val displayName: String = "iOS 6 Music"
    // Its nav bar already has a Settings button, which stands in for the host's gear.
    override val settingsGearAlignment: Alignment? = null
    override val supportsEdgeToEdge: Boolean = false

    private val stack = mutableStateListOf<GDest>(GDest.Root)
    private var forward = true
    private var tab by mutableStateOf(Tab.PLAYLISTS)
    private var query by mutableStateOf("")
    private var overlay by mutableStateOf<GOverlay?>(null)

    internal fun push(dest: GDest) {
        forward = true
        stack.add(dest)
    }

    internal fun pop(): Boolean {
        if (stack.size <= 1) return false
        forward = false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** The label of the page beneath the current one, for its back button. */
    internal fun backLabel(): String {
        val below = stack.getOrNull(stack.lastIndex - 1) ?: return "Back"
        return if (below == GDest.Root) tab.label else below.title
    }

    override fun onBackPressed(): Boolean {
        if (overlay != null) {
            overlay = null
            return true
        }
        return pop()
    }

    @Composable
    override fun Render(session: SkinSession) {
        SkinTheme(colors = SkinColors.lightDefault(), typography = SkinTypography.default(), darkSystemBars = false) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Glossy.ListBg)) {
                ProvidePoints(maxWidth) {
                    AnimatedContent(
                        targetState = stack.last(),
                        transitionSpec = {
                            val dir = if (forward) 1 else -1
                            (slideInHorizontally(tween(280)) { it * dir } togetherWith
                                slideOutHorizontally(tween(280)) { -it * dir / 3 })
                        },
                        label = "glossy-nav",
                    ) { dest ->
                        when (dest) {
                            GDest.Root -> RootScreen(session)
                            is GDest.ArtistAlbums -> ArtistAlbumsScreen(session, dest)
                            is GDest.AlbumDetail -> AlbumScreen(session, dest)
                            is GDest.Songs -> SongsScreen(session, dest)
                            GDest.Genres -> GenresScreen(session)
                            is GDest.NowPlaying -> NowPlayingScreen(this@GlossySkin, session, dest.lyrics)
                            is GDest.PickSongs -> PickSongsScreen(session, dest)
                        }
                    }
                    when (val shown = overlay) {
                        is GOverlay.Name -> NameAlert(shown) { overlay = null }
                        is GOverlay.Confirm -> ConfirmSheet(shown) { overlay = null }
                        null -> {}
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Root: tabs
    // ---------------------------------------------------------------------------------------

    @Composable
    private fun RootScreen(session: SkinSession) {
        val playback by session.playback.collectAsState()
        Column(modifier = Modifier.fillMaxSize()) {
            NavBar(
                title = tab.label,
                left = {
                    Box(session.settingsButtonAnchor.modifier) {
                        BarButton("Settings", session.openAppSettings)
                    }
                },
                right = nowPlayingButton(playback.song != null),
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Glossy.ListBg)) {
                when (tab) {
                    Tab.PLAYLISTS -> PlaylistsTab(session)
                    Tab.ARTISTS -> ArtistsTab(session)
                    Tab.SONGS -> SongsTab(session)
                    Tab.ALBUMS -> AlbumsTab(session)
                    Tab.MORE -> MoreTab(session)
                }
            }
            TabBar(tab) {
                tab = it
                query = ""
            }
        }
    }

    private fun nowPlayingButton(visible: Boolean): (@Composable () -> Unit)? =
        if (!visible) null else { { BarButton("Now\nPlaying", { push(GDest.NowPlaying()) }, Point.RIGHT, dark = true, twoLine = true) } }

    /** A search bar heading the list; while it holds text the list is replaced by results. */
    @Composable
    private fun Searchable(session: SkinSession, list: @Composable () -> Unit) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchBar(query) { query = it }
            if (query.isBlank()) list() else SearchResultsList(session, query)
        }
    }

    @Composable
    private fun PlaylistsTab(session: SkinSession) {
        val playlists =
            remember(session.library) { session.library.playlists() }.collectAsLazyPagingItems().itemSnapshotList.items
        Searchable(session) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "add") {
                    ListRow("Add Playlist...", {
                        overlay =
                            GOverlay.Name("New Playlist", "Enter a name for this playlist", "") { name ->
                                push(GDest.PickSongs(null, name))
                            }
                    }, chevron = false)
                }
                items(playlists, key = { it.id }) { p ->
                    ListRow(p.name, { push(GDest.Songs(SongSource.PLAYLIST, p.id, p.name)) })
                }
            }
        }
    }

    @Composable
    private fun ArtistsTab(session: SkinSession) {
        val artists =
            remember(session.library) { session.library.artists() }.collectAsLazyPagingItems().itemSnapshotList.items
        Searchable(session) {
            IndexedList(artists, nameOf = { it.name }, key = { it.id }) { a ->
                val name = a.name.ifBlank { "Unknown Artist" }
                ListRow(name, { push(GDest.ArtistAlbums(a.id, name)) })
            }
        }
    }

    @Composable
    private fun SongsTab(session: SkinSession) {
        val songs = remember(session.library) { session.library.songs() }.collectAsLazyPagingItems().itemSnapshotList.items
        Searchable(session) {
            IndexedList(
                songs,
                nameOf = { it.title },
                key = { it.id },
                top = {
                    item(key = "shuffle") {
                        ShuffleRow {
                            session.commands.shuffleAll()
                            push(GDest.NowPlaying())
                        }
                    }
                },
                topCount = 1,
            ) { s ->
                SongRow(s) {
                    session.commands.playSongFromAll(s.id)
                    push(GDest.NowPlaying())
                }
            }
        }
    }

    @Composable
    private fun AlbumsTab(session: SkinSession) {
        val albums = remember(session.library) { session.library.albums() }.collectAsLazyPagingItems().itemSnapshotList.items
        Searchable(session) {
            IndexedList(
                albums,
                nameOf = { it.title },
                key = { it.id },
                top = {
                    item(key = "all") {
                        ListRow("All Songs", { push(GDest.Songs(SongSource.ALL, "", "Songs")) }, chevron = false)
                    }
                },
                topCount = 1,
            ) { a ->
                AlbumRow(a)
            }
        }
    }

    @Composable
    private fun MoreTab(session: SkinSession) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { ListRow("Genres", { push(GDest.Genres) }) }
            item { ListRow("Lyrics", { push(GDest.NowPlaying(lyrics = true)) }) }
            item { ListRow("Settings", session.openAppSettings) }
        }
    }

    @Composable
    private fun AlbumRow(a: SkinAlbum) {
        ListRow(
            a.title,
            { push(GDest.AlbumDetail(a.id, a.title)) },
            subtitle = a.artist.ifBlank { "Unknown Artist" },
            leading = { RowArt(a.albumArtUri) },
            height = 45f,
            titleSize = 18f,
            chevron = false,
        )
    }

    @Composable
    private fun SongRow(s: SkinSong, onClick: () -> Unit) {
        ListRow(s.title, onClick, subtitle = s.artist.ifBlank { "Unknown Artist" }, titleSize = 18f, chevron = false)
    }

    @Composable
    private fun ShuffleRow(onClick: () -> Unit) {
        ListRow("Shuffle", onClick, chevron = false, trailingIcon = {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(pt(22), pt(16))) {
                drawShuffleGlyph(Color(0xFF5E5E60))
            }
        })
    }

    /**
     * An alphabetised list with sticky grey section bars and the A-Z strip. Names that start with
     * anything but a letter file under "#" at the end, as on the original.
     */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun <T : Any> IndexedList(
        items: List<T>,
        nameOf: (T) -> String,
        key: (T) -> Any,
        top: LazyListScope.() -> Unit = {},
        topCount: Int = 0,
        row: @Composable (T) -> Unit,
    ) {
        val groups =
            remember(items) {
                val byLetter = items.groupBy { item ->
                    nameOf(item).trim().firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' }?.toString() ?: "#"
                }
                byLetter.keys.sortedWith(compareBy({ it == "#" }, { it })).map { it to byLetter.getValue(it) }
            }
        val state = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val headerIndex =
            remember(groups, topCount) {
                var i = topCount
                groups.associate { (letter, list) -> letter to i.also { i += list.size + 1 } }
            }
        val indexed = groups.size > 1
        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalIndexInset provides if (indexed) pt(INDEX_STRIP_PT) else 0.dp) {
                LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                    top()
                    groups.forEach { (letter, list) ->
                        stickyHeader(key = "h-$letter") { SectionHeader(letter) }
                        items(list, key = key) { row(it) }
                    }
                }
            }
            if (indexed) {
                val letters = ('A'..'Z').map { it.toString() } + "#"
                IndexStrip(
                    letters,
                    onLetter = { l ->
                        val target = groups.firstOrNull { (g, _) -> g == l || (g != "#" && l != "#" && g > l) }?.first
                            ?: groups.last().first
                        scope.launch { state.scrollToItem(headerIndex.getValue(target)) }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }

    @Composable
    private fun SearchResultsList(session: SkinSession, q: String) {
        var debounced by remember { mutableStateOf(q) }
        LaunchedEffect(q) {
            delay(200)
            debounced = q
        }
        val results by remember(session.library, debounced) { session.library.search(debounced) }
            .collectAsState(initial = SkinSearchResults())
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (results.artists.isNotEmpty()) {
                item { SectionHeader("Artists") }
                items(results.artists, key = { "a-${it.id}" }) { a ->
                    val name = a.name.ifBlank { "Unknown Artist" }
                    ListRow(name, { push(GDest.ArtistAlbums(a.id, name)) })
                }
            }
            if (results.albums.isNotEmpty()) {
                item { SectionHeader("Albums") }
                items(results.albums, key = { "b-${it.id}" }) { AlbumRow(it) }
            }
            if (results.songs.isNotEmpty()) {
                item { SectionHeader("Songs") }
                items(results.songs, key = { "s-${it.id}" }) { s ->
                    SongRow(s) {
                        session.commands.playSongs(results.songs, results.songs.indexOf(s))
                        push(GDest.NowPlaying())
                    }
                }
            }
            if (results.isEmpty && debounced.isNotBlank()) item { EmptyNote("No Results") }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Pages
    // ---------------------------------------------------------------------------------------

    @Composable
    private fun Page(
        session: SkinSession,
        title: String,
        left: (@Composable () -> Unit)? = null,
        right: (@Composable () -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        val playback by session.playback.collectAsState()
        Column(modifier = Modifier.fillMaxSize().background(Glossy.ListBg)) {
            NavBar(
                title = title,
                left = left ?: { BarButton(backLabel(), { pop() }, Point.LEFT) },
                right = right ?: nowPlayingButton(playback.song != null),
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
            TabBar(tab) {
                tab = it
                query = ""
                while (stack.size > 1) stack.removeAt(stack.lastIndex)
            }
        }
    }

    @Composable
    private fun ArtistAlbumsScreen(session: SkinSession, dest: GDest.ArtistAlbums) {
        val albums =
            remember(session.library, dest.id) { session.library.albumsByArtist(dest.id) }
                .collectAsLazyPagingItems()
                .itemSnapshotList
                .items
        Page(session, dest.title) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    ListRow("All Songs", {
                        session.commands.playArtist(dest.id)
                        push(GDest.NowPlaying())
                    }, chevron = false)
                }
                items(albums, key = { it.id }) { AlbumRow(it) }
            }
        }
    }

    @Composable
    private fun AlbumScreen(session: SkinSession, dest: GDest.AlbumDetail) {
        val unordered by remember(session.library, dest.id) { session.library.songsByAlbum(dest.id) }
            .collectAsState(initial = emptyList())
        val songs = remember(unordered) { inAlbumOrder(unordered) }
        val album by produceState<SkinAlbum?>(null, dest.id) { value = session.library.findAlbum(dest.id) }
        Page(session, dest.title) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { AlbumHeader(album, songs) }
                items(songs, key = { it.id }) { s ->
                    TrackRow(s) {
                        session.commands.playSongFromAlbum(s.id, dest.id)
                        push(GDest.NowPlaying())
                    }
                }
            }
        }
    }

    @Composable
    private fun SongsScreen(session: SkinSession, dest: GDest.Songs) {
        if (dest.kind == SongSource.PLAYLIST) {
            PlaylistScreen(session, dest)
            return
        }
        val songs: List<SkinSong> =
            when (dest.kind) {
                SongSource.ALL ->
                    remember(session.library) { session.library.songs() }.collectAsLazyPagingItems().itemSnapshotList.items
                SongSource.GENRE ->
                    remember(session.library, dest.id) { session.library.songsByGenre(dest.id) }
                        .collectAsLazyPagingItems()
                        .itemSnapshotList
                        .items
                SongSource.PLAYLIST -> error("playlists have their own screen")
            }
        Page(session, dest.title) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    ShuffleRow {
                        if (songs.isNotEmpty()) {
                            session.commands.playSongs(songs.shuffled(), 0)
                            push(GDest.NowPlaying())
                        }
                    }
                }
                items(songs, key = { it.id }) { s ->
                    SongRow(s) {
                        when (dest.kind) {
                            SongSource.ALL -> session.commands.playSongFromAll(s.id)
                            SongSource.GENRE -> session.commands.playSongFromGenre(s.id, dest.id)
                            SongSource.PLAYLIST -> {}
                        }
                        push(GDest.NowPlaying())
                    }
                }
            }
        }
    }

    /**
     * A playlist: Edit / Clear / Delete in a band under the bar, then Shuffle and the songs. Edit
     * mode swaps the band for the name (typing renames it), gives every song a minus disc that arms
     * a Delete button, and puts "+" (more songs) and Done in the bar.
     */
    @Composable
    private fun PlaylistScreen(session: SkinSession, dest: GDest.Songs) {
        val songs by remember(session.library, dest.id) { session.library.songsByPlaylist(dest.id) }
            .collectAsState(initial = emptyList())
        // The live name, so a rename shows here and survives a trip to the picker and back. Until
        // the rename lands in the library, the name just committed stands in for it.
        val playlists =
            remember(session.library) { session.library.playlists() }.collectAsLazyPagingItems().itemSnapshotList.items
        var committed by remember { mutableStateOf<String?>(null) }
        val stored = playlists.firstOrNull { it.id == dest.id }?.name
        val title = committed ?: stored ?: dest.title
        if (committed != null && stored == committed) committed = null
        var editing by remember { mutableStateOf(false) }
        var name by remember { mutableStateOf("") }
        var armed by remember { mutableStateOf<Int?>(null) }
        val focus = LocalFocusManager.current
        val finishEditing = {
            focus.clearFocus()
            val trimmed = name.trim()
            if (trimmed.isNotEmpty() && trimmed != title) {
                session.commands.renamePlaylist(dest.id, trimmed)
                committed = trimmed
            }
            armed = null
            editing = false
        }
        Page(
            session,
            title,
            left = if (editing) { { BarButton("+", { push(GDest.PickSongs(dest.id, title)) }, textSize = 22f) } } else null,
            right = if (editing) { { BarButton("Done", finishEditing) } } else null,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PlaylistBand(
                    editing = editing,
                    name = name,
                    onName = { name = it },
                    onEdit = {
                        name = title
                        editing = true
                    },
                    onClear = {
                        if (songs.isNotEmpty()) {
                            overlay =
                                GOverlay.Confirm("Clear Playlist") {
                                    // From the end, so each index still names the song it did.
                                    songs.withIndex().reversed().forEach { (i, s) ->
                                        session.commands.removeFromPlaylist(dest.id, s.id, i)
                                    }
                                }
                        }
                    },
                    onDelete = {
                        overlay =
                            GOverlay.Confirm("Delete Playlist") {
                                session.commands.deletePlaylist(dest.id)
                                pop()
                            }
                    },
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (!editing) {
                        item(key = "shuffle") {
                            ShuffleRow {
                                if (songs.isNotEmpty()) {
                                    session.commands.playSongs(songs.shuffled(), 0)
                                    push(GDest.NowPlaying())
                                }
                            }
                        }
                    }
                    // Keyed by place as well as id: a playlist can hold the same song twice.
                    itemsIndexed(songs, key = { i, s -> "$i-${s.id}" }) { i, s ->
                        val artist = s.artist.ifBlank { "Unknown Artist" }
                        if (editing) {
                            EditableSongRow(
                                s.title,
                                artist,
                                armed = armed == i,
                                onArm = { armed = if (armed == i) null else i },
                                onDelete = {
                                    armed = null
                                    session.commands.removeFromPlaylist(dest.id, s.id, i)
                                },
                            )
                        } else {
                            SongRow(s) {
                                session.commands.playSongFromPlaylist(s.id, dest.id)
                                push(GDest.NowPlaying())
                            }
                        }
                    }
                    if (songs.isEmpty() && !editing) item(key = "empty") { EmptyNote("No Songs") }
                }
            }
        }
    }

    /**
     * Pick songs for a playlist: every song with a blue "+", greyed once picked (tap again to put it
     * back). Done writes them in the order they were picked and returns.
     */
    @Composable
    private fun PickSongsScreen(session: SkinSession, dest: GDest.PickSongs) {
        val all = remember(session.library) { session.library.songs() }.collectAsLazyPagingItems().itemSnapshotList.items
        val picked = remember { mutableStateListOf<SkinSong>() }
        var filter by remember { mutableStateOf("") }
        val shown =
            remember(all, filter) {
                val q = filter.trim()
                if (q.isEmpty()) all
                else all.filter { it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true) }
            }
        val done = {
            if (dest.playlistId == null) {
                session.commands.createPlaylist(dest.name.ifBlank { null }, picked.toList())
            } else if (picked.isNotEmpty()) {
                session.commands.addToPlaylist(dest.playlistId, picked.toList())
            }
            pop()
            Unit
        }
        Column(modifier = Modifier.fillMaxSize().background(Glossy.ListBg)) {
            NavBar(title = "Songs", right = { BarButton("Done", done) })
            PromptBand(
                if (dest.name.isBlank()) "Add songs to the new playlist."
                else "Add songs to \u201C${dest.name}\u201D."
            )
            SearchBar(filter) { filter = it }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                IndexedList(shown, nameOf = { it.title }, key = { it.id }) { s ->
                    val added = s in picked
                    ListRow(
                        s.title,
                        {
                            if (added) picked.remove(s) else picked.add(s)
                            Unit
                        },
                        subtitle = s.artist.ifBlank { "Unknown Artist" },
                        titleSize = 18f,
                        chevron = false,
                        dimmed = added,
                        trailingIcon = { PlusBadge(added) },
                    )
                }
            }
        }
    }

    @Composable
    private fun GenresScreen(session: SkinSession) {
        val genres = remember(session.library) { session.library.genres() }.collectAsLazyPagingItems().itemSnapshotList.items
        Page(session, "Genres") {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(genres, key = { it.id }) { g ->
                    val name = g.name.ifBlank { "Unknown Genre" }
                    ListRow(name, { push(GDest.Songs(SongSource.GENRE, g.id, name)) })
                }
            }
        }
    }

    /** The album page's head: a grey gradient band with the cover, its reflection and the details. */
    @Composable
    private fun AlbumHeader(album: SkinAlbum?, songs: List<SkinSong>) {
        val minutes = (songs.sumOf { it.durationMs } / 60_000).toInt()
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(pt(104))
                    .background(Brush.verticalGradient(listOf(Color(0xFFE6E6E8), Color(0xFFB1B1B3))))
                    .drawBehind {
                        drawLine(Color(0xFF8E8E90), Offset(0f, size.height - 1), Offset(size.width, size.height - 1), 2f)
                    }
                    .padding(start = pt(10), top = pt(10)),
        ) {
            Column {
                Box(modifier = Modifier.size(pt(76)).background(Color.White).padding(pt(2))) { RowArt(album?.albumArtUri) }
                // The reflection: the cover's lower edge, flipped and fading into the band.
                Box(
                    modifier =
                        Modifier.size(pt(76), pt(14)).graphicsLayer {
                            scaleY = -1f
                            alpha = 0.35f
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.White).padding(pt(2))) { RowArt(album?.albumArtUri) }
                }
            }
            Column(modifier = Modifier.padding(start = pt(10), end = pt(10))) {
                BasicText(album?.artist.orEmpty(), style = embossed(13f, Color(0xFF3E3E40)), maxLines = 1)
                BasicText(album?.title.orEmpty(), style = embossed(18f, Color(0xFF222224)), maxLines = 1)
                album?.year?.let { BasicText("Released $it", style = embossed(10f, Color(0xFF5D5D5F), androidx.compose.ui.text.font.FontWeight.Normal)) }
                BasicText(
                    "${songs.size} ${if (songs.size == 1) "Song" else "Songs"}, $minutes Mins.",
                    style = embossed(10f, Color(0xFF5D5D5F), androidx.compose.ui.text.font.FontWeight.Normal),
                )
            }
        }
    }

    /** A track row on the album page: number column, divider, title, duration. */
    @Composable
    private fun TrackRow(s: SkinSong, onClick: () -> Unit) {
        var pressed = false
        val modifier = Modifier.pressAware(onClick) { pressed = it }
        val text = if (pressed) Color.White else Color(0xFF333335)
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(pt(44))
                    .then(
                        if (pressed) Modifier.background(Brush.verticalGradient(listOf(Glossy.SelectTop, Glossy.SelectBottom)))
                        else Modifier.background(Glossy.ListBg)
                    )
                    .drawBehind {
                        drawLine(Glossy.SepLight, Offset(0f, 0f), Offset(size.width, 0f), 2f)
                        drawLine(Glossy.SepDark, Offset(0f, size.height - 1), Offset(size.width, size.height - 1), 2f)
                    },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(pt(38)), contentAlignment = Alignment.Center) {
                BasicText((s.trackNumber ?: 0).takeIf { it > 0 }?.toString() ?: "", style = heros(15f, text))
            }
            Box(
                modifier =
                    Modifier.width(pt(2)).height(pt(44)).drawBehind {
                        drawLine(Glossy.SepDark, Offset(0f, 0f), Offset(0f, size.height), 2f)
                        drawLine(Glossy.SepLight, Offset(2f, 0f), Offset(2f, size.height), 2f)
                    }
            )
            BasicText(
                s.title,
                modifier = Modifier.weight(1f).padding(start = pt(10)),
                style = heros(16f, text),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            BasicText(formatTime(s.durationMs), style = heros(16f, text), modifier = Modifier.padding(horizontal = pt(10)))
        }
    }
}

/**
 * An album's songs by disc, then track number; untagged tracks keep their place after the tagged
 * ones. The library hands them over in no particular order.
 */
internal fun inAlbumOrder(songs: List<SkinSong>): List<SkinSong> =
    songs.sortedWith(compareBy({ it.discNumber ?: 1 }, { it.trackNumber ?: Int.MAX_VALUE }))

/** m:ss */
internal fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** The crossed arrows of shuffle, drawn to fill the scope. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShuffleGlyph(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = h * 0.14f
    fun arrow(fromY: Float, toY: Float) {
        val path =
            androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, fromY)
                lineTo(w * 0.22f, fromY)
                cubicTo(w * 0.5f, fromY, w * 0.45f, toY, w * 0.72f, toY)
                lineTo(w * 0.8f, toY)
            }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawPath(
            androidx.compose.ui.graphics.Path().apply {
                moveTo(w, toY)
                lineTo(w * 0.76f, toY - h * 0.22f)
                lineTo(w * 0.76f, toY + h * 0.22f)
                close()
            },
            color,
        )
    }
    arrow(h * 0.25f, h * 0.75f)
    arrow(h * 0.75f, h * 0.25f)
}
