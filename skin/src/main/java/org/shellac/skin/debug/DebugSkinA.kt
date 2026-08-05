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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import org.shellac.skin.AppSkin
import org.shellac.skin.SkinSession
import org.shellac.skin.support.SkinAlbumArt
import org.shellac.skin.support.SkinBackStack
import org.shellac.skin.support.SkinColors
import org.shellac.skin.support.SkinLibraryGate
import org.shellac.skin.support.SkinTheme
import org.shellac.skin.support.SkinTypography
import org.shellac.skin.support.rememberInterpolatedPosition
import org.shellac.skin.support.rotaryInput

/** Deliberately ugly light-ish debug skin used to exercise the skin contract. */
class DebugSkinA : AppSkin {
    override val id = "debug-a"
    override val displayName = "Debug A"
    override val supportsEdgeToEdge = false

    private enum class Screen { ALBUMS, NOW_PLAYING, QUEUE }

    // 4.5: DebugSkinA demonstrates the (optional) back-stack primitive. onBackPressed() is a
    // plain method and cannot see a rememberSaveable, so the stack lives in a field on the
    // skin object, not only in composition. DebugSkinB intentionally stays on its flat enum
    // to show the non-forced path.
    private val backStack = SkinBackStack(Screen.ALBUMS)

    // 4.3: DebugSkinA styles itself with a light palette; darkSystemBars = false.
    private val colors =
        SkinColors(
            background = Color(0xFFF5F0E8),
            surface = Color(0xFFFFFFFF),
            onBackground = Color(0xFF333333),
            onSurface = Color(0xFF49454F),
            accent = Color(0xFF6750A4),
            onAccent = Color(0xFFFFFFFF),
        )
    private val typography =
        SkinTypography(
            title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            body = TextStyle(fontSize = 16.sp),
            label = TextStyle(fontSize = 12.sp),
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

    override fun onBackPressed(): Boolean = backStack.pop()

    @Composable
    override fun Render(session: SkinSession) {
        SkinTheme(
            colors = colors,
            typography = typography,
            darkSystemBars = false,
        ) {
            when (backStack.current) {
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
        val titleStyle = SkinTheme.typography.title.copy(color = fg)
        Column(Modifier.fillMaxSize().background(bg).padding(12.dp)) {
            BasicText("DEBUG A ALBUMS", style = titleStyle)
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)
                .background(colors.accent.copy(alpha = 0.22f))
                .clickable(onClick = session.openAppSettings).padding(8.dp), contentAlignment = Alignment.Center) {
                BasicText("OPEN APP SETTINGS", style = TextStyle(color = colors.onAccent))
            }
            Box(Modifier.fillMaxWidth().padding(bottom = 6.dp).background(Color(0xFFF5D0A8))
                .clickable { session.commands.shuffleAll() }.padding(8.dp), contentAlignment = Alignment.Center) {
                BasicText("SHUFFLE ALL", style = TextStyle(fontWeight = FontWeight.Bold, color = Color(0xFF4A2A00)))
            }
            // 4.6: replace the hand-rolled when(loadState) with the library gate; each branch
            // is styled by this skin.
            SkinLibraryGate(
                loadState = load.value,
                scanning = { loaded, explored ->
                    BasicText("SCANNING... loaded=$loaded explored=$explored", style = TextStyle(color = fg))
                },
                failed = { message ->
                    BasicText("LOAD FAILED: $message", style = TextStyle(color = Color(0xFF880000)))
                },
                empty = {
                    BasicText("Library is EMPTY.", style = TextStyle(color = fg))
                },
                ready = {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(albums.itemCount) { i ->
                            val album = albums[i]
                            if (album != null) {
                                val isExpanded = expandedId == album.id
                                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(Color(0x1A888888))) {
                                    Row(Modifier.fillMaxWidth()
                                        .clickable {
                                            session.commands.playAlbum(album.id)
                                            backStack.push(Screen.NOW_PLAYING)
                                        }
                                        .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        // 4.2: small album art per row with this skin's fallback
                                        // (a cream box with the title's first letter).
                                        SkinAlbumArt(
                                            uri = album.albumArtUri,
                                            contentDescription = album.title,
                                            modifier = Modifier.size(48.dp),
                                        ) {
                                            Box(Modifier.size(48.dp).background(Color(0xFFE3DCC8)),
                                                contentAlignment = Alignment.Center) {
                                                BasicText(
                                                    album.title.firstOrNull()?.uppercase() ?: "?",
                                                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF444444)))
                                            }
                                        }
                                        BasicText("  ${album.title}  --  ${album.artist}",
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
                                        val songsFlow = remember(album.id) { session.library.songsByAlbum(album.id) }
                                        val songs by songsFlow.collectAsState(initial = emptyList())
                                        Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp)) {
                                            BasicText("${songs.size} songs loaded.",
                                                style = TextStyle(fontSize = 12.sp, color = fg))
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
        Column(Modifier.fillMaxSize().background(bg).padding(12.dp)) {
            BasicText("NOW PLAYING (debug-a)", style = SkinTheme.typography.title.copy(color = fg))
            if (song == null) {
                BasicText("No song loaded.", style = TextStyle(color = fg))
            } else {
                // 4.2: large album art with this skin's fallback (cream box + first letter).
                SkinAlbumArt(
                    uri = song.albumArtUri,
                    contentDescription = song.album,
                    modifier = Modifier.size(200.dp),
                ) {
                    Box(Modifier.size(200.dp).background(Color(0xFFE3DCC8)), contentAlignment = Alignment.Center) {
                        BasicText(
                            song.title.firstOrNull()?.uppercase() ?: "?",
                            style = TextStyle(fontSize = 80.sp, fontWeight = FontWeight.Bold, color = Color(0xFF444444)))
                    }
                }
                BasicText("Title  : ${song.title}", style = TextStyle(fontSize = 16.sp, color = fg))
                BasicText("Artist : ${song.artist}", style = TextStyle(fontSize = 16.sp, color = fg))
                BasicText("Album  : ${song.album}", style = TextStyle(fontSize = 16.sp, color = fg))
                BasicText("Playing: ${pb.value.isPlaying}",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = if (pb.value.isPlaying) Color(0xFF006600) else Color(0xFFAA0000)))
                // 4.1: show BOTH raw (10 Hz engine) and interpolated (frame clock) side by
                // side so the difference and any seek resync are obvious.
                BasicText("raw    ${fmt(raw)}    (10 Hz engine)", style = TextStyle(fontSize = 15.sp, color = fg))
                BasicText("smooth ${fmtTenths(smooth)}  (interpolated)", style = TextStyle(fontSize = 15.sp, color = fg))
                // 4.1: progress bar driven by the interpolated value.
                val progress = if (dur > 0L) (smooth.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(10.dp)
                    .background(colors.surface).border(1.dp, colors.accent)) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.accent))
                }
            }
            BasicText("${fmt(raw)} / ${fmt(dur)}",
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
                    .clickable { session.commands.seekTo((raw - 10_000).coerceIn(0L, dur)) }
                    .padding(8.dp), contentAlignment = Alignment.Center) {
                    BasicText("-10s", style = TextStyle(fontWeight = FontWeight.Bold, color = fg))
                }
                Box(Modifier.weight(1f).padding(horizontal = 4.dp).background(Color(0xFFD8C8E8))
                    .clickable { session.commands.seekTo((raw + 10_000).coerceIn(0L, dur)) }
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
            // 4.4: rotary helper test area - the only way to hand-verify the gesture.
            RotaryTestArea(colors = colors, fg = fg)
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).background(Color(0xFFB8ECC8))
                .clickable { backStack.push(Screen.QUEUE) }.padding(8.dp), contentAlignment = Alignment.Center) {
                BasicText("OPEN QUEUE", style = TextStyle(fontWeight = FontWeight.Bold, color = Color(0xFF003300)))
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
                BasicText(if (total >= 0) "ROTARY: +$total" else "ROTARY: $total",
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = fg))
                BasicText(if (last >= 0) "last delta: +$last" else "last delta: $last",
                    style = TextStyle(fontSize = 14.sp, color = fg))
                Box(Modifier.padding(top = 6.dp).background(Color(0xFFFFD0D0))
                    .clickable { total = 0; last = 0 }.padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center) {
                    BasicText("RESET", style = TextStyle(color = Color(0xFF880000)))
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
        Column(Modifier.fillMaxSize().background(bg).padding(12.dp)) {
            BasicText("QUEUE (debug-a)", style = TextStyle(fontSize = 20.sp, color = fg))
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color(0xFFCCE5FF))
                .clickable { backStack.pop() }.padding(8.dp), contentAlignment = Alignment.Center) {
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
