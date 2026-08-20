/*
 * Copyright (c) 2026 Shellac Project
 * ZuneLyrics.kt is part of Shellac.
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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.shellac.skin.SkinLyricLine
import org.shellac.skin.SkinLyricsState
import org.shellac.skin.SkinSession
import org.shellac.skin.support.SkinTheme
import org.shellac.skin.support.activeLyricIndexAt
import org.shellac.skin.support.rememberInterpolatedPosition

/**
 * Where the sung line sits in the viewport, as a fraction of its height.
 *
 * Upper third rather than dead centre: what a reader needs is the line being sung plus the ones
 * about to arrive, so the larger share of the screen belongs below the anchor.
 */
private const val ACTIVE_LINE_ANCHOR = 0.34f

/** How long after a deliberate scroll the sheet goes back to following playback. */
private const val FOLLOW_RESUME_MS = 4_000L

/**
 * Line-to-line handoff. Long enough to read as a fade rather than a swap, short enough that a fast
 * couplet is not still animating when the next line starts.
 */
private const val HANDOFF_MS = 260

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
                SkinLyricsState.NoTrack ->
                    LyricsNotice(
                        headline = "nothing playing",
                        detail = "start a track and its words turn up here",
                    )
                SkinLyricsState.Disabled ->
                    LyricsNotice(
                        headline = "lookup is off",
                        detail =
                            "words come from a lyrics service, so it is yours to switch " +
                                "on. doing so sends the playing track's title and artist.",
                        action = {
                            AccentLink(text = "open settings", onClick = session.openAppSettings)
                        },
                    )
                SkinLyricsState.Loading -> LoadingNotice()
                // Deliberately not phrased as a failure. Most libraries contain plenty of
                // tracks no service has, and an apologetic screen for the ordinary case
                // teaches the reader to distrust the feature.
                SkinLyricsState.NotFound ->
                    LyricsNotice(
                        headline = "no words for this one",
                        detail = "the service has nothing filed against this track",
                    )
                is SkinLyricsState.Failed ->
                    LyricsNotice(
                        headline = "couldn't reach the service",
                        detail = lyricsState.message,
                        footnote = "it will try again the next time this track plays",
                    )
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

    val lyricsFlow =
        remember(session.lyrics, songId, calculation = { session.lyrics.lyrics(songId) })
    return lyricsFlow.collectAsState(initial = SkinLyricsState.Loading).value
}

