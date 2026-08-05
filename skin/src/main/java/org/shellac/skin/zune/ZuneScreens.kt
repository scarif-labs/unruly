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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.shellac.skin.LibraryLoadState
import org.shellac.skin.SkinAlbum
import org.shellac.skin.SkinArtist
import org.shellac.skin.SkinGenre
import org.shellac.skin.SkinLibrary
import org.shellac.skin.SkinPlaylist
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
            ZuneFrame(title = "music") {
                ZuneSectionLabel("indexing")
                BasicText(
                    text = if (loaded == null || explored == null) "scanning" else "$loaded / $explored",
                    style = SkinTheme.typography.body,
                )
            }
        },
        failed = { message ->
            ZuneFrame(title = "music") {
                ZuneSectionLabel("library error")
                BasicText(text = message, style = SkinTheme.typography.body)
            }
        },
        empty = {
            ZuneFrame(title = "music") {
                ZuneEmptyMessage("nothing here yet")
                ZuneUtilityLink(text = "settings", onClick = session.openAppSettings)
            }
        },
        ready = {
            val pagerState = rememberPagerState(pageCount = { Pivot.entries.size })
            val pagerScope = rememberCoroutineScope()
            ZuneFrame(
                title = "music",
                utility = {
                    ZuneUtilityLink(text = "search", onClick = { navigateToSearch() })
                    ZuneUtilityLink(text = "queue", onClick = { navigateToQueue() })
                    ZuneUtilityLink(
                        text = "now playing",
                        onClick = { navigateToNowPlaying(session) },
                    )
                    ZuneUtilityLink(text = "settings", onClick = session.openAppSettings)
                },
            ) {
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
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PivotHeadings(
    pagerState: androidx.compose.foundation.pager.PagerState,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Pivot.entries.forEachIndexed { index, pivot ->
            val active = pagerState.currentPage == index
            BasicText(
                text = pivot.title,
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
    val artists = remember(session.library) { session.library.artists() }.collectAsLazyPagingItems()
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        content = {
            items(
                count = artists.itemCount,
                key = { index -> artists.peek(index)?.id ?: index },
                itemContent = { index ->
                    artists[index]?.let { artist ->
                        ZuneListRow(
                            title = artist.name,
                            subtitle = "${artist.albumCount} albums  ·  ${artist.songCount} songs",
                            onClick = { open(ZuneDestination.ArtistAlbums(artist.id, artist.name)) },
                        )
                    }
                },
            )
        },
    )
}

@Composable
private fun ZuneSkin.albumsPivot(session: SkinSession) {
    val albums = remember(session.library) { session.library.albums() }.collectAsLazyPagingItems()
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
                            onClick = { open(ZuneDestination.AlbumSongs(album.id, album.title)) },
                        )
                    }
                },
            )
        },
    )
}

