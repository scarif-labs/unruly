/*
 * Copyright (c) 2026 Unruly Project
 * WheelMenuList.kt is part of Unruly.
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

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey

/**
 * The reusable list primitive the whole Touch Wheel skin is built on.
 *
 * **The list is anchored to the top and the highlight moves down it.** The content only scrolls
 * once the selection would leave the viewport, and then by the smallest amount that brings it back
 * into view. This is how the hardware being matched behaves, and it is why short menus sit flush at
 * the top rather than floating in the middle of the screen.
 *
 * (An earlier revision pinned the selection to the vertical centre and slid the list beneath it.
 * That was a deliberate choice, but it left a blank half-screen above every short menu and did not
 * match the reference hardware. The scroll-into-view behaviour here replaced it.)
 *
 * Selection is driven exclusively by rotation (see [WheelMenuState.onRotate]); the [LazyColumn] is
 * not independently finger-scrollable.
 *
 * The caller owns the selection and hands it in as [selectedIndex]; this component only renders and
 * scrolls. That keeps the state logic unit-testable (see [clampIndex]) while the layout stays a
 * pure function of its inputs.
 *
 * @param items The rows, in display order.
 * @param selectedIndex Index of the highlighted row. It is clamped into range locally, so a filter
 *   that shrinks the list under the selection can never crash the scroll.
 * @param modifier Applied to the viewport.
 * @param key Optional stable key per item (e.g. `{ it.id }`) so rows are reused rather than rebuilt
 *   on scroll.
 * @param itemContent Renders one row, receiving the item and whether it is selected.
 */
@Composable
fun <T> WheelMenuList(
    items: List<T>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    showScrollbar: Boolean = false,
    itemContent: @Composable (item: T, isSelected: Boolean) -> Unit,
) {
    val listState = rememberLazyListState()

    if (items.isEmpty()) return

    // The library can change under the selection - a filter shrinks the list while the index
    // still points past the new end. Clamp the index we act on so we never ask the list to
    // scroll to an index that does not exist. This is the "clamp instead of throw" guarantee
    // for the render path (the state path is handled by clampIndex).
    val effectiveIndex = selectedIndex.coerceIn(0, items.lastIndex)

    ScrollbarFrame(listState, items.size, showScrollbar, modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // The wheel is the only way to move: letting the finger scroll the list would
            // fight the selection model and desync it from the rotation state. Rotation never
            // goes through LazyColumn's gestures, so this is safe to leave off. It does not
            // block programmatic scrolling, which is what revealItem relies on.
            userScrollEnabled = false,
        ) {
            items(items, key = key) { item ->
                // Reference-compare the row against the item at the selected index. Cheap and
                // allocation-free (no per-frame object is created in the item path).
                val isSelected = item === items[effectiveIndex]
                Box(modifier = Modifier.fillMaxWidth()) { itemContent(item, isSelected) }
            }
        }
    }

    LaunchedEffect(effectiveIndex, items.size) { listState.revealItem(effectiveIndex) }
}

/**
 * Paged counterpart of the [List]-based [WheelMenuList] above. Same top-anchored behaviour; the
 * differences are all consequences of never holding the whole collection.
 *
 * **Never materialise [items].** An earlier version reached the plain-`List` overload by calling
 * `items[itemCount - 1]`, which tells Paging to load every page up to the end - so each screen
 * pulled the entire library into memory. That is invisible on a 2,000-track device and a hard
 * failure at the 20,000 this project targets. Rows are fetched by index inside the item lambda
 * instead, so Paging loads only around the visible window.
 *
 * Two consequences for callers:
 * 1. **[itemContent] receives `T?`.** A row whose page has not loaded yet is null; render a
 *    placeholder of the same height, or the list will jump when it arrives.
 * 2. **Selection is decided by index**, not by identity. The `List` overload can compare `item ===
 *    items[effectiveIndex]`; here that would mark *every* unloaded row selected, since all null
 *    placeholders are equal.
 *
 * To resolve the selected item outside composition (activating it, say), use
 * `LazyPagingItems.peek(index)` rather than `get`: `peek` does not register a load, so pressing a
 * button cannot itself trigger a fetch. It returns null for an unloaded row, which callers should
 * treat as "do nothing".
 *
 * @param items The paged rows, in display order.
 * @param selectedIndex Index of the highlighted row, clamped into range locally.
 * @param modifier Applied to the viewport.
 * @param key Optional stable key per item, wrapped via `LazyPagingItems.itemKey` so placeholders
 *   get a positional key instead of colliding on null.
 * @param itemContent Renders one row, receiving the item (null if unloaded) and whether it is
 *   selected.
 */
