package org.shellac.skin.zune

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.shellac.skin.SkinAlbum
import org.shellac.skin.SkinSearchResults
import org.shellac.skin.SkinSession
import org.shellac.skin.SkinSong
import org.shellac.skin.support.SkinAlbumArt
import org.shellac.skin.support.SkinLibraryGate
import org.shellac.skin.support.SkinNoResults
import org.shellac.skin.support.SkinTheme
import org.shellac.skin.support.rememberInterpolatedPosition

private enum class Pivot(val title: String) {
    ARTISTS("artists"),
    ALBUMS("albums"),
    SONGS("songs"),
    GENRES("genres"),
    PLAYLISTS("playlists"),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ZuneSkin.libraryScreen(session: SkinSession) {
    val loadState by session.library.loadState.collectAsState()
    SkinLibraryGate(
        loadState = loadState,
        scanning = { loaded, explored ->
            BlackFrame(
                title = "music",
                utility = { AccentLink(text = "settings", onClick = session.openAppSettings) },
                content = {
                    SectionLabel("indexing")
                    BasicText(
                        text = if (loaded == null || explored == null) "scanning" else "$loaded / $explored",
                        style = SkinTheme.typography.body,
                    )
                },
            )
        },
        failed = { message ->
            BlackFrame(
                title = "music",
                utility = { AccentLink(text = "settings", onClick = session.openAppSettings) },
                content = {
                    SectionLabel("library error")
                    BasicText(text = message, style = SkinTheme.typography.body)
                },
            )
        },
        empty = {
            BlackFrame(title = "music", content = {
                EmptyMessage("nothing here yet")
                AccentLink(text = "settings", onClick = session.openAppSettings)
            })
        },
        ready = {
            val pagerState = rememberPagerState(pageCount = { Pivot.entries.size })
            val pagerScope = rememberCoroutineScope()
            BlackFrame(
                title = "music",
                utility = {
                    AccentLink(text = "search", onClick = { navigateToSearch() })
                    AccentLink(text = "queue", onClick = { navigateToQueue() })
                    AccentLink(
                        text = "now playing",
                        onClick = { navigateToNowPlaying() },
                    )
                    AccentLink(text = "settings", onClick = session.openAppSettings)
                },
                content = {
                PivotHeadings(
                    pagerState = pagerState,
                    onSelect = { page ->
                        pagerScope.launch { pagerState.animateScrollToPage(page) }
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageContent = { page ->
                        PivotPage(session = session, pivot = Pivot.entries[page])
                    },
                )
                },
            )
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PivotHeadings(
    pagerState: androidx.compose.foundation.pager.PagerState,
    onSelect: (Int) -> Unit,
) {
    // Horizontally scrollable: the inactive headings are meant to run off the edge, but they
    // still have to be reachable, and a plain Row would compress them to fit instead.
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.Bottom,
    ) {
        Pivot.entries.forEachIndexed { index, pivot ->
            val active = pagerState.currentPage == index
            BasicText(
                text = pivot.title,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .padding(end = if (active) 18.dp else 14.dp)
                    .clickable(onClick = { onSelect(index) }),
                style = if (active) {
                    SkinTheme.typography.title.copy(fontSize = 29.sp, lineHeight = 30.sp)
                } else {
                    SkinTheme.typography.label.copy(fontSize = 17.sp, color = Color(0xFF555555))
                },
            )
        }
    }
}

@Composable
private fun ZuneSkin.PivotPage(session: SkinSession, pivot: Pivot) {
    when (pivot) {
        Pivot.ARTISTS -> artistsPivot(session)
        Pivot.ALBUMS -> albumsPivot(session)
        Pivot.SONGS -> songsPivot(session)
        Pivot.GENRES -> genresPivot(session)
        Pivot.PLAYLISTS -> playlistsPivot(session)
    }
}

@Composable
private fun ZuneSkin.artistsPivot(session: SkinSession) {
    val artists = remember(session.library, calculation = { session.library.artists() }).collectAsLazyPagingItems()
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        content = {
            items(
                count = artists.itemCount,
                key = { index -> artists.peek(index)?.id ?: index },
                itemContent = { index ->
                    artists[index]?.let { artist ->
                        LibraryRow(
                            title = artist.name,
                            subtitle = "${plural(artist.albumCount, "album")}  ·  ${plural(artist.songCount, "song")}",
                            onClick = { open(SkinDestination.ArtistAlbums(artist.id, artist.name)) },
                        )
                    }
                },
            )
        },
    )
}

@Composable
private fun ZuneSkin.albumsPivot(session: SkinSession) {
    val albums = remember(session.library, calculation = { session.library.albums() }).collectAsLazyPagingItems()
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        content = {
            items(
                count = albums.itemCount,
                key = { index -> albums.peek(index)?.id ?: index },
                itemContent = { index ->
                    albums[index]?.let { album ->
                        AlbumRow(
                            album = album,
                            onClick = { open(SkinDestination.AlbumSongs(album.id, album.title)) },
                        )
                    }
                },
            )
        },
    )
}

@Composable
private fun ZuneSkin.songsPivot(session: SkinSession) {
    val songs = remember(session.library, calculation = { session.library.songs() }).collectAsLazyPagingItems()
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        content = {
            items(
                count = songs.itemCount,
                key = { index -> songs.peek(index)?.id ?: index },
                itemContent = { index ->
                    songs[index]?.let { song ->
                        SongRow(
                            song = song,
                            index = index,
                            onClick = {
                                session.commands.playSongFromAll(song.id)
                                open(SkinDestination.NowPlaying)
                            },
                        )
                    }
                },
            )
        },
    )
}

@Composable
private fun ZuneSkin.genresPivot(session: SkinSession) {
    val genres = remember(session.library, calculation = { session.library.genres() }).collectAsLazyPagingItems()
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        content = {
            items(
                count = genres.itemCount,
                key = { index -> genres.peek(index)?.id ?: index },
                itemContent = { index ->
                    genres[index]?.let { genre ->
                        LibraryRow(
                            title = genre.name,
                            subtitle = plural(genre.songCount, "song"),
                            onClick = { open(SkinDestination.GenreSongs(genre.id, genre.name)) },
                        )
                    }
                },
            )
        },
    )
}

