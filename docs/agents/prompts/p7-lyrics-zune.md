Add lyrics to the Zune skin in the Shellac Android project.

Working directory is the repo root.

## Searching this repo

Scope every search to `skin/src/main/java`. The repo vendors a 609 MB `media/` submodule
containing ffmpeg's test-vector suite; an unscoped search drowns in it.

## What already exists — read it and use its REAL signatures

- `skin/src/main/java/org/shellac/skin/SkinLyrics.kt` — `SkinLyricLine`, `SkinLyrics`,
  `SkinLyricsState`, `SkinLyricsSource`
- `skin/src/main/java/org/shellac/skin/support/LyricsSupport.kt` — `activeLyricIndexAt`.
  **Use it. Do not write your own.**
- `skin/src/main/java/org/shellac/skin/AppSkin.kt` — `SkinSession.lyrics`
- `skin/src/main/java/org/shellac/skin/support/PositionTicker.kt` —
  `rememberInterpolatedPosition`
- `skin/src/main/java/org/shellac/skin/zune/` — the whole skin: `ZuneSkin.kt`,
  `ZuneTheme.kt` (`BlackFrame`, `AccentLink`, `SkinPaletteColors`, `SkinPaletteTypography`),
  `ZuneScreens.kt`

## Your job

Only files under `skin/src/main/java/org/shellac/skin/zune/`. Another agent is working in
`org.shellac.skin.wheel` — do not touch it. Do NOT modify the contract, `support/`, or any
`build.gradle`.

## Hard constraints

1. **Only `org.shellac.skin` types plus Compose/Kotlin stdlib.** Do NOT import
   `org.oxycblt.auxio` or `org.oxycblt.musikr`. Do not read `app/` or `musikr/` at all.
2. Do NOT import anything from `org.shellac.skin.wheel`. This skin shares nothing with it —
   that independence is the point of it existing.
3. `collectAsStateWithLifecycle` is NOT available. Use `collectAsState()`.
4. Pass function-typed arguments **by name** when a function takes more than one — a trailing
   lambda binds to the LAST parameter and function subtyping hides the mistake.

Pinned: Kotlin 2.3.10, Compose BOM 2026.06.01, paging 3.5.0, minSdk 24, Java 21.

## Where it goes

The now-playing screen is full-bleed album art with the track details over its lower half, and
`queue` / `settings` links at the top right. Add a `lyrics` link beside those, opening a
lyrics view for the playing track.

Style it as the rest of this skin: black ground, large light lowercase type, one orange
accent, generous left margin, no chrome. The current line should use the accent colour. Match
`ZuneTheme.kt`'s existing primitives rather than inventing new ones.

Handle **every** `SkinLyricsState`, because all of them happen:

| State | Show |
|---|---|
| `NoTrack` | "nothing playing" |
| `Disabled` | that lookup is off, and that it is switchable in settings |
| `Loading` | "loading lyrics" |
| `NotFound` | "no lyrics found" — an ordinary outcome, not an error |
| `Failed` | the message, phrased as a retryable problem |
| `Found` | the lyrics |

## Synced vs unsynced

`SkinLyrics.isSynced` tells you which you have. **Both must work.**

- **Synced**: highlight the line for the current position and keep it in view. Get the index
  from `activeLyricIndexAt(lines, positionMs)` — it returns **-1 before the first line**,
  which is correct during an intro and must not be treated as line 0. Drive it from
  `rememberInterpolatedPosition(playback)`, not `playback.positionMs`: the engine reports at
  10 Hz and the raw value visibly steps.
- **Unsynced**: render `plain` as a plain scrollable column with no highlight.

Auto-scroll the active line into view for synced lyrics. Let the user scroll freely; do not
yank the list back mid-gesture.

## The track changing underneath

The view stays open across track changes. When the playing track changes it must re-request
for the new track and reset its scroll. Key your `remember`/`LaunchedEffect` on the song id.

**`session.lyrics.lyrics(songId)` returns a NEW cold Flow on every call.** Never call it in a
composition body without `remember` keyed on the song id, or you will re-issue a request on
every recomposition.

## Back behaviour

The skin holds navigation in a field-backed `SkinBackStack` because `onBackPressed()` is not
composable. Add the lyrics destination to that model the same way the existing screens do, so
back returns to now-playing.

## Verify

You CANNOT run Gradle in this sandbox — it fails with
`java.net.SocketException: Operation not permitted`. Do not try.

Re-read your files and check every symbol against its declaration: exact parameter names,
order, nullability, and whether something returns `Flow`, `StateFlow` or a plain value.
Confirm: `grep -rn "org.shellac.skin.wheel" skin/src/main/java/org/shellac/skin/zune/` returns
nothing.

## Report to stdout

1. Files created and changed
2. How you handled synced vs unsynced
3. How auto-scroll coexists with manual scrolling
4. How a track change mid-screen is handled
5. Anything in the contract or support layer that was missing or awkward
