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

    private enum class Screen { ALBUMS, NOW_PLAYING }

    // onBackPressed() is a plain method and cannot see a rememberSaveable; a tiny
    // mutableStateOf field on the skin object is fine for a debug skin.
    private var screen by mutableStateOf(Screen.ALBUMS)
    private val bg = Color(0xFF0D0D12)
    private val fg = Color(0xFFCCFFCC)

    override fun onBackPressed(): Boolean {
        if (screen == Screen.NOW_PLAYING) { screen = Screen.ALBUMS; return true }
        return false
    }

    @Composable
    override fun Render(session: SkinSession) {
        when (screen) {
            Screen.ALBUMS -> AlbumsScreen(session)
            Screen.NOW_PLAYING -> NowPlayingScreen(session)
        }
    }

    @Composable
    private fun AlbumsScreen(session: SkinSession) {
        val albums = session.library.albums().collectAsLazyPagingItems()
        val load = session.library.loadState.collectAsState()
        Column(Modifier.fillMaxSize().background(bg).padding(14.dp)) {
            BasicText("*** DEBUG B ALBUMS ***",
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic, color = fg))
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(Color(0xFF7A0044))
                .clickable(onClick = session.openAppSettings).padding(9.dp), contentAlignment = Alignment.Center) {
                BasicText("SETTINGS (app)", style = TextStyle(fontWeight = FontWeight.Bold, color = Color(0xFFFF88DD)))
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
                                    Row(Modifier.fillMaxWidth().background(Color(0xFF2A2A33))
                                        .clickable { session.commands.playAlbum(album.id); screen = Screen.NOW_PLAYING }
                                        .padding(10.dp)) {
                                        BasicText("$i. ${album.title}",
                                            style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = fg))
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
            Box(Modifier.fillMaxWidth().padding(top = 12.dp).background(Color(0xFF8844AA))
                .clickable { session.commands.playPause() }.padding(11.dp), contentAlignment = Alignment.Center) {
                BasicText("TOGGLE PLAY / PAUSE",
                    style = TextStyle(fontWeight = FontWeight.Black, color = Color(0xFFFFDDFF)))
            }
        }
    }
}

