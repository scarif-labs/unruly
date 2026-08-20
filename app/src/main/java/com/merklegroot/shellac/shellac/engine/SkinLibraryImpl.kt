/*
 * Copyright (c) 2026 Shellac Project
 * SkinLibraryImpl.kt is part of Shellac.
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
import androidx.paging.PagingData
import com.merklegroot.shellac.music.IndexingState
import com.merklegroot.shellac.music.MusicRepository
import com.merklegroot.shellac.search.SearchEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import org.oxycblt.musikr.IndexingProgress
import org.shellac.skin.LibraryLoadState
import org.shellac.skin.SkinAlbum
import org.shellac.skin.SkinArtist
import org.shellac.skin.SkinGenre
import org.shellac.skin.SkinLibrary
import org.shellac.skin.SkinPlaylist
import org.shellac.skin.SkinSearchResults
import org.shellac.skin.SkinSong
import org.shellac.skin.SkinSort

class SkinLibraryImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val searchEngine: SearchEngine,
) : SkinLibrary {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Ticks whenever the library changes.
     *
     * MUST be shared. As a cold `callbackFlow` this registered a **new**
     * `MusicRepository.UpdateListener` per collector, and since every `albums()` / `songs()` call
     * returns a fresh flow, a recomposing skin churned listeners several times a second:
     * ```
     * D MusicRepositoryImpl: Removing ...SkinLibraryImpl$changes$1$listener$1@f2d83b6
     * D MusicRepositoryImpl: Adding   ...SkinLibraryImpl$changes$1$listener$1@5d5538e
     * ```
     *
     * `shareIn` collapses that to one listener for all collectors. `replay = 1` means a new
     * collector immediately gets the current state instead of waiting for the next change - without
     * it, a skin subscribing after indexing finished would show an empty list forever.
     */
    private val changes: Flow<Unit> =
        callbackFlow {
                val listener =
                    object : MusicRepository.UpdateListener {
                        override fun onMusicChanges(changes: MusicRepository.Changes) {
                            trySend(Unit)
                        }
                    }
                musicRepository.addUpdateListener(listener)
                // Prime it so collectors render whatever is already loaded.
                trySend(Unit)
                awaitClose { musicRepository.removeUpdateListener(listener) }
            }
            .conflate()
            .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    override val loadState: StateFlow<LibraryLoadState> =
        callbackFlow {
                val listener =
                    object : MusicRepository.IndexingListener {
                        override fun onIndexingStateChanged() {
                            trySend(musicRepository.indexingState.toSkinLoadState())
                        }
                    }
                musicRepository.addIndexingListener(listener)
                awaitClose { musicRepository.removeIndexingListener(listener) }
            }
            .stateIn(scope, SharingStarted.Eagerly, musicRepository.indexingState.toSkinLoadState())

    override fun artists(sort: SkinSort): Flow<PagingData<SkinArtist>> =
        changes.map {
            musicRepository.library
                ?.artists
                .orEmpty()
                .sortForSkin(context, sort)
                .map { it.toSkin(context) }
                .paged()
        }

    override fun albums(sort: SkinSort): Flow<PagingData<SkinAlbum>> =
        changes.map {
            musicRepository.library
                ?.albums
                .orEmpty()
                .sortForSkin(context, sort)
                .map { it.toSkin(context) }
                .paged()
        }

    override fun songs(sort: SkinSort): Flow<PagingData<SkinSong>> =
        changes.map {
            musicRepository.library
                ?.songs
                .orEmpty()
                .sortForSkin(context, sort)
                .map { it.toSkin(context) }
                .paged()
        }

    override fun genres(): Flow<PagingData<SkinGenre>> =
        changes.map { musicRepository.library?.genres.orEmpty().map { it.toSkin(context) }.paged() }

    override fun playlists(): Flow<PagingData<SkinPlaylist>> =
        changes.map {
            musicRepository.library?.playlists.orEmpty().map { it.toSkin(context) }.paged()
        }

    override fun albumsByArtist(artistId: String): Flow<PagingData<SkinAlbum>> =
        changes.map {
            val artist =
                musicRepository.library?.artists?.firstOrNull { it.uid.toString() == artistId }
            if (artist == null) {
                emptyList<SkinAlbum>().paged()
            } else {
                val explicitIds = artist.explicitAlbums.map { it.uid }.toSet()
                val albums =
                    (artist.explicitAlbums.map { it to true } +
                            artist.implicitAlbums
                                .filterNot { it.uid in explicitIds }
                                .map { it to false })
                        .sortAlbumsForSkin(context, SkinSort.DEFAULT)
                        .map { (album, explicit) -> album.toSkin(context, explicit) }
                albums.paged()
            }
        }

    override fun songsByAlbum(albumId: String): Flow<List<SkinSong>> =
        changes.map {
            musicRepository.library
                ?.albums
                ?.firstOrNull { it.uid.toString() == albumId }
                ?.songs
                .orEmpty()
                .map { it.toSkin(context) }
        }

    override fun songsByGenre(genreId: String): Flow<PagingData<SkinSong>> =
        changes.map {
            musicRepository.library
                ?.genres
                ?.firstOrNull { it.uid.toString() == genreId }
                ?.songs
                .orEmpty()
                .map { it.toSkin(context) }
                .paged()
        }

    override fun songsByPlaylist(playlistId: String): Flow<List<SkinSong>> =
        changes.map {
            musicRepository.library
                ?.playlists
                ?.firstOrNull { it.uid.toString() == playlistId }
                ?.songs
                .orEmpty()
                .map { it.toSkin(context) }
        }

    override fun search(query: String): Flow<SkinSearchResults> =
        flowOf(query).debounce(250L).mapLatest { currentQuery ->
            val current = musicRepository.library
            val result =
                searchEngine.search(
                    SearchEngine.Items(
                        songs = current?.songs?.takeIf { it.isNotEmpty() },
                        albums = current?.albums?.takeIf { it.isNotEmpty() },
                        artists = current?.artists?.takeIf { it.isNotEmpty() },
                        genres = current?.genres?.takeIf { it.isNotEmpty() },
                        playlists = current?.playlists?.takeIf { it.isNotEmpty() },
                    ),
                    currentQuery,
                )
            SkinSearchResults(
                songs = result.songs.orEmpty().map { it.toSkin(context) },
                albums = result.albums.orEmpty().map { it.toSkin(context) },
                artists = result.artists.orEmpty().map { it.toSkin(context) },
                genres = result.genres.orEmpty().map { it.toSkin(context) },
                playlists = result.playlists.orEmpty().map { it.toSkin(context) },
            )
        }

    override suspend fun findAlbum(albumId: String): SkinAlbum? =
        musicRepository.library
            ?.albums
            ?.firstOrNull { it.uid.toString() == albumId }
            ?.toSkin(context)

    override suspend fun findArtist(artistId: String): SkinArtist? =
        musicRepository.library
            ?.artists
            ?.firstOrNull { it.uid.toString() == artistId }
            ?.toSkin(context)

    private fun <T : Any> List<T>.paged(): PagingData<T> = PagingData.from(this)

    private fun IndexingState?.toSkinLoadState(): LibraryLoadState =
        when (this) {
            null -> LibraryLoadState.Idle
            is IndexingState.Indexing ->
                when (val progress = progress) {
                    is IndexingProgress.Songs ->
                        LibraryLoadState.Scanning(progress.loaded, progress.explored)
                    IndexingProgress.Indeterminate -> LibraryLoadState.Scanning(null, null)
                }
            is IndexingState.Completed ->
                error?.let {
                    LibraryLoadState.Failed(it.message ?: it::class.simpleName ?: "Indexing failed")
                } ?: LibraryLoadState.Ready(musicRepository.library?.empty() ?: true)
        }
}
