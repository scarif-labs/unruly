package org.shellac.skin.wheel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import org.shellac.skin.AppSkin
import org.shellac.skin.SkinAlbum
import org.shellac.skin.SkinArtist
import org.shellac.skin.SkinGenre
import org.shellac.skin.SkinPlaylist
import org.shellac.skin.SkinSession
import org.shellac.skin.SkinSong
import org.shellac.skin.support.SkinBackStack
import org.shellac.skin.support.SkinColors
import org.shellac.skin.support.SkinLibraryGate
import org.shellac.skin.support.SkinTheme
import org.shellac.skin.support.SkinTypography

private enum class RootItem {
    MUSIC,
    SHUFFLE_SONGS,
    NOW_PLAYING,
}

private enum class MusicItem {
    ARTISTS,
    ALBUMS,
    SONGS,
    GENRES,
    PLAYLISTS,
}

class TouchWheelSkin : AppSkin {
    override val id: String = "touch-wheel"
    override val displayName: String = "Touch Wheel"
    override val supportsEdgeToEdge: Boolean = false

    private val backStack = SkinBackStack<WheelDestination>(WheelDestination.Root)
    private val selectionIndices = mutableStateMapOf<WheelDestination, Int>()

    private val colors =
        SkinColors(
            background = Color(0xFF101214),
            surface = Color(0xFF202428),
            onBackground = Color(0xFFE2E5E7),
            onSurface = Color(0xFFB3B8BC),
            accent = Color(0xFF9EA5AA),
            onAccent = Color(0xFF101214),
        )

    private val typography =
        SkinTypography(
            title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
            body = TextStyle(fontSize = 17.sp),
            label = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )

    override fun onBackPressed(): Boolean = backStack.pop()

    @Composable
    override fun Render(session: SkinSession) {
        SkinTheme(colors = colors, typography = typography, darkSystemBars = true) {
            when (val destination = backStack.current) {
                WheelDestination.Root -> rootScreen(session)
                WheelDestination.MusicMenu -> musicScreen(session)
                WheelDestination.Artists -> artistsScreen(session)
                WheelDestination.Albums -> albumsScreen(session)
                WheelDestination.Songs -> songsScreen(session)
                WheelDestination.Genres -> genresScreen(session)
                WheelDestination.Playlists -> playlistsScreen(session)
                is WheelDestination.ArtistAlbums -> artistAlbumsScreen(session, destination)
                is WheelDestination.AlbumSongs -> albumSongsScreen(session, destination)
                is WheelDestination.GenreSongs -> genreSongsScreen(session, destination)
                is WheelDestination.PlaylistSongs -> playlistSongsScreen(session, destination)
                WheelDestination.NowPlaying -> nowPlayingScreen(session)
            }
        }
    }

    @Composable
    private fun rootScreen(session: SkinSession) {
        val items = RootItem.entries.toList()
        wheelScaffold(
            session = session,
            destination = WheelDestination.Root,
            itemCount = items.size,
            resolveSelected = items::getOrNull,
            itemLabel = { item ->
                when (item) {
                    RootItem.MUSIC -> "Music"
                    RootItem.SHUFFLE_SONGS -> "Shuffle Songs"
                    RootItem.NOW_PLAYING -> "Now Playing"
                }
            },
            onCenter = { item, _ ->
                when (item) {
                    RootItem.MUSIC -> backStack.push(WheelDestination.MusicMenu)
                    RootItem.SHUFFLE_SONGS -> {
                        session.commands.shuffleAll()
                        backStack.push(WheelDestination.NowPlaying)
                    }
                    RootItem.NOW_PLAYING -> backStack.push(WheelDestination.NowPlaying)
                }
            },
        ) { selectedIndex ->
            wheelListBody(
                title = "Touch Wheel",
                items = items,
                selectedIndex = selectedIndex,
                itemLabel = { item ->
                    when (item) {
                        RootItem.MUSIC -> "Music"
                        RootItem.SHUFFLE_SONGS -> "Shuffle Songs"
                        RootItem.NOW_PLAYING -> "Now Playing"
                    }
                },
            )
        }
    }

    @Composable
    private fun musicScreen(session: SkinSession) {
        val items = MusicItem.entries.toList()
        wheelScaffold(
            session = session,
            destination = WheelDestination.MusicMenu,
            itemCount = items.size,
            resolveSelected = items::getOrNull,
            itemLabel = { item -> item.name.lowercase().replaceFirstChar { it.uppercase() } },
            onCenter = { item, _ ->
                backStack.push(
                    when (item) {
                        MusicItem.ARTISTS -> WheelDestination.Artists
                        MusicItem.ALBUMS -> WheelDestination.Albums
                        MusicItem.SONGS -> WheelDestination.Songs
                        MusicItem.GENRES -> WheelDestination.Genres
                        MusicItem.PLAYLISTS -> WheelDestination.Playlists
                    },
                )
            },
        ) { selectedIndex ->
            wheelListBody(
                title = "Music",
                items = items,
                selectedIndex = selectedIndex,
                itemLabel = { item -> item.name.lowercase().replaceFirstChar { it.uppercase() } },
            )
        }
    }

