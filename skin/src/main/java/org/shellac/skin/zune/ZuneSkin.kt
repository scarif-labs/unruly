package org.shellac.skin.zune

import androidx.compose.runtime.Composable
import org.shellac.skin.AppSkin
import org.shellac.skin.SkinSession
import org.shellac.skin.support.SkinBackStack

internal sealed interface ZuneDestination {
    data object Library : ZuneDestination
    data object Search : ZuneDestination
    data object NowPlaying : ZuneDestination
    data object Queue : ZuneDestination
    data class ArtistAlbums(val artistId: String, val artistName: String) : ZuneDestination
    data class AlbumSongs(val albumId: String, val albumTitle: String) : ZuneDestination
    data class GenreSongs(val genreId: String, val genreName: String) : ZuneDestination
    data class PlaylistSongs(val playlistId: String, val playlistName: String) : ZuneDestination
}

class ZuneSkin : AppSkin {
    override val id: String = "zune"
    override val displayName: String = "Zune HD"
    override val supportsEdgeToEdge: Boolean = false

    private val backStack = SkinBackStack<ZuneDestination>(ZuneDestination.Library)

    override fun onBackPressed(): Boolean = navigateBack()

    internal fun open(destination: ZuneDestination) {
        backStack.push(destination)
    }

    internal fun navigateBack(): Boolean = backStack.pop()

    @Composable
    override fun Render(session: SkinSession) {
        when (val destination = backStack.current) {
            ZuneDestination.Library -> libraryScreen(session)
            ZuneDestination.Search -> searchScreen(session)
            ZuneDestination.NowPlaying -> nowPlayingScreen(session)
            ZuneDestination.Queue -> queueScreen(session)
            is ZuneDestination.ArtistAlbums -> artistAlbumsScreen(session, destination)
            is ZuneDestination.AlbumSongs -> albumSongsScreen(session, destination)
            is ZuneDestination.GenreSongs -> genreSongsScreen(session, destination)
            is ZuneDestination.PlaylistSongs -> playlistSongsScreen(session, destination)
        }
    }

    internal fun navigateToNowPlaying(session: SkinSession) {
        open(ZuneDestination.NowPlaying)
    }

    internal fun navigateToQueue() {
        open(ZuneDestination.Queue)
    }

    internal fun navigateToSearch() {
        open(ZuneDestination.Search)
    }
}
