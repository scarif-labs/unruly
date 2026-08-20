/*
 * Copyright (c) 2026 Shellac Project
 * ZuneSkin.kt is part of Shellac.
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
 
package org.shellac.skin.zune

import androidx.compose.runtime.Composable
import org.shellac.skin.AppSkin
import org.shellac.skin.SkinSession
import org.shellac.skin.support.SkinBackStack

internal sealed interface SkinDestination {
    data object Library : SkinDestination

    data object Search : SkinDestination

    data object NowPlaying : SkinDestination

    data object Lyrics : SkinDestination

    data object Queue : SkinDestination

    data class ArtistAlbums(val artistId: String, val artistName: String) : SkinDestination

    data class AlbumSongs(val albumId: String, val albumTitle: String) : SkinDestination

    data class GenreSongs(val genreId: String, val genreName: String) : SkinDestination

    data class PlaylistSongs(val playlistId: String, val playlistName: String) : SkinDestination
}

class ZuneSkin : AppSkin {
    override val id: String = "zune"
    override val displayName: String = "Zune HD"
    override val supportsEdgeToEdge: Boolean = false

    private val backStack = SkinBackStack<SkinDestination>(SkinDestination.Library)

    override fun onBackPressed(): Boolean = navigateBack()

    internal fun open(destination: SkinDestination) {
        backStack.push(destination)
    }

    internal fun navigateBack(): Boolean = backStack.pop()

    @Composable
    override fun Render(session: SkinSession) {
        when (val destination = backStack.current) {
            SkinDestination.Library -> libraryScreen(session)
            SkinDestination.Search -> searchScreen(session)
            SkinDestination.NowPlaying -> nowPlayingScreen(session)
            SkinDestination.Lyrics -> lyricsScreen(session)
            SkinDestination.Queue -> queueScreen(session)
            is SkinDestination.ArtistAlbums -> artistAlbumsScreen(session, destination)
            is SkinDestination.AlbumSongs -> albumSongsScreen(session, destination)
            is SkinDestination.GenreSongs -> genreSongsScreen(session, destination)
            is SkinDestination.PlaylistSongs -> playlistSongsScreen(session, destination)
        }
    }

    internal fun navigateToNowPlaying() {
        open(SkinDestination.NowPlaying)
    }

    internal fun navigateToQueue() {
        open(SkinDestination.Queue)
    }

    internal fun navigateToLyrics() {
        open(SkinDestination.Lyrics)
    }

    internal fun navigateToSearch() {
        open(SkinDestination.Search)
    }
}
