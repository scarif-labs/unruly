/*
 * Copyright (c) 2026 Unruly Project
 * EngineAdapter.kt is part of Unruly.
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
 
package com.merklegroot.unruly.unruly.engine

import android.content.Context
import com.merklegroot.unruly.list.ListSettings
import com.merklegroot.unruly.music.MusicRepository
import com.merklegroot.unruly.playback.PlaybackViewModel
import com.merklegroot.unruly.playback.state.PlaybackStateManager
import com.merklegroot.unruly.playback.state.RepeatMode
import com.merklegroot.unruly.search.SearchEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.unruly.skin.SkinCommands
import org.unruly.skin.SkinLibrary
import org.unruly.skin.SkinPlaybackState
import org.unruly.skin.SkinRepeatMode
import org.unruly.skin.SkinSession

/**
 * The sole composition point between Unruly and Unruly. The [PlaybackViewModel] is supplied when
 * the host binds the adapter because it is an Activity-scoped Hilt ViewModel.
 */
class EngineAdapter
@Inject
constructor(
    @ApplicationContext context: Context,
    musicRepository: MusicRepository,
    searchEngine: SearchEngine,
    playbackManager: PlaybackStateManager,
    listSettings: ListSettings,
    private val lyrics: SkinLyricsSourceImpl,
) {
    private val context = context.applicationContext
    private val musicRepository = musicRepository
    private val searchEngine = searchEngine
    private val playbackManager = playbackManager
    private val listSettings = listSettings

    val library: SkinLibrary = SkinLibraryImpl(this.context, musicRepository, searchEngine)

    fun bind(playbackModel: PlaybackViewModel, openAppSettings: () -> Unit): SkinSession {
        val binding = Binding(playbackModel)
        return SkinSession(binding.playback, library, binding.commands, lyrics, openAppSettings)
    }

    inner class Binding internal constructor(private val playbackModel: PlaybackViewModel) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // NOTE: kotlinx.coroutines `combine` only has typed overloads for up to FIVE flows.
        // Passing six falls through to the vararg `Array<Any?>` form, and every lambda
        // parameter then fails to infer. Nest the combines instead of widening the arity.
        val playback: StateFlow<SkinPlaybackState> =
            combine(
                    combine(
                        playbackModel.song,
                        playbackModel.isPlaying,
                        playbackModel.positionDs,
                    ) { song, isPlaying, positionDs ->
                        Triple(song, isPlaying, positionDs)
                    },
                    combine(playbackModel.repeatMode, playbackModel.isShuffled) {
                        repeatMode,
                        isShuffled ->
                        repeatMode to isShuffled
                    },
                    playbackModel.pagerQueue,
                ) { (song, isPlaying, positionDs), (repeatMode, isShuffled), pagerQueue ->
                    val queue = pagerQueue.queue.map { it.toSkin(context) }
                    SkinPlaybackState(
                        song = song?.toSkin(context),
                        isPlaying = isPlaying,
                        positionMs = positionDs * 100L,
                        durationMs = song?.durationMs ?: 0L,
                        queue = queue,
                        queueIndex = pagerQueue.index.takeIf { it in queue.indices } ?: -1,
                        repeatMode = repeatMode.toSkin(),
                        isShuffled = isShuffled,
                    )
                }
                .stateIn(scope, SharingStarted.Eagerly, SkinPlaybackState.EMPTY)

        val commands: SkinCommands =
            SkinCommandsImpl(
                context,
                scope,
                musicRepository,
                playbackModel,
                playbackManager,
                listSettings,
            )
    }

    private fun RepeatMode.toSkin(): SkinRepeatMode =
        when (this) {
            RepeatMode.NONE -> SkinRepeatMode.NONE
            RepeatMode.ALL -> SkinRepeatMode.ALL
            RepeatMode.TRACK -> SkinRepeatMode.TRACK
        }
}
