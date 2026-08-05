package org.shellac.skin.wheel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
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
    QUEUE,
    SHUFFLE_SONGS,
    NOW_PLAYING,
    SETTINGS,
}

private enum class MusicItem {
    SEARCH,
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

    // Which way the last navigation went, so the screen slides in from the correct edge.
    // A plain field, not remembered state: onBackPressed is not composable and has to be
    // able to set it too (see AppSkin rule 2).
    private var navigatingForward = true

    // Lets the on-screen destination intercept MENU. Registered by whichever screen needs it
    // and cleared on dispose. A field, not composition state, because onBackPressed is not
    // composable and must consult the same hook - otherwise the wheel's MENU zone and the
    // system back gesture would disagree.
    private var menuOverride: (() -> Boolean)? = null

    // Search query lives here rather than in the search screen, so leaving the screen and
    // coming back does not discard what was typed.
    private var searchQuery by mutableStateOf("")
    private val selectionIndices = mutableStateMapOf<WheelDestination, Int>()

    // White screen, black text, blue gradient selection - see WheelChrome.kt.
    private val colors = ClassicColors
    private val typography = ClassicTypography

    override fun onBackPressed(): Boolean = handleMenu()

    /** MENU and system back are the same action, including any screen-level override. */
    private fun handleMenu(): Boolean {
        if (menuOverride?.invoke() == true) return true
        return navigateBack()
    }

    private fun navigateTo(destination: WheelDestination) {
        navigatingForward = true
        backStack.push(destination)
    }

    private fun navigateBack(): Boolean {
        navigatingForward = false
        return backStack.pop()
    }

    @Composable
    override fun Render(session: SkinSession) {
        SkinTheme(colors = colors, typography = typography, darkSystemBars = false) {
            when (val destination = backStack.current) {
                WheelDestination.Root -> rootScreen(session)
                WheelDestination.MusicMenu -> musicScreen(session)
                WheelDestination.Queue -> queueScreen(session)
                is WheelDestination.QueueActions -> queueActionsScreen(session, destination)
                WheelDestination.Search -> searchScreen(session)
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
                    RootItem.QUEUE -> "Queue"
                    RootItem.SHUFFLE_SONGS -> "Shuffle Songs"
                    RootItem.NOW_PLAYING -> "Now Playing"
                    RootItem.SETTINGS -> "Settings"
                }
            },
            onCenter = { item, _ ->
                when (item) {
                    RootItem.MUSIC -> navigateTo(WheelDestination.MusicMenu)
                    RootItem.QUEUE -> navigateTo(WheelDestination.Queue)
                    RootItem.SHUFFLE_SONGS -> {
                        session.commands.shuffleAll()
                        navigateTo(WheelDestination.NowPlaying)
                    }
                    RootItem.NOW_PLAYING -> navigateTo(WheelDestination.NowPlaying)
                    // Skin switching is deliberately not a skin's job - the host owns it, so
                    // it stays reachable even when a skin is broken. This just opens it.
                    RootItem.SETTINGS -> session.openAppSettings()
                }
            },
        ) { selectedIndex ->
            wheelListBody(
                session = session,
                title = "Touch Wheel",
                items = items,
                selectedIndex = selectedIndex,
                itemLabel = { item ->
                    when (item) {
                        RootItem.MUSIC -> "Music"
                        RootItem.QUEUE -> "Queue"
                        RootItem.SHUFFLE_SONGS -> "Shuffle Songs"
                        RootItem.NOW_PLAYING -> "Now Playing"
                        RootItem.SETTINGS -> "Settings"
                    }
                },
                // Shuffle acts immediately; the rest open a screen.
                showChevron = { it != RootItem.SHUFFLE_SONGS },
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
                navigateTo(
                    when (item) {
                        MusicItem.SEARCH -> WheelDestination.Search
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
                session = session,
                title = "Music",
                items = items,
                selectedIndex = selectedIndex,
                itemLabel = { item -> item.name.lowercase().replaceFirstChar { it.uppercase() } },
                showChevron = { true },
            )
        }
    }

    @Composable
    private fun artistsScreen(session: SkinSession) {
        val artists = remember(session.library) { session.library.artists() }.collectAsLazyPagingItems()
        pagedLibraryScreen(
            session = session,
            destination = WheelDestination.Artists,
            showChevron = true,
            title = "Artists",
            items = artists,
            itemLabel = SkinArtist::name,
            itemKey = SkinArtist::id,
            onCenter = { artist, _ ->
                navigateTo(WheelDestination.ArtistAlbums(artist.id, artist.name))
            },
        )
    }

    @Composable
    private fun albumsScreen(session: SkinSession) {
        val albums = remember(session.library) { session.library.albums() }.collectAsLazyPagingItems()
        pagedLibraryScreen(
            session = session,
            destination = WheelDestination.Albums,
            showChevron = true,
            title = "Albums",
            items = albums,
            itemLabel = { album -> "${album.title} — ${album.artist}" },
            itemKey = SkinAlbum::id,
            onCenter = { album, _ ->
                navigateTo(WheelDestination.AlbumSongs(album.id, album.title))
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
                navigateTo(WheelDestination.NowPlaying)
            },
        )
    }

    @Composable
    private fun genresScreen(session: SkinSession) {
        val genres = remember(session.library) { session.library.genres() }.collectAsLazyPagingItems()
        pagedLibraryScreen(
            session = session,
            destination = WheelDestination.Genres,
            showChevron = true,
            title = "Genres",
            items = genres,
            itemLabel = SkinGenre::name,
            itemKey = SkinGenre::id,
            onCenter = { genre, _ ->
                navigateTo(WheelDestination.GenreSongs(genre.id, genre.name))
            },
        )
    }

    @Composable
    private fun playlistsScreen(session: SkinSession) {
        val playlists = remember(session.library) { session.library.playlists() }.collectAsLazyPagingItems()
        pagedLibraryScreen(
            session = session,
            destination = WheelDestination.Playlists,
            showChevron = true,
            title = "Playlists",
            items = playlists,
            itemLabel = { playlist -> playlist.name },
            itemKey = SkinPlaylist::id,
            onCenter = { playlist, _ ->
                navigateTo(WheelDestination.PlaylistSongs(playlist.id, playlist.name))
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
                navigateTo(WheelDestination.AlbumSongs(album.id, album.title))
            },
            showChevron = true,
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
                navigateTo(WheelDestination.NowPlaying)
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
                navigateTo(WheelDestination.NowPlaying)
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
                navigateTo(WheelDestination.NowPlaying)
            },
        )
    }

