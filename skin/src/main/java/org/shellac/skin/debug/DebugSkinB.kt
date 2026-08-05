package org.shellac.skin.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import org.shellac.skin.SkinSession
import org.shellac.skin.support.SkinAlbumArt
import org.shellac.skin.support.SkinColors
import org.shellac.skin.support.SkinLibraryGate
import org.shellac.skin.support.SkinTheme
import org.shellac.skin.support.SkinTypography
import org.shellac.skin.support.rememberInterpolatedPosition
import org.shellac.skin.support.rotaryInput

/** Deliberately ugly dark debug skin (heavy/big font + "index. title" rows). */
class DebugSkinB : AppSkin {
    override val id = "debug-b"
    override val displayName = "Debug B"
    override val supportsEdgeToEdge = false

    private enum class Screen { ALBUMS, NOW_PLAYING, QUEUE }

    // 4.5: DebugSkinB intentionally stays on its flat enum field. The back stack is an
    // OPTIONAL primitive and a flat skin must not be forced into it; DebugSkinA demonstrates
    // the stack alternative. onBackPressed() is a plain method, so this field lives on the
    // skin object (per AppSkin rule 2).
    private var screen by mutableStateOf(Screen.ALBUMS)

    // 4.3: DebugSkinB styles itself with a dark palette; darkSystemBars = true.
    private val colors =
        SkinColors(
            background = Color(0xFF0D0D12),
            surface = Color(0xFF1E1E28),
            onBackground = Color(0xFFCCFFCC),
            onSurface = Color(0xFFBDBDD0),
            accent = Color(0xFFBB86FC),
            onAccent = Color(0xFF000000),
        )
    private val typography =
        SkinTypography(
            title = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic),
            body = TextStyle(fontSize = 18.sp),
            label = TextStyle(fontSize = 13.sp),
        )

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%02d:%02d".format(s / 60, s % 60)
    }

    // Tenths-of-a-second variant so the interpolated (frame-clock) position is visibly
    // finer-grained than the raw 10 Hz engine value during the manual test.
    private fun fmtTenths(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val tenths = (ms % 1000).coerceAtLeast(0) / 100
        return "%02d:%02d.%d".format(s / 60, s % 60, tenths)
    }

    override fun onBackPressed(): Boolean =
        when (screen) {
            Screen.QUEUE -> { screen = Screen.NOW_PLAYING; true }
            Screen.NOW_PLAYING -> { screen = Screen.ALBUMS; true }
            Screen.ALBUMS -> false
        }

    @Composable
    override fun Render(session: SkinSession) {
        SkinTheme(
            colors = colors,
            typography = typography,
            darkSystemBars = true,
        ) {
            when (screen) {
                Screen.ALBUMS -> AlbumsScreen(session)
                Screen.NOW_PLAYING -> NowPlayingScreen(session)
                Screen.QUEUE -> QueueScreen(session)
            }
        }
    }

    @Composable
    private fun AlbumsScreen(session: SkinSession) {
        // session.library.albums() returns a NEW Flow per call; remember it so we subscribe
        // once instead of re-subscribing the repository listener on every recomposition.
        val albums = remember { session.library.albums() }.collectAsLazyPagingItems()
        val load = session.library.loadState.collectAsState()
        var expandedId by remember { mutableStateOf<String?>(null) }
        val colors = SkinTheme.colors
        val bg = colors.background
        val fg = colors.onBackground
        Column(Modifier.fillMaxSize().background(bg).padding(14.dp)) {
            BasicText("*** DEBUG B ALBUMS ***",
                style = SkinTheme.typography.title.copy(color = fg))
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(Color(0xFF7A0044))
                .clickable(onClick = session.openAppSettings).padding(9.dp), contentAlignment = Alignment.Center) {
                BasicText("SETTINGS (app)", style = TextStyle(fontWeight = FontWeight.Bold, color = Color(0xFFFF88DD)))
            }
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp).background(Color(0xFF4A3A1A))
                .clickable { session.commands.shuffleAll() }.padding(9.dp), contentAlignment = Alignment.Center) {
                BasicText("SHUFFLE ALL  (shuffleAll)", style = TextStyle(fontWeight = FontWeight.Black, color = Color(0xFFFFCC88)))
            }
            // 4.6: replace the hand-rolled when(loadState) with the library gate; each branch
            // is styled by this skin (heavy type).
            SkinLibraryGate(
                loadState = load.value,
                scanning = { loaded, explored ->
                    BasicText("[SCANNING] loaded=$loaded explored=$explored",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = fg))
                },
                failed = { message ->
                    BasicText("[FAILED] $message",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6666)))
                },
                empty = {
                    BasicText("[EMPTY] nothing to show",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = fg))
                },
                ready = {
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
                                        .padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        // 4.2: small album art per row with this skin's different
                                        // fallback (accent-purple box + first letter).
                                        SkinAlbumArt(
                                            uri = album.albumArtUri,
                                            contentDescription = album.title,
                                            modifier = Modifier.size(48.dp),
                                        ) {
                                            Box(Modifier.size(48.dp).background(Color(0xFF2A2A55)),
                                                contentAlignment = Alignment.Center) {
                                                BasicText(
                                                    album.title.firstOrNull()?.uppercase() ?: "?",
                                                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Black,
                                                        color = Color(0xFFFF88DD)))
                                            }
                                        }
                                        BasicText("  $i. ${album.title}",
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
                                        val songsFlow = remember(album.id) { session.library.songsByAlbum(album.id) }
                                        val songs by songsFlow.collectAsState(initial = emptyList())
                                        Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 6.dp)) {
                                            BasicText("[$i] ${songs.size} SONGS",
                                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, color = fg))
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
                                                BasicText("   $j. ${s.title}",
                                                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
            )
        }
    }

    @Composable
    private fun NowPlayingScreen(session: SkinSession) {
        val pb = session.playback.collectAsState()
        val song = pb.value.song
        val dur = pb.value.durationMs
        val raw = pb.value.positionMs
        // 4.1: interpolated frame-clock position drives the display and the progress bar.
        val smooth by rememberInterpolatedPosition(pb.value)
        val colors = SkinTheme.colors
        val bg = colors.background
        val fg = colors.onBackground
        Column(Modifier.fillMaxSize().background(bg).padding(14.dp)) {
            BasicText(">> NOW PLAYING (debug-b) <<",
                style = SkinTheme.typography.title.copy(color = fg))
            if (song == null) {
                BasicText("No song loaded.", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = fg))
            } else {
                // 4.2: large album art with this skin's different fallback (purple box + letter).
                SkinAlbumArt(
                    uri = song.albumArtUri,
                    contentDescription = song.album,
                    modifier = Modifier.size(200.dp),
                ) {
                    Box(Modifier.size(200.dp).background(Color(0xFF2A2A55)), contentAlignment = Alignment.Center) {
                        BasicText(
                            song.title.firstOrNull()?.uppercase() ?: "?",
                            style = TextStyle(fontSize = 90.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF88DD)))
                    }
                }
                BasicText("Title  : ${song.title}", style = TextStyle(fontSize = 20.sp, color = fg))
                BasicText("Artist : ${song.artist}", style = TextStyle(fontSize = 20.sp, color = fg))
                BasicText("Album  : ${song.album}", style = TextStyle(fontSize = 20.sp, color = fg))
                BasicText("Playing: ${pb.value.isPlaying}",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = if (pb.value.isPlaying) Color(0xFF66FF66) else Color(0xFFFF8866)))
                // 4.1: show BOTH raw (10 Hz engine) and interpolated (frame clock) side by
                // side so the difference and any seek resync are obvious.
                BasicText("raw    ${fmt(raw)}    (10 Hz engine)",
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = fg))
                BasicText("smooth ${fmtTenths(smooth)}  (interpolated)",
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = fg))
                // 4.1: progress bar driven by the interpolated value.
                val progress = if (dur > 0L) (smooth.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(10.dp)
                    .background(colors.surface).border(1.dp, colors.accent)) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.accent))
                }
            }
            BasicText("${fmt(raw)} / ${fmt(dur)}",
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
                    .clickable { session.commands.seekTo((raw - 10_000).coerceIn(0L, dur)) }
                    .padding(9.dp), contentAlignment = Alignment.Center) {
                    BasicText("-10s", style = TextStyle(fontWeight = FontWeight.Black, color = fg))
                }
                Box(Modifier.weight(1f).padding(horizontal = 4.dp).background(Color(0xFF4A2A55))
                    .clickable { session.commands.seekTo((raw + 10_000).coerceIn(0L, dur)) }
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
            // 4.4: rotary helper test area - the only way to hand-verify the gesture.
            RotaryTestArea(colors = colors, fg = fg)
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).background(Color(0xFF2A2A55))
                .clickable { screen = Screen.QUEUE }.padding(9.dp), contentAlignment = Alignment.Center) {
                BasicText("OPEN QUEUE", style = TextStyle(fontWeight = FontWeight.Black, color = Color(0xFF88CCFF)))
            }
        }
    }

    @Composable
    private fun RotaryTestArea(colors: SkinColors, fg: Color) {
        var total by remember { mutableStateOf(0) }
        var last by remember { mutableStateOf(0) }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(150.dp)
            .border(2.dp, colors.accent)
            .rotaryInput(
                onClick = { delta ->
                    last = delta
                    total += delta
                },
            ), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(">> ROTARY <<",
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, color = colors.accent))
                BasicText(if (total >= 0) "ROTARY: +$total" else "ROTARY: $total",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black, color = fg))
                BasicText(if (last >= 0) "last delta: +$last" else "last delta: $last",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = fg))
                Box(Modifier.padding(top = 6.dp).background(Color(0xFF551111))
                    .clickable { total = 0; last = 0 }.padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center) {
                    BasicText("[RESET]", style = TextStyle(fontWeight = FontWeight.Black, color = Color(0xFFFF8888)))
                }
            }
        }
    }

    @Composable
    private fun QueueScreen(session: SkinSession) {
        val pb = session.playback.collectAsState()
        val queue = pb.value.queue
        val colors = SkinTheme.colors
        val bg = colors.background
        val fg = colors.onBackground
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
