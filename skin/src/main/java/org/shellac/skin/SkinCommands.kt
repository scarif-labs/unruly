package org.shellac.skin

/**
 * Everything a skin can ask the engine to do.
 *
 * ## How `playX` maps to the engine
 *
 * The engine DOES provide collection-level play methods, taking objects rather than ids:
 * `play(album: Album)`, `play(artist: Artist)`, `play(genre: Genre)`,
 * `play(playlist: Playlist)`, `play(songs: List<Song>)`, plus `playNext` equivalents.
 * So `playAlbum(albumId)` is: resolve id -> `Album` -> `play(album)`. **Prefer these** over
 * hand-rolling "first song + PlaySong.FromAlbum", which risks diverging from Auxio's own
 * shuffle and queue-construction semantics.
 *
 * (An earlier revision of this doc claimed these overloads did not exist. They do.)
 *
 * For song-level playback with a browsing context, the engine uses:
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
 * Here the browsing context is a *parameter*, not a method set — used when a skin plays one
 * song from within an album/artist/genre listing. Either way, all id-to-object resolution
 * lives entirely in the adapter (DECISION D4).
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
