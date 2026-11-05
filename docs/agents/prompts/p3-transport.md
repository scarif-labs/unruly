Extend the two existing debug skins in the Unruly Android project so that EVERY command in the skin contract is reachable from the UI. Right now only play/pause is wired, so the rest of the contract has never been exercised at runtime. The point of this task is coverage, not looks.

Working directory is the repo root.

## Files to modify

- skin/src/main/java/org/unruly/skin/debug/DebugSkinA.kt
- skin/src/main/java/org/unruly/skin/debug/DebugSkinB.kt

Do not create new files. Do not modify anything else. Do not touch any build.gradle. Do not modify the contract files.

## Critical constraint

You may ONLY use types from the `org.unruly.skin` package plus Compose/Kotlin stdlib. Read these first:

- skin/src/main/java/org/unruly/skin/SkinCommands.kt
- skin/src/main/java/org/unruly/skin/SkinModels.kt
- skin/src/main/java/org/unruly/skin/SkinLibrary.kt
- skin/src/main/java/org/unruly/skin/AppSkin.kt

You must NOT import anything from `org.oxycblt.auxio` or `org.oxycblt.musikr`. The skin module cannot see those packages. Do not read app/ or musikr/ at all.

Available in this module: androidx.compose.runtime, androidx.compose.ui, androidx.compose.foundation, androidx.compose.material3, androidx.paging.compose. Use `collectAsState()` (NOT collectAsStateWithLifecycle, which is not available here).

## Pinned versions

Kotlin 2.3.10, Compose BOM 2026.06.01, paging 3.5.0, minSdk 24, Java 21.

## What to add to the now-playing screen of BOTH skins

Every one of these must call the corresponding `session.commands` method:

1. Previous / play-pause / next buttons in a row.
2. A seek control. A simple approach is fine: two buttons "-10s" and "+10s" that call `seekTo(positionMs +/- 10_000)`, clamped to `0..durationMs`. Read current position and duration from the playback state.
3. A shuffle toggle showing current `isShuffled` state.
4. A repeat control showing the current `SkinRepeatMode`. Use `cycleRepeatMode()` in one skin and `setRepeatMode(mode.next())` in the other, so both code paths get exercised.
5. Position/duration readout as `mm:ss / mm:ss`, derived from the playback state's millisecond values.

## Add a queue screen to BOTH skins

Reachable from now-playing, and a third level in the back stack (album list -> now playing -> queue). Update `onBackPressed()` accordingly so it pops correctly through all levels and only returns false at the album-list root.

The queue screen shows the queue from the playback state, with the current `queueIndex` visually marked, and per row:
- tap the row -> `playQueueItem(index)`
- a "remove" control -> `removeFromQueue(index)`
- "up"/"down" controls -> `moveInQueue(index, index - 1)` / `moveInQueue(index, index + 1)`, guarded at the ends

## Add to the album list screen of BOTH skins

Per album row, alongside the existing tap-to-play:
- "queue" -> load that album's songs and call `addToQueue(songs)`
- "next" -> call `playNext(songs)`

To get an album's songs use `session.library.songsByAlbum(albumId)`, which returns `Flow<List<SkinSong>>`.

IMPORTANT: do not call `songsByAlbum` in the composition body for every row - that creates a new flow per row per recomposition. Collect it only when the control is actually activated, or collect lazily for the expanded row only.

Also add a "shuffle all" control somewhere on the album list, calling `shuffleAll()`.

## Performance requirement - this matters

Every `session.library.xxx()` call returns a NEW Flow. Calling one directly in a composition body causes a fresh subscription on every recomposition. Wrap such calls in `remember { ... }` keyed appropriately. A previous version of this app churned repository listeners several times per second exactly this way.

## Keep the two skins distinguishable

DebugSkinA stays light-ish with plain rows. DebugSkinB stays dark with row-number prefixes and heavier text. Ugly is fine; both must remain visually distinct at a glance.

## Verify

You cannot run Gradle in this sandbox - do not try. Instead re-read your files and check every symbol against the four contract files. Check exact method names, parameter counts, and nullability.

## Report to stdout

1. Files changed
2. A checklist of every SkinCommands method and where it is now reachable in the UI
3. Any contract method you could NOT surface, and why
4. Anywhere the contract made this awkward
