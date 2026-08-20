Build the Touch Wheel skin's menu hierarchy for the Shellac Android project
(buildspec Phase 5e). This is the first real skin, and the first thing to exercise the whole
contract in anger.

Working directory is the repo root.

## Naming

**Touch Wheel** — never "iPod", never "click wheel". Use `TouchWheel` naming and do not
reference Apple anywhere in code or comments.

## Where the code goes

Create NEW files under `skin/src/main/java/org/shellac/skin/wheel/`:
- `TouchWheelSkin.kt`   — the `AppSkin` implementation
- `WheelDestination.kt` — the navigation model

Do NOT modify `TouchWheel.kt`, `WheelZone.kt`, or `WheelMenuList.kt` — they are finished and
verified. Do not modify the contract or anything under `support/`. Do not touch any
`build.gradle`.

## Hard constraints

1. **Only `org.shellac.skin` types plus Compose/Kotlin stdlib.** Do NOT import
   `org.oxycblt.auxio` or `org.oxycblt.musikr`. Do not read `app/` or `musikr/` at all.
2. `collectAsStateWithLifecycle` is NOT available. Use `collectAsState()`.

Pinned: Kotlin 2.3.10, Compose BOM 2026.06.01, paging 3.5.0, minSdk 24, Java 21.

## Read these first and use their REAL signatures

- `skin/src/main/java/org/shellac/skin/AppSkin.kt`
- `skin/src/main/java/org/shellac/skin/SkinLibrary.kt`
- `skin/src/main/java/org/shellac/skin/SkinCommands.kt`
- `skin/src/main/java/org/shellac/skin/SkinModels.kt`
- `skin/src/main/java/org/shellac/skin/wheel/TouchWheel.kt`
- `skin/src/main/java/org/shellac/skin/wheel/WheelMenuList.kt`
- `skin/src/main/java/org/shellac/skin/wheel/WheelZone.kt`
- `skin/src/main/java/org/shellac/skin/support/SkinBackStack.kt`
- `skin/src/main/java/org/shellac/skin/support/SkinStates.kt`
- `skin/src/main/java/org/shellac/skin/support/SkinTheme.kt`

## Screen layout

Split the screen: **content region on top, wheel below**. The wheel occupies roughly the
lower 45% and must stay circular and centred. Use `BoxWithConstraints` and the
`wheelDiameterFor` helper if `TouchWheel.kt` provides one.

## Navigation model

```kotlin
sealed interface WheelDestination {
    data object Root : WheelDestination
    data object MusicMenu : WheelDestination
    data object Artists : WheelDestination
    data object Albums : WheelDestination
    data object Songs : WheelDestination
    data object Genres : WheelDestination
    data object Playlists : WheelDestination
    data class ArtistAlbums(val artistId: String, val title: String) : WheelDestination
    data class AlbumSongs(val albumId: String, val title: String) : WheelDestination
    data class GenreSongs(val genreId: String, val title: String) : WheelDestination
    data class PlaylistSongs(val playlistId: String, val title: String) : WheelDestination
    data object NowPlaying : WheelDestination
}
```

### Menu structure

- **Root**: `Music`, `Shuffle Songs`, `Now Playing`
- **Music**: `Artists`, `Albums`, `Songs`, `Genres`, `Playlists`
- **Artists** → pick artist → that artist's albums → pick album → its songs → pick song → plays, go to NowPlaying
- **Albums** → pick album → its songs → pick song → plays
- **Songs** → pick song → plays
- **Genres** → pick genre → its songs → pick song → plays
- **Playlists** → pick playlist → its songs → pick song → plays
- `Shuffle Songs` at root calls `shuffleAll()` and goes to NowPlaying
- `Now Playing` at root goes to NowPlaying without changing playback

## Wheel wiring

- **Rotation** moves the selection in the current list (use `WheelMenuState`).
- **CENTER** activates the selected item (descend, or play).
- **MENU** ascends one level. At Root it does nothing — the skin returns false from
  `onBackPressed()` so the host exits.
- **PLAY_PAUSE** → `session.commands.playPause()` from anywhere.
- **NEXT** / **PREVIOUS** → `next()` / `previous()` from anywhere.

## Navigation state — read this carefully

`AppSkin.onBackPressed()` is NOT composable and cannot read a `rememberSaveable`. Hold the
back stack in a **field on the skin object** — use `SkinBackStack` from `support/`. Read
`AppSkin.kt`'s rule 2, which documents this explicitly.

`onBackPressed()` must pop the stack and return true, or return false when already at Root.
The MENU zone must do exactly the same thing as system back.

Each destination needs its **own selection index**, preserved when you ascend — descending
into an album and coming back must land on the album you were on, not reset to the top.
Keep a per-destination index map keyed by the destination.

## Loading and empty states

Wrap list screens in `SkinLibraryGate` from `support/SkinStates.kt`. Do not hand-roll the
`when` over `LibraryLoadState`.

## Now playing (minimal for this sub-phase)

Text only: title, artist, album, and `n of m` from the queue. Phase 5f builds the real one —
do NOT build art, a progress bar, or scrubbing here.

## Performance

Every `session.library.xxx()` returns a NEW Flow. NEVER call one in a composition body
without `remember` keyed appropriately — this app previously churned repository listeners
several times a second exactly that way. `songsByAlbum(id)` etc. must be remembered per id.

## Theming

Wrap the skin in `SkinTheme` with its own palette. Something restrained and monochrome suits
this skin; do not copy the debug skins' colours.

## Verify

You CANNOT run Gradle in this sandbox — it fails with
`java.net.SocketException: Operation not permitted`. Do not try.

Re-read your files and check every symbol against the files listed above — exact parameter
names, ordering, nullability, and whether something returns `Flow`, `StateFlow`,
`PagingData`, or a plain `List`. Note that `songsByAlbum` and `songsByPlaylist` return
`Flow<List<SkinSong>>` while `albums()`, `artists()`, `songs()`, `genres()`, `playlists()`,
`albumsByArtist()`, and `songsByGenre()` return `Flow<PagingData<...>>`.

## Report to stdout

1. Files created
2. How you handled the paged-vs-list difference in `SkinLibrary`
3. How the back stack is held so `onBackPressed()` can reach it
4. How per-destination selection indices are preserved
5. **Anything in the contract that was missing, awkward, or forced a workaround** — this is
   the first real skin, so this feedback is the most valuable output of the task