@Composable
private fun ZuneSkin.songsPivot(session: SkinSession) {
    val songs = remember(session.library) { session.library.songs() }.collectAsLazyPagingItems()
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        content = {
            items(
                count = songs.itemCount,
                key = { index -> songs.peek(index)?.id ?: index },
                itemContent = { index ->
                    songs[index]?.let { song ->
                        ZuneSongRow(
                            song = song,
                            index = index,
                            onClick = {
                                session.commands.playSongFromAll(song.id)
                                open(ZuneDestination.NowPlaying)
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
    val genres = remember(session.library) { session.library.genres() }.collectAsLazyPagingItems()
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        content = {
            items(
                count = genres.itemCount,
                key = { index -> genres.peek(index)?.id ?: index },
                itemContent = { index ->
                    genres[index]?.let { genre ->
                        ZuneListRow(
                            title = genre.name,
                            subtitle = "${genre.songCount} songs",
                            onClick = { open(ZuneDestination.GenreSongs(genre.id, genre.name)) },
                        )
                    }
                },
            )
        },
    )
}

@Composable
private fun ZuneSkin.playlistsPivot(session: SkinSession) {
    val playlists = remember(session.library) { session.library.playlists() }.collectAsLazyPagingItems()
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        content = {
            items(
                count = playlists.itemCount,
                key = { index -> playlists.peek(index)?.id ?: index },
                itemContent = { index ->
                    playlists[index]?.let { playlist ->
                        ZuneListRow(
                            title = playlist.name,
                            subtitle = "${playlist.songCount} songs  ·  ${formatDuration(playlist.durationMs)}",
                            onClick = {
                                open(ZuneDestination.PlaylistSongs(playlist.id, playlist.name))
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
                text = "${album.artist}  ·  ${album.songCount} songs",
                style = SkinTheme.typography.label,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
internal fun ZuneSkin.artistAlbumsScreen(
    session: SkinSession,
    destination: ZuneDestination.ArtistAlbums,
) {
    val albums = remember(session.library, destination.artistId) {
        session.library.albumsByArtist(destination.artistId)
    }.collectAsLazyPagingItems()
    LibraryBackFrame(
        title = destination.artistName,
        session = session,
        onSettings = session.openAppSettings,
        onBack = { navigateBack() },
    ) {
        SkinLibraryGate(
            loadState = session.library.loadState.collectAsState().value,
            scanning = { _, _ -> ZuneEmptyMessage("scanning") },
            failed = { message -> ZuneEmptyMessage(message) },
            empty = { ZuneEmptyMessage("no albums") },
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
                                            open(ZuneDestination.AlbumSongs(album.id, album.title))
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            },
        )
    }
}

@Composable
internal fun ZuneSkin.albumSongsScreen(
    session: SkinSession,
    destination: ZuneDestination.AlbumSongs,
) {
    val songsFlow = remember(session.library, destination.albumId) {
        session.library.songsByAlbum(destination.albumId)
    }
    val songs by songsFlow.collectAsState(initial = emptyList())
    LibraryBackFrame(
        title = destination.albumTitle,
        session = session,
        onSettings = session.openAppSettings,
        onBack = { navigateBack() },
    ) {
        if (songs.isEmpty()) {
            ZuneEmptyMessage("no songs")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                content = {
                    items(
                        items = songs,
                        key = { song -> song.id },
                        itemContent = { song ->
                            ZuneSongRow(
                                song = song,
                                onClick = {
                                    session.commands.playSongFromAlbum(song.id, destination.albumId)
                                    open(ZuneDestination.NowPlaying)
                                },
                            )
                        },
                    )
                },
            )
        }
    }
}

@Composable
internal fun ZuneSkin.genreSongsScreen(
    session: SkinSession,
    destination: ZuneDestination.GenreSongs,
) {
    val songs = remember(session.library, destination.genreId) {
        session.library.songsByGenre(destination.genreId)
    }.collectAsLazyPagingItems()
    LibraryBackFrame(
        title = destination.genreName,
        session = session,
        onSettings = session.openAppSettings,
        onBack = { navigateBack() },
    ) {
        SkinLibraryGate(
            loadState = session.library.loadState.collectAsState().value,
            scanning = { _, _ -> ZuneEmptyMessage("scanning") },
            failed = { message -> ZuneEmptyMessage(message) },
            empty = { ZuneEmptyMessage("no songs") },
            ready = {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    content = {
                        items(
                            count = songs.itemCount,
                            key = { index -> songs.peek(index)?.id ?: index },
                            itemContent = { index ->
                                songs[index]?.let { song ->
                                    ZuneSongRow(
                                        song = song,
                                        onClick = {
                                            session.commands.playSongFromGenre(song.id, destination.genreId)
                                            open(ZuneDestination.NowPlaying)
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            },
        )
    }
}

@Composable
internal fun ZuneSkin.playlistSongsScreen(
    session: SkinSession,
    destination: ZuneDestination.PlaylistSongs,
) {
    val songsFlow = remember(session.library, destination.playlistId) {
        session.library.songsByPlaylist(destination.playlistId)
    }
    val songs by songsFlow.collectAsState(initial = emptyList())
    LibraryBackFrame(
        title = destination.playlistName,
        session = session,
        onSettings = session.openAppSettings,
        onBack = { navigateBack() },
    ) {
        if (songs.isEmpty()) {
            ZuneEmptyMessage("no songs")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                content = {
                    items(
                        items = songs,
                        key = { song -> song.id },
                        itemContent = { song ->
                            ZuneSongRow(
                                song = song,
                                onClick = {
                                    session.commands.playSongFromPlaylist(song.id, destination.playlistId)
                                    open(ZuneDestination.NowPlaying)
                                },
                            )
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun LibraryBackFrame(
    title: String,
    session: SkinSession,
    onSettings: () -> Unit,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    ZuneFrame(
        title = title,
        utility = {
            ZuneUtilityLink(text = "back", onClick = onBack)
            ZuneUtilityLink(text = "settings", onClick = onSettings)
        },
        content = content,
    )
}

@Composable
internal fun ZuneSkin.searchScreen(session: SkinSession) {
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        delay(250L)
        debouncedQuery = query
    }
    val searchFlow = remember(session.library, debouncedQuery) {
        session.library.search(debouncedQuery)
    }
    val results by searchFlow.collectAsState(initial = SkinSearchResults())
    LibraryBackFrame(
        title = "search",
        session = session,
        onSettings = session.openAppSettings,
        onBack = { navigateBack() },
    ) {
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
        if (debouncedQuery.isEmpty()) {
            ZuneEmptyMessage("search your library")
        } else if (results.isEmpty) {
            SkinNoResults(query = debouncedQuery) { value ->
                ZuneEmptyMessage("no results for $value")
            }
        } else {
            SearchResults(session = session, results = results)
        }
    }
}

@Composable
private fun ZuneSkin.SearchResults(session: SkinSession, results: SkinSearchResults) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        content = {
            if (results.songs.isNotEmpty()) {
                item { ZuneSectionLabel("songs") }
                items(
                    items = results.songs,
                    key = { song -> "song-${song.id}" },
                    itemContent = { song ->
                        ZuneSongRow(
                            song = song,
                            onClick = {
                                session.commands.playSongs(results.songs, results.songs.indexOf(song))
                                open(ZuneDestination.NowPlaying)
                            },
                        )
                    },
                )
            }
            if (results.albums.isNotEmpty()) {
                item { ZuneSectionLabel("albums") }
                items(
                    items = results.albums,
                    key = { album -> "album-${album.id}" },
                    itemContent = { album ->
                        AlbumRow(
                            album = album,
                            onClick = { open(ZuneDestination.AlbumSongs(album.id, album.title)) },
                        )
                    },
                )
            }
            if (results.artists.isNotEmpty()) {
                item { ZuneSectionLabel("artists") }
                items(
                    items = results.artists,
                    key = { artist -> "artist-${artist.id}" },
                    itemContent = { artist ->
                        ZuneListRow(
                            title = artist.name,
                            subtitle = "${artist.albumCount} albums  ·  ${artist.songCount} songs",
                            onClick = { open(ZuneDestination.ArtistAlbums(artist.id, artist.name)) },
                        )
                    },
                )
            }
            if (results.genres.isNotEmpty()) {
                item { ZuneSectionLabel("genres") }
                items(
                    items = results.genres,
                    key = { genre -> "genre-${genre.id}" },
                    itemContent = { genre ->
                        ZuneListRow(
                            title = genre.name,
                            subtitle = "${genre.songCount} songs",
                            onClick = { open(ZuneDestination.GenreSongs(genre.id, genre.name)) },
                        )
                    },
                )
            }
            if (results.playlists.isNotEmpty()) {
                item { ZuneSectionLabel("playlists") }
                items(
                    items = results.playlists,
                    key = { playlist -> "playlist-${playlist.id}" },
                    itemContent = { playlist ->
                        ZuneListRow(
                            title = playlist.name,
                            subtitle = "${playlist.songCount} songs  ·  ${formatDuration(playlist.durationMs)}",
                            onClick = {
                                open(ZuneDestination.PlaylistSongs(playlist.id, playlist.name))
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
    ZuneFrame(
        title = "now playing",
        utility = {
            ZuneUtilityLink(text = "queue", onClick = { navigateToQueue() })
            ZuneUtilityLink(text = "settings", onClick = session.openAppSettings)
        },
    ) {
        if (song == null) {
            ZuneEmptyMessage("nothing playing")
        } else {
            SkinAlbumArt(
                uri = song.albumArtUri,
                contentDescription = song.album,
                modifier = Modifier.size(250.dp),
                contentScale = ContentScale.Crop,
                fallback = {
                    Box(
                        modifier = Modifier.size(250.dp).background(SkinTheme.colors.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = song.album.take(1).lowercase(),
                            style = SkinTheme.typography.title.copy(color = SkinTheme.colors.accent),
                        )
                    }
                },
            )
            BasicText(
                text = song.title,
                style = SkinTheme.typography.title.copy(fontSize = 34.sp, lineHeight = 36.sp),
                modifier = Modifier.padding(top = 18.dp),
            )
            BasicText(text = song.artist, style = SkinTheme.typography.body)
            BasicText(text = song.album, style = SkinTheme.typography.label)
            ScrubbableProgress(
                positionMs = smoothPosition,
                durationMs = playback.durationMs,
                onSeek = { positionMs -> session.commands.seekTo(positionMs) },
                modifier = Modifier.padding(top = 22.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BasicText(text = formatDuration(smoothPosition), style = SkinTheme.typography.label)
                BasicText(
                    text = formatSignedDuration(smoothPosition - playback.durationMs),
                    style = SkinTheme.typography.label,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ZuneUtilityLink(text = "previous", onClick = session.commands::previous)
                ZuneUtilityLink(text = if (playback.isPlaying) "pause" else "play", onClick = session.commands::playPause)
                ZuneUtilityLink(text = "next", onClick = session.commands::next)
            }
        }
    }
}

@Composable
private fun ScrubbableProgress(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragPositionMs by remember(durationMs) { mutableStateOf<Long?>(null) }
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
    ZuneFrame(
        title = "queue",
        utility = { ZuneUtilityLink(text = "settings", onClick = session.openAppSettings) },
    ) {
        if (playback.queue.isEmpty()) {
            ZuneEmptyMessage("queue is empty")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                content = {
                    items(
                        items = playback.queue,
                        key = { song -> song.id },
                        itemContent = { song ->
                            val index = playback.queue.indexOf(song)
                            ZuneSongRow(
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
    }
}
