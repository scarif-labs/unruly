Build the queue and search screens for the Touch Wheel skin in the Unruly Android project
(buildspec Phase 5g).

Working directory is the repo root.

## Naming

**Touch Wheel** — never "iPod", never "click wheel". Do not reference Apple anywhere in code
or comments.

## Searching this repo

Scope every search to `skin/` — e.g. `rg -n "pattern" skin/src/main/java`. The repo vendors
a 609 MB `media/` submodule containing ffmpeg's test-vector suite; an unscoped search drowns
in it. In particular **never search for a bare phase id like `5g`** — short hex-like strings
match every checksum in those fixtures.

## Where the code goes

New files under `skin/src/main/java/org/unruly/skin/wheel/`:
- `WheelQueue.kt`  — queue screen
- `WheelSearch.kt` — search screen

Edit `TouchWheelSkin.kt` and `WheelDestination.kt` to add and route the destinations.

Do NOT modify `TouchWheel.kt`, `WheelZone.kt`, `WheelMenuList.kt`, `WheelNowPlaying.kt`,
the contract, anything under `support/`, or any `build.gradle`.

## Hard constraints

1. **Only `org.unruly.skin` types plus Compose/Kotlin stdlib.** Do NOT import
   `org.oxycblt.auxio` or `org.oxycblt.musikr`. Do not read `app/` or `musikr/` at all.
2. `collectAsStateWithLifecycle` is NOT available. Use `collectAsState()`.
3. Pass function-typed arguments **by name** when a function takes more than one — a trailing
   lambda binds to the LAST parameter and function subtyping hides the mistake.

Pinned: Kotlin 2.3.10, Compose BOM 2026.06.01, paging 3.5.0, minSdk 24, Java 21.

## Read these first and use their REAL signatures

- `skin/src/main/java/org/unruly/skin/SkinLibrary.kt` — `search(...)`, `SkinSearchResults`
- `skin/src/main/java/org/unruly/skin/SkinCommands.kt` — queue commands, `playSongFrom*`
- `skin/src/main/java/org/unruly/skin/SkinModels.kt` — `SkinPlaybackState.queue`, `queueIndex`
- `skin/src/main/java/org/unruly/skin/wheel/TouchWheelSkin.kt` — `wheelScaffold`,
  `wheelListBody`, `selectionIndices`, the back stack
- `skin/src/main/java/org/unruly/skin/wheel/WheelMenuList.kt` — both overloads

`wheelScaffold` takes `itemCount` plus `resolveSelected: (Int) -> T?`. Reuse it; do not build
a parallel scaffold.

## Queue screen

Reachable from the root menu — add a `Queue` entry.

- Lists `playback.queue`, with the currently playing item marked (a `▶` prefix or similar).
- Selection starts on the **currently playing item** (`playback.queueIndex`), not at the top.
- CENTER on an item → `playQueueItem(index)`.
- The queue changes underneath as tracks advance. Do not let the selection jump around while
  the user is browsing: seed from `queueIndex` when the screen opens, then leave it alone.

### Reordering and removal

The contract has `moveInQueue(from, to)` and `removeFromQueue(index)`. The wheel has no
obvious gesture for these, so use an explicit item mode:

- CENTER on the selected item opens a small action list for that track: `Play`, `Move Up`,
  `Move Down`, `Remove`, `Cancel`.
- `Move Up`/`Move Down` call `moveInQueue` and keep the selection **following the moved
  track**, so repeated moves walk it through the queue.
- Guard the ends: no move up at index 0, no move down at the last index.

## Search screen

Reachable from the Music menu — add a `Search` entry.

Wheel-driven character selection:

- An alphabet strip (`A`–`Z`, `0`–`9`, space, plus a `DEL` entry).
- Rotation moves through the characters; CENTER appends the highlighted one (or deletes).
- Show the query string being built, prominently.
- Below it, live results as the query changes.

**Debounce the query.** Do not call `search()` on every character — wait ~250 ms after the
last keystroke, via a `LaunchedEffect` keyed on the query with a `delay`, so each new
character cancels the pending search.

Results are grouped (songs / albums / artists). Render them as one flat list with section
headers, since the wheel moves through a single index. CENTER on:
- a song → play it (`playSongFromAll(song.id)`)
- an album → its songs
- an artist → its albums

Empty query → no results and no search call. No matches → say so.

## Reuse `SkinLibraryGate`

Wrap anything reading the library in `SkinLibraryGate` from `support/SkinStates.kt`. Do not
hand-roll the `when` over `LibraryLoadState`.

## Two traps this skin has already hit

1. **Do not materialise a paged flow.** Never call `items[itemCount - 1]` or otherwise force
   Paging to load to the end. Use the `LazyPagingItems` overload of `WheelMenuList`, and
   `peek(index)` (not `get`) to resolve a selection outside composition.
2. **Do not clobber a restored selection index.** `wheelScaffold` skips its re-clamp while
   `itemCount == 0`; if you add any similar effect, guard it the same way, or a screen will
   reset to the top before its first page loads.

## Verify

You CANNOT run Gradle in this sandbox — it fails with
`java.net.SocketException: Operation not permitted`. Do not try.

Re-read your files and check every symbol against its declaration: exact parameter names,
order, nullability, and whether something returns `Flow`, `StateFlow`, `PagingData`, or a
plain `List`.

## Report to stdout

1. Files created and changed
2. The exact signature of `search(...)` as you found it, and how you debounced
3. How the queue selection follows a moved track, and how you guarded the ends
4. How you kept the queue selection stable while tracks advance underneath
5. Anything in the contract or support layer that was missing, awkward, or forced a
   workaround