@Composable
private fun SyncedLyrics(songId: String?, lines: List<SkinLyricLine>, positionMs: Long) {
    val activeIndex = activeLyricIndexAt(lines, positionMs)
    val listState = remember(songId) { LazyListState() }

    // Following is suspended on the drag *interaction*, not on isScrollInProgress. That flag
    // goes false the instant the fling's momentum dies, which is typically well under a
    // second after the finger leaves - so a reader who flicked back to check an earlier verse
    // had the sheet snatched away from them almost immediately. Every drag start and stop
    // bumps the sequence below, which restarts the resume clock, so the countdown only ever
    // runs from the last moment the user touched the list.
    var followsPlayback by remember(songId) { mutableStateOf(true) }
    var gestureSequence by remember(songId) { mutableStateOf(0) }
    var isDragging by remember(songId) { mutableStateOf(false) }

    LaunchedEffect(
        listState,
        block = {
            listState.interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is DragInteraction.Start -> {
                        isDragging = true
                        gestureSequence += 1
                    }
                    is DragInteraction.Stop,
                    is DragInteraction.Cancel -> {
                        isDragging = false
                        gestureSequence += 1
                    }
                    else -> Unit
                }
            }
        },
    )

    LaunchedEffect(
        gestureSequence,
        block = {
            if (gestureSequence == 0) return@LaunchedEffect
            followsPlayback = false
            // While a finger is still down there is no point starting the clock; the stop event
            // bumps the sequence again and this effect restarts with the timer that matters.
            if (isDragging) return@LaunchedEffect
            delay(FOLLOW_RESUME_MS)
            followsPlayback = true
        },
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        content = {
            val anchor = maxHeight * ACTIVE_LINE_ANCHOR
            val anchorPx = with(LocalDensity.current) { anchor.roundToPx() }

            // scrollToItem positions an item relative to the *viewport start*, so an unpadded
            // list simply runs out of travel at both ends: the first lines can never move down
            // to the anchor and the last lines can never move up to it, and the current line
            // drifts to a screen edge instead. Padding by the anchor above and by the whole
            // remainder below buys the sheet the slack to put any line - first and last
            // included - exactly on the anchor at scrollOffset 0.
            LaunchedEffect(
                songId,
                activeIndex,
                followsPlayback,
                anchorPx,
                block = {
                    if (!followsPlayback) return@LaunchedEffect
                    if (activeIndex < 0) {
                        // Instrumental intro. Cancelling the top padding shows the sheet from its
                        // top edge; anchoring line 0 here would make the display look one line
                        // ahead of the song before a word has been sung.
                        listState.animateScrollToItem(index = 0, scrollOffset = anchorPx)
                    } else {
                        listState.animateScrollToItem(index = activeIndex, scrollOffset = 0)
                    }
                },
            )

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = anchor, bottom = maxHeight - anchor),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
                content = {
                    itemsIndexed(
                        items = lines,
                        key = { index, line -> "$songId-$index-${line.startMs}" },
                        itemContent = { index, line ->
                            // A null distance means "no line is current yet", which is not the
                            // same as "line 0 is current" - see the -1 contract on
                            // activeLyricIndexAt.
                            LyricLine(
                                text = line.text,
                                distance = if (activeIndex < 0) null else index - activeIndex,
                            )
                        },
                    )
                },
            )
        },
    )
}

/**
 * One line of the sheet, graded by how far it is from the line being sung.
 *
 * Both animations are per-line and driven by that distance alone, so the whole grade costs one
 * float and one colour per *visible* line, not per line in the sheet.
 */
@Composable
private fun LyricLine(text: String, distance: Int?) {
    val emphasis =
        animateFloatAsState(
            targetValue = if (distance == 0) 1f else 0f,
            animationSpec = tween(durationMillis = HANDOFF_MS),
            label = "zuneLyricEmphasis",
        )
    val color by
        animateColorAsState(
            targetValue =
                lyricLineColor(
                    distance = distance,
                    sung = SkinTheme.colors.onSurface,
                    unsung = SkinTheme.colors.onBackground,
                ),
            animationSpec = tween(durationMillis = HANDOFF_MS),
            label = "zuneLyricColor",
        )
    val accent = SkinTheme.colors.accent

    BasicText(
        text = text,
        modifier =
            Modifier.fillMaxWidth()
                // The State is read inside the draw lambda rather than captured as a value, so
                // the lambda is allocated once for the line instead of once per animation frame.
                .drawBehind {
                    val alpha = emphasis.value
                    if (alpha <= 0.01f) return@drawBehind
                    val inset = size.height * 0.12f
                    drawRect(
                        color = accent.copy(alpha = alpha),
                        topLeft = Offset(x = 0f, y = inset),
                        size =
                            Size(
                                width = 2.dp.toPx(),
                                height = (size.height - inset * 2f).coerceAtLeast(0f),
                            ),
                    )
                }
                .padding(start = 16.dp),
        style =
            SkinTheme.typography.body.copy(
                color = color,
                fontSize = (19f + 11f * emphasis.value).sp,
                lineHeight = (26f + 12f * emphasis.value).sp,
            ),
    )
}

/**
 * Emphasis grade. Sung lines recede into the surface grey while unsung ones stay white, so the two
 * halves of the sheet are told apart by hue as well as by opacity - at these opacities a dim white
 * and a dim grey are otherwise hard to separate on near-black.
 *
 * A null [distance] is the pre-first-line state: every line reads the same, muted, and nothing is
 * dominant.
 */
