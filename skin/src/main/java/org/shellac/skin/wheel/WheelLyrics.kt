/*
 * Copyright (c) 2026 Shellac Project
 * WheelLyrics.kt is part of Shellac.
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
 
package org.shellac.skin.wheel

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.shellac.skin.SkinLyricLine
import org.shellac.skin.SkinLyricsState
import org.shellac.skin.SkinSession
import org.shellac.skin.support.activeLyricIndexAt
import org.shellac.skin.support.rememberInterpolatedPosition

/** How long after the last wheel click the sheet goes back to following playback. */
private const val FOLLOW_RESUME_MS = 3_000L

/**
 * Where the sung line sits in the screen, as a fraction of its height.
 *
 * This is the one place the skin departs from [WheelMenuList]'s top-anchored, scroll-into-view
 * behaviour, and deliberately: that rule exists so a *short menu* sits flush at the top instead of
 * floating in the middle. A lyric sheet is neither short nor a menu - there is no selection to walk
 * down, the content always outruns a 4:3 screen, and a current line that drifts to the bottom edge
 * and snaps is exactly the artefact the anchor removes.
 */
private const val ACTIVE_LINE_ANCHOR = 0.38f

/** Line-to-line handoff. Short: the screen is small and a slow fade on it looks like lag. */
private const val HANDOFF_MS = 180

// The originals' selection fill. Declared here rather than borrowed from WheelChrome so it can
// be hoisted out of the draw lambda - a Brush built per frame would allocate once per line per
// frame of the handoff. Size-relative, so one instance serves every row height.
private val SelectionFill = Brush.verticalGradient(listOf(Color(0xFF8FB6EC), Color(0xFF2454A0)))

private val LyricSung = Color(0xFFB4B4B4)
private val LyricNear = Color(0xFF2E2E2E)
private val LyricMid = Color(0xFF6A6A6A)
private val LyricFar = Color(0xFF9A9A9A)
private val LyricMuted = Color(0xFF5A5A5A)

/**
 * Browse state owned by the Touch Wheel lyrics destination.
 *
 * The wheel is the only input this screen has, so rotation browses the sheet the same way it walks
 * a menu: one click, one line. [browsedIndex] is null whenever the display is simply following
 * playback, which is the resting state.
 *
 * [lineCount] and [activeIndex] are plain fields, not snapshot state: they are written from
 * composition by [update] and read only from the wheel's rotation callback, so making them
 * observable would feed a write back into the composition that produced it.
 */
internal class WheelLyricsState {
    private var lineCount: Int = 0
    private var activeIndex: Int = -1

    /** Line the wheel has browsed to, or null while the sheet follows playback. */
    var browsedIndex by mutableStateOf<Int?>(null)
        private set

    /** Bumped on every click so the resume timer can restart from the latest one. */
    var rotationSequence by mutableStateOf(0)
        private set

    fun update(lineCount: Int, activeIndex: Int) {
        this.lineCount = lineCount
        this.activeIndex = activeIndex
    }

    fun onRotate(delta: Int) {
        if (delta == 0 || lineCount == 0) return
        // A first click starts from whatever the reader is looking at - the sung line - rather
        // than from the top of the sheet, which would throw away their place in the song.
        val from = browsedIndex ?: activeIndex.coerceAtLeast(0)
        browsedIndex = (from + delta).coerceIn(0, lineCount - 1)
        rotationSequence += 1
    }

    fun resumeFollowing() {
        browsedIndex = null
    }

    fun resetForTrack() {
        browsedIndex = null
    }
}

@Composable
internal fun rememberWheelLyricsState(): WheelLyricsState = remember { WheelLyricsState() }

