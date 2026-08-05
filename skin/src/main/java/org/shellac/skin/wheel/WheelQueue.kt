package org.shellac.skin.wheel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.shellac.skin.SkinSession
import org.shellac.skin.SkinSong
import org.shellac.skin.support.SkinTheme

internal enum class QueueAction(val label: String) {
    PLAY("Play"),
    MOVE_UP("Move Up"),
    MOVE_DOWN("Move Down"),
    REMOVE("Remove"),
    CANCEL("Cancel"),
}

@Composable
internal fun WheelQueue(
    session: SkinSession,
    onCenter: (SkinSong, Int) -> Unit,
    scaffold: @Composable (
        Int,
        Int,
        (Int) -> SkinSong?,
        (SkinSong, Int) -> Unit,
        @Composable (Int) -> Unit,
    ) -> Unit,
) {
    val playback by session.playback.collectAsState()
    val queue = playback.queue
    val initialSelection =
        remember(WheelDestination.Queue, queue.isNotEmpty()) {
            if (queue.isEmpty()) 0 else playback.queueIndex.coerceIn(0, queue.lastIndex)
        }

    scaffold(
        queue.size,
        initialSelection,
        queue::getOrNull,
        onCenter,
    ) { selectedIndex ->
        val colors = SkinTheme.colors
        val typography = SkinTheme.typography
        Column(
            modifier = Modifier.fillMaxSize().background(colors.background).padding(top = 16.dp),
        ) {
            BasicText(
                "Queue",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = typography.title.copy(color = colors.onBackground),
            )
            if (queue.isEmpty()) {
                BasicText(
                    "Queue is empty",
                    modifier = Modifier.padding(20.dp),
                    style = typography.body.copy(color = colors.onSurface),
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
                    WheelMenuList(
                        items = queue,
                        selectedIndex = selectedIndex,
                        itemContent = { song, isSelected ->
                            val index = queue.indexOf(song)
                            val isPlaying = index == playback.queueIndex
                            BasicText(
                                text =
                                    buildString {
                                        if (isPlaying) append("▶ ")
                                        append(song.title)
                                        append(" — ")
                                        append(song.artist)
                                    },
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .background(
                                            if (isSelected) colors.accent else colors.surface,
                                        )
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                style =
                                    typography.body.copy(
                                        color = if (isSelected) colors.onAccent else colors.onSurface,
                                        fontWeight =
                                            if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun WheelQueueActions(
    destination: WheelDestination.QueueActions,
    onAction: (QueueAction, Int) -> Unit,
    scaffold: @Composable (
        Int,
        Int,
        (Int) -> QueueAction?,
        (QueueAction, Int) -> Unit,
        @Composable (Int) -> Unit,
    ) -> Unit,
) {
    val actions = QueueAction.entries.toList()
    scaffold(
        actions.size,
        0,
        actions::getOrNull,
        onAction,
    ) { selectedIndex ->
        val colors = SkinTheme.colors
        val typography = SkinTheme.typography
        Column(
            modifier = Modifier.fillMaxSize().background(colors.background).padding(top = 16.dp),
        ) {
            BasicText(
                "Queue: ${destination.title}",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = typography.title.copy(color = colors.onBackground),
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
                WheelMenuList(
                    items = actions,
                    selectedIndex = selectedIndex,
                    itemContent = { action, isSelected ->
                        BasicText(
                            action.label,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .background(if (isSelected) colors.accent else colors.surface)
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                            style =
                                typography.body.copy(
                                    color = if (isSelected) colors.onAccent else colors.onSurface,
                                    fontWeight =
                                        if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                ),
                        )
                    },
                )
            }
        }
    }
}
