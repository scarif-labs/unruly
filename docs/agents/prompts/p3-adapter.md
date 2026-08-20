Implement the Shellac engine adapter (buildspec Phase 3.5) in the Auxio Android project.

This is the ONLY class that is allowed to know both Auxio's internals and the Shellac skin
contract. Getting it right is what keeps skins portable.

Working directory is the repo root.

## Read these first, in full

1. `ARCHITECTURE-NOTES.md` (repo root) — the verified engine map. Every symbol in it was
   read from source and cited `path:line`. **Trust it over your own recollection.**
2. The contract, which you must implement exactly:
   - `skin/src/main/java/org/shellac/skin/SkinModels.kt`
   - `skin/src/main/java/org/shellac/skin/SkinLibrary.kt`
   - `skin/src/main/java/org/shellac/skin/SkinCommands.kt`
   - `skin/src/main/java/org/shellac/skin/AppSkin.kt`

## Hard rules

1. **NEVER modify anything under `app/src/main/java/org/oxycblt/auxio/playback/`,
   `app/src/main/java/org/oxycblt/auxio/music/`, or `musikr/`.** Read them freely; do not
   edit them. If the adapter seems to need a change there, STOP and report what is missing.
2. **Do not modify the contract in `skin/`.** If the contract is wrong or insufficient,
   STOP and report — do not "fix" it.
3. Do not touch any `build.gradle`.
4. Do not invent APIs. Every Auxio symbol must appear in `ARCHITECTURE-NOTES.md` or be one
   you have read in source. If you cannot find something, report it as NOT FOUND.

## Where to put it

`app/src/main/java/org/oxycblt/auxio/shellac/engine/` — suggested split:
`EngineAdapter.kt`, `SkinLibraryImpl.kt`, `SkinCommandsImpl.kt`, `Mappers.kt`.

## Pinned versions

Kotlin 2.3.10 · AGP 9.2.1 · Compose BOM 2026.06.01 · paging 3.5.0 · coroutines 1.10.2 ·
Java 21 · minSdk 24 · Hilt 2.59.2 (the project uses Hilt/Dagger with `@Inject` constructors)

## What to build

### A. Mappers — Auxio types to Shellac types
- ids: `music.uid.toString()` (see ARCHITECTURE-NOTES §4 "Identity" — **not** a Long)
- names: Auxio's `Name` is an object, not a String. Use the existing extension
  `org.oxycblt.auxio.music.resolve(context)` (`app/.../music/MusicUtil.kt:37`). The adapter
  therefore needs a `Context`.
- `SkinSong.year` = `song.date?.year` (DECISION D1)
- `SkinAlbum.year` = `album.dates?.min?.year` (DECISION D1)
- album art: read ARCHITECTURE-NOTES §4 "Album art", then read the actual Coil setup in
  source. Report exactly what identifies artwork — if it is not a `Uri`, say so clearly
  rather than forcing one, and report what the contract should use instead.

### B. `SkinPlaybackState` flow
Combine `PlaybackViewModel`'s `song`, `isPlaying`, `positionDs`, `repeatMode`, `isShuffled`
and `pagerQueue` into one `StateFlow<SkinPlaybackState>`.
⚠️ **`positionDs` is DECIseconds. `SkinPlaybackState.positionMs` is milliseconds. Convert
(`* 100`). This conversion must exist here and nowhere else.**
`durationMs` comes from the current song, not from the player.

### C. `SkinLibrary`
- `loadState`: bridge Auxio's `IndexingListener` / `IndexingState` into a
  `StateFlow<LibraryLoadState>` with `callbackFlow`. Map `Completed(error = null)` to
  `Ready`, `Completed(error != null)` to `Failed`, `Indexing` to `Scanning`.
- listings: the engine gives in-memory `Collection`s. Sort them per `SkinSort`, then wrap in
  `PagingData` — `PagingSource` over an in-memory list is fine and expected here.
- `albumsByArtist`: union `explicitAlbums` + `implicitAlbums`, setting
  `SkinAlbum.isExplicitForArtist` accordingly (DECISION D2).
- `search`: use Auxio's `SearchEngine` (`app/.../search/SearchEngine.kt:40`), feeding it the
  library. Debounce.

### D. `SkinCommands`
Read the `PlaySong` section of ARCHITECTURE-NOTES carefully. There is **no** `playAlbum` in
the engine. Each `playX` must: resolve the id → pick the start song → call
`PlaybackViewModel.play(song, PlaySong.FromX)`.
**Empty or unknown target = no-op, never an exception** (see the contract's doc comment).
`seekTo(positionMs)` must convert ms → ds before calling Auxio.

## Verify

You CANNOT run Gradle in this sandbox — it fails with
`java.net.SocketException: Operation not permitted`. Do not try; it wastes your budget.

Instead: re-read every file you wrote and check each Auxio symbol against
`ARCHITECTURE-NOTES.md` or the source. Compilation will be verified by the reviewer.

## Report to stdout

1. Files created
2. Every Auxio symbol used, with `path:line`
3. **Anything in ARCHITECTURE-NOTES.md that turned out to be wrong** — this matters a lot
4. Anything in the contract that could not be implemented against the real engine
5. What identifies album art, concretely
6. Any place you were forced to guess