@Composable
internal fun WheelLyrics(session: SkinSession, state: WheelLyricsState) {
    val playback by session.playback.collectAsState()
    val songId = playback.song?.id
    val lyricsFlow =
        remember(
            session.lyrics,
            songId,
            calculation = {
                if (songId == null) {
                    snapshotFlow { SkinLyricsState.NoTrack }
                } else {
                    session.lyrics.lyrics(songId)
                }
            },
        )
    val lyricsState by
        lyricsFlow.collectAsState(
            initial = if (songId == null) SkinLyricsState.NoTrack else SkinLyricsState.Loading
        )
    val interpolatedPositionMs by rememberInterpolatedPosition(playback)
    val listState = remember(songId, calculation = { LazyListState() })

    val currentState = lyricsState
    val lyrics = (currentState as? SkinLyricsState.Found)?.lyrics
    val isSynced = lyrics != null && lyrics.isSynced
    val lineCount =
        when {
            lyrics == null -> 0
            lyrics.isSynced -> lyrics.synced.size
            else -> lyrics.plain.size
        }
    val activeIndex =
        if (lyrics != null && lyrics.isSynced) {
            activeLyricIndexAt(lyrics.synced, interpolatedPositionMs)
        } else {
            -1
        }

    state.update(lineCount = lineCount, activeIndex = activeIndex)
    // Clamped on read rather than in update: a sheet that shrinks under the browse position
    // would otherwise have composition write to snapshot state it had just read.
    val browsedIndex = if (lineCount == 0) null else state.browsedIndex?.coerceIn(0, lineCount - 1)

    LaunchedEffect(songId, block = { state.resetForTrack() })

    // Resume following a few seconds after the last click. Only for timed lyrics: with no
    // timings there is nothing to return to, and silently yanking an untimed sheet back to the
    // top would undo the reader's browse for no reason.
    LaunchedEffect(
        state.rotationSequence,
        isSynced,
        block = {
            if (state.rotationSequence == 0 || !isSynced) return@LaunchedEffect
            delay(FOLLOW_RESUME_MS)
            state.resumeFollowing()
        },
    )

    Column(
        modifier = Modifier.fillMaxSize().background(ClassicColors.background),
        content = {
            ClassicTitleBar(
                title = "Lyrics",
                isPlaying = playback.isPlaying,
                hasSong = playback.song != null,
            )
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                content = {
                    val screenHeight = maxHeight
                    when (currentState) {
                        SkinLyricsState.NoTrack ->
                            ClassicNotice(
                                title = "Nothing Playing",
                                detail = "Start a track to see its words.",
                            )
                        SkinLyricsState.Disabled ->
                            ClassicNotice(
                                title = "Lyrics Off",
                                detail = "Lyrics are looked up online. Switch it on in Settings.",
                            )
                        SkinLyricsState.Loading -> ClassicLoadingNotice()
                        // An ordinary outcome, not a fault: no apology, no error styling.
                        SkinLyricsState.NotFound ->
                            ClassicNotice(
                                title = "No Lyrics",
                                detail = "Nothing on file for this track.",
                            )
                        is SkinLyricsState.Failed ->
                            ClassicNotice(
                                title = "Lyrics Unavailable",
                                detail = currentState.message,
                            )
                        is SkinLyricsState.Found -> {
                            if (currentState.lyrics.isSynced) {
                                SyncedLyrics(
                                    lines = currentState.lyrics.synced,
                                    activeIndex = activeIndex,
                                    browsedIndex = browsedIndex,
                                    listState = listState,
                                    screenHeight = screenHeight,
                                )
                            } else {
                                PlainLyrics(
                                    lines = currentState.lyrics.plain,
                                    browsedIndex = browsedIndex,
                                    listState = listState,
                                    screenHeight = screenHeight,
                                )
                            }
                        }
                    }
                },
            )
        },
    )
}

@Composable
private fun SyncedLyrics(
    lines: List<SkinLyricLine>,
    activeIndex: Int,
    browsedIndex: Int?,
    listState: LazyListState,
    screenHeight: Dp,
) {
    val anchor = screenHeight * ACTIVE_LINE_ANCHOR
    val anchorPx = with(LocalDensity.current) { anchor.roundToPx() }

    // scrollToItem measures from the viewport start, so without content padding the first and
    // last lines can never reach the anchor - the list runs out of travel and the current line
    // ends up pinned to an edge. Padding by the anchor above and the remainder below is what
    // makes an anchored position reachable for every line in the sheet.
    LaunchedEffect(
        activeIndex,
        browsedIndex,
        anchorPx,
        lines.size,
        block = {
            when {
                // One click, one line, immediately. An animation queued per click falls behind a
                // fast spin of the wheel and the control stops feeling mechanical.
                browsedIndex != null ->
                    listState.scrollToItem(index = browsedIndex, scrollOffset = 0)
                // Instrumental intro: cancel the top padding and show the sheet from its top edge.
                // Anchoring line 0 would read as if it were already being sung.
                activeIndex < 0 -> listState.scrollToItem(index = 0, scrollOffset = anchorPx)
                else -> listState.animateScrollToItem(index = activeIndex, scrollOffset = 0)
            }
        },
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = anchor, bottom = screenHeight - anchor),
        // The wheel is the only way to move, as everywhere else in this skin. A finger-
        // scrollable list would desync the browse index from what is on screen.
        userScrollEnabled = false,
        content = {
            itemsIndexed(
                items = lines,
                key = { index, line -> "$index:${line.startMs}" },
                itemContent = { index, line ->
                    LyricLine(
                        text = line.text,
                        // Null means no line is current yet - not the same as line 0 being
                        // current. See the -1 contract on activeLyricIndexAt.
                        distance = if (activeIndex < 0) null else index - activeIndex,
                        isSelected =
                            if (browsedIndex != null) {
                                index == browsedIndex
                            } else {
                                index == activeIndex
                            },
                        isBrowsing = browsedIndex != null,
                    )
                },
            )
        },
    )
}