private fun lyricLineColor(distance: Int?, sung: Color, unsung: Color): Color {
    if (distance == null) return unsung.copy(alpha = 0.45f)
    if (distance == 0) return unsung
    if (distance > 0) {
        return unsung.copy(
            alpha =
                when (distance) {
                    1 -> 0.62f
                    2 -> 0.40f
                    else -> 0.26f
                }
        )
    }
    return sung.copy(
        alpha =
            when (distance) {
                -1 -> 0.46f
                -2 -> 0.30f
                else -> 0.18f
            }
    )
}

/**
 * Untimed lyrics, which is roughly half of what the service returns.
 *
 * Set as stanzas rather than as one row per line: without a current line there is nothing to
 * highlight, so the sheet should read as a printed lyric page. Reusing the synced layout with the
 * highlighting switched off looks like a broken synced view instead.
 */
@Composable
private fun PlainLyrics(songId: String?, lines: List<String>) {
    val stanzas = remember(songId, lines) { groupStanzas(lines) }
    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxWidth(),
        content = {
            item(
                key = "$songId-untimed",
                content = {
                    BasicText(
                        text = "untimed  ·  full text",
                        style = SkinTheme.typography.label.copy(color = SkinTheme.colors.accent),
                    )
                },
            )
            itemsIndexed(
                items = stanzas,
                key = { index, _ -> "$songId-stanza-$index" },
                itemContent = { _, stanza ->
                    BasicText(
                        text = stanza,
                        style = SkinTheme.typography.body.copy(fontSize = 20.sp, lineHeight = 31.sp),
                    )
                },
            )
        },
    )
}

/**
 * Folds blank-line-separated runs into single blocks.
 *
 * Done once per sheet behind a remember: a stanza is one text node, so a long sheet costs a handful
 * of items rather than several hundred, and the blank lines stop being rows of nothing.
 */
private fun groupStanzas(lines: List<String>): List<String> {
    val stanzas = mutableListOf<String>()
    val current = StringBuilder()
    for (line in lines) {
        if (line.isBlank()) {
            if (current.isNotEmpty()) {
                stanzas.add(current.toString())
                current.clear()
            }
        } else {
            if (current.isNotEmpty()) current.append('\n')
            current.append(line)
        }
    }
    if (current.isNotEmpty()) stanzas.add(current.toString())
    return stanzas
}

/** The shared shape for every non-Found state: big thin headline, small tracked detail. */
@Composable
private fun LyricsNotice(
    headline: String,
    detail: String,
    footnote: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = {
            BasicText(
                text = headline,
                style = SkinTheme.typography.title.copy(fontSize = 34.sp, lineHeight = 36.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(text = detail, style = SkinTheme.typography.label)
            if (footnote != null) {
                BasicText(text = footnote, style = SkinTheme.typography.label)
            }
            if (action != null) action()
        },
    )
}

/**
 * Lookup is a network round trip and can take seconds. A motionless screen for that long reads as a
 * hang, so the accent rule sweeps: it carries no progress information, only the fact that something
 * is still happening.
 */
@Composable
private fun LoadingNotice() {
    var extended by remember { mutableStateOf(false) }
    LaunchedEffect(
        Unit,
        block = {
            while (true) {
                extended = !extended
                delay(900)
            }
        },
    )
    val extent by
        animateFloatAsState(
            targetValue = if (extended) 1f else 0.12f,
            animationSpec = tween(durationMillis = 850),
            label = "zuneLyricsSweep",
        )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = {
            BasicText(
                text = "looking for words",
                style = SkinTheme.typography.title.copy(fontSize = 34.sp, lineHeight = 36.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier =
                    Modifier.fillMaxWidth(extent).height(2.dp).background(SkinTheme.colors.accent)
            )
            BasicText(text = "asking the lyrics service", style = SkinTheme.typography.label)
        },
    )
}
