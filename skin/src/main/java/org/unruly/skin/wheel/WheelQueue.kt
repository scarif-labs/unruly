/*
 * Copyright (c) 2026 Unruly Project
 * WheelQueue.kt is part of Unruly.
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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.unruly.skin.SkinSession
import org.unruly.skin.SkinSong

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
    scaffold:
        @Composable
        (Int, Int, (Int) -> SkinSong?, (SkinSong, Int) -> Unit, @Composable (Int) -> Unit) -> Unit,
) {
    val playback by session.playback.collectAsState()
    val queue = playback.queue
    val initialSelection =
        remember(WheelDestination.Queue, queue.isNotEmpty()) {
            if (queue.isEmpty()) 0 else playback.queueIndex.coerceIn(0, queue.lastIndex)
        }

    scaffold(queue.size, initialSelection, queue::getOrNull, onCenter) { selectedIndex ->
        Column(modifier = Modifier.fillMaxSize().background(ClassicColors.background)) {
            ClassicTitleBar(
                title = "Queue",
                isPlaying = playback.isPlaying,
                hasSong = playback.song != null,
            )
            if (queue.isEmpty()) {
                ClassicEmptyLabel("Queue is empty")
            } else {
                WheelMenuList(
                    items = queue,
                    selectedIndex = selectedIndex,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    showScrollbar = true,
                    itemContent = { song, isSelected ->
                        ClassicMenuRow(
                            label = "${song.title} — ${song.artist}",
                            isSelected = isSelected,
                            showChevron = true,
                            // The speaker marks the playing entry, as in the originals' lists.
                            isNowPlaying = queue.indexOf(song) == playback.queueIndex,
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun WheelQueueActions(
    destination: WheelDestination.QueueActions,
    onAction: (QueueAction, Int) -> Unit,
    scaffold:
        @Composable
        (
            Int, Int, (Int) -> QueueAction?, (QueueAction, Int) -> Unit, @Composable (Int) -> Unit,
        ) -> Unit,
) {
    val actions = QueueAction.entries.toList()
    scaffold(actions.size, 0, actions::getOrNull, onAction) { selectedIndex ->
        Column(modifier = Modifier.fillMaxSize().background(ClassicColors.background)) {
            ClassicTitleBar(title = destination.title, isPlaying = false, hasSong = false)
            WheelMenuList(
                items = actions,
                selectedIndex = selectedIndex,
                modifier = Modifier.fillMaxWidth().weight(1f),
                itemContent = { action, isSelected ->
                    ClassicMenuRow(label = action.label, isSelected = isSelected, showChevron = false)
                },
            )
        }
    }
}
