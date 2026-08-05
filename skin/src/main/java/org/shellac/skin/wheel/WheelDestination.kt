package org.shellac.skin.wheel

sealed interface WheelDestination {
    data object Root : WheelDestination
    data object MusicMenu : WheelDestination
    data object Queue : WheelDestination
    data class QueueActions(val queueIndex: Int, val songId: String, val title: String) : WheelDestination
    data object Search : WheelDestination
    data object Artists : WheelDestination
    data object Albums : WheelDestination
    data object Songs : WheelDestination
    data object Genres : WheelDestination
    data object Playlists : WheelDestination
    data class ArtistAlbums(val artistId: String, val title: String) : WheelDestination
    data class AlbumSongs(val albumId: String, val title: String) : WheelDestination
    data class GenreSongs(val genreId: String, val title: String) : WheelDestination
    data class PlaylistSongs(val playlistId: String, val title: String) : WheelDestination
    data object NowPlaying : WheelDestination
}