    @Composable
    private fun artistsScreen(session: SkinSession) {
        val artists = remember(session.library) { session.library.artists() }.collectAsLazyPagingItems()
        pagedLibraryScreen(
            session = session,
            destination = WheelDestination.Artists,
            title = "Artists",
            items = artists,
            itemLabel = SkinArtist::name,
            itemKey = SkinArtist::id,
            onCenter = { artist, _ ->
                backStack.push(WheelDestination.ArtistAlbums(artist.id, artist.name))
            },
        )
    }

    @Composable
    private fun albumsScreen(session: SkinSession) {
        val albums = remember(session.library) { session.library.albums() }.collectAsLazyPagingItems()
        pagedLibraryScreen(
            session = session,
            destination = WheelDestination.Albums,
            title = "Albums",
            items = albums,
            itemLabel = { album -> "${album.title} — ${album.artist}" },
            itemKey = SkinAlbum::id,
            onCenter = { album, _ ->
                backStack.push(WheelDestination.AlbumSongs(album.id, album.title))
            },
        )
    }

    @Composable
    private fun songsScreen(session: SkinSession) {
        val songs = remember(session.library) { session.library.songs() }.collectAsLazyPagingItems()
        pagedLibraryScreen(
            session = session,
            destination = WheelDestination.Songs,
            title = "Songs",
            items = songs,
            itemLabel = { song -> "${song.title} — ${song.artist}" },
            itemKey = SkinSong::id,
            onCenter = { song, _ ->
                session.commands.playSongFromAll(song.id)
                backStack.push(WheelDestination.NowPlaying)
            },
        )
    }

    @Composable
    private fun genresScreen(session: SkinSession) {
        val genres = remember(session.library) { session.library.genres() }.collectAsLazyPagingItems()
        pagedLibraryScreen(
            session = session,
            destination = WheelDestination.Genres,
            title = "Genres",
            items = genres,
            itemLabel = SkinGenre::name,
            itemKey = SkinGenre::id,
            onCenter = { genre, _ ->
                backStack.push(WheelDestination.GenreSongs(genre.id, genre.name))
            },
        )
    }

    @Composable
    private fun playlistsScreen(session: SkinSession) {
        val playlists = remember(session.library) { session.library.playlists() }.collectAsLazyPagingItems()
        pagedLibraryScreen(
            session = session,
            destination = WheelDestination.Playlists,
            title = "Playlists",
            items = playlists,
            itemLabel = { playlist -> playlist.name },
            itemKey = SkinPlaylist::id,
            onCenter = { playlist, _ ->
                backStack.push(WheelDestination.PlaylistSongs(playlist.id, playlist.name))
            },
        )
    }

    @Composable
    private fun artistAlbumsScreen(
        session: SkinSession,
        destination: WheelDestination.ArtistAlbums,
    ) {
        val albums =
            remember(session.library, destination.artistId) {
                session.library.albumsByArtist(destination.artistId)
            }.collectAsLazyPagingItems()
        pagedLibraryScreen(
            session = session,
            destination = destination,
            title = destination.title,
            items = albums,
            itemLabel = { album -> "${album.title} — ${album.artist}" },
            itemKey = SkinAlbum::id,
            onCenter = { album, _ ->
                backStack.push(WheelDestination.AlbumSongs(album.id, album.title))
            },
        )
    }

    @Composable
    private fun albumSongsScreen(
        session: SkinSession,
        destination: WheelDestination.AlbumSongs,
    ) {
        val songs by
            remember(session.library, destination.albumId) {
                session.library.songsByAlbum(destination.albumId)
            }.collectAsState(initial = emptyList())
        listLibraryScreen(
            session = session,
            destination = destination,
            title = destination.title,
            items = songs,
            itemLabel = { song -> "${song.title} — ${song.artist}" },
            itemKey = SkinSong::id,
            onCenter = { song, _ ->
                session.commands.playSongFromAlbum(song.id, destination.albumId)
                backStack.push(WheelDestination.NowPlaying)
            },
        )
    }

    @Composable
    private fun genreSongsScreen(
        session: SkinSession,
        destination: WheelDestination.GenreSongs,
    ) {
        val songs =
            remember(session.library, destination.genreId) {
                session.library.songsByGenre(destination.genreId)
            }.collectAsLazyPagingItems()
        pagedLibraryScreen(
            session = session,
            destination = destination,
            title = destination.title,
            items = songs,
            itemLabel = { song -> "${song.title} — ${song.artist}" },
            itemKey = SkinSong::id,
            onCenter = { song, _ ->
                session.commands.playSongFromGenre(song.id, destination.genreId)
                backStack.push(WheelDestination.NowPlaying)
            },
        )
    }

