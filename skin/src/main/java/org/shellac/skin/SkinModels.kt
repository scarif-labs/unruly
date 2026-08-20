/*
 * Copyright (c) 2026 Shellac Project
 * SkinModels.kt is part of Shellac.
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

import android.net.Uri

/**
 * Shellac's own music model types.
 *
 * These are deliberately NOT Auxio's types. Auxio exposes `org.oxycblt.musikr.Song` and friends as
 * *interfaces* backed by a live indexer graph; depending on them directly would mean every skin
 * breaks whenever upstream changes shape. Everything here is an immutable data class owned by us.
 *
 * ## Identity
 *
 * [id] is always a String. Auxio identifies music with `org.oxycblt.musikr.Music.UID` - a custom
 * type wrapping a format, an item kind and a UUID - **not** a Long. The engine adapter converts
 * with `uid.toString()`, which yields the stable compressed `u...` form that Auxio itself persists
 * to Room. Never assume ids are numeric or orderable.
 *
 * ## Time
 *
 * Every duration and position here is **milliseconds**. Auxio's `PlaybackViewModel` exposes
 * position in *deci*seconds (`positionDs`, 1/10 s, polled at 10 Hz). That conversion lives in the
 * engine adapter and nowhere else.
 */
data class SkinSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String,
    val artistId: String,
    val albumArtUri: Uri?,
    val durationMs: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
    /**
     * Release year, or null.
     *
     * DECISION D1: Auxio has no `year` property anywhere. Song carries `date: Date?`; this is
     * derived as `song.date?.year`.
     */
    val year: Int?,
)

data class SkinAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String,
    val albumArtUri: Uri?,
    val songCount: Int,
    val durationMs: Long,
    /**
     * DECISION D1: Auxio's Album has `dates: Date.Range?`, not a year. This is
     * `album.dates?.min?.year` - the original release rather than a reissue date. Reissue
     * information is intentionally discarded; expose the range here if a skin ever needs it.
     */
    val year: Int?,
    /**
     * DECISION D2: Auxio splits an artist's albums into `explicitAlbums` (credited album artist)
     * and `implicitAlbums` (appears-on). [SkinLibrary.albumsByArtist] returns both, unioned, and
     * sets this flag so a skin can distinguish "albums by" from "appears on" if it wants to. A skin
     * that ignores this flag gets sensible default behaviour.
     *
     * Only meaningful in results from [SkinLibrary.albumsByArtist]; false elsewhere.
     */
    val isExplicitForArtist: Boolean = false,
)

data class SkinArtist(
    val id: String,
    val name: String,
    val albumArtUri: Uri?,
    val albumCount: Int,
    val songCount: Int,
)

data class SkinGenre(val id: String, val name: String, val albumArtUri: Uri?, val songCount: Int)

data class SkinPlaylist(
    val id: String,
    val name: String,
    val albumArtUri: Uri?,
    val songCount: Int,
    val durationMs: Long,
)

enum class SkinRepeatMode {
    /** Maps to Auxio `RepeatMode.NONE`. */
    NONE,

    /** Maps to Auxio `RepeatMode.ALL`. */
    ALL,

    /** Maps to Auxio `RepeatMode.TRACK`. */
    TRACK;

    /** Cycle order used by [SkinCommands.cycleRepeatMode]. */
    fun next(): SkinRepeatMode =
        when (this) {
            NONE -> ALL
            ALL -> TRACK
            TRACK -> NONE
        }
}

/**
 * A complete snapshot of playback state.
 *
 * [positionMs] comes from a 10 Hz polled source. A skin that wants smooth animation must
 * interpolate rather than render this value directly - see the position ticker in the skin support
 * layer (buildspec Phase 4.1).
 */
data class SkinPlaybackState(
    val song: SkinSong?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val queue: List<SkinSong>,
    val queueIndex: Int,
    val repeatMode: SkinRepeatMode,
    val isShuffled: Boolean,
) {
    companion object {
        /** Safe initial value before the engine has reported anything. */
        val EMPTY =
            SkinPlaybackState(
                song = null,
                isPlaying = false,
                positionMs = 0L,
                durationMs = 0L,
                queue = emptyList(),
                queueIndex = -1,
                repeatMode = SkinRepeatMode.NONE,
                isShuffled = false,
            )
    }
}
