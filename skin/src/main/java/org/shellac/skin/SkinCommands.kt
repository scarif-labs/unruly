/*
 * Copyright (c) 2026 Shellac Project
 * SkinCommands.kt is part of Shellac.
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

/**
 * Everything a skin can ask the engine to do.
 *
 * ## How `playX` maps to the engine
 *
 * The engine DOES provide collection-level play methods, taking objects rather than ids:
 * `play(album: Album)`, `play(artist: Artist)`, `play(genre: Genre)`, `play(playlist: Playlist)`,
 * `play(songs: List<Song>)`, plus `playNext` equivalents. So `playAlbum(albumId)` is: resolve id ->
 * `Album` -> `play(album)`. **Prefer these** over hand-rolling "first song + PlaySong.FromAlbum",
 * which risks diverging from Auxio's own shuffle and queue-construction semantics.
 *
 * (An earlier revision of this doc claimed these overloads did not exist. They do.)
 *
 * For song-level playback with a browsing context, the engine uses:
 * ```kotlin
 * PlaybackViewModel.play(song: Song, with: PlaySong)
 *
 * sealed interface PlaySong {
 *     data object FromAll
 *     data object FromAlbum
 *     data class  FromArtist(val which: Artist?)
 *     data class  FromGenre(val which: Genre?)
 *     data class  FromPlaylist(val which: Playlist)
 *     data object ByItself
 * }
 * ```
 *
 * Here the browsing context is a *parameter*, not a method set — used when a skin plays one song
 * from within an album/artist/genre listing. Either way, all id-to-object resolution lives entirely
 * in the adapter (DECISION D4).
 *
 * ## Empty collections
 *
 * Every `playX` is a **no-op** when the target is empty or the id is unknown. It does not throw. A
 * skin driving a wheel or remote cannot usefully handle an exception mid-gesture, and silently
 * doing nothing matches what a physical player does.
 */
interface SkinCommands {
    fun playPause()

    fun next()

    fun previous()

    /** Position in **milliseconds**. The adapter converts to Auxio's deciseconds. */
    fun seekTo(positionMs: Long)

    fun toggleShuffle()

    fun cycleRepeatMode()

    fun setRepeatMode(mode: SkinRepeatMode)

    /** Plays the album, starting at [startIndex] in its track order. */
    fun playAlbum(albumId: String, startIndex: Int = 0)

    fun playArtist(artistId: String)

    fun playGenre(genreId: String)

    fun playPlaylist(playlistId: String)

    /** Plays an arbitrary list, e.g. search results. Maps to `PlaySong.ByItself`. */
    fun playSongs(songs: List<SkinSong>, startIndex: Int = 0)

    // ---- Play one song *within* a browsing context -------------------------------------
    //
    // These exist because of a gap the first real skin (Touch Wheel, Phase 5e) hit head-on.
    //
    // `playSongs(list, startIndex)` needs the whole collection materialised. But
    // `SkinLibrary.songs()`, `songsByGenre()` and `albumsByArtist()` return `PagingData`,
    // which by construction never hands you a complete list. To play track #500 of a
    // 20,000-song library the skin was forced to page the entire library into memory just
    // to build the argument - defeating paging, and failing buildspec Phase 6.2 outright.
    //
    // The engine never had this problem: `play(song, PlaySong.FromGenre(genre))` takes one
    // song plus a context. These methods expose that directly, so a skin plays what it can
    // see (one id) without materialising what it cannot. Prefer them over `playSongs` for
    // anything backed by a paged flow; `playSongs` remains right for genuinely ad-hoc lists
    // like search results, which are small and already in hand.
    //
    // Each is a no-op when either id is unknown, per the empty-collection rule above.

    /** Plays [songId] with the whole library as its queue context. `PlaySong.FromAll`. */
    fun playSongFromAll(songId: String)

    /** Plays [songId] with [albumId]'s track list as its queue context. */
    fun playSongFromAlbum(songId: String, albumId: String)

    /** Plays [songId] with [artistId]'s songs as its queue context. */
    fun playSongFromArtist(songId: String, artistId: String)

    /** Plays [songId] with [genreId]'s songs as its queue context. */
    fun playSongFromGenre(songId: String, genreId: String)

    /** Plays [songId] with [playlistId]'s track list as its queue context. */
    fun playSongFromPlaylist(songId: String, playlistId: String)

    /** Shuffles the entire library. Maps to `PlaySong.FromAll` with shuffle enabled. */
    fun shuffleAll()

    fun playQueueItem(index: Int)

    fun addToQueue(songs: List<SkinSong>)

    fun playNext(songs: List<SkinSong>)

    fun removeFromQueue(index: Int)

    fun moveInQueue(from: Int, to: Int)
}
