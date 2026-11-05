Build the menu list component for the "Touch Wheel" skin in the Unruly Android project
(buildspec Phase 5d). This is the reusable list primitive the whole skin is built on, so it
matters more than its size suggests.

Working directory is the repo root.

## Naming

The skin is called **Touch Wheel**, never "iPod" and never "click wheel". Use
`TouchWheel` / `touchwheel` naming. Do not reference Apple anywhere.

## Where the code goes

Create ONE new file: `skin/src/main/java/org/unruly/skin/wheel/WheelMenuList.kt`

Do NOT create `TouchWheel.kt` or `WheelZone.kt` — another agent is writing those right now.
Do not modify any existing file. Do not touch any `build.gradle`.

## Hard constraints

1. **Only `org.unruly.skin` types plus Compose/Kotlin stdlib.** Do NOT import
   `org.oxycblt.auxio` or `org.oxycblt.musikr`. Do not read `app/` or `musikr/`.
2. `collectAsStateWithLifecycle` is NOT available. Use `collectAsState()`.
3. Read `skin/src/main/java/org/unruly/skin/support/SkinTheme.kt` and take colours and text
   styles from `SkinTheme.colors` / `SkinTheme.typography` rather than hardcoding.

Pinned: Kotlin 2.3.10, Compose BOM 2026.06.01, paging 3.5.0, minSdk 24, Java 21.

## What to build

```kotlin
@Composable
fun <T> WheelMenuList(
    items: List<T>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, isSelected: Boolean) -> Unit,
)

@Stable
class WheelMenuState(initialIndex: Int = 0) {
    val selectedIndex: Int
    fun onRotate(delta: Int, itemCount: Int)
    fun reset()
}

@Composable
fun rememberWheelMenuState(initialIndex: Int = 0): WheelMenuState
```

### The defining behaviour

**The selection is fixed vertically; the items move under it.** This is the opposite of a
normal list, where the selection moves and the list stays put. Get this right — it is the
whole character of the component.

1. The selected row sits at a **fixed vertical position** (default: centred in the
   viewport). As the selection index changes, the *content* scrolls so the newly selected
   item lands at that same fixed position.
2. Use a `LazyColumn` with a `LazyListState`, and drive it with
   `scrollToItem(index, scrollOffset)` — computing an offset that centres the item. Do NOT
   use `animateScrollToItem` for wheel-driven movement: at one item per click it must feel
   immediate, and queued animations lag behind fast scrolling. Say this in a comment.
3. `onRotate(delta, itemCount)` moves the selection by `delta` items and **clamps at both
   ends** — it must not wrap around. At index 0, a negative delta stays at 0.
4. Selection changes must be driven by rotation only. The list itself should not be
   independently finger-scrollable — pass `userScrollEnabled = false` to the LazyColumn, so
   the wheel is the only way to move. Comment on why.

### Performance

Buildspec 5d requires "a 2,000-item list scrolls without stutter".

- Use a stable `key` in `items(...)` if `T` allows; accept an optional
  `key: ((T) -> Any)?` parameter so callers can supply one.
- Do not allocate per-frame in the item content path.
- `scrollToItem` must be called from a `LaunchedEffect` keyed on the selected index, not on
  every recomposition.

### Edge cases — all of these must be handled

- Empty list: render nothing, and `onRotate` must not crash or move the index off zero.
- `itemCount` smaller than the current index (the library changed under the selection):
  clamp the index into range rather than throwing.
- A single item: rotation in either direction keeps index 0.

Also provide a pure helper so the clamping is checkable without Compose:

```kotlin
internal fun clampIndex(current: Int, delta: Int, itemCount: Int): Int
```

Return 0 when `itemCount <= 0`.

## Verify

You CANNOT run Gradle in this sandbox — it fails with
`java.net.SocketException: Operation not permitted`. Do not try.

Re-read your file and check every symbol exists, and that `clampIndex` behaves for:
`(0, -1, 10) -> 0`, `(9, +1, 10) -> 9`, `(5, +100, 10) -> 9`, `(5, -100, 10) -> 0`,
`(3, 0, 0) -> 0`, `(50, 0, 10) -> 9`.

## Report to stdout

1. File created
2. How you centre the selected item, including the exact offset calculation
3. Why `scrollToItem` rather than `animateScrollToItem`
4. Every edge case you handled
5. Anything in the support layer or contract that was missing or awkward