/**
 * Untimed lyrics: a page of text the wheel scrolls, with no selection bar.
 *
 * There is no current line, so a highlight would be inventing one. Anchoring is dropped too -
 * top-aligned page scrolling is what the rest of the skin does, and with nothing to follow there is
 * nothing to anchor.
 */
@Composable
private fun PlainLyrics(
    lines: List<String>,
    browsedIndex: Int?,
    listState: LazyListState,
    screenHeight: Dp,
) {
    LaunchedEffect(
        browsedIndex,
        lines.size,
        block = {
            if (browsedIndex == null) return@LaunchedEffect
            listState.scrollToItem(index = browsedIndex, scrollOffset = 0)
        },
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = screenHeight * 0.5f),
        userScrollEnabled = false,
        content = {
            itemsIndexed(
                items = lines,
                key = { index, _ -> "plain:$index" },
                itemContent = { _, line ->
                    LyricLine(text = line, distance = null, isSelected = false, isBrowsing = false)
                },
            )
        },
    )
}

/**
 * One row of the sheet.
 *
 * The selection bar is the skin's own vocabulary reused: the line being sung is selected in the
 * same way a menu row is. While the wheel is browsing, the bar follows the wheel and the sung line
 * falls back to the accent colour, so the reader can still see where the song has got to without
 * two bars competing for the same meaning.
 */
@Composable
private fun LyricLine(text: String, distance: Int?, isSelected: Boolean, isBrowsing: Boolean) {
    val barAlpha =
        animateFloatAsState(
            targetValue = if (isSelected) 1f else 0f,
            animationSpec = tween(durationMillis = HANDOFF_MS),
            label = "wheelLyricBar",
        )
    val color by
        animateColorAsState(
            targetValue = if (isSelected) Color.White else lyricLineColor(distance),
            animationSpec = tween(durationMillis = HANDOFF_MS),
            label = "wheelLyricColor",
        )

    BasicText(
        text = text,
        modifier =
            Modifier.fillMaxWidth()
                // The State is read at draw time rather than captured as a value, so this lambda
                // is built once per row instead of once per animation frame.
                .drawBehind {
                    val alpha = barAlpha.value
                    if (alpha > 0.01f) drawRect(brush = SelectionFill, alpha = alpha)
                }
                .padding(horizontal = 8.dp, vertical = 3.dp),
        style =
            ClassicTypography.body.copy(
                color = color,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                // Weight is switched rather than animated - it cannot interpolate, and at this
                // size the bar and the colour already carry the transition.
                fontWeight = if (isSelected && !isBrowsing) FontWeight.Bold else FontWeight.Normal,
            ),
    )
}

/**
 * Emphasis grade for an unselected line, by distance from the line being sung.
 *
 * Sung lines go pale rather than dark: on a white screen receding means losing contrast, which is
 * the opposite of what the near-black skin does with the same information. A null [distance] is the
 * pre-first-line state - nothing is current, so nothing is graded.
 */
private fun lyricLineColor(distance: Int?): Color {
    if (distance == null) return ClassicColors.onBackground
    if (distance == 0) return ClassicColors.accent
    if (distance < 0) return LyricSung
    return when (distance) {
        1 -> LyricNear
        2 -> LyricMid
        else -> LyricFar
    }
}

/** The shared shape for every non-Found state: centred on the small screen, small grey detail. */
@Composable
private fun ClassicNotice(title: String, detail: String? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = {
            BasicText(
                text = title,
                style =
                    ClassicTypography.title.copy(
                        color = ClassicColors.onBackground,
                        textAlign = TextAlign.Center,
                    ),
            )
            if (detail != null) {
                BasicText(
                    text = detail,
                    modifier = Modifier.padding(top = 6.dp),
                    style =
                        ClassicTypography.label.copy(
                            color = LyricMuted,
                            textAlign = TextAlign.Center,
                        ),
                )
            }
        },
    )
}

/**
 * Lookup is a network round trip. On a screen this small a static caption for two seconds is
 * indistinguishable from a frozen device, so the bar sweeps - it reports liveness, not progress.
 */
@Composable
private fun ClassicLoadingNotice() {
    var extended by remember { mutableStateOf(false) }
    LaunchedEffect(
        Unit,
        block = {
            while (true) {
                extended = !extended
                delay(800)
            }
        },
    )
    val fraction by
        animateFloatAsState(
            targetValue = if (extended) 0.95f else 0.05f,
            animationSpec = tween(durationMillis = 760),
            label = "wheelLyricsSweep",
        )

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = {
            BasicText(
                text = "Loading Lyrics",
                style = ClassicTypography.title.copy(color = ClassicColors.onBackground),
            )
            ClassicProgressBar(
                fraction = fraction,
                modifier = Modifier.fillMaxWidth(0.6f).padding(top = 8.dp),
            )
        },
    )
}
