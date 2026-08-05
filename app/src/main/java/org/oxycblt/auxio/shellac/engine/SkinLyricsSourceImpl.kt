package org.oxycblt.auxio.shellac.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.music.resolveNames
import org.oxycblt.auxio.shellac.engine.lyrics.LrcLibTrack
import org.oxycblt.auxio.shellac.engine.lyrics.LyricsRepository
import org.shellac.skin.SkinLyricsSource
import org.shellac.skin.SkinLyricsState

@Singleton
class SkinLyricsSourceImpl
@Inject
constructor(
    @ApplicationContext context: Context,
    private val musicRepository: MusicRepository,
    private val lyricsRepository: LyricsRepository,
) : SkinLyricsSource {
    private val context = context.applicationContext
    private val preferences =
        this.context.getSharedPreferences(LyricsPreferences.PREFS_NAME, Context.MODE_PRIVATE)

    override fun lyrics(songId: String): Flow<SkinLyricsState> =
        flow {
            if (!preferences.getBoolean(
                    LyricsPreferences.LOOKUP_ENABLED_KEY,
                    LyricsPreferences.DEFAULT_LOOKUP_ENABLED,
                )
            ) {
                emit(SkinLyricsState.Disabled)
                return@flow
            }

            emit(SkinLyricsState.Loading)
            val song = musicRepository.library?.songs?.firstOrNull { it.uid.toString() == songId }
            if (song == null) {
                emit(SkinLyricsState.NoTrack)
                return@flow
            }

            emit(
                lyricsRepository.lookup(
                    songId = songId,
                    track =
                        LrcLibTrack(
                            artist = song.artists.resolveNames(context),
                            title = song.name.resolve(context),
                            album = song.album.name.resolve(context),
                            durationMs = song.durationMs,
                        ),
                )
            )
        }
            .catch { emit(SkinLyricsState.Failed("Could not load lyrics")) }
}
