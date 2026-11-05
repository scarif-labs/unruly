/*
 * Copyright (c) 2026 Unruly Project
 * UnrulyInteropSpike.kt is part of Unruly.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package com.merklegroot.unruly.unruly.spike

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
import com.merklegroot.unruly.home.HomeViewModel
import com.merklegroot.unruly.music.resolve
import com.merklegroot.unruly.playback.PlaybackViewModel

@Composable
fun UnrulyInteropSpike(playbackModel: PlaybackViewModel, homeModel: HomeViewModel) {
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
            items(albums) { album -> Text(album.name.resolve(context)) }
        }
    }
}
