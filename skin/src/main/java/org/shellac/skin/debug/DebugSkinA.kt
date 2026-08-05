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

    private enum class Screen { ALBUMS, NOW_PLAYING }

    // onBackPressed() is a plain method and cannot see a rememberSaveable; a tiny
    // mutableStateOf field on the skin object is fine for a debug skin.
    private var screen by mutableStateOf(Screen.ALBUMS)
    private val bg = Color(0xFFF5F0E8)
    private val fg = Color(0xFF333333)

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
        Column(Modifier.fillMaxSize().background(bg).padding(12.dp)) {
            BasicText("DEBUG A ALBUMS", style = TextStyle(fontSize = 22.sp, color = fg))
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(Color(0xFFCCE5FF))
                .clickable(onClick = session.openAppSettings).padding(8.dp), contentAlignment = Alignment.Center) {
                BasicText("OPEN APP SETTINGS", style = TextStyle(color = Color(0xFF001133)))
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
                                    Row(Modifier.fillMaxWidth().background(Color(0x1A888888))
                                        .clickable { session.commands.playAlbum(album.id); screen = Screen.NOW_PLAYING }
                                        .padding(8.dp)) {
                                        BasicText("${album.title}  --  ${album.artist}", style = TextStyle(fontSize = 16.sp, color = fg))
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
        Column(Modifier.fillMaxSize().background(bg).padding(12.dp)) {
            BasicText("NOW PLAYING (debug-a, raw ms)", style = TextStyle(fontSize = 20.sp, color = fg))
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
            Box(Modifier.fillMaxWidth().padding(top = 12.dp).background(Color(0xFF90EE90))
                .clickable { session.commands.playPause() }.padding(10.dp), contentAlignment = Alignment.Center) {
                BasicText("PLAY / PAUSE", style = TextStyle(color = Color(0xFF002200)))
            }
        }
    }
}
