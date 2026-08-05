package org.shellac.skin.wheel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * Which list the wheel is currently driving.
 *
 * A wheel has one axis of input, so a screen that both composes a query and browses its
 * results has to say which of the two the wheel is moving. The first implementation put the
 * alphabet and the results in a **single** list, alphabet first: after typing one letter the
 * results sat 38 rows below the selection, and typing a second letter meant rotating all the
 * way back. That is the "search is majorly broken" report.
 *
 * MENU switches focus, which is what the hardware did. Results stay visible in the side panel
 * while typing, so the query can be refined without changing focus at all.
 */
private enum class SearchFocus {
    CHARACTERS,
    RESULTS,
}

internal sealed interface WheelSearchItem {
    val label: String

    data class Character(val value: String) : WheelSearchItem {
        override val label: String =
            when (value) {
                " " -> "space"
                "DEL" -> "delete"
                else -> value
            }
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

/**
 * @param query The current query. Held by the skin rather than here, so leaving the screen
 *   and coming back does not lose what was typed.
 * @param onQueryChange Reports edits back to the owner.
 * @param registerMenuOverride Lets this screen intercept MENU (and system back) while it is
 *   on screen. Returns true when it handled the press.
 */
@Composable
internal fun WheelSearch(
    session: SkinSession,
    query: String,
    onQueryChange: (String) -> Unit,
    registerMenuOverride: ((() -> Boolean)?) -> Unit,
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
                query = query,
                onQueryChange = onQueryChange,
                registerMenuOverride = registerMenuOverride,
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
    query: String,
    onQueryChange: (String) -> Unit,
    registerMenuOverride: ((() -> Boolean)?) -> Unit,
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
    var results by remember { mutableStateOf(SkinSearchResults()) }
    var searching by remember { mutableStateOf(false) }
    var focus by remember { mutableStateOf(SearchFocus.CHARACTERS) }

    LaunchedEffect(query) {
        if (query.isEmpty()) {
            results = SkinSearchResults()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        // Each keystroke cancels the pending search rather than issuing one per character.
        delay(SEARCH_DEBOUNCE_MS)
        session.library.search(query).collect { next ->
            results = next
            searching = false
        }
    }

    val resultItems = resultItems(results = results, searching = searching, query = query)
    val hasResults = resultItems.any { it !is WheelSearchItem.Message }

    // Falling back out of an empty result list keeps the wheel on something it can move.
    if (focus == SearchFocus.RESULTS && !hasResults) focus = SearchFocus.CHARACTERS

    // MENU steps CHARACTERS -> RESULTS -> out of the screen. It always terminates: from
    // RESULTS it returns false, so the skin performs its normal back. Registered for the
    // lifetime of this screen so the MENU zone and the system back gesture behave alike.
    DisposableEffect(focus, hasResults) {
        registerMenuOverride {
            if (focus == SearchFocus.CHARACTERS && hasResults) {
                focus = SearchFocus.RESULTS
                true
            } else {
                false
            }
        }
        onDispose { registerMenuOverride(null) }
    }

    val characterItems =
        remember { SEARCH_CHARACTERS.map(WheelSearchItem::Character) }
    val items: List<WheelSearchItem> =
        if (focus == SearchFocus.CHARACTERS) characterItems else resultItems

    scaffold(
        items.size,
        items::getOrNull,
        { item, _ ->
            when (item) {
                is WheelSearchItem.Character ->
                    onQueryChange(
                        if (item.value == "DEL") query.dropLast(1) else query + item.value,
                    )
                is WheelSearchItem.SongResult -> onSong(item.song)
                is WheelSearchItem.AlbumResult -> onAlbum(item.album)
                is WheelSearchItem.ArtistResult -> onArtist(item.artist)
                is WheelSearchItem.Section, is WheelSearchItem.Message -> Unit
            }
        },
    ) { selectedIndex ->
        SearchBody(
            query = query,
            focus = focus,
            hasResults = hasResults,
            items = items,
            selectedIndex = selectedIndex,
            resultSummary = resultSummary(results = results, searching = searching, query = query),
        )
    }
}

@Composable
private fun SearchBody(
    query: String,
    focus: SearchFocus,
    hasResults: Boolean,
    items: List<WheelSearchItem>,
    selectedIndex: Int,
    resultSummary: List<String>,
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        ClassicTitleBar(title = "Search", isPlaying = false, hasSong = false)

        // The query is the point of the screen, so it gets the largest type on it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF2F2F2))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            BasicText(
                if (query.isEmpty()) "type a query" else query,
                style = ClassicTypography.title.copy(
                    color = if (query.isEmpty()) Color(0xFF9A9A9A) else Color.Black,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        ClassicSplitLayout(
            modifier = Modifier.weight(1f),
            panel = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFEDEDED))
                        .padding(6.dp),
                ) {
                    // Results stay visible while typing, so the query can be refined without
                    // switching focus at all.
                    resultSummary.forEach { line ->
                        BasicText(
                            line,
                            style = ClassicTypography.label.copy(color = Color(0xFF4A4A4A)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(modifier = Modifier.weight(1f))
                    BasicText(
                        when {
                            focus == SearchFocus.RESULTS -> "menu: back"
                            hasResults -> "menu: results"
                            else -> "menu: back"
                        },
                        style = ClassicTypography.label.copy(color = Color(0xFF7A7A7A)),
                    )
                }
            },
            menu = {
                if (items.isEmpty()) {
                    ClassicEmptyLabel("No items")
                } else {
                    WheelMenuList(
                        items = items,
                        selectedIndex = selectedIndex,
                        modifier = Modifier.fillMaxSize(),
                    ) { item, isSelected ->
                        if (item is WheelSearchItem.Section) {
                            BasicText(
                                item.label,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE0E0E0))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                style = ClassicTypography.label.copy(color = Color(0xFF4A4A4A)),
                            )
                        } else {
                            ClassicMenuRow(
                                label = item.label,
                                isSelected = isSelected,
                                showChevron = item is WheelSearchItem.AlbumResult ||
                                    item is WheelSearchItem.ArtistResult,
                            )
                        }
                    }
                }
            },
        )
    }
}

/** Result rows only - the alphabet is a separate list now, not prepended to these. */
private fun resultItems(
    results: SkinSearchResults,
    searching: Boolean,
    query: String,
): List<WheelSearchItem> {
    if (query.isEmpty()) return emptyList()
    if (searching) return listOf(WheelSearchItem.Message("Searching…"))

    val items = mutableListOf<WheelSearchItem>()
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
    if (items.isEmpty()) items += WheelSearchItem.Message("No matches")
    return items
}

private fun resultSummary(
    results: SkinSearchResults,
    searching: Boolean,
    query: String,
): List<String> =
    when {
        query.isEmpty() -> listOf("")
        searching -> listOf("searching…")
        else -> {
            val counts = mutableListOf<String>()
            if (results.songs.isNotEmpty()) counts += "${results.songs.size} songs"
            if (results.albums.isNotEmpty()) counts += "${results.albums.size} albums"
            if (results.artists.isNotEmpty()) counts += "${results.artists.size} artists"
            if (counts.isEmpty()) listOf("no matches") else counts
        }
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
        Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
            ClassicTitleBar(title = "Search", isPlaying = false, hasSong = false)
            BasicText(
                message,
                modifier = Modifier.padding(10.dp),
                style = typography.body.copy(color = colors.onSurface),
            )
        }
    }
}
