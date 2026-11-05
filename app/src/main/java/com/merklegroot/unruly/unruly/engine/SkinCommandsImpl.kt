/*
 * Copyright (c) 2026 Unruly Project
 * SkinCommandsImpl.kt is part of Unruly.
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
 
package com.merklegroot.unruly.unruly.engine

import android.content.Context
import com.merklegroot.unruly.R
import com.merklegroot.unruly.list.ListSettings
import com.merklegroot.unruly.music.MusicRepository
import com.merklegroot.unruly.music.resolve
import com.merklegroot.unruly.playback.PlaySong
import com.merklegroot.unruly.playback.PlaybackViewModel
import com.merklegroot.unruly.playback.state.PlaybackStateManager
import com.merklegroot.unruly.playback.state.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import org.unruly.skin.SkinCommands
import org.unruly.skin.SkinRepeatMode
import org.unruly.skin.SkinSong

class SkinCommandsImpl(
    private val context: Context,
    private val scope: CoroutineScope,
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
    override fun setRepeatMode(mode: SkinRepeatMode) = playbackManager.repeatMode(mode.toUnruly())

    override fun playAlbum(albumId: String, startIndex: Int) {
        val album = findAlbum(albumId) ?: return
        val song = listSettings.albumSongSort.songs(album.songs).getOrNull(startIndex) ?: return
        playbackModel.play(song, PlaySong.FromAlbum)
    }

    // These use Unruly's own collection-level overloads (PlaybackViewModel.kt:356/376/396)
    // rather than hand-rolling "first song + PlaySong.FromX". Unruly applies its own sort,
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

    // Song-plus-context playback. Each maps onto PlaybackViewModel.play(song, PlaySong),
    // which is how Unruly itself starts playback from a browsing list. Resolution is by id
    // only, so a skin backed by a paged flow never has to materialise the collection just
    // to name a starting point (see the SkinCommands doc for why that mattered).
    //
    // FromArtist/FromGenre take a nullable "which" - null means "let Unruly pick the song's
    // own artist/genre". We pass the resolved object when the id is known, so the queue is
    // built from the collection the user was actually browsing, which matters for a song
    // belonging to more than one artist or genre.
    override fun playSongFromAll(songId: String) {
        val song = findSongById(songId) ?: return
        playbackModel.play(song, PlaySong.FromAll)
    }

    override fun playSongFromAlbum(songId: String, albumId: String) {
        val song = findSongById(songId) ?: return
        if (findAlbum(albumId) == null) return
        playbackModel.play(song, PlaySong.FromAlbum)
    }

    override fun playSongFromArtist(songId: String, artistId: String) {
        val song = findSongById(songId) ?: return
        val artist = findArtist(artistId) ?: return
        playbackModel.play(song, PlaySong.FromArtist(artist))
    }

    override fun playSongFromGenre(songId: String, genreId: String) {
        val song = findSongById(songId) ?: return
        val genre = findGenre(genreId) ?: return
        playbackModel.play(song, PlaySong.FromGenre(genre))
    }

    override fun playSongFromPlaylist(songId: String, playlistId: String) {
        val song = findSongById(songId) ?: return
        val playlist = findPlaylist(playlistId) ?: return
        playbackModel.play(song, PlaySong.FromPlaylist(playlist))
    }

    // The engine throws ("Invalid playback parameters") when there is no library yet or it is
    // empty; the contract promises a no-op. Found by the Crossbar skin tapping Shuffle All during
    // a cold start, before the library had loaded.
    override fun shuffleAll() {
        if (musicRepository.library?.songs.isNullOrEmpty()) return
        playbackModel.shuffleAll()
    }

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

    override fun createPlaylist(name: String?, songs: List<SkinSong>) = editPlaylists {
        val chosen = name?.trim()?.takeIf { it.isNotEmpty() } ?: defaultPlaylistName()
        musicRepository.createPlaylist(chosen, findSongs(songs))
    }

    override fun addToPlaylist(playlistId: String, songs: List<SkinSong>) = editPlaylists {
        val playlist = findPlaylist(playlistId) ?: return@editPlaylists
        val found = findSongs(songs).takeIf { it.isNotEmpty() } ?: return@editPlaylists
        musicRepository.addToPlaylist(found, playlist)
    }

    override fun renamePlaylist(playlistId: String, name: String) = editPlaylists {
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return@editPlaylists
        val playlist = findPlaylist(playlistId) ?: return@editPlaylists
        musicRepository.renamePlaylist(playlist, trimmed)
    }

    override fun deletePlaylist(playlistId: String) = editPlaylists {
        findPlaylist(playlistId)?.let { musicRepository.deletePlaylist(it) }
    }

    override fun removeFromPlaylist(playlistId: String, songId: String, index: Int) =
        editPlaylists {
            val playlist = findPlaylist(playlistId) ?: return@editPlaylists
            if (playlist.songs.getOrNull(index)?.uid?.toString() != songId) return@editPlaylists
            musicRepository.rewritePlaylist(
                playlist,
                playlist.songs.filterIndexed { i, _ -> i != index },
            )
        }

    /**
     * Runs one playlist edit after the previous one has landed. Each repository call reads the
     * library, derives a new one and writes it back, so two edits racing in parallel (a double tap
     * on "Add to Playlist") would each start from the same library and the second would silently
     * drop the first. Ids are resolved inside [edit], against the library as it stands once the
     * earlier edits are done.
     */
    private fun editPlaylists(edit: suspend () -> Unit) {
        scope.launch { playlistEdits.withLock { edit() } }
    }

    private val playlistEdits = Mutex()

    /** "Playlist 1", "Playlist 2", ...: the first not taken, as the stock playlist picker does. */
    private fun defaultPlaylistName(): String {
        val taken =
            musicRepository.library?.playlists.orEmpty().mapTo(HashSet()) {
                it.name.resolve(context)
            }
        return generateSequence(1) { it + 1 }
            .map { context.getString(R.string.fmt_def_playlist, it) }
            .first { it !in taken }
    }

    private fun findSong(song: SkinSong): Song? = findSongById(song.id)

    private fun findSongById(id: String): Song? =
        musicRepository.library?.songs?.firstOrNull { it.uid.toString() == id }

    /**
     * Resolves a batch in one pass.
     *
     * Deliberately NOT `songs.mapNotNull(::findSong)` — that is a linear scan of the whole library
     * per element, i.e. O(queue x library). Adding a 500-track queue against the 20,000-track
     * library of buildspec Phase 6.2 would be 10 million comparisons. Building one index and
     * looking up against it keeps it linear.
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

    private fun SkinRepeatMode.toUnruly(): RepeatMode =
        when (this) {
            SkinRepeatMode.NONE -> RepeatMode.NONE
            SkinRepeatMode.ALL -> RepeatMode.ALL
            SkinRepeatMode.TRACK -> RepeatMode.TRACK
        }
}
