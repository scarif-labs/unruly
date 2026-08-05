package org.shellac.skin.wheel

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.shellac.skin.SkinLyricLine
import org.shellac.skin.SkinLyricsState
import org.shellac.skin.SkinSession
import org.shellac.skin.support.activeLyricIndexAt
import org.shellac.skin.support.rememberInterpolatedPosition

private val ROTATION_SCROLL_STEP = 34.dp
private const val FOLLOW_RESUME_MS = 3_000L

/** Rotation and follow-along state owned by the Touch Wheel destination. */
internal class WheelLyricsState {
    var followsCurrentLine by mutableStateOf(true)
        private set

    private var pendingRotation by mutableStateOf(0)
    private var rotationVersion by mutableStateOf(0)

    val rotationSequence: Int
        get() = rotationVersion

    fun onRotate(delta: Int) {
        if (delta == 0) return
        pendingRotation += delta
        rotationVersion += 1
        followsCurrentLine = false
    }

    fun takePendingRotation(): Int {
        val delta = pendingRotation
        pendingRotation = 0
        return delta
    }

    fun resumeFollowing() {
        followsCurrentLine = true
    }

    fun resetForTrack() {
        pendingRotation = 0
        followsCurrentLine = true
    }
}

@Composable
internal fun rememberWheelLyricsState(): WheelLyricsState = remember { WheelLyricsState() }

@Composable
internal fun WheelLyrics(
    session: SkinSession,
    state: WheelLyricsState,
) {
    val playback by session.playback.collectAsState()
    val songId = playback.song?.id
    val lyricsFlow = remember(session.lyrics, songId, calculation = {
        if (songId == null) {
            snapshotFlow { SkinLyricsState.NoTrack }
        } else {
            session.lyrics.lyrics(songId)
        }
    })
    val lyricsState by lyricsFlow.collectAsState(
        initial = if (songId == null) SkinLyricsState.NoTrack else SkinLyricsState.Loading,
    )
    val interpolatedPositionMs by rememberInterpolatedPosition(playback)
    val listState = remember(songId, calculation = { LazyListState() })
    val density = LocalDensity.current

    val activeIndex =
        when (val currentState = lyricsState) {
            is SkinLyricsState.Found ->
                if (currentState.lyrics.isSynced) {
                    activeLyricIndexAt(currentState.lyrics.synced, interpolatedPositionMs)
                } else {
                    -1
                }
            else -> -1
        }
    LaunchedEffect(songId, block = {
        state.resetForTrack()
    })

    LaunchedEffect(songId, state.rotationSequence, block = {
        val delta = state.takePendingRotation()
        if (delta == 0) return@LaunchedEffect

        if (lyricsState is SkinLyricsState.Found) {
            val pixels = with(density) { ROTATION_SCROLL_STEP.toPx() }
            listState.scrollBy(delta * pixels)
        }

        val sequence = state.rotationSequence
        var startedAt = Long.MIN_VALUE
        while (true) {
            val now = withFrameMillis { it }
            if (startedAt == Long.MIN_VALUE) startedAt = now
            if (now - startedAt >= FOLLOW_RESUME_MS) break
        }
        if (state.rotationSequence == sequence) {
            state.resumeFollowing()
        }
    })

    LaunchedEffect(songId, activeIndex, state.followsCurrentLine, block = {
        if (state.followsCurrentLine && activeIndex >= 0) {
            listState.animateScrollToItem(activeIndex)
        }
    })

    Column(
        modifier = Modifier.fillMaxSize().background(ClassicColors.background),
        content = {
            ClassicTitleBar(
                title = "Lyrics",
                isPlaying = playback.isPlaying,
                hasSong = playback.song != null,
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
                content = {
                    when (val currentState = lyricsState) {
                        SkinLyricsState.NoTrack -> lyricsMessage("Nothing playing")
                        SkinLyricsState.Disabled ->
                            lyricsMessage("Lyrics lookup is off. You can switch it on in Settings.")
                        SkinLyricsState.Loading -> lyricsMessage("Loading lyrics…")
                        SkinLyricsState.NotFound -> lyricsMessage("No lyrics found")
                        is SkinLyricsState.Failed ->
                            lyricsMessage("Couldn't load lyrics. Try again: ${currentState.message}")
                        is SkinLyricsState.Found -> {
                            if (currentState.lyrics.isSynced) {
                                SyncedLyrics(
                                    lines = currentState.lyrics.synced,
                                    activeIndex = activeIndex,
                                    listState = listState,
                                )
                            } else {
                                PlainLyrics(lines = currentState.lyrics.plain, listState = listState)
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
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        content = {
            itemsIndexed(
                items = lines,
                key = { index, line -> "${index}:${line.startMs}" },
                itemContent = { index, line ->
                    LyricLine(
                        text = line.text,
                        isActive = index == activeIndex,
                    )
                },
            )
        },
    )
}

@Composable
private fun PlainLyrics(
    lines: List<String>,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        content = {
            items(
                items = lines,
                itemContent = { line -> LyricLine(text = line, isActive = false) },
            )
        },
    )
}

@Composable
private fun LyricLine(
    text: String,
    isActive: Boolean,
) {
    BasicText(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        style = ClassicTypography.body.copy(
            color = if (isActive) ClassicColors.accent else ClassicColors.onBackground,
        ),
    )
}

@Composable
private fun lyricsMessage(text: String) {
    ClassicEmptyLabel(text = text)
}
