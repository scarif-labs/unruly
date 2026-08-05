package org.oxycblt.auxio.shellac.engine

import org.oxycblt.auxio.list.ListSettings
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.playback.PlaySong
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.playback.state.RepeatMode
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import org.shellac.skin.SkinCommands
import org.shellac.skin.SkinRepeatMode
import org.shellac.skin.SkinSong

class SkinCommandsImpl(
    private val musicRepository: MusicRepository,
    private val playbackModel: PlaybackViewModel,
    private val playbackManager: PlaybackStateManager,
    private val listSettings: ListSettings,
) : SkinCommands {
    override fun playPause() = playbackModel.togglePlaying()

    override fun next() = playbackModel.next()

    override fun previous() = playbackModel.prev()

    override fun seekTo(positionMs: Long) = playbackModel.seekTo(positionMs / 100L)

    override fun toggleShuffle() = playbackModel.toggleShuffled()

    override fun cycleRepeatMode() = playbackModel.toggleRepeatMode()

    // PlaybackStateManager exposes a direct setter (PlaybackStateManager.kt:227). The
    // previous implementation cycled toggleRepeatMode() until it matched, which emitted
    // intermediate states to every listener and would spin forever if the enum order ever
    // changed.
    override fun setRepeatMode(mode: SkinRepeatMode) = playbackManager.repeatMode(mode.toAuxio())

    override fun playAlbum(albumId: String, startIndex: Int) {
        val album = findAlbum(albumId) ?: return
        val song = listSettings.albumSongSort.songs(album.songs).getOrNull(startIndex) ?: return
        playbackModel.play(song, PlaySong.FromAlbum)
    }

    // These use Auxio's own collection-level overloads (PlaybackViewModel.kt:356/376/396)
    // rather than hand-rolling "first song + PlaySong.FromX". Auxio applies its own sort,
    // shuffle and queue-construction semantics inside them; reimplementing that here would
    // silently diverge from how the app itself starts playback.
    override fun playArtist(artistId: String) {
        findArtist(artistId)?.let(playbackModel::play)
    }

    override fun playGenre(genreId: String) {
        findGenre(genreId)?.let(playbackModel::play)
    }

    override fun playPlaylist(playlistId: String) {
        findPlaylist(playlistId)?.let(playbackModel::play)
    }

    // play(songs: List<Song>) (PlaybackViewModel.kt:416) plays a list atomically. The
    // previous implementation played the first song then appended the rest via addToQueue,
    // which briefly exposed a one-item queue and produced a different shuffle result.
    override fun playSongs(songs: List<SkinSong>, startIndex: Int) {
        val resolved = findSongs(songs)
        if (resolved.isEmpty()) return
        val startAt = startIndex.coerceIn(0, resolved.lastIndex)
        playbackModel.play(resolved.drop(startAt) + resolved.take(startAt))
    }

    override fun shuffleAll() = playbackModel.shuffleAll()

    override fun playQueueItem(index: Int) {
        if (index in playbackModel.pagerQueue.value.queue.indices) playbackManager.goto(index)
    }

    override fun addToQueue(songs: List<SkinSong>) {
        findSongs(songs).takeIf { it.isNotEmpty() }?.let { playbackModel.addToQueue(it) }
    }

    override fun playNext(songs: List<SkinSong>) {
        findSongs(songs).takeIf { it.isNotEmpty() }?.let { playbackModel.playNext(it) }
    }

    override fun removeFromQueue(index: Int) {
        if (index in playbackModel.pagerQueue.value.queue.indices) {
            playbackManager.removeQueueItem(index)
        }
    }

    override fun moveInQueue(from: Int, to: Int) {
        val indices = playbackModel.pagerQueue.value.queue.indices
        if (from in indices && to in indices) playbackManager.moveQueueItem(from, to)
    }

    private fun findSong(song: SkinSong): Song? =
        musicRepository.library?.songs?.firstOrNull { it.uid.toString() == song.id }

    /**
     * Resolves a batch in one pass.
     *
     * Deliberately NOT `songs.mapNotNull(::findSong)` — that is a linear scan of the whole
     * library per element, i.e. O(queue x library). Adding a 500-track queue against the
     * 20,000-track library of buildspec Phase 6.2 would be 10 million comparisons. Building
     * one index and looking up against it keeps it linear.
     */
    private fun findSongs(songs: List<SkinSong>): List<Song> {
        if (songs.isEmpty()) return emptyList()
        val wanted = songs.mapTo(HashSet()) { it.id }
        val index =
            musicRepository.library
                ?.songs
                ?.asSequence()
                ?.map { it.uid.toString() to it }
                ?.filter { it.first in wanted }
                ?.toMap() ?: return emptyList()
        return songs.mapNotNull { index[it.id] }
    }

    private fun findAlbum(id: String): Album? =
        musicRepository.library?.albums?.firstOrNull { it.uid.toString() == id }

    private fun findArtist(id: String): Artist? =
        musicRepository.library?.artists?.firstOrNull { it.uid.toString() == id }

    private fun findGenre(id: String): Genre? =
        musicRepository.library?.genres?.firstOrNull { it.uid.toString() == id }

    private fun findPlaylist(id: String): Playlist? =
        musicRepository.library?.playlists?.firstOrNull { it.uid.toString() == id }

    private fun SkinRepeatMode.toAuxio(): RepeatMode =
        when (this) {
            SkinRepeatMode.NONE -> RepeatMode.NONE
            SkinRepeatMode.ALL -> RepeatMode.ALL
            SkinRepeatMode.TRACK -> RepeatMode.TRACK
        }
}