@Composable
private fun ZuneSkin.playlistsPivot(session: SkinSession) {
    val playlists = remember(session.library, calculation = { session.library.playlists() }).collectAsLazyPagingItems()
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        content = {
            items(
                count = playlists.itemCount,
                key = { index -> playlists.peek(index)?.id ?: index },
                itemContent = { index ->
                    playlists[index]?.let { playlist ->
                        LibraryRow(
                            title = playlist.name,
                            subtitle = "${playlist.songCount} songs  ·  ${formatDuration(playlist.durationMs)}",
                            onClick = {
                                open(SkinDestination.PlaylistSongs(playlist.id, playlist.name))
                            },
                        )
                    }
                },
            )
        },
    )
}

@Composable
private fun AlbumRow(album: SkinAlbum, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkinAlbumArt(
            uri = album.albumArtUri,
            contentDescription = album.title,
            modifier = Modifier.size(52.dp),
            contentScale = ContentScale.Crop,
            fallback = {
                Box(
                    modifier = Modifier.size(52.dp).background(SkinTheme.colors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = album.title.take(1).lowercase(),
                        style = SkinTheme.typography.body.copy(color = SkinTheme.colors.accent),
                    )
                }
            },
        )
        Column(modifier = Modifier.padding(start = 14.dp)) {
            BasicText(text = album.title, style = SkinTheme.typography.body)
            BasicText(
                text = "${album.artist}  ·  ${plural(album.songCount, "song")}",
                style = SkinTheme.typography.label,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
internal fun ZuneSkin.artistAlbumsScreen(
    session: SkinSession,
    destination: SkinDestination.ArtistAlbums,
) {
    val albums = remember(
        session.library,
        destination.artistId,
        calculation = { session.library.albumsByArtist(destination.artistId) },
    ).collectAsLazyPagingItems()
    LibraryBackFrame(
        title = destination.artistName,
        onSettings = session.openAppSettings,
        onBack = { navigateBack() },
        content = {
        SkinLibraryGate(
            loadState = session.library.loadState.collectAsState().value,
            scanning = { _, _ -> EmptyMessage("scanning") },
            failed = { message -> EmptyMessage(message) },
            empty = { EmptyMessage("no albums") },
            ready = {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    content = {
                        items(
                            count = albums.itemCount,
                            key = { index -> albums.peek(index)?.id ?: index },
                            itemContent = { index ->
                                albums[index]?.let { album ->
                                    AlbumRow(
                                        album = album,
                                        onClick = {
                                            open(SkinDestination.AlbumSongs(album.id, album.title))
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            },
        )
        },
    )
}

@Composable
internal fun ZuneSkin.albumSongsScreen(
    session: SkinSession,
    destination: SkinDestination.AlbumSongs,
) {
    val songsFlow = remember(
        session.library,
        destination.albumId,
        calculation = { session.library.songsByAlbum(destination.albumId) },
    )
    val songs by songsFlow.collectAsState(initial = emptyList())
    LibraryBackFrame(
        title = destination.albumTitle,
        onSettings = session.openAppSettings,
        onBack = { navigateBack() },
        content = {
        libraryContentGate(session = session, content = {
            if (songs.isEmpty()) {
                EmptyMessage("no songs")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    content = {
                        items(
                            items = songs,
                            key = { song -> song.id },
                            itemContent = { song ->
                                SongRow(
                                    song = song,
                                    onClick = {
                                        session.commands.playSongFromAlbum(song.id, destination.albumId)
                                        open(SkinDestination.NowPlaying)
                                    },
                                )
                            },
                        )
                    },
                )
            }
        })
        },
    )
}

@Composable
internal fun ZuneSkin.genreSongsScreen(
    session: SkinSession,
    destination: SkinDestination.GenreSongs,
) {
    val songs = remember(
        session.library,
        destination.genreId,
        calculation = { session.library.songsByGenre(destination.genreId) },
    ).collectAsLazyPagingItems()
    LibraryBackFrame(
        title = destination.genreName,
        onSettings = session.openAppSettings,
        onBack = { navigateBack() },
        content = {
        SkinLibraryGate(
            loadState = session.library.loadState.collectAsState().value,
            scanning = { _, _ -> EmptyMessage("scanning") },
            failed = { message -> EmptyMessage(message) },
            empty = { EmptyMessage("no songs") },
            ready = {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    content = {
                        items(
                            count = songs.itemCount,
                            key = { index -> songs.peek(index)?.id ?: index },
                            itemContent = { index ->
                                songs[index]?.let { song ->
                                    SongRow(
                                        song = song,
                                        onClick = {
                                            session.commands.playSongFromGenre(song.id, destination.genreId)
                                            open(SkinDestination.NowPlaying)
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            },
        )
        },
    )
}

@Composable
internal fun ZuneSkin.playlistSongsScreen(
    session: SkinSession,
    destination: SkinDestination.PlaylistSongs,
) {
    val songsFlow = remember(
        session.library,
        destination.playlistId,
        calculation = { session.library.songsByPlaylist(destination.playlistId) },
    )
    val songs by songsFlow.collectAsState(initial = emptyList())
    LibraryBackFrame(
        title = destination.playlistName,
        onSettings = session.openAppSettings,
        onBack = { navigateBack() },
        content = {
        libraryContentGate(session = session, content = {
            if (songs.isEmpty()) {
                EmptyMessage("no songs")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    content = {
                        items(
                            items = songs,
                            key = { song -> song.id },
                            itemContent = { song ->
                                SongRow(
                                    song = song,
                                    onClick = {
                                        session.commands.playSongFromPlaylist(song.id, destination.playlistId)
                                        open(SkinDestination.NowPlaying)
                                    },
                                )
                            },
                        )
                    },
                )
            }
        })
        },
    )
}

@Composable
private fun LibraryBackFrame(
    title: String,
    onSettings: () -> Unit,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    BlackFrame(
        title = title,
        utility = {
            AccentLink(text = "back", onClick = onBack)
            AccentLink(text = "settings", onClick = onSettings)
        },
        content = content,
    )
}

@Composable
internal fun ZuneSkin.searchScreen(session: SkinSession) {
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    LaunchedEffect(query, block = {
        delay(250L)
        debouncedQuery = query
    })
    val searchFlow = remember(
        session.library,
        debouncedQuery,
        calculation = { session.library.search(debouncedQuery) },
    )
    val results by searchFlow.collectAsState(initial = SkinSearchResults())
    LibraryBackFrame(
        title = "search",
        onSettings = session.openAppSettings,
        onBack = { navigateBack() },
        content = {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            textStyle = SkinTheme.typography.body.copy(color = SkinTheme.colors.onBackground),
            singleLine = true,
            decorationBox = { innerTextField ->
                Column {
                    if (query.isEmpty()) {
                        BasicText(
                            text = "type to search",
                            style = SkinTheme.typography.body.copy(color = SkinTheme.colors.onSurface),
                        )
                    }
                    innerTextField()
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp).height(1.dp)
                            .background(SkinTheme.colors.accent),
                    )
                }
            },
        )
        libraryContentGate(session = session, content = {
            if (debouncedQuery.isEmpty()) {
                EmptyMessage("search your library")
            } else if (results.isEmpty) {
                SkinNoResults(
                    query = debouncedQuery,
                    content = { value -> EmptyMessage("no results for $value") },
                )
            } else {
                SearchResults(session = session, results = results)
            }
        })
        },
    )
}

@Composable
private fun libraryContentGate(
    session: SkinSession,
    content: @Composable () -> Unit,
) {
    val loadState by session.library.loadState.collectAsState()
    SkinLibraryGate(
        loadState = loadState,
        scanning = { _, _ -> EmptyMessage("scanning") },
        failed = { message -> EmptyMessage(message) },
        empty = { EmptyMessage("nothing here yet") },
        ready = content,
    )
}

@Composable
private fun ZuneSkin.SearchResults(session: SkinSession, results: SkinSearchResults) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        content = {
            if (results.songs.isNotEmpty()) {
                item(content = { SectionLabel("songs") })
                items(
                    items = results.songs,
                    key = { song -> "song-${song.id}" },
                    itemContent = { song ->
                        SongRow(
                            song = song,
                            onClick = {
                                session.commands.playSongs(results.songs, results.songs.indexOf(song))
                                open(SkinDestination.NowPlaying)
                            },
                        )
                    },
                )
            }
            if (results.albums.isNotEmpty()) {
                item(content = { SectionLabel("albums") })
                items(
                    items = results.albums,
                    key = { album -> "album-${album.id}" },
                    itemContent = { album ->
                        AlbumRow(
                            album = album,
                            onClick = { open(SkinDestination.AlbumSongs(album.id, album.title)) },
                        )
                    },
                )
            }
            if (results.artists.isNotEmpty()) {
                item(content = { SectionLabel("artists") })
                items(
                    items = results.artists,
                    key = { artist -> "artist-${artist.id}" },
                    itemContent = { artist ->
                        LibraryRow(
                            title = artist.name,
                            subtitle = "${plural(artist.albumCount, "album")}  ·  ${plural(artist.songCount, "song")}",
                            onClick = { open(SkinDestination.ArtistAlbums(artist.id, artist.name)) },
                        )
                    },
                )
            }
            if (results.genres.isNotEmpty()) {
                item(content = { SectionLabel("genres") })
                items(
                    items = results.genres,
                    key = { genre -> "genre-${genre.id}" },
                    itemContent = { genre ->
                        LibraryRow(
                            title = genre.name,
                            subtitle = plural(genre.songCount, "song"),
                            onClick = { open(SkinDestination.GenreSongs(genre.id, genre.name)) },
                        )
                    },
                )
            }
            if (results.playlists.isNotEmpty()) {
                item(content = { SectionLabel("playlists") })
                items(
                    items = results.playlists,
                    key = { playlist -> "playlist-${playlist.id}" },
                    itemContent = { playlist ->
                        LibraryRow(
                            title = playlist.name,
                            subtitle = "${playlist.songCount} songs  ·  ${formatDuration(playlist.durationMs)}",
                            onClick = {
                                open(SkinDestination.PlaylistSongs(playlist.id, playlist.name))
                            },
                        )
                    },
                )
            }
        },
    )
}

@Composable
internal fun ZuneSkin.nowPlayingScreen(session: SkinSession) {
    val playback by session.playback.collectAsState()
    val smoothPosition by rememberInterpolatedPosition(playback)
    val song = playback.song

    // Deliberately NOT BlackFrame. On the hardware this is modelled on, now-playing is not a
    // titled page with a thumbnail on it - the artwork IS the screen, full bleed, with the
    // track details sitting over its lower half. A 250dp square centred in an otherwise empty
    // column is a generic music app, not this one.
    SkinTheme(
        colors = SkinPaletteColors,
        typography = SkinPaletteTypography,
        darkSystemBars = true,
        content = {
            Box(modifier = Modifier.fillMaxSize().background(SkinPaletteColors.background)) {
                if (song == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                        content = { EmptyMessage("nothing playing") },
                    )
                } else {
                    SkinAlbumArt(
                        uri = song.albumArtUri,
                        contentDescription = song.album,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        fallback = {
                            Box(modifier = Modifier.fillMaxSize().background(SkinPaletteColors.surface))
                        },
                    )

                    // Scrim, so the text stays legible over a cover of any brightness. Without
                    // it the layout works on dark art and is unreadable on pale art.
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            // Reaches near-solid well above the text block. An earlier,
                            // gentler ramp left the cover's own lettering showing through
                            // behind the progress bar, which read as two sets of type
                            // fighting each other.
                            Brush.verticalGradient(
                                0.00f to Color(0x00000000),
                                0.35f to Color(0x40000000),
                                0.55f to Color(0xB3000000),
                                0.70f to Color(0xF0000000),
                                1.00f to Color(0xFF000000),
                            ),
                        ),
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 20.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AccentLink(text = "queue", onClick = { navigateToQueue() })
                        AccentLink(text = "lyrics", onClick = { navigateToLyrics() })
                        AccentLink(text = "settings", onClick = session.openAppSettings)
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 28.dp, end = 20.dp, bottom = 26.dp),
                    ) {
                        BasicText(
                            text = song.title,
                            style = SkinTheme.typography.title.copy(
                                fontSize = 30.sp,
                                lineHeight = 33.sp,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        BasicText(
                            text = song.artist,
                            style = SkinTheme.typography.body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        BasicText(
                            text = song.album,
                            style = SkinTheme.typography.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ScrubbableProgress(
                            positionMs = smoothPosition,
                            durationMs = playback.durationMs,
                            onSeek = { positionMs -> session.commands.seekTo(positionMs) },
                            modifier = Modifier.padding(top = 20.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            BasicText(
                                text = formatDuration(smoothPosition),
                                style = SkinTheme.typography.label,
                            )
                            BasicText(
                                text = formatSignedDuration(smoothPosition - playback.durationMs),
                                style = SkinTheme.typography.label,
                            )
                        }
                        // Transport is centred while the track details stay on the left
                        // margin. The controls are the one thing here you reach for without
                        // looking, so they sit under the thumb rather than following the
                        // type.
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            AccentLink(text = "previous", onClick = session.commands::previous)
                            AccentLink(
                                text = if (playback.isPlaying) "pause" else "play",
                                onClick = session.commands::playPause,
                                modifier = Modifier.padding(horizontal = 18.dp),
                            )
                            AccentLink(text = "next", onClick = session.commands::next)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ScrubbableProgress(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragPositionMs by remember(durationMs, calculation = { mutableStateOf<Long?>(null) })
    val latestPositionMs by rememberUpdatedState(positionMs)
    val commitSeek by rememberUpdatedState(onSeek)
    val shownPositionMs = dragPositionMs ?: positionMs
    val duration = durationMs.coerceAtLeast(0L)
    val progress =
        if (duration == 0L) 0f else (shownPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp)
            .pointerInput(durationMs, block = {
                var target = latestPositionMs.coerceIn(0L, duration)
                fun positionAt(x: Float): Long {
                    if (duration == 0L || size.width <= 0) return 0L
                    return (duration.toDouble() * (x / size.width).coerceIn(0f, 1f)).toLong()
                }
                detectDragGestures(
                    onDragStart = { offset ->
                        target = positionAt(offset.x)
                        dragPositionMs = target
                    },
                    onDrag = { change, _ ->
                        target = positionAt(change.position.x)
                        dragPositionMs = target
                    },
                    onDragEnd = {
                        dragPositionMs = null
                        commitSeek(target)
                    },
                    onDragCancel = { dragPositionMs = null },
                )
            }),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.CenterStart)
                .background(SkinTheme.colors.surface),
        )
        Box(
            modifier = Modifier.fillMaxWidth(progress).height(3.dp).align(Alignment.CenterStart)
                .background(SkinTheme.colors.accent),
        )
    }
}

@Composable
internal fun ZuneSkin.queueScreen(session: SkinSession) {
    val playback by session.playback.collectAsState()
    BlackFrame(
        title = "queue",
        utility = { AccentLink(text = "settings", onClick = session.openAppSettings) },
        content = {
        if (playback.queue.isEmpty()) {
            EmptyMessage("queue is empty")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                content = {
                    items(
                        count = playback.queue.size,
                        key = { index -> "${playback.queue[index].id}-$index" },
                        itemContent = { index ->
                            val song = playback.queue[index]
                            SongRow(
                                song = song,
                                index = index,
                                trailing = if (index == playback.queueIndex) "playing" else null,
                                onClick = { session.commands.playQueueItem(index) },
                            )
                        },
                    )
                },
            )
        }
        },
    )
}
