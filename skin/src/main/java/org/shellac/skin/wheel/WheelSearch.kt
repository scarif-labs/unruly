package org.shellac.skin.wheel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.shellac.skin.SkinAlbum
import org.shellac.skin.SkinArtist
import org.shellac.skin.SkinSearchResults
import org.shellac.skin.SkinSession
import org.shellac.skin.SkinSong
import org.shellac.skin.support.SkinLibraryGate
import org.shellac.skin.support.SkinTheme

private const val SEARCH_DEBOUNCE_MS = 250L

internal sealed interface WheelSearchItem {
    val label: String

    data class Character(val value: String) : WheelSearchItem {
        override val label: String = if (value == " ") "SPACE" else value
    }

    data class Section(val title: String) : WheelSearchItem {
        override val label: String = title
    }

    data class SongResult(val song: SkinSong) : WheelSearchItem {
        override val label: String = "${song.title} — ${song.artist}"
    }

    data class AlbumResult(val album: SkinAlbum) : WheelSearchItem {
        override val label: String = "${album.title} — ${album.artist}"
    }

    data class ArtistResult(val artist: SkinArtist) : WheelSearchItem {
        override val label: String = artist.name
    }

    data class Message(override val label: String) : WheelSearchItem
}

private val SEARCH_CHARACTERS =
    ('A'..'Z').map(Char::toString) +
        ('0'..'9').map(Char::toString) +
        listOf(" ", "DEL")

@Composable
internal fun WheelSearch(
    session: SkinSession,
    onSong: (SkinSong) -> Unit,
    onAlbum: (SkinAlbum) -> Unit,
    onArtist: (SkinArtist) -> Unit,
    scaffold: @Composable (
        Int,
        (Int) -> WheelSearchItem?,
        (WheelSearchItem, Int) -> Unit,
        @Composable (Int) -> Unit,
    ) -> Unit,
) {
    val loadState by session.library.loadState.collectAsState()
    SkinLibraryGate(
        loadState = loadState,
        scanning = { loaded, explored ->
            searchStatus(
                scaffold = scaffold,
                message =
                    if (loaded == null || explored == null) "Scanning…"
                    else "Scanning… $loaded of $explored",
            )
        },
        failed = { message ->
            searchStatus(scaffold = scaffold, message = "Load failed: $message")
        },
        empty = {
            searchStatus(scaffold = scaffold, message = "Library is empty")
        },
        ready = {
            readySearch(
                session = session,
                onSong = onSong,
                onAlbum = onAlbum,
                onArtist = onArtist,
                scaffold = scaffold,
            )
        },
    )
}

@Composable
private fun readySearch(
    session: SkinSession,
    onSong: (SkinSong) -> Unit,
    onAlbum: (SkinAlbum) -> Unit,
    onArtist: (SkinArtist) -> Unit,
    scaffold: @Composable (
        Int,
        (Int) -> WheelSearchItem?,
        (WheelSearchItem, Int) -> Unit,
        @Composable (Int) -> Unit,
    ) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(SkinSearchResults()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        results = SkinSearchResults()
        if (query.isEmpty()) {
            searching = false
            return@LaunchedEffect
        }

        searching = true
        delay(SEARCH_DEBOUNCE_MS)
        session.library.search(query).collect { nextResults ->
            results = nextResults
            searching = false
        }
    }

    val items = searchItems(results = results, query = query, searching = searching)
    scaffold(
        items.size,
        items::getOrNull,
        { item, _ ->
            when (item) {
                is WheelSearchItem.Character -> {
                    query =
                        if (item.value == "DEL") query.dropLast(1)
                        else query + item.value
                }
                is WheelSearchItem.SongResult -> onSong(item.song)
                is WheelSearchItem.AlbumResult -> onAlbum(item.album)
                is WheelSearchItem.ArtistResult -> onArtist(item.artist)
                is WheelSearchItem.Section, is WheelSearchItem.Message -> Unit
            }
        },
    ) { selectedIndex ->
        val colors = SkinTheme.colors
        val typography = SkinTheme.typography
        Column(
            modifier = Modifier.fillMaxSize().background(colors.background).padding(top = 16.dp),
        ) {
            BasicText(
                "Search",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = typography.title.copy(color = colors.onBackground),
            )
            BasicText(
                if (query.isEmpty()) "Query: —" else "Query: $query",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style =
                    typography.title.copy(
                        color = colors.onBackground,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    ),
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp)) {
                WheelMenuList(
                    items = items,
                    selectedIndex = selectedIndex,
                    itemContent = { item, isSelected ->
                        val isHeader =
                            item is WheelSearchItem.Section || item is WheelSearchItem.Message
                        BasicText(
                            item.label,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .background(
                                        when {
                                            isSelected -> colors.accent
                                            isHeader -> colors.background
                                            else -> colors.surface
                                        },
                                    )
                                    .padding(
                                        horizontal = if (isHeader) 20.dp else 24.dp,
                                        vertical = 8.dp,
                                    ),
                            style =
                                (if (isHeader) typography.label else typography.body).copy(
                                    color = if (isSelected) colors.onAccent else colors.onSurface,
                                    fontWeight =
                                        if (isSelected || isHeader) FontWeight.Medium
                                        else FontWeight.Normal,
                                ),
                        )
                    },
                )
            }
        }
    }
}

private fun searchItems(
    results: SkinSearchResults,
    query: String,
    searching: Boolean,
): List<WheelSearchItem> {
    val items: MutableList<WheelSearchItem> =
        SEARCH_CHARACTERS.map(WheelSearchItem::Character).toMutableList()
    if (query.isEmpty()) return items
    if (searching) {
        items += WheelSearchItem.Message("Searching…")
        return items
    }

    if (results.songs.isNotEmpty()) {
        items += WheelSearchItem.Section("Songs")
        items += results.songs.map(WheelSearchItem::SongResult)
    }
    if (results.albums.isNotEmpty()) {
        items += WheelSearchItem.Section("Albums")
        items += results.albums.map(WheelSearchItem::AlbumResult)
    }
    if (results.artists.isNotEmpty()) {
        items += WheelSearchItem.Section("Artists")
        items += results.artists.map(WheelSearchItem::ArtistResult)
    }
    if (results.songs.isEmpty() && results.albums.isEmpty() && results.artists.isEmpty()) {
        items += WheelSearchItem.Message("No matches")
    }
    return items
}

@Composable
private fun searchStatus(
    scaffold: @Composable (
        Int,
        (Int) -> WheelSearchItem?,
        (WheelSearchItem, Int) -> Unit,
        @Composable (Int) -> Unit,
    ) -> Unit,
    message: String,
) {
    scaffold(
        0,
        { _: Int -> null },
        { _, _ -> },
    ) {
        val colors = SkinTheme.colors
        val typography = SkinTheme.typography
        Column(
            modifier = Modifier.fillMaxSize().background(colors.background).padding(20.dp),
        ) {
            BasicText("Search", style = typography.title.copy(color = colors.onBackground))
            BasicText(
                message,
                modifier = Modifier.padding(top = 20.dp),
                style = typography.body.copy(color = colors.onSurface),
            )
        }
    }
}
