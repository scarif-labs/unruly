package org.oxycblt.auxio.shellac.spike

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.oxycblt.auxio.home.HomeViewModel
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.playback.PlaybackViewModel

@Composable
fun ShellacInteropSpike(playbackModel: PlaybackViewModel, homeModel: HomeViewModel) {
    val song by playbackModel.song.collectAsStateWithLifecycle()
    val isPlaying by playbackModel.isPlaying.collectAsStateWithLifecycle()
    val albums by homeModel.albumList.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = Modifier.padding(8.dp)) {
        Text("COMPOSE ALIVE")
        Text(song?.name?.resolve(context) ?: "(nothing playing)")
        Button(onClick = { playbackModel.togglePlaying() }) {
            Text(if (isPlaying) "Pause" else "Play")
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            items(albums) { album ->
                Text(album.name.resolve(context))
            }
        }
    }
}
