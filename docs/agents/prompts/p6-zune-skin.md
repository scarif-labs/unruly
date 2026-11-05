Build a second complete skin for the Unruly Android project, styled after the Zune HD music
player (buildspec Phase 6.1 "second skin").

Working directory is the repo root.

## Searching this repo

Scope every search to `skin/` — e.g. `rg -n "pattern" skin/src/main/java`. The repo vendors a
609 MB `media/` submodule containing ffmpeg's test-vector suite; an unscoped search drowns in
it. Never search for a bare short token like a phase id — hex-like strings match every
checksum in those fixtures.

## Why this skin exists

It is the proof that the skin contract is genuinely general. The existing skin is a
wheel-driven hierarchy modelled on a click-wheel device. This one must share **nothing** with
it: different navigation model, different input model, different visual language. If you find
yourself wanting to import anything from `org.unruly.skin.wheel`, stop — that is the signal
the contract is being bypassed.

## Where the code goes

New package: `skin/src/main/java/org/unruly/skin/zune/`

Suggested split (adjust if you have a better one):
- `ZuneSkin.kt`       — the `AppSkin` implementation and navigation
- `ZuneTheme.kt`      — palette, typography, shared visual primitives
- `ZuneScreens.kt`    — the individual screens

Do NOT modify anything outside that package. Not `org.unruly.skin.wheel`, not the contract,
not `support/`, not any `build.gradle`. Registration in the host is handled separately — just
export a `class ZuneSkin : AppSkin`.

## Hard constraints

1. **Only `org.unruly.skin` types plus Compose/Kotlin/Paging.** Do NOT import
   `org.oxycblt.auxio` or `org.oxycblt.musikr`. Do not read `app/` or `musikr/` at all.
2. `collectAsStateWithLifecycle` is NOT available. Use `collectAsState()`.
3. Pass function-typed arguments **by name** when a function takes more than one — a trailing
   lambda binds to the LAST parameter and function subtyping hides the mistake.
4. Do not reference Microsoft or Zune trademarks in identifiers. `ZuneSkin` as the class name
   is fine as an internal handle, but do not put logos, brand marks, or product names into
   user-visible strings beyond the skin's `displayName`.

Pinned: Kotlin 2.3.10, Compose BOM 2026.06.01, paging 3.5.0, minSdk 24, Java 21.

## Read these first and use their REAL signatures

- `skin/src/main/java/org/unruly/skin/AppSkin.kt` — `AppSkin`, `SkinSession`
- `skin/src/main/java/org/unruly/skin/SkinLibrary.kt`
- `skin/src/main/java/org/unruly/skin/SkinCommands.kt`
- `skin/src/main/java/org/unruly/skin/SkinModels.kt`
- `skin/src/main/java/org/unruly/skin/support/` — all of it; reuse these rather than
  reimplementing (`SkinTheme`, `SkinAlbumArt`, `SkinBackStack`, `SkinStates`,
  `PositionTicker`)

## The visual language

Zune HD's interface is the direct precursor of Metro. What defines it:

- **Black background**, near-black rather than grey. Content floats on it with no cards, no
  borders, no shadows.
- **Typography is the interface.** Very large, light-weight, tightly-tracked headings in
  **lowercase**. Section titles are oversized — big enough that they often **clip at the right
  edge of the screen**, deliberately. That bleed is a signature, not a bug: it implies the
  content continues beyond the frame.
- **One hot accent colour** against the black. Use a warm orange (`0xFFF0640A`-ish). Accent is
  used sparingly — the active pivot, the progress fill, the playing item.
- **Horizontal pivot navigation.** A row of section headings (`artists  albums  songs
  genres  playlists`) where the active one is white/large and the neighbours are dimmed and
  partially off-screen. Swiping horizontally moves between them.
- **No chrome.** No title bars, no buttons with borders, no icons where a word will do.
- Generous whitespace and a strong left margin.

Use `HorizontalPager` from `androidx.compose.foundation.pager` for the pivot.

## What it must do

A complete UI — the contract requires a skin to own every screen:

1. **Library browsing** across artists, albums, songs, genres, playlists, as pivot sections.
2. **Drill-down**: artist → albums → songs; album → songs; genre → songs; playlist → songs.
3. **Now playing**: large album art, title/artist/album, progress bar, elapsed/remaining,
   play-pause, next, previous, and **draggable scrubbing** on the progress bar.
4. **Queue** view.
5. **Search** — a plain text field is correct here; this is a touchscreen device, not a wheel.
   Debounce the query ~250 ms via a `LaunchedEffect` keyed on it, so each keystroke cancels
   the pending search. Do NOT search on every character.
6. **A settings entry** that calls `session.openAppSettings()`, so the user can switch skins
   from inside this one.
7. `onBackPressed()` — pop your own navigation, return false at the root so the host exits.

## Traps this project has already hit — do not repeat them

1. **Never materialise a paged flow.** `albums()`, `artists()`, `songs()`, `genres()`,
   `playlists()`, `albumsByArtist()` and `songsByGenre()` return `Flow<PagingData<...>>`.
   Never call `items[itemCount - 1]` or otherwise force Paging to load to the end — it drags
   the whole library into memory and fails at the 20,000 tracks this project targets. Use
   `collectAsLazyPagingItems()` and index rows lazily; use `peek(index)` when resolving a
   selection outside composition.
   **Note the asymmetry:** `songsByAlbum` and `songsByPlaylist` return `Flow<List<SkinSong>>`,
   not paged.
2. **Every `session.library.xxx()` call returns a NEW Flow.** Never call one in a composition
   body without `remember` keyed appropriately, or you will churn repository listeners several
   times a second.
3. **`AppSkin.onBackPressed()` is not composable** and cannot read a `rememberSaveable`. Hold
   navigation state in a **field** on the skin object — use `SkinBackStack` from `support/`.
4. **Use `rememberInterpolatedPosition(playback)`** for the progress bar, not
   `playback.positionMs`. The engine reports position at 10 Hz, so the raw value visibly
   steps.
5. **Scrubbing must not seek per drag event.** Track a local target while dragging and issue
   one `seekTo` on release.
6. **Guard `durationMs == 0`.** Every division needs it, or a zero-duration track produces a
   NaN progress fraction.
7. **Playing a song from a paged list**: use `playSongFromAll(songId)` /
   `playSongFromGenre(songId, genreId)` etc. rather than `playSongs(list, index)`, which would
   need the whole collection materialised. Read the doc comment on those methods in
   `SkinCommands.kt`.

## Loading and empty states

Wrap library reads in `SkinLibraryGate` from `support/SkinStates.kt`. Do not hand-roll the
`when` over `LibraryLoadState`.

## Verify

You CANNOT run Gradle in this sandbox — it fails with
`java.net.SocketException: Operation not permitted`. Do not try.

Re-read your files and check every symbol against its declaration: exact parameter names,
order, nullability, and whether something returns `Flow`, `StateFlow`, `PagingData`, or a
plain `List`. Confirm the isolation gate: `grep -rn "org.oxycblt" skin/src/main/java/org/unruly/skin/zune/`
must return nothing.

## Report to stdout

1. Files created
2. Your navigation model, and how `onBackPressed` reaches it
3. How you handled the paged-vs-plain-list asymmetry
4. How scrubbing avoids seeking per drag event
5. **Anything in the contract that was missing, awkward, or forced a workaround.** This is the
   second independent skin, so this is the most valuable output of the task — it is the real
   test of whether the contract generalises beyond the skin it was designed alongside.
