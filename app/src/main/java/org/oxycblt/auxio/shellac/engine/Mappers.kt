package org.oxycblt.auxio.shellac.engine

import android.content.Context
import android.net.Uri
import org.oxycblt.auxio.image.CoverProvider
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.music.resolveNames
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import org.shellac.skin.SkinAlbum
import org.shellac.skin.SkinArtist
import org.shellac.skin.SkinGenre
import org.shellac.skin.SkinPlaylist
import org.shellac.skin.SkinSong
import org.shellac.skin.SkinSort

internal fun Song.toSkin(context: Context): SkinSong {
    val artist = artists.firstOrNull()
    return SkinSong(
        id = uid.toString(),
        title = name.resolve(context),
        artist = artists.resolveNames(context),
        album = album.name.resolve(context),
        albumId = album.uid.toString(),
        artistId = artist?.uid?.toString().orEmpty(),
        albumArtUri = cover.toArtUri(),
        durationMs = durationMs,
        trackNumber = track,
        discNumber = disc?.number,
        year = date?.year,
    )
}

internal fun Album.toSkin(context: Context, isExplicitForArtist: Boolean = false): SkinAlbum {
    val artist = artists.firstOrNull()
    return SkinAlbum(
        id = uid.toString(),
        title = name.resolve(context),
        artist = artists.resolveNames(context),
        artistId = artist?.uid?.toString().orEmpty(),
        albumArtUri = covers.covers.firstOrNull().toArtUri(),
        songCount = songs.size,
        durationMs = durationMs,
        year = dates?.min?.year,
        isExplicitForArtist = isExplicitForArtist,
    )
}

internal fun Artist.toSkin(context: Context): SkinArtist {
    return SkinArtist(
        id = uid.toString(),
        name = name.resolve(context),
        albumArtUri = covers.covers.firstOrNull().toArtUri(),
        albumCount = explicitAlbums.size,
        songCount = songs.size,
    )
}

internal fun Genre.toSkin(context: Context): SkinGenre =
    SkinGenre(
        id = uid.toString(),
        name = name.resolve(context),
        albumArtUri = covers.covers.firstOrNull().toArtUri(),
        songCount = songs.size,
    )

internal fun Playlist.toSkin(context: Context): SkinPlaylist =
    SkinPlaylist(
        id = uid.toString(),
        name = name.resolve(context),
        albumArtUri = covers.covers.firstOrNull().toArtUri(),
        songCount = songs.size,
        durationMs = durationMs,
    )

private fun org.oxycblt.musikr.covers.Cover?.toArtUri(): Uri? =
    this?.let { Uri.withAppendedPath(CoverProvider.CONTENT_URI, it.id) }

// @JvmName required: all five of these erase to the same JVM signature
// (sortForSkin(Collection, Context, SkinSort)) and clash without it.
@JvmName("sortSongsForSkin")
internal fun Collection<Song>.sortForSkin(context: Context, sort: SkinSort): List<Song> {
    val sorted =
        when (sort.by) {
            SkinSort.By.NAME -> sortedBy { it.name.resolve(context) }
            SkinSort.By.ARTIST -> sortedBy { it.artists.resolveNames(context) }
            SkinSort.By.ALBUM -> sortedBy { it.album.name.resolve(context) }
            SkinSort.By.YEAR -> sortedBy { it.date?.year }
            SkinSort.By.DURATION -> sortedBy { it.durationMs }
            SkinSort.By.DATE_ADDED -> sortedBy { it.addedMs }
        }
    return if (sort.ascending) sorted else sorted.asReversed()
}

// @JvmName required: all five of these erase to the same JVM signature
// (sortForSkin(Collection, Context, SkinSort)) and clash without it.
@JvmName("sortAlbumListForSkin")
internal fun Collection<Album>.sortForSkin(context: Context, sort: SkinSort): List<Album> {
    val sorted =
        when (sort.by) {
            SkinSort.By.NAME -> sortedBy { it.name.resolve(context) }
            SkinSort.By.ARTIST -> sortedBy { it.artists.resolveNames(context) }
            SkinSort.By.ALBUM -> sortedBy { it.name.resolve(context) }
            SkinSort.By.YEAR -> sortedBy { it.dates?.min?.year }
            SkinSort.By.DURATION -> sortedBy { it.durationMs }
            SkinSort.By.DATE_ADDED -> sortedBy { it.addedMs }
        }
    return if (sort.ascending) sorted else sorted.asReversed()
}