    @Composable
    private fun nowPlayingScreen(session: SkinSession) {
        val nowPlayingState = rememberWheelNowPlayingState()
        wheelScaffold<SkinSong>(
            session = session,
            destination = WheelDestination.NowPlaying,
            itemCount = 0,
            resolveSelected = { null },
            itemLabel = { it.title },
            onCenter = { _, _ -> },
            onRotate = nowPlayingState::onRotate,
        ) {
            WheelNowPlaying(session = session, state = nowPlayingState)
        }
    }

    @Composable
    private fun queueScreen(session: SkinSession) {
        WheelQueue(
            session = session,
            onCenter = { song, index ->
                navigateTo(
                    WheelDestination.QueueActions(
                        queueIndex = index,
                        songId = song.id,
                        title = song.title,
                    ),
                )
            },
            scaffold = { itemCount, initialSelection, resolveSelected, onCenter, body ->
                wheelScaffold(
                    session = session,
                    destination = WheelDestination.Queue,
                    itemCount = itemCount,
                    initialSelection = initialSelection,
                    resolveSelected = resolveSelected,
                    itemLabel = { song -> "${song.title} — ${song.artist}" },
                    onCenter = onCenter,
                    body = body,
                )
            },
        )
    }

    @Composable
    private fun queueActionsScreen(
        session: SkinSession,
        destination: WheelDestination.QueueActions,
    ) {
        val playback by session.playback.collectAsState()
        WheelQueueActions(
            destination = destination,
            onAction = { action, actionIndex ->
                when (action) {
                    QueueAction.PLAY -> {
                        session.commands.playQueueItem(index = destination.queueIndex)
                        backStack.pop()
                    }
                    QueueAction.MOVE_UP -> {
                        if (destination.queueIndex > 0 &&
                            destination.queueIndex <= playback.queue.lastIndex
                        ) {
                            val nextDestination =
                                destination.copy(queueIndex = destination.queueIndex - 1)
                            session.commands.moveInQueue(
                                from = destination.queueIndex,
                                to = nextDestination.queueIndex,
                            )
                            selectionIndices[WheelDestination.Queue] = nextDestination.queueIndex
                            selectionIndices[nextDestination] = actionIndex
                            backStack.pop()
                            navigateTo(nextDestination)
                        }
                    }
                    QueueAction.MOVE_DOWN -> {
                        if (destination.queueIndex in 0 until playback.queue.lastIndex) {
                            val nextDestination =
                                destination.copy(queueIndex = destination.queueIndex + 1)
                            session.commands.moveInQueue(
                                from = destination.queueIndex,
                                to = nextDestination.queueIndex,
                            )
                            selectionIndices[WheelDestination.Queue] = nextDestination.queueIndex
                            selectionIndices[nextDestination] = actionIndex
                            backStack.pop()
                            navigateTo(nextDestination)
                        }
                    }
                    QueueAction.REMOVE -> {
                        session.commands.removeFromQueue(index = destination.queueIndex)
                        val newQueueSize = (playback.queue.size - 1).coerceAtLeast(0)
                        selectionIndices[WheelDestination.Queue] =
                            if (newQueueSize == 0) 0
                            else destination.queueIndex.coerceIn(0, newQueueSize - 1)
                        backStack.pop()
                    }
                    QueueAction.CANCEL -> backStack.pop()
                }
            },
            scaffold = { itemCount, initialSelection, resolveSelected, onCenter, body ->
                wheelScaffold(
                    session = session,
                    destination = destination,
                    itemCount = itemCount,
                    initialSelection = initialSelection,
                    resolveSelected = resolveSelected,
                    itemLabel = QueueAction::label,
                    onCenter = onCenter,
                    body = body,
                )
            },
        )
    }

