/*
 * Copyright (c) 2026 Shellac Project
 * SkinLyricsSourceImpl.kt is part of Shellac.
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
 
package com.merklegroot.shellac.shellac.engine

import android.content.Context
import com.merklegroot.shellac.music.MusicRepository
import com.merklegroot.shellac.music.resolve
import com.merklegroot.shellac.music.resolveNames
import com.merklegroot.shellac.shellac.engine.lyrics.LrcLibTrack
import com.merklegroot.shellac.shellac.engine.lyrics.LyricsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
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
                if (
                    !preferences.getBoolean(
                        LyricsPreferences.LOOKUP_ENABLED_KEY,
                        LyricsPreferences.DEFAULT_LOOKUP_ENABLED,
                    )
                ) {
                    emit(SkinLyricsState.Disabled)
                    return@flow
                }

                emit(SkinLyricsState.Loading)
                val song =
                    musicRepository.library?.songs?.firstOrNull { it.uid.toString() == songId }
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
