/*
 * Copyright (c) 2026 Shellac Project
 * SkinLibrary.kt is part of Shellac.
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
 
package org.shellac.skin

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** How a skin wants a listing ordered. */
data class SkinSort(val by: By, val ascending: Boolean = true) {
    enum class By {
        NAME,
        ARTIST,
        ALBUM,
        YEAR,
        DURATION,
        DATE_ADDED,
    }

    companion object {
        val DEFAULT = SkinSort(By.NAME, ascending = true)
    }
}

/**
 * Library indexing state.
 *
 * Mirrors Auxio's `IndexingState`, with one deliberate addition. Auxio has **no explicit Ready
 * state** - success is `Completed(error = null)`, which is easy to misread as an error case.
 * [Ready] and [Failed] split that apart so a skin cannot accidentally treat a successful load as a
 * failure.
 */
sealed interface LibraryLoadState {
    /** No load has been attempted yet. */
    data object Idle : LibraryLoadState

    /**
     * Indexing in progress.
     *
     * @param loaded Number of audio files whose tags have been parsed so far.
     * @param explored Number of files discovered so far. Always >= [loaded]; the gap is the parse
     *   backlog. Useful as a progress denominator, but it grows during the scan, so `loaded /
     *   explored` moves backwards as often as forwards — show it as a ratio only if you are
     *   comfortable with that.
     *
     * Both are null while the engine reports indeterminate progress, which it does during its final
     * graph-building pass. Render an indeterminate state, not "null".
     */
    data class Scanning(val loaded: Int?, val explored: Int?) : LibraryLoadState

    /** Indexing finished successfully. [isEmpty] distinguishes "no music" from "not loaded". */
    data class Ready(val isEmpty: Boolean) : LibraryLoadState

    /** Indexing finished with an error. */
    data class Failed(val message: String) : LibraryLoadState
}

data class SkinSearchResults(
    val songs: List<SkinSong> = emptyList(),
    val albums: List<SkinAlbum> = emptyList(),
    val artists: List<SkinArtist> = emptyList(),
    val genres: List<SkinGenre> = emptyList(),
    val playlists: List<SkinPlaylist> = emptyList(),
) {
    val isEmpty: Boolean
        get() =
            songs.isEmpty() &&
                albums.isEmpty() &&
                artists.isEmpty() &&
                genres.isEmpty() &&
                playlists.isEmpty()
}

/**
 * Read access to the music library.
 *
 * ## On paging - read this before changing it
 *
 * Top-level listings return `Flow<PagingData<T>>`. The buildspec justifies this as preventing a
 * skin from requesting "all songs" as a materialised list.
 *
 * **That rationale does not hold, and it is important not to believe it.** Auxio's
 * `org.oxycblt.musikr.Library` exposes `songs`, `albums`, `artists` and the rest as plain in-memory
 * `Collection`s. The entire library is already materialised in RAM by the engine before a skin sees
 * any of it. Paging here **cannot** reduce engine memory.
 *
 * It is kept regardless, for reasons that are real:
 * - it bounds **UI/adapter** memory, which is what Phase 3's "scroll a large list, memory stays
 *   flat" test actually measures;
 * - it stops skins materialising their own second copy of the library;
 * - it preserves the option of a genuinely lazy source if musikr ever gains one, without a breaking
 *   contract change.
 *
 * Relationship accessors that are inherently small ([songsByAlbum], [songsByPlaylist]) return plain
 * lists - paging a 12-track album is pure ceremony.
 */
interface SkinLibrary {
    /**
     * Whole-library indexing state.
     *
     * **Precedence:** this outranks `LazyPagingItems.loadState`. Paging's own load state describes
     * fetching one page from an already-built library; this describes whether there is a library at
     * all. Gate the screen on this, and use paging's state only for in-list append spinners. Both
     * report "loading" at startup, so a skin that keys off paging alone will show an empty list
     * rather than a scanning indicator.
     */
    val loadState: StateFlow<LibraryLoadState>

    fun artists(sort: SkinSort = SkinSort.DEFAULT): Flow<PagingData<SkinArtist>>

    fun albums(sort: SkinSort = SkinSort.DEFAULT): Flow<PagingData<SkinAlbum>>

    fun songs(sort: SkinSort = SkinSort.DEFAULT): Flow<PagingData<SkinSong>>

    fun genres(): Flow<PagingData<SkinGenre>>

    fun playlists(): Flow<PagingData<SkinPlaylist>>

    /**
     * DECISION D2: returns both explicit and implicit albums, unioned. Each result carries
     * [SkinAlbum.isExplicitForArtist] so a skin may separate them.
     */
    fun albumsByArtist(artistId: String): Flow<PagingData<SkinAlbum>>

    fun songsByAlbum(albumId: String): Flow<List<SkinSong>>

    fun songsByGenre(genreId: String): Flow<PagingData<SkinSong>>

    fun songsByPlaylist(playlistId: String): Flow<List<SkinSong>>

    /**
     * Search across the library.
     *
     * Auxio's `SearchEngine.search(items, query)` filters a collection supplied by the caller
     * rather than querying a store, so the adapter is responsible for feeding it the library.
     * Implementations should debounce; a skin may call this per keystroke.
     */
    fun search(query: String): Flow<SkinSearchResults>

    /** Single-item lookups, for restoring navigation state after process death. */
    suspend fun findAlbum(albumId: String): SkinAlbum?

    suspend fun findArtist(artistId: String): SkinArtist?
}
