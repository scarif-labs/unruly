package org.shellac.skin.zune

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import org.shellac.skin.SkinLyricLine
import org.shellac.skin.SkinLyricsState
import org.shellac.skin.SkinSession
import org.shellac.skin.support.SkinTheme
import org.shellac.skin.support.activeLyricIndexAt
import org.shellac.skin.support.rememberInterpolatedPosition

@Composable
internal fun ZuneSkin.lyricsScreen(session: SkinSession) {
    val playback by session.playback.collectAsState()
    val songId = playback.song?.id
    val lyricsState = rememberLyricsState(session = session, songId = songId)
    val positionMs by rememberInterpolatedPosition(playback)

    BlackFrame(
        title = "lyrics",
        utility = {
            AccentLink(text = "back", onClick = { navigateBack() })
            AccentLink(text = "settings", onClick = session.openAppSettings)
        },
        content = {
            playback.song?.let { song ->
                BasicText(
                    text = song.title,
                    style = SkinTheme.typography.title.copy(fontSize = 30.sp, lineHeight = 33.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText(
                    text = song.artist,
                    style = SkinTheme.typography.label,
                    modifier = Modifier.padding(top = 2.dp, bottom = 18.dp),
                )
            }

            when (lyricsState) {
                SkinLyricsState.NoTrack -> LyricsMessage(text = "nothing playing")
                SkinLyricsState.Disabled -> DisabledLyricsMessage(onSettings = session.openAppSettings)
                SkinLyricsState.Loading -> LyricsMessage(text = "loading lyrics")
                SkinLyricsState.NotFound -> LyricsMessage(text = "no lyrics found")
                is SkinLyricsState.Failed -> FailedLyricsMessage(message = lyricsState.message)
                is SkinLyricsState.Found -> {
                    if (lyricsState.lyrics.isSynced) {
                        SyncedLyrics(
                            songId = songId,
                            lines = lyricsState.lyrics.synced,
                            positionMs = positionMs,
                        )
                    } else {
                        PlainLyrics(songId = songId, lines = lyricsState.lyrics.plain)
                    }
                }
            }
        },
    )
}

@Composable
private fun rememberLyricsState(session: SkinSession, songId: String?): SkinLyricsState {
    if (songId == null) return SkinLyricsState.NoTrack

    val lyricsFlow = remember(session.lyrics, songId, calculation = {
        session.lyrics.lyrics(songId)
    })
    return lyricsFlow.collectAsState(initial = SkinLyricsState.Loading).value
}

@Composable
private fun SyncedLyrics(
    songId: String?,
    lines: List<SkinLyricLine>,
    positionMs: Long,
) {
    val activeIndex = activeLyricIndexAt(lines, positionMs)
    val listState = remember(songId) { LazyListState() }

    LaunchedEffect(songId, activeIndex, block = {
        if (activeIndex >= 0 && !listState.isScrollInProgress) {
            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == activeIndex }
            if (!isVisible && !listState.isScrollInProgress) {
                listState.animateScrollToItem(activeIndex)
            }
        }
    })

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
        content = {
            itemsIndexed(
                items = lines,
                key = { index, line -> "$songId-$index-${line.startMs}" },
                itemContent = { index, line ->
                    BasicText(
                        text = line.text,
                        style = if (index == activeIndex) {
                            SkinTheme.typography.body.copy(color = SkinTheme.colors.accent)
                        } else {
                            SkinTheme.typography.body
                        },
                    )
                },
            )
        },
    )
}

@Composable
private fun PlainLyrics(songId: String?, lines: List<String>) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
        content = {
            itemsIndexed(
                items = lines,
                key = { index, _ -> "$songId-$index" },
                itemContent = { _, line ->
                    BasicText(text = line, style = SkinTheme.typography.body)
                },
            )
        },
    )
}

@Composable
private fun LyricsMessage(text: String) {
    BasicText(text = text, style = SkinTheme.typography.body)
}

@Composable
private fun DisabledLyricsMessage(onSettings: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = {
            BasicText(text = "lyrics lookup is off", style = SkinTheme.typography.body)
            BasicText(text = "switchable in settings", style = SkinTheme.typography.label)
            AccentLink(text = "settings", onClick = onSettings)
        },
    )
}

@Composable
private fun FailedLyricsMessage(message: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = {
            BasicText(text = "couldn't load lyrics", style = SkinTheme.typography.body)
            BasicText(text = message, style = SkinTheme.typography.label)
            BasicText(text = "try again later", style = SkinTheme.typography.label)
        },
    )
}