    @Composable
    private fun searchScreen(session: SkinSession) {
        WheelSearch(
            session = session,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            registerMenuOverride = { menuOverride = it },
            onSong = { song ->
                session.commands.playSongFromAll(songId = song.id)
                navigateTo(WheelDestination.NowPlaying)
            },
            onAlbum = { album ->
                navigateTo(WheelDestination.AlbumSongs(albumId = album.id, title = album.title))
            },
            onArtist = { artist ->
                navigateTo(WheelDestination.ArtistAlbums(artistId = artist.id, title = artist.name))
            },
            scaffold = { itemCount, resolveSelected, onCenter, body ->
                wheelScaffold(
                    session = session,
                    destination = WheelDestination.Search,
                    itemCount = itemCount,
                    resolveSelected = resolveSelected,
                    itemLabel = WheelSearchItem::label,
                    onCenter = onCenter,
                    body = body,
                )
            },
        )
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
        showChevron: Boolean = false,
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
                        session = session,
                        title = title,
                        items = items,
                        selectedIndex = selectedIndex,
                        itemLabel = itemLabel,
                        itemKey = itemKey,
                        showChevron = { showChevron },
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
        showChevron: Boolean = false,
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
                        session = session,
                        title = title,
                        items = items,
                        selectedIndex = selectedIndex,
                        itemLabel = itemLabel,
                        itemKey = itemKey,
                        showChevron = { showChevron },
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
        initialSelection: Int? = null,
        resolveSelected: (Int) -> T?,
        itemLabel: (T) -> String,
        onCenter: (T, Int) -> Unit,
        onRotate: ((Int) -> Unit)? = null,
        body: @Composable (selectedIndex: Int) -> Unit,
    ) {
        val wheelState =
            remember(destination, initialSelection) {
                WheelMenuState(selectionIndices[destination] ?: initialSelection ?: 0)
            }
        // Re-clamp the restored index once the collection is known, in case it shrank while
        // we were away. Only when something is actually loaded: clampIndex returns 0 for an
        // empty list, and a paged screen always begins at itemCount == 0 before its first
        // page arrives (as does the scanning/failed status screen, which passes 0). Running
        // this then would write 0 straight over the index we just restored - which is
        // exactly what made descending into an album and pressing MENU return to the top of
        // the list instead of to the album you came from.
        LaunchedEffect(itemCount) {
            if (itemCount <= 0) return@LaunchedEffect
            wheelState.onRotate(delta = 0, itemCount = itemCount)
            selectionIndices[destination] = wheelState.selectedIndex
        }

        val view = LocalView.current
        // Proportions taken from the hardware rather than from the phone. On that device the
        // screen is 4:3 and the wheel is roughly 70% of the body width, which together take
        // about three quarters of the face. A modern phone is far taller relative to its
        // width (~2.2:1 against ~1.7:1), so filling the height would stretch both out of
        // shape - and letting the wheel span the full width is what made it oversized.
        // Instead the face is sized to whichever of width or height binds first, then centred,
        // with the body colour filling the space left over.
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(BodySilver)) {
            val margin = 12.dp
            val faceWidth =
                minOf(maxWidth - margin * 2, (maxHeight - margin * 2) / FACE_HEIGHT_RATIO)
            val screenHeight = faceWidth * SCREEN_ASPECT
            val gap = faceWidth * FACE_GAP_RATIO
            val wheelDiameter = faceWidth * WHEEL_WIDTH_RATIO

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Only the screen slides; the wheel is a physical control and must not
                // move with it. Just an enter animation - the outgoing screen has already
                // left the composition by the time the new one runs, so there is nothing
                // left to animate out, and a slide-in alone reads correctly.
                val slide =
                    remember(destination) { Animatable(if (navigatingForward) 1f else -1f) }
                LaunchedEffect(destination) {
                    slide.animateTo(0f, animationSpec = tween(durationMillis = 170))
                }
                Box(
                    modifier = Modifier
                        .width(faceWidth)
                        .height(screenHeight)
                        .border(1.dp, BodyBezel)
                        .background(colors.background)
                        .clipToBounds(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = slide.value * size.width },
                    ) {
                        body(wheelState.selectedIndex)
                    }
                }
                Spacer(modifier = Modifier.height(gap))
                Box(
                    modifier = Modifier.size(wheelDiameter),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TouchWheel(
                            modifier = Modifier.fillMaxSize(),
                            onZonePressed = { zone ->
                                view.performWheelPress()
                                when (zone) {
                                    WheelZone.MENU -> handleMenu()
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
                                view.performWheelTick()
                                if (onRotate != null) {
                                    onRotate(delta)
                                } else {
                                    wheelState.onRotate(delta, itemCount)
                                    selectionIndices[destination] = wheelState.selectedIndex
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun <T : Any> wheelListBody(
        session: SkinSession,
        title: String,
        items: List<T>,
        selectedIndex: Int,
        itemLabel: (T) -> String,
        itemKey: ((T) -> Any)? = null,
        showChevron: (T) -> Boolean = { false },
    ) {
        val playback by session.playback.collectAsState()
        Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
            ClassicTitleBar(
                title = title,
                isPlaying = playback.isPlaying,
                hasSong = playback.song != null,
            )
            ClassicSplitLayout(
                modifier = Modifier.weight(1f),
                panel = { ClassicArtPanel(playback.song) },
                menu = {
                    if (items.isEmpty()) {
                        ClassicEmptyLabel("No items")
                    } else {
                        WheelMenuList(
                            items = items,
                            selectedIndex = selectedIndex,
                            modifier = Modifier.fillMaxSize(),
                            key = itemKey,
                        ) { item, isSelected ->
                            ClassicMenuRow(
                                label = itemLabel(item),
                                isSelected = isSelected,
                                showChevron = showChevron(item),
                            )
                        }
                    }
                },
            )
        }
    }

    @Composable
    private fun <T : Any> wheelListBody(
        session: SkinSession,
        title: String,
        items: LazyPagingItems<T>,
        selectedIndex: Int,
        itemLabel: (T) -> String,
        itemKey: ((T) -> Any)? = null,
        showChevron: (T) -> Boolean = { false },
    ) {
        val playback by session.playback.collectAsState()
        Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
            ClassicTitleBar(
                title = title,
                isPlaying = playback.isPlaying,
                hasSong = playback.song != null,
            )
            ClassicSplitLayout(
                modifier = Modifier.weight(1f),
                panel = { ClassicArtPanel(playback.song) },
                menu = {
                    if (items.itemCount == 0) {
                        ClassicEmptyLabel("No items")
                    } else {
                        WheelMenuList(
                            items = items,
                            selectedIndex = selectedIndex,
                            modifier = Modifier.fillMaxSize(),
                            key = itemKey,
                        ) { item, isSelected ->
                            // A row whose page has not arrived yet still needs full height,
                            // or the fixed-centre offset jumps when it resolves.
                            ClassicMenuRow(
                                label = item?.let(itemLabel) ?: "Loading…",
                                isSelected = isSelected,
                                showChevron = item != null && showChevron(item),
                            )
                        }
                    }
                },
            )
        }
    }

    private fun scanningMessage(loaded: Int?, explored: Int?): String =
        if (loaded == null || explored == null) {
            "Scanning…"
        } else {
            "Scanning… $loaded of $explored"
        }
}