@Composable
fun <T : Any> WheelMenuList(
    items: LazyPagingItems<T>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    showScrollbar: Boolean = false,
    itemContent: @Composable (item: T?, isSelected: Boolean) -> Unit,
) {
    val listState = rememberLazyListState()

    if (items.itemCount == 0) return

    val effectiveIndex = selectedIndex.coerceIn(0, items.itemCount - 1)
    val itemKey = key?.let { items.itemKey(it) }

    ScrollbarFrame(listState, items.itemCount, showScrollbar, modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
        ) {
            items(count = items.itemCount, key = itemKey) { index ->
                val item = items[index]
                Box(modifier = Modifier.fillMaxWidth()) {
                    itemContent(item, index == effectiveIndex)
                }
            }
        }
    }

    LaunchedEffect(effectiveIndex, items.itemCount) { listState.revealItem(effectiveIndex) }
}

/**
 * Lays [list] out with the classic scrollbar beside it when [showScrollbar] is set and the rows
 * overflow the viewport. A list that fits shows no bar and keeps the full width, as on the
 * hardware.
 */
@Composable
private fun ScrollbarFrame(
    listState: LazyListState,
    total: Int,
    showScrollbar: Boolean,
    modifier: Modifier,
    list: @Composable () -> Unit,
) {
    val metrics by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val rowSize = info.visibleItemsInfo.firstOrNull()?.size ?: 0
            if (rowSize <= 0) {
                null
            } else {
                val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                val first =
                    listState.firstVisibleItemIndex +
                        listState.firstVisibleItemScrollOffset.toFloat() / rowSize
                first to viewport / rowSize
            }
        }
    }
    Row(modifier = modifier) {
        ClassicRowGrid(modifier = Modifier.weight(1f).fillMaxHeight()) { list() }
        val (first, visible) = metrics ?: return@Row
        if (showScrollbar && total > visible) {
            ClassicScrollbar(first = first, visible = visible, total = total)
        }
    }
}

/**
 * Scrolls the minimum distance needed to bring [index] fully into view, and nothing when it is
 * already visible.
 *
 * Deliberately not `scrollToItem(index)`: that would snap the row to the top of the viewport on
 * every click, so the highlight would never appear to move. Walking down a menu has to leave the
 * list still while the selection travels, and only push it when the selection reaches the bottom
 * edge.
 *
 * `scrollBy` rather than `animateScrollBy`: at one item per wheel click the move must feel
 * immediate, and queued animations lag behind fast rotation.
 */
private suspend fun LazyListState.revealItem(index: Int) {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) {
        // Not laid out at all - either the first frame, or a jump far outside the window.
        scrollToItem(index)
        return
    }
    val overshootBelow = item.offset + item.size - info.viewportEndOffset
    if (overshootBelow > 0) {
        scrollBy(overshootBelow.toFloat())
        return
    }
    val overshootAbove = item.offset - info.viewportStartOffset
    if (overshootAbove < 0) {
        scrollBy(overshootAbove.toFloat())
    }
}

/**
 * Pure, Compose-free selection driver for a wheel list.
 *
 * The index is read back via [selectedIndex]. Moving is done exclusively through [onRotate], which
 * advances by [delta] and **clamps at both ends - it never wraps**: at index 0 a negative delta
 * stays at 0, at the last index a positive delta stays put.
 *
 * @param initialIndex Where the wheel starts; defaults to the top of the list.
 */
@Stable
class WheelMenuState(initialIndex: Int = 0) {
    private var _selectedIndex by mutableStateOf(initialIndex.coerceAtLeast(0))

    /** The currently selected row index. Always in `0..itemCount-1` after any rotate. */
    val selectedIndex: Int
        get() = _selectedIndex

    /**
     * Moves the selection by [delta] items, saturated at both ends of a [itemCount]-item list.
     * Passing `delta = 0` with a fresh [itemCount] is a safe way to re-clamp after the library
     * shrank under the selection - clampIndex handles that for you.
     */
    fun onRotate(delta: Int, itemCount: Int) {
        _selectedIndex = clampIndex(_selectedIndex, delta, itemCount)
    }

    /** Returns the selection to the top of the list. */
    fun reset() {
        _selectedIndex = 0
    }
}

/** Creates a [WheelMenuState] scoped to the current composition. */
@Composable
fun rememberWheelMenuState(initialIndex: Int = 0): WheelMenuState {
    return remember { WheelMenuState(initialIndex) }
}

/**
 * Clamps [current] + [delta] into the valid range of a [itemCount]-item list.
 *
 * The result never wraps around: moving past either end saturates at the end. Returns 0 when
 * [itemCount] is non-positive (empty list), so a wheel over no items stays at zero.
 *
 * Examples:
 * - `(0, -1, 10) -> 0`, `(9, +1, 10) -> 9` (saturating at each end)
 * - `(5, +100, 10) -> 9`, `(5, -100, 10) -> 0` (massive deltas saturate)
 * - `(3, 0, 0) -> 0` (empty list), `(50, 0, 10) -> 9` (index past the end clamps down)
 */
internal fun clampIndex(current: Int, delta: Int, itemCount: Int): Int {
    if (itemCount <= 0) return 0
    return (current + delta).coerceIn(0, itemCount - 1)
}
