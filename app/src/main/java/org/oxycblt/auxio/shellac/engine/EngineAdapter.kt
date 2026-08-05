package org.oxycblt.auxio.shellac.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.oxycblt.auxio.list.ListSettings
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.playback.state.RepeatMode
import org.oxycblt.auxio.search.SearchEngine
import org.shellac.skin.SkinCommands
import org.shellac.skin.SkinLibrary
import org.shellac.skin.SkinPlaybackState
import org.shellac.skin.SkinRepeatMode
import org.shellac.skin.SkinSession

/**
 * The sole composition point between Auxio and Shellac. The [PlaybackViewModel] is supplied when
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
) {
    private val context = context.applicationContext
    private val musicRepository = musicRepository
    private val searchEngine = searchEngine
    private val playbackManager = playbackManager
    private val listSettings = listSettings

    val library: SkinLibrary = SkinLibraryImpl(this.context, musicRepository, searchEngine)

    fun bind(playbackModel: PlaybackViewModel, openAppSettings: () -> Unit): SkinSession {
        val binding = Binding(playbackModel)
        return SkinSession(binding.playback, library, binding.commands, openAppSettings)
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
                    ) { song, isPlaying, positionDs -> Triple(song, isPlaying, positionDs) },
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
            SkinCommandsImpl(musicRepository, playbackModel, playbackManager, listSettings)
    }

    private fun RepeatMode.toSkin(): SkinRepeatMode =
        when (this) {
            RepeatMode.NONE -> SkinRepeatMode.NONE
            RepeatMode.ALL -> SkinRepeatMode.ALL
            RepeatMode.TRACK -> SkinRepeatMode.TRACK
        }
}