    @Composable
    private fun playlistSongsScreen(
        session: SkinSession,
        destination: WheelDestination.PlaylistSongs,
    ) {
        val songs by
            remember(session.library, destination.playlistId) {
                session.library.songsByPlaylist(destination.playlistId)
            }.collectAsState(initial = emptyList())
        listLibraryScreen(
            session = session,
            destination = destination,
            title = destination.title,
            items = songs,
            itemLabel = { song -> "${song.title} — ${song.artist}" },
            itemKey = SkinSong::id,
            onCenter = { song, _ ->
                session.commands.playSongFromPlaylist(song.id, destination.playlistId)
                backStack.push(WheelDestination.NowPlaying)
            },
        )
    }

    @Composable
    private fun nowPlayingScreen(session: SkinSession) {
        val playback by session.playback.collectAsState()
        val song = playback.song
        val queueSize = playback.queue.size
        val queuePosition =
            if (playback.queueIndex in playback.queue.indices) playback.queueIndex + 1
            else if (song == null) 0 else 1

        wheelScaffold<SkinSong>(
            session = session,
            destination = WheelDestination.NowPlaying,
            itemCount = 0,
            resolveSelected = { null },
            itemLabel = { it.title },
            onCenter = { _, _ -> },
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(colors.background).padding(20.dp),
            ) {
                BasicText("Now Playing", style = typography.title.copy(color = colors.onBackground))
                if (song == null) {
                    BasicText(
                        "No song loaded",
                        modifier = Modifier.padding(top = 20.dp),
                        style = typography.body.copy(color = colors.onBackground),
                    )
                } else {
                    BasicText(
                        song.title,
                        modifier = Modifier.padding(top = 20.dp),
                        style = typography.title.copy(color = colors.onBackground),
                    )
                    BasicText(
                        song.artist,
                        modifier = Modifier.padding(top = 8.dp),
                        style = typography.body.copy(color = colors.onSurface),
                    )
                    BasicText(
                        song.album,
                        modifier = Modifier.padding(top = 4.dp),
                        style = typography.body.copy(color = colors.onSurface),
                    )
                    BasicText(
                        "$queuePosition of $queueSize",
                        modifier = Modifier.padding(top = 20.dp),
                        style = typography.label.copy(color = colors.onSurface),
                    )
                }
            }
        }
    }

    @Composable
    private fun <T : Any> pagedLibraryScreen(
        session: SkinSession,
        destination: WheelDestination,
        title: String,
        items: LazyPagingItems<T>,
        itemLabel: (T) -> String,
        itemKey: (T) -> Any,
        onCenter: (T, Int) -> Unit,
    ) {
        val loadState by session.library.loadState.collectAsState()
        SkinLibraryGate(
            loadState = loadState,
            scanning = { loaded, explored ->
                statusScreen(session, destination, title, scanningMessage(loaded, explored))
            },
            failed = { message -> statusScreen(session, destination, title, "Load failed: $message") },
            empty = { statusScreen(session, destination, title, "Library is empty") },
            ready = {
                wheelScaffold(
                    session = session,
                    destination = destination,
                    itemCount = items.itemCount,
                    resolveSelected = items::peek,
                    itemLabel = itemLabel,
                    onCenter = onCenter,
                ) { selectedIndex ->
                    wheelListBody(
                        title = title,
                        items = items,
                        selectedIndex = selectedIndex,
                        itemLabel = itemLabel,
                        itemKey = itemKey,
                    )
                }
            },
        )
    }

    @Composable
    private fun <T : Any> listLibraryScreen(
        session: SkinSession,
        destination: WheelDestination,
        title: String,
        items: List<T>,
        itemLabel: (T) -> String,
        itemKey: (T) -> Any,
        onCenter: (T, Int) -> Unit,
    ) {
        val loadState by session.library.loadState.collectAsState()
        SkinLibraryGate(
            loadState = loadState,
            scanning = { loaded, explored ->
                statusScreen(session, destination, title, scanningMessage(loaded, explored))
            },
            failed = { message -> statusScreen(session, destination, title, "Load failed: $message") },
            empty = { statusScreen(session, destination, title, "Library is empty") },
            ready = {
                wheelScaffold(
                    session = session,
                    destination = destination,
                    itemCount = items.size,
                    resolveSelected = items::getOrNull,
                    itemLabel = itemLabel,
                    onCenter = onCenter,
                ) { selectedIndex ->
                    wheelListBody(
                        title = title,
                        items = items,
                        selectedIndex = selectedIndex,
                        itemLabel = itemLabel,
                        itemKey = itemKey,
                    )
                }
            },
        )
    }

    @Composable
    private fun statusScreen(
        session: SkinSession,
        destination: WheelDestination,
        title: String,
        message: String,
    ) {
        wheelScaffold<SkinSong>(
            session = session,
            destination = destination,
            itemCount = 0,
            resolveSelected = { null },
            itemLabel = { it.title },
            onCenter = { _, _ -> },
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(colors.background).padding(20.dp),
            ) {
                BasicText(title, style = typography.title.copy(color = colors.onBackground))
                BasicText(
                    message,
                    modifier = Modifier.padding(top = 20.dp),
                    style = typography.body.copy(color = colors.onSurface),
                )
            }
        }
    }

    @Composable
    private fun <T : Any> wheelScaffold(
        session: SkinSession,
        destination: WheelDestination,
        itemCount: Int,
        resolveSelected: (Int) -> T?,
        itemLabel: (T) -> String,
        onCenter: (T, Int) -> Unit,
        body: @Composable (selectedIndex: Int) -> Unit,
    ) {
        val wheelState =
            remember(destination) {
                WheelMenuState(selectionIndices[destination] ?: 0)
            }
        LaunchedEffect(itemCount) {
            wheelState.onRotate(delta = 0, itemCount = itemCount)
            selectionIndices[destination] = wheelState.selectedIndex
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.background)) {
            val wheelAreaHeight = maxHeight * 0.45f
            val wheelDiameter = wheelDiameterFor(maxWidth, wheelAreaHeight)
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    body(wheelState.selectedIndex)
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(wheelAreaHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = Modifier.size(wheelDiameter)) {
                        TouchWheel(
                            modifier = Modifier.fillMaxSize(),
                            onZonePressed = { zone ->
                                when (zone) {
                                    WheelZone.MENU -> backStack.pop()
                                    WheelZone.NEXT -> session.commands.next()
                                    WheelZone.PREVIOUS -> session.commands.previous()
                                    WheelZone.PLAY_PAUSE -> session.commands.playPause()
                                    WheelZone.CENTER -> {
                                        if (itemCount > 0) {
                                            val index = wheelState.selectedIndex.coerceIn(0, itemCount - 1)
                                            resolveSelected(index)?.let { onCenter(it, index) }
                                        }
                                    }
                                }
                            },
                            onRotate = { delta ->
                                wheelState.onRotate(delta, itemCount)
                                selectionIndices[destination] = wheelState.selectedIndex
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun <T : Any> wheelListBody(
        title: String,
        items: List<T>,
        selectedIndex: Int,
        itemLabel: (T) -> String,
        itemKey: ((T) -> Any)? = null,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(colors.background).padding(top = 16.dp),
        ) {
            BasicText(
                title,
                modifier = Modifier.padding(horizontal = 20.dp),
                style = typography.title.copy(color = colors.onBackground),
            )
            if (items.isEmpty()) {
                BasicText(
                    "No items",
                    modifier = Modifier.padding(20.dp),
                    style = typography.body.copy(color = colors.onSurface),
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
                    WheelMenuList(
                        items = items,
                        selectedIndex = selectedIndex,
                        key = itemKey,
                    ) { item, isSelected ->
                        BasicText(
                            itemLabel(item),
                            modifier =
                                Modifier.fillMaxWidth()
                                    .background(if (isSelected) colors.accent else colors.surface)
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                            style =
                                typography.body.copy(
                                    color = if (isSelected) colors.onAccent else colors.onSurface,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                ),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun <T : Any> wheelListBody(
        title: String,
        items: LazyPagingItems<T>,
        selectedIndex: Int,
        itemLabel: (T) -> String,
        itemKey: ((T) -> Any)? = null,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(colors.background).padding(top = 16.dp),
        ) {
            BasicText(
                title,
                modifier = Modifier.padding(horizontal = 20.dp),
                style = typography.title.copy(color = colors.onBackground),
            )
            if (items.itemCount == 0) {
                BasicText(
                    "No items",
                    modifier = Modifier.padding(20.dp),
                    style = typography.body.copy(color = colors.onSurface),
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
                    WheelMenuList(
                        items = items,
                        selectedIndex = selectedIndex,
                        key = itemKey,
                    ) { item, isSelected ->
                        BasicText(
                            item?.let(itemLabel) ?: "Loading…",
                            modifier =
                                Modifier.fillMaxWidth()
                                    .background(if (isSelected) colors.accent else colors.surface)
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                            style =
                                typography.body.copy(
                                    color = if (isSelected) colors.onAccent else colors.onSurface,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                ),
                        )
                    }
                }
            }
        }
    }

    private fun scanningMessage(loaded: Int?, explored: Int?): String =
        if (loaded == null || explored == null) {
            "Scanning…"
        } else {
            "Scanning… $loaded of $explored"
        }
}