// @JvmName required: all five of these erase to the same JVM signature
// (sortForSkin(Collection, Context, SkinSort)) and clash without it.
@JvmName("sortArtistsForSkin")
internal fun Collection<Artist>.sortForSkin(context: Context, sort: SkinSort): List<Artist> {
    val sorted =
        when (sort.by) {
            SkinSort.By.NAME, SkinSort.By.ARTIST -> sortedBy { it.name.resolve(context) }
            SkinSort.By.ALBUM -> sortedBy { it.explicitAlbums.size }
            SkinSort.By.YEAR -> sortedBy { artist -> artist.songs.mapNotNull { it.date?.year }.minOrNull() }
            SkinSort.By.DURATION -> sortedBy { it.durationMs ?: 0L }
            SkinSort.By.DATE_ADDED -> sortedBy { artist -> artist.songs.minOfOrNull { it.addedMs } }
        }
    return if (sort.ascending) sorted else sorted.asReversed()
}

// @JvmName required: all five of these erase to the same JVM signature
// (sortForSkin(Collection, Context, SkinSort)) and clash without it.
@JvmName("sortGenresForSkin")
internal fun Collection<Genre>.sortForSkin(context: Context, sort: SkinSort): List<Genre> {
    val sorted =
        when (sort.by) {
            SkinSort.By.NAME -> sortedBy { it.name.resolve(context) }
            SkinSort.By.ARTIST ->
                sortedBy { genre -> genre.artists.map { it.name.resolve(context) }.sorted().joinToString() }
            SkinSort.By.ALBUM -> sortedBy { genre -> genre.songs.map { it.album.uid }.distinct().size }
            SkinSort.By.YEAR -> sortedBy { genre -> genre.songs.mapNotNull { it.date?.year }.minOrNull() }
            SkinSort.By.DURATION -> sortedBy { it.durationMs }
            SkinSort.By.DATE_ADDED -> sortedBy { genre -> genre.songs.minOfOrNull { it.addedMs } }
        }
    return if (sort.ascending) sorted else sorted.asReversed()
}

// @JvmName required: all five of these erase to the same JVM signature
// (sortForSkin(Collection, Context, SkinSort)) and clash without it.
@JvmName("sortPlaylistsForSkin")
internal fun Collection<Playlist>.sortForSkin(context: Context, sort: SkinSort): List<Playlist> {
    val sorted =
        when (sort.by) {
            SkinSort.By.NAME -> sortedBy { it.name.resolve(context) }
            SkinSort.By.ARTIST ->
                sortedBy { playlist -> playlist.songs.flatMap { it.artists }.resolveNames(context) }
            SkinSort.By.ALBUM -> sortedBy { playlist -> playlist.songs.map { it.album.uid }.distinct().size }
            SkinSort.By.YEAR -> sortedBy { playlist -> playlist.songs.mapNotNull { it.date?.year }.minOrNull() }
            SkinSort.By.DURATION -> sortedBy { it.durationMs }
            SkinSort.By.DATE_ADDED -> sortedBy { playlist -> playlist.songs.minOfOrNull { it.addedMs } }
        }
    return if (sort.ascending) sorted else sorted.asReversed()
}

internal fun Collection<Pair<Album, Boolean>>.sortAlbumsForSkin(
    context: Context,
    sort: SkinSort,
): List<Pair<Album, Boolean>> {
    val sorted =
        when (sort.by) {
            SkinSort.By.NAME, SkinSort.By.ALBUM -> sortedBy { it.first.name.resolve(context) }
            SkinSort.By.ARTIST -> sortedBy { it.first.artists.resolveNames(context) }
            SkinSort.By.YEAR -> sortedBy { it.first.dates?.min?.year }
            SkinSort.By.DURATION -> sortedBy { it.first.durationMs }
            SkinSort.By.DATE_ADDED -> sortedBy { it.first.addedMs }
        }
    return if (sort.ascending) sorted else sorted.asReversed()
}
