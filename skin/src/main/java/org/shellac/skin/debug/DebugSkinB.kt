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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import org.shellac.skin.AppSkin
import org.shellac.skin.LibraryLoadState
import org.shellac.skin.SkinSession

/** Deliberately ugly dark debug skin (heavy/big font + "index. title" rows). */
class DebugSkinB : AppSkin {
    override val id = "debug-b"
    override val displayName = "Debug B"
    override val supportsEdgeToEdge = false

    private enum class Screen { ALBUMS, NOW_PLAYING, QUEUE }

    // onBackPressed() is a plain method and cannot see a rememberSaveable; a tiny
    // mutableStateOf field on the skin object is fine for a debug skin.
    private var screen by mutableStateOf(Screen.ALBUMS)
    private val bg = Color(0xFF0D0D12)
    private val fg = Color(0xFFCCFFCC)

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
        Column(Modifier.fillMaxSize().background(bg).padding(14.dp)) {
            BasicText("*** DEBUG B ALBUMS ***",
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic, color = fg))
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(Color(0xFF7A0044))
                .clickable(onClick = session.openAppSettings).padding(9.dp), contentAlignment = Alignment.Center) {
                BasicText("SETTINGS (app)", style = TextStyle(fontWeight = FontWeight.Bold, color = Color(0xFFFF88DD)))
            }
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp).background(Color(0xFF4A3A1A))
                .clickable { session.commands.shuffleAll() }.padding(9.dp), contentAlignment = Alignment.Center) {
                BasicText("SHUFFLE ALL  (shuffleAll)", style = TextStyle(fontWeight = FontWeight.Black, color = Color(0xFFFFCC88)))
            }
            when (val state = load.value) {
                is LibraryLoadState.Scanning ->
                    BasicText("[SCANNING] loaded=${state.loaded} explored=${state.explored}",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = fg))
                is LibraryLoadState.Failed ->
                    BasicText("[FAILED] ${state.message}",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6666)))
                is LibraryLoadState.Ready -> {
                    if (state.isEmpty) {
                        BasicText("[EMPTY] nothing to show",
                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = fg))
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(albums.itemCount) { i ->
                                val album = albums[i]
                                if (album != null) {
                                    val isExpanded = expandedId == album.id
                                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color(0xFF2A2A33))) {
                                        Row(Modifier.fillMaxWidth()
                                            .clickable {
                                                session.commands.playAlbum(album.id)
                                                screen = Screen.NOW_PLAYING
                                            }
                                            .padding(10.dp)) {
                                            BasicText("$i. ${album.title}",
                                                style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = fg))
                                        }
                                        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 5.dp)) {
                                            Box(Modifier.weight(1f).background(Color(0xFF113355))
                                                .clickable { expandedId = if (isExpanded) null else album.id }
                                                .padding(6.dp), contentAlignment = Alignment.Center) {
                                                BasicText(if (isExpanded) "[COLLAPSE]" else "[QUEUE / NEXT]",
                                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF88CCFF)))
                                            }
                                        }
                                        if (isExpanded) {
                                            // songsByAlbum returns a NEW Flow per call; remember it keyed on the
                                            // album id so we subscribe once per expanded row, not per recomposition.
                                            val songsFlow = remember(album.id) { session.library.songsByAlbum(album.id) }
                                            val songs by songsFlow.collectAsState(initial = emptyList())
                                            Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 6.dp)) {
                                                BasicText("[$i] ${songs.size} SONGS", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, color = fg))
                                                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                                                    Box(Modifier.weight(1f).background(Color(0xFF1A4A1A))
                                                        .clickable { session.commands.addToQueue(songs) }
                                                        .padding(6.dp), contentAlignment = Alignment.Center) {
                                                        BasicText("QUEUE-ALL (addToQueue)", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF88FF88)))
                                                    }
                                                    Box(Modifier.weight(1f).padding(start = 4.dp).background(Color(0xFF4A3A1A))
                                                        .clickable { session.commands.playNext(songs) }
                                                        .padding(6.dp), contentAlignment = Alignment.Center) {
                                                        BasicText("NEXT (playNext)", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFCC88)))
                                                    }
                                                }
                                                songs.forEachIndexed { j, s ->
                                                    BasicText("   $j. ${s.title}", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                LibraryLoadState.Idle ->
                    BasicText("[IDLE] waiting...",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = fg))
            }
        }
    }

    @Composable
    private fun NowPlayingScreen(session: SkinSession) {
        val pb = session.playback.collectAsState()
        val song = pb.value.song
        val pos = pb.value.positionMs
        val dur = pb.value.durationMs
        Column(Modifier.fillMaxSize().background(bg).padding(14.dp)) {
            BasicText(">> NOW PLAYING (debug-b) <<",
                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black, color = fg))
            if (song == null) {
                BasicText("No song loaded.", style = TextStyle(fontSize = 18.sp, color = fg))
            } else {
                BasicText("Title  : ${song.title}",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = fg))
                BasicText("Artist : ${song.artist}", style = TextStyle(fontSize = 20.sp, color = fg))
                BasicText("Album  : ${song.album}", style = TextStyle(fontSize = 20.sp, color = fg))
                BasicText("Playing: ${pb.value.isPlaying}",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = if (pb.value.isPlaying) Color(0xFF66FF66) else Color(0xFFFF8866)))
                BasicText("pos=${pb.value.positionMs} ms  dur=${pb.value.durationMs} ms",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = fg))
            }
            BasicText("${fmt(pos)} / ${fmt(dur)}",
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Black, color = fg))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Box(Modifier.weight(1f).background(Color(0xFF3A3A44)).clickable { session.commands.previous() }
                    .padding(12.dp), contentAlignment = Alignment.Center) {
                    BasicText("PREV", style = TextStyle(fontWeight = FontWeight.Black, color = fg))
                }
                Box(Modifier.weight(1f).padding(horizontal = 4.dp).background(Color(0xFF8844AA))
                    .clickable { session.commands.playPause() }.padding(12.dp), contentAlignment = Alignment.Center) {
                    BasicText("PLAY/PAUSE", style = TextStyle(fontWeight = FontWeight.Black, color = Color(0xFFFFDDFF)))
                }
                Box(Modifier.weight(1f).background(Color(0xFF3A3A44)).clickable { session.commands.next() }
                    .padding(12.dp), contentAlignment = Alignment.Center) {
                    BasicText("NEXT", style = TextStyle(fontWeight = FontWeight.Black, color = fg))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Box(Modifier.weight(1f).background(Color(0xFF4A2A55))
                    .clickable { session.commands.seekTo((pos - 10_000).coerceIn(0L, dur)) }
                    .padding(9.dp), contentAlignment = Alignment.Center) {
                    BasicText("-10s", style = TextStyle(fontWeight = FontWeight.Black, color = fg))
                }
                Box(Modifier.weight(1f).padding(horizontal = 4.dp).background(Color(0xFF4A2A55))
                    .clickable { session.commands.seekTo((pos + 10_000).coerceIn(0L, dur)) }
                    .padding(9.dp), contentAlignment = Alignment.Center) {
                    BasicText("+10s", style = TextStyle(fontWeight = FontWeight.Black, color = fg))
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).background(Color(0xFF0B3D0B))
                .clickable { session.commands.toggleShuffle() }.padding(9.dp), contentAlignment = Alignment.Center) {
                BasicText("SHUFFLE: ${pb.value.isShuffled}", style = TextStyle(fontWeight = FontWeight.Black, color = Color(0xFF88FF88)))
            }
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).background(Color(0xFF7A0044))
                .clickable { session.commands.setRepeatMode(pb.value.repeatMode.next()) }.padding(9.dp), contentAlignment = Alignment.Center) {
                BasicText("REPEAT: ${pb.value.repeatMode}  [setNext]", style = TextStyle(fontWeight = FontWeight.Black, color = Color(0xFFFF88DD)))
            }
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).background(Color(0xFF2A2A55))
                .clickable { screen = Screen.QUEUE }.padding(9.dp), contentAlignment = Alignment.Center) {
                BasicText("OPEN QUEUE", style = TextStyle(fontWeight = FontWeight.Black, color = Color(0xFF88CCFF)))
            }
        }
    }

    @Composable
    private fun QueueScreen(session: SkinSession) {
        val pb = session.playback.collectAsState()
        val queue = pb.value.queue
        Column(Modifier.fillMaxSize().background(bg).padding(14.dp)) {
            BasicText(">>> QUEUE (debug-b) <<<",
                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black, color = fg))
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color(0xFF7A0044))
                .clickable { screen = Screen.NOW_PLAYING }.padding(9.dp), contentAlignment = Alignment.Center) {
                BasicText("[BACK TO NOW PLAYING]", style = TextStyle(fontWeight = FontWeight.Bold, color = Color(0xFFFF88DD)))
            }
            if (queue.isEmpty()) {
                BasicText("[EMPTY QUEUE]", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = fg))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(queue.size) { idx ->
                        val song = queue[idx]
                        val isCurrent = idx == pb.value.queueIndex
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(
                            if (isCurrent) Color(0xFF1A3D1A) else Color(0xFF2A2A33))) {
                            Row(Modifier.fillMaxWidth()
                                .clickable { session.commands.playQueueItem(idx) }
                                .padding(8.dp)) {
                                BasicText(
                                    (if (isCurrent) ">> CURRENT " else "") + "$idx. ${song.title}  --  ${song.artist}",
                                    style = TextStyle(fontSize = 18.sp,
                                        fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold,
                                        color = if (isCurrent) Color(0xFF88FF88) else fg))
                            }
                            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp)) {
                                Box(Modifier.weight(1f).background(Color(0xFF551111))
                                    .clickable { session.commands.removeFromQueue(idx) }.padding(6.dp), contentAlignment = Alignment.Center) {
                                    BasicText("REMOVE", style = TextStyle(fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFFFF8888)))
                                }
                                Box(Modifier.weight(1f).padding(horizontal = 3.dp).background(Color(0xFF113355))
                                    .clickable(enabled = idx > 0) { session.commands.moveInQueue(idx, idx - 1) }
                                    .padding(6.dp), contentAlignment = Alignment.Center) {
                                    BasicText("UP", style = TextStyle(fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF88CCFF)))
                                }
                                Box(Modifier.weight(1f).background(Color(0xFF113355))
                                    .clickable(enabled = idx < queue.size - 1) { session.commands.moveInQueue(idx, idx + 1) }
                                    .padding(6.dp), contentAlignment = Alignment.Center) {
                                    BasicText("DOWN", style = TextStyle(fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF88CCFF)))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

