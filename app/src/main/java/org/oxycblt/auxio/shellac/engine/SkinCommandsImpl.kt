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

    override fun setRepeatMode(mode: SkinRepeatMode) {
        val target = mode.toAuxio()
        var current = playbackModel.repeatMode.value
        while (current != target) {
            playbackModel.toggleRepeatMode()
            current = current.increment()
        }
    }

    override fun playAlbum(albumId: String, startIndex: Int) {
        val album = findAlbum(albumId) ?: return
        val song = listSettings.albumSongSort.songs(album.songs).getOrNull(startIndex) ?: return
        playbackModel.play(song, PlaySong.FromAlbum)
    }

    override fun playArtist(artistId: String) {
        val artist = findArtist(artistId) ?: return
        val song = listSettings.artistSongSort.songs(artist.songs).firstOrNull() ?: return
        playbackModel.play(song, PlaySong.FromArtist(artist))
    }

    override fun playGenre(genreId: String) {
        val genre = findGenre(genreId) ?: return
        val song = listSettings.genreSongSort.songs(genre.songs).firstOrNull() ?: return
        playbackModel.play(song, PlaySong.FromGenre(genre))
    }

    override fun playPlaylist(playlistId: String) {
        val playlist = findPlaylist(playlistId) ?: return
        val song = playlist.songs.firstOrNull() ?: return
        playbackModel.play(song, PlaySong.FromPlaylist(playlist))
    }

    override fun playSongs(songs: List<SkinSong>, startIndex: Int) {
        val requestedStart = songs.getOrNull(startIndex)?.let(::findSong) ?: return
        val resolved = songs.mapNotNull(::findSong)
        if (resolved.isEmpty()) return

        val startAt = resolved.indexOf(requestedStart)
        if (startAt < 0) return
        val ordered = resolved.drop(startAt) + resolved.take(startAt)
        // PlaySong.ByItself supplies the required starting-song context; append the remainder
        // through the real queue API so the contract's arbitrary-list command remains a list play.
        playbackModel.play(ordered.first(), PlaySong.ByItself)
        if (ordered.size > 1) playbackModel.addToQueue(ordered.drop(1))
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

    private fun findSongs(songs: List<SkinSong>): List<Song> = songs.mapNotNull(::findSong)

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
