package org.shellac.skin.wheel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged

/**
 * The reusable list primitive the whole Touch Wheel skin is built on.
 *
 * **The selection is fixed vertically; the items move under it** - the opposite of a
 * normal list (where the selection moves and the list stays put). The selected row always
 * sits at a fixed vertical position (default: centred in the viewport) and, as the
 * selection index changes, the *content* scrolls so the newly selected item lands at that
 * same fixed position. Selection is driven exclusively by rotation (see
 * [WheelMenuState.onRotate]); the [LazyColumn] is not independently finger-scrollable.
 *
 * The caller owns the selection (via a [WheelMenuState] or any [Int] it likes) and hands
 * it in as [selectedIndex]; this component only renders and scrolls. This keeps the state
 * logic unit-testable (see [clampIndex]) while the layout stays a pure function of its
 * inputs.
 *
 * @param items The rows, in display order.
 * @param selectedIndex Index of the highlighted row. It is clamped into range locally, so
 *   a filter that shrinks the list under the selection can never crash the scroll.
 * @param modifier Applied to the viewport.
 * @param key Optional stable key per item (e.g. `{ it.id }`) so rows are reused rather
 *   than rebuilt on scroll. When null, position is used. Must be null or stable to avoid
 *   per-frame work; no allocation happens in the item path either way.
 * @param itemContent Renders one row, receiving the [item] and whether it is selected.
 */
@Composable
fun <T> WheelMenuList(
    items: List<T>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    itemContent: @Composable (item: T, isSelected: Boolean) -> Unit,
) {
    val listState = rememberLazyListState()

    // Pixels of the viewport and of one row, measured so we can centre the selection.
    // Both start at 0 (nothing measured yet) and are filled in by the first layout pass.
    var viewportHeight by remember { mutableStateOf(0) }
    var rowHeight by remember { mutableStateOf(0) }

    // Empty list: render nothing. There is no row to centre, and onRotate/clamping keep
    // the index at zero, so nothing here is allowed to run (not even a scroll).
    if (items.isEmpty()) return

    // The library can change under the selection - a filter shrinks the list while the
    // index still points past the new end. Clamp the index we act on so we never ask the
    // list to scroll to an index that does not exist. This is the "clamp instead of
    // throw" guarantee for the render path (the state path is handled by clampIndex).
    val effectiveIndex = selectedIndex.coerceIn(0, items.lastIndex)

    // Centre the selected row: push its top edge down by half the leftover space so the
    // item sits dead-centre in the viewport. Blank space above item 0 is intentional - the
    // list genuinely moves under the stationary wheel cursor, never the reverse.
    val centreOffset = if (viewportHeight > 0 && rowHeight > 0) {
        (viewportHeight - rowHeight) / 2
    } else {
        0
    }

    Box(modifier = modifier.onSizeChanged { viewportHeight = it.height }) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // The wheel is the only way to move: letting the finger scroll the list would
            // fight the fixed-cursor model and desync it from the rotation state. Rotation
            // never goes through LazyColumn's gestures, so this is safe to leave off.
            userScrollEnabled = false,
        ) {
            items(items, key = key) { item ->
                // Reference-compare the row against the item at the selected index. Cheap
                // and allocation-free (no per-frame object is created in the item path).
                val isSelected = item === items[effectiveIndex]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { rowHeight = it.height },
                ) {
                    itemContent(item, isSelected)
                }
            }
        }
    }

    // scrollToItem, NOT animateScrollToItem: at one item per wheel click the move must
    // feel immediate, and queued animations lag behind fast scrolling (each rotation
    // queues another animation that the previous one is still running to, so the list
    // visibly drags behind the finger). scrollToItem snaps instantly to the target.
    // LaunchedEffect keyed on the index (and the measured sizes) so we scroll only when
    // the target actually changes, never on unrelated recompositions.
    LaunchedEffect(effectiveIndex, viewportHeight, rowHeight) {
        // Wait until the first layout pass has measured both sizes so the offset is real;
        // until then there is nothing meaningful to centre against.
        if (viewportHeight > 0 && rowHeight > 0) {
            listState.scrollToItem(effectiveIndex, centreOffset)
        }
    }
}

/**
 * Pure, Compose-free selection driver for a wheel list.
 *
 * The index is read back via [selectedIndex]. Moving is done exclusively through
 * [onRotate], which advances by [delta] and **clamps at both ends - it never wraps**:
 * at index 0 a negative delta stays at 0, at the last index a positive delta stays put.
 *
 * @param initialIndex Where the wheel starts; defaults to the top of the list.
 */
@Stable
class WheelMenuState(initialIndex: Int = 0) {
    private var _selectedIndex by mutableStateOf(initialIndex.coerceAtLeast(0))

    /** The currently selected row index. Always in `0..itemCount-1` after any rotate. */
    val selectedIndex: Int get() = _selectedIndex

    /**
     * Moves the selection by [delta] items, saturated at both ends of a [itemCount]-item
     * list. Passing `delta = 0` with a fresh [itemCount] is a safe way to re-clamp after
     * the library shrank under the selection - clampIndex handles that for you.
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
 * The result never wraps around: moving past either end saturates at the end. Returns 0
 * when [itemCount] is non-positive (empty list), so a wheel over no items stays at zero.
 *
 * Examples:
 *  - `(0, -1, 10) -> 0`, `(9, +1, 10) -> 9` (saturating at each end)
 *  - `(5, +100, 10) -> 9`, `(5, -100, 10) -> 0` (massive deltas saturate)
 *  - `(3, 0, 0) -> 0` (empty list), `(50, 0, 10) -> 9` (index past the end clamps down)
 */
internal fun clampIndex(current: Int, delta: Int, itemCount: Int): Int {
    if (itemCount <= 0) return 0
    return (current + delta).coerceIn(0, itemCount - 1)
}

