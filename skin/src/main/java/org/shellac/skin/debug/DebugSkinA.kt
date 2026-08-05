package org.shellac.skin.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import org.shellac.skin.AppSkin
import org.shellac.skin.LibraryLoadState
import org.shellac.skin.SkinSession

/** Deliberately ugly light-ish debug skin used to exercise the skin contract. */
class DebugSkinA : AppSkin {
    override val id = "debug-a"
    override val displayName = "Debug A"
    override val supportsEdgeToEdge = false

    private enum class Screen { ALBUMS, NOW_PLAYING, QUEUE }

    // onBackPressed() is a plain method and cannot see a rememberSaveable; a tiny
    // mutableStateOf field on the skin object is fine for a debug skin.
    private var screen by mutableStateOf(Screen.ALBUMS)
    private val bg = Color(0xFFF5F0E8)
    private val fg = Color(0xFF333333)

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%02d:%02d".format(s / 60, s % 60)
    }

    override fun onBackPressed(): Boolean =
        when (screen) {
            Screen.QUEUE -> { screen = Screen.NOW_PLAYING; true }
            Screen.NOW_PLAYING -> { screen = Screen.ALBUMS; true }
            Screen.ALBUMS -> false
        }

    @Composable
    override fun Render(session: SkinSession) {
        when (screen) {
            Screen.ALBUMS -> AlbumsScreen(session)
            Screen.NOW_PLAYING -> NowPlayingScreen(session)
            Screen.QUEUE -> QueueScreen(session)
        }
    }

    @Composable
    private fun AlbumsScreen(session: SkinSession) {
        val albums = session.library.albums().collectAsLazyPagingItems()
        val load = session.library.loadState.collectAsState()
        var expandedId by remember { mutableStateOf<String?>(null) }
        Column(Modifier.fillMaxSize().background(bg).padding(12.dp)) {
            BasicText("DEBUG A ALBUMS", style = TextStyle(fontSize = 22.sp, color = fg))
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(Color(0xFFCCE5FF))
                .clickable(onClick = session.openAppSettings).padding(8.dp), contentAlignment = Alignment.Center) {
                BasicText("OPEN APP SETTINGS", style = TextStyle(color = Color(0xFF001133)))
            }
            Box(Modifier.fillMaxWidth().padding(bottom = 6.dp).background(Color(0xFFF5D0A8))
                .clickable { session.commands.shuffleAll() }.padding(8.dp), contentAlignment = Alignment.Center) {
                BasicText("SHUFFLE ALL", style = TextStyle(fontWeight = FontWeight.Bold, color = Color(0xFF4A2A00)))
            }
            when (val state = load.value) {
                is LibraryLoadState.Scanning ->
                    BasicText("SCANNING... loaded=${state.loaded} explored=${state.explored}", style = TextStyle(color = fg))
                is LibraryLoadState.Failed ->
                    BasicText("LOAD FAILED: ${state.message}", style = TextStyle(color = Color(0xFF880000)))
                is LibraryLoadState.Ready -> {
                    if (state.isEmpty) {
                        BasicText("Library is EMPTY.", style = TextStyle(color = fg))
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(albums.itemCount) { i ->
                                val album = albums[i]
                                if (album != null) {
                                    val isExpanded = expandedId == album.id
                                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(Color(0x1A888888))) {
                                        Row(Modifier.fillMaxWidth()
                                            .clickable {
                                                session.commands.playAlbum(album.id)
                                                screen = Screen.NOW_PLAYING
                                            }
                                            .padding(8.dp)) {
                                            BasicText("${album.title}  --  ${album.artist}",
                                                style = TextStyle(fontSize = 16.sp, color = fg))
                                        }
                                        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp)) {
                                            Box(Modifier.weight(1f).background(Color(0xFFB8D8F0))
                                                .clickable { expandedId = if (isExpanded) null else album.id }
                                                .padding(6.dp), contentAlignment = Alignment.Center) {
                                                BasicText(if (isExpanded) "COLLAPSE" else "QUEUE / NEXT",
                                                    style = TextStyle(fontSize = 12.sp, color = Color(0xFF003366)))
                                            }
                                        }
                                        if (isExpanded) {
                                            // songsByAlbum returns a NEW Flow per call; remember it keyed on the
                                            // album id so we subscribe once per expanded row, not per recomposition.
                                            val songsFlow = remember(album.id) { session.library.songsByAlbum(album.id) }
                                            val songs by songsFlow.collectAsState(initial = emptyList())
                                            Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp)) {
                                                BasicText("${songs.size} songs loaded.", style = TextStyle(fontSize = 12.sp, color = fg))
                                                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                                                    Box(Modifier.weight(1f).background(Color(0xFFB8ECC8))
                                                        .clickable { session.commands.addToQueue(songs) }
                                                        .padding(6.dp), contentAlignment = Alignment.Center) {
                                                        BasicText("ADD TO QUEUE", style = TextStyle(fontSize = 12.sp, color = Color(0xFF003300)))
                                                    }
                                                    Box(Modifier.weight(1f).padding(start = 4.dp).background(Color(0xFFF4D0A0))
                                                        .clickable { session.commands.playNext(songs) }
                                                        .padding(6.dp), contentAlignment = Alignment.Center) {
                                                        BasicText("PLAY NEXT", style = TextStyle(fontSize = 12.sp, color = Color(0xFF4A2A00)))
                                                    }
                                                }
                                                songs.forEach { s ->
                                                    BasicText("  [q] ${s.title}", style = TextStyle(fontSize = 12.sp, color = fg))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                LibraryLoadState.Idle -> BasicText("WAITING FOR LIBRARY...", style = TextStyle(color = fg))
            }
        }
    }

    @Composable
    private fun NowPlayingScreen(session: SkinSession) {
        val pb = session.playback.collectAsState()
        val song = pb.value.song
        val pos = pb.value.positionMs
        val dur = pb.value.durationMs
        Column(Modifier.fillMaxSize().background(bg).padding(12.dp)) {
            BasicText("NOW PLAYING (debug-a)", style = TextStyle(fontSize = 20.sp, color = fg))
            if (song == null) {
                BasicText("No song loaded.", style = TextStyle(color = fg))
            } else {
                BasicText("Title  : ${song.title}", style = TextStyle(fontSize = 16.sp, color = fg))
                BasicText("Artist : ${song.artist}", style = TextStyle(fontSize = 16.sp, color = fg))
                BasicText("Album  : ${song.album}", style = TextStyle(fontSize = 16.sp, color = fg))
                BasicText("Playing: ${pb.value.isPlaying}",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = if (pb.value.isPlaying) Color(0xFF006600) else Color(0xFFAA0000)))
                BasicText("pos=${pb.value.positionMs} ms  dur=${pb.value.durationMs} ms",
                    style = TextStyle(fontSize = 16.sp, color = fg))
            }
            BasicText("${fmt(pos)} / ${fmt(dur)}",
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = fg))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Box(Modifier.weight(1f).background(Color(0xFFE0E0E0)).clickable { session.commands.previous() }
                    .padding(10.dp), contentAlignment = Alignment.Center) {
                    BasicText("PREV", style = TextStyle(fontWeight = FontWeight.Bold, color = fg))
                }
                Box(Modifier.weight(1f).padding(horizontal = 4.dp).background(Color(0xFF90EE90))
                    .clickable { session.commands.playPause() }.padding(10.dp), contentAlignment = Alignment.Center) {
                    BasicText("PLAY/PAUSE", style = TextStyle(fontWeight = FontWeight.Bold, color = Color(0xFF002200)))
                }
                Box(Modifier.weight(1f).background(Color(0xFFE0E0E0)).clickable { session.commands.next() }
                    .padding(10.dp), contentAlignment = Alignment.Center) {
                    BasicText("NEXT", style = TextStyle(fontWeight = FontWeight.Bold, color = fg))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Box(Modifier.weight(1f).background(Color(0xFFD8C8E8))
                    .clickable { session.commands.seekTo((pos - 10_000).coerceIn(0L, dur)) }
                    .padding(8.dp), contentAlignment = Alignment.Center) {
                    BasicText("-10s", style = TextStyle(fontWeight = FontWeight.Bold, color = fg))
                }
                Box(Modifier.weight(1f).padding(horizontal = 4.dp).background(Color(0xFFD8C8E8))
                    .clickable { session.commands.seekTo((pos + 10_000).coerceIn(0L, dur)) }
                    .padding(8.dp), contentAlignment = Alignment.Center) {
                    BasicText("+10s", style = TextStyle(fontWeight = FontWeight.Bold, color = fg))
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).background(Color(0xFFCCE5FF))
                .clickable { session.commands.toggleShuffle() }.padding(8.dp), contentAlignment = Alignment.Center) {
                BasicText("SHUFFLE: ${pb.value.isShuffled}", style = TextStyle(fontWeight = FontWeight.Bold, color = Color(0xFF003366)))
            }
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).background(Color(0xFFF0D8C0))
                .clickable { session.commands.cycleRepeatMode() }.padding(8.dp), contentAlignment = Alignment.Center) {
                BasicText("REPEAT: ${pb.value.repeatMode}  [cycle]", style = TextStyle(fontWeight = FontWeight.Bold, color = Color(0xFF4A2A00)))
            }
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).background(Color(0xFFB8ECC8))
                .clickable { screen = Screen.QUEUE }.padding(8.dp), contentAlignment = Alignment.Center) {
                BasicText("OPEN QUEUE", style = TextStyle(fontWeight = FontWeight.Bold, color = Color(0xFF003300)))
            }
        }
    }

    @Composable
    private fun QueueScreen(session: SkinSession) {
        val pb = session.playback.collectAsState()
        val queue = pb.value.queue
        Column(Modifier.fillMaxSize().background(bg).padding(12.dp)) {
            BasicText("QUEUE (debug-a)", style = TextStyle(fontSize = 20.sp, color = fg))
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color(0xFFCCE5FF))
                .clickable { screen = Screen.NOW_PLAYING }.padding(8.dp), contentAlignment = Alignment.Center) {
                BasicText("BACK TO NOW PLAYING", style = TextStyle(color = Color(0xFF001133)))
            }
            if (queue.isEmpty()) {
                BasicText("Queue is empty.", style = TextStyle(color = fg))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(queue.size) { idx ->
                        val song = queue[idx]
                        val isCurrent = idx == pb.value.queueIndex
                        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(
                            if (isCurrent) Color(0xFFCCFFCC) else Color(0x1A888888))) {
                            Row(Modifier.fillMaxWidth()
                                .clickable { session.commands.playQueueItem(idx) }
                                .padding(6.dp)) {
                                BasicText(
                                    (if (isCurrent) ">> " else "   ") + "${song.title}  --  ${song.artist}",
                                    style = TextStyle(fontSize = 15.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = fg))
                            }
                            Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 4.dp)) {
                                Box(Modifier.weight(1f).background(Color(0xFFFFD0D0))
                                    .clickable { session.commands.removeFromQueue(idx) }.padding(5.dp), contentAlignment = Alignment.Center) {
                                    BasicText("REMOVE", style = TextStyle(fontSize = 12.sp, color = Color(0xFF880000)))
                                }
                                Box(Modifier.weight(1f).padding(horizontal = 3.dp).background(Color(0xFFD8E8FF))
                                    .clickable(enabled = idx > 0) { session.commands.moveInQueue(idx, idx - 1) }
                                    .padding(5.dp), contentAlignment = Alignment.Center) {
                                    BasicText("UP", style = TextStyle(fontSize = 12.sp, color = Color(0xFF003366)))
                                }
                                Box(Modifier.weight(1f).background(Color(0xFFD8E8FF))
                                    .clickable(enabled = idx < queue.size - 1) { session.commands.moveInQueue(idx, idx + 1) }
                                    .padding(5.dp), contentAlignment = Alignment.Center) {
                                    BasicText("DOWN", style = TextStyle(fontSize = 12.sp, color = Color(0xFF003366)))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
