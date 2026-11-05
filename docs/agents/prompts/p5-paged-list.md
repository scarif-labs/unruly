Fix a paging regression in the Touch Wheel skin of the Unruly Android project.

Working directory is the repo root.

## Naming

**Touch Wheel** — never "iPod", never "click wheel". Do not reference Apple anywhere.

## The bug

`skin/src/main/java/org/unruly/skin/wheel/TouchWheelSkin.kt` ends with this helper:

```kotlin
private fun <T : Any> LazyPagingItems<T>.wheelItems(): List<T> {
    if (itemCount > 0) {
        this[itemCount - 1]
    }
    return itemSnapshotList.items
}
```

Indexing the LAST element tells Paging to load everything up to the end, so every paged
screen drags the entire library into memory and hands it on as a `List`. That defeats
paging completely. The project must handle a 20,000-track library, so this has to go.

**Delete `wheelItems()` and every call to it.** Nothing may convert `LazyPagingItems` into
a full `List`.

## What to build

### 1. A paged overload of `WheelMenuList`

In `skin/src/main/java/org/unruly/skin/wheel/WheelMenuList.kt`, ADD an overload beside the
existing `List<T>` one. Do not change, rename, or reimplement the existing overload — other
screens use it and it is verified.

```kotlin
@Composable
fun <T : Any> WheelMenuList(
    items: LazyPagingItems<T>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    itemContent: @Composable (item: T?, isSelected: Boolean) -> Unit,
)
```

Note `item: T?` — a paged row that has not loaded yet is null, and the caller renders a
placeholder. Keep every behaviour of the existing overload: fixed-centre selection,
`scrollToItem` (never `animateScrollToItem`), `userScrollEnabled = false`, index clamped
into `0 until itemCount`, renders nothing when `itemCount == 0`.

Use `androidx.paging.compose.items` / `LazyPagingItems.itemKey` where it helps, but the row
count must come from `items.itemCount` and rows must be fetched by index so Paging loads
around the visible window only.

Determine `isSelected` by comparing the row's **index** to the selected index. The existing
overload uses `item === items[effectiveIndex]`; that is wrong here because a null
placeholder would match every other null.

### 2. Rework the paged screens in `TouchWheelSkin.kt`

`pagedLibraryScreen` currently materialises via `wheelItems()` and passes a `List` down.
Change it to pass the `LazyPagingItems` through to the new overload. Specifically:

- The wheel's item count comes from `items.itemCount`.
- CENTER must resolve the selected item **without** triggering a load: use
  `items.peek(index)`, not `items[index]`. `peek` does not tell Paging to fetch. If it
  returns null (row not loaded), do nothing — that is the correct behaviour, not a crash.
- `wheelScaffold` currently takes `items: List<T>`. Give it what it actually needs instead:
  an item count, plus a way to resolve the selected item on CENTER. Something like
  `itemCount: Int` and `resolveSelected: (Int) -> T?`. Keep the `List` screens working by
  passing `list.size` and `list::getOrNull`.

### 3. Use the new song-plus-context commands

`SkinCommands` (in `skin/src/main/java/org/unruly/skin/SkinCommands.kt`) has just gained:

```kotlin
fun playSongFromAll(songId: String)
fun playSongFromAlbum(songId: String, albumId: String)
fun playSongFromArtist(songId: String, artistId: String)
fun playSongFromGenre(songId: String, genreId: String)
fun playSongFromPlaylist(songId: String, playlistId: String)
```

These were added **because of this exact bug** — they let a skin start playback from one
song id without materialising the collection. Rewire song playback to use them:

- `Songs` screen (paged, whole library) → `playSongFromAll(song.id)`
- `GenreSongs` screen (paged) → `playSongFromGenre(song.id, destination.genreId)`
- `AlbumSongs` screen (plain `List`) → `playSongFromAlbum(song.id, destination.albumId)`
- `PlaylistSongs` screen (plain `List`) → `playSongFromPlaylist(song.id, destination.playlistId)`

No song playback path may call `playSongs(...)` any more. Read the doc comment on these
methods in `SkinCommands.kt` before using them.

## Hard constraints

1. **Only `org.unruly.skin` types plus Compose/Kotlin/Paging.** Do NOT import
   `org.oxycblt.auxio` or `org.oxycblt.musikr`. Do not read `app/` or `musikr/` at all.
2. `collectAsStateWithLifecycle` is NOT available. Use `collectAsState()`.
3. Do not modify `TouchWheel.kt`, `WheelZone.kt`, the contract, anything under `support/`,
   or any `build.gradle`.
4. Every `session.library.xxx()` returns a NEW Flow — keep the existing `remember` keying
   exactly as it is. Do not introduce an unremembered flow call in a composition body.

Pinned: Kotlin 2.3.10, Compose BOM 2026.06.01, paging 3.5.0, minSdk 24, Java 21.

## A trap to avoid

A trailing lambda binds to the LAST parameter. `wheelListBody(title, items, index) { ... }`
silently bound the lambda to `key: ((T) -> Any)?` instead of `itemLabel: (T) -> String`,
because `(T) -> String` is a subtype of `(T) -> Any`. Pass these arguments **by name**.

## Verify

You CANNOT run Gradle in this sandbox — it fails with
`java.net.SocketException: Operation not permitted`. Do not try.

Re-read your files and check every symbol against its declaration: exact parameter names,
order, nullability, and whether something returns `Flow`, `StateFlow`, `PagingData`, or a
plain `List`. Confirm `wheelItems` no longer appears anywhere:
`grep -rn "wheelItems" skin/` must return nothing.

## Report to stdout

1. Files changed
2. The paged `WheelMenuList` overload's signature as you wrote it, and how you decided
   `isSelected`
3. Where you used `peek` vs indexed access, and why each
4. How `wheelScaffold` now gets its item count and resolves CENTER
5. Anything in the contract or support layer that was still missing or awkward
