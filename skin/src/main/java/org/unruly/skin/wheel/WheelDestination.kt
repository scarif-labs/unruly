/*
 * Copyright (c) 2026 Unruly Project
 * WheelDestination.kt is part of Unruly.
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
 
package org.unruly.skin.wheel

sealed interface WheelDestination {
    data object Root : WheelDestination

    data object MusicMenu : WheelDestination

    data object Queue : WheelDestination

    data class QueueActions(val queueIndex: Int, val songId: String, val title: String) :
        WheelDestination

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

    data object Lyrics : WheelDestination
}
