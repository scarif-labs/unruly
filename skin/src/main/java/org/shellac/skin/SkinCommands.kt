package org.shellac.skin

/**
 * Everything a skin can ask the engine to do.
 *
 * ## Why the `playX` methods are not a thin wrapper
 *
 * The buildspec assumes the engine offers `playAlbum(albumId)`, `playArtist(artistId)` and
 * so on. **It does not.** Auxio has exactly one entry point:
 *
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
 * Playback context is a *parameter*, not a method set. Each command below therefore
 * requires the adapter to resolve the id, pick a starting song, and call `play(song, ...)`
 * with the matching context. That translation is real work and lives entirely in the
 * adapter - see DECISION D4.
 *
 * ## Empty collections
 *
 * Every `playX` is a **no-op** when the target is empty or the id is unknown. It does not
 * throw. A skin driving a wheel or remote cannot usefully handle an exception mid-gesture,
 * and silently doing nothing matches what a physical player does.
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

    /** Shuffles the entire library. Maps to `PlaySong.FromAll` with shuffle enabled. */
    fun shuffleAll()

    fun playQueueItem(index: Int)

    fun addToQueue(songs: List<SkinSong>)

    fun playNext(songs: List<SkinSong>)

    fun removeFromQueue(index: Int)

    fun moveInQueue(from: Int, to: Int)
}
