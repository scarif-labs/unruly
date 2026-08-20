Add a lyrics screen to the Touch Wheel skin in the Shellac Android project.

Working directory is the repo root.

## Searching this repo

Scope every search to `skin/src/main/java`. The repo vendors a 609 MB `media/` submodule
containing ffmpeg's test-vector suite; an unscoped search drowns in it.

## Naming

**Touch Wheel** — never "iPod", never "click wheel". Do not reference Apple anywhere.

## What already exists — read it and use its REAL signatures

- `skin/src/main/java/org/shellac/skin/SkinLyrics.kt` — `SkinLyricLine`, `SkinLyrics`,
  `SkinLyricsState`, `SkinLyricsSource`
- `skin/src/main/java/org/shellac/skin/support/LyricsSupport.kt` — `activeLyricIndexAt`.
  **Use it. Do not write your own.**
- `skin/src/main/java/org/shellac/skin/AppSkin.kt` — `SkinSession.lyrics`
- `skin/src/main/java/org/shellac/skin/support/PositionTicker.kt` —
  `rememberInterpolatedPosition`
- `skin/src/main/java/org/shellac/skin/wheel/` — the whole skin. In particular
  `WheelChrome.kt` (`ClassicTitleBar`, `ClassicMenuRow`, `ClassicSplitLayout`,
  `ClassicColors`, `ClassicTypography`), `WheelMenuList.kt`, `TouchWheelSkin.kt`
  (`wheelScaffold`, `navigateTo`, `WheelDestination`)

## Your job

Only these files:
- NEW `skin/src/main/java/org/shellac/skin/wheel/WheelLyrics.kt`
- `WheelDestination.kt` — add a `Lyrics` destination
- `TouchWheelSkin.kt` — route it and add the entry point

Do NOT modify `TouchWheel.kt`, `WheelZone.kt`, `WheelMenuList.kt`, `WheelChrome.kt`,
`WheelNowPlaying.kt`, `WheelQueue.kt`, `WheelSearch.kt`, the contract, `support/`, or any
`build.gradle`. Another agent is working in `org.shellac.skin.zune` — do not touch it.

## Hard constraints

1. **Only `org.shellac.skin` types plus Compose/Kotlin stdlib.** Do NOT import
   `org.oxycblt.auxio` or `org.oxycblt.musikr`. Do not read `app/` or `musikr/` at all.
2. `collectAsStateWithLifecycle` is NOT available. Use `collectAsState()`.
3. Pass function-typed arguments **by name** when a function takes more than one — a trailing
   lambda binds to the LAST parameter and function subtyping hides the mistake.

Pinned: Kotlin 2.3.10, Compose BOM 2026.06.01, paging 3.5.0, minSdk 24, Java 21.

## Entry point

Add a **Lyrics** row to the root menu, alongside Music / Queue / Shuffle Songs / Now Playing /
Settings. It opens the lyrics screen for whatever is playing.

## The screen

Styled like the rest of this skin — `ClassicTitleBar` titled `Lyrics`, white ground, black
text, the blue accent for the current line. Reuse `WheelChrome.kt`; do not invent new styling.

Handle **every** `SkinLyricsState`, because all of them happen:

| State | Show |
|---|---|
| `NoTrack` | "Nothing playing" |
| `Disabled` | that lookup is off, and that it is switchable in Settings |
| `Loading` | "Loading lyrics…" |
| `NotFound` | "No lyrics found" — an ordinary outcome, not an error |
| `Failed` | the message, phrased as a retryable problem |
| `Found` | the lyrics |

### Synced vs unsynced

`SkinLyrics.isSynced` tells you which you have. **Both must work.**

- **Synced**: highlight the line for the current position. Get the index from
  `activeLyricIndexAt(lines, positionMs)` — it returns **-1 before the first line**, which is
  correct during an intro and must not be treated as line 0. Drive it from
  `rememberInterpolatedPosition(playback)`, not from `playback.positionMs`: the engine reports
  at 10 Hz and the raw value visibly steps.
- **Unsynced**: render `plain` as a scrollable list with no highlight.

### Wheel wiring

- **Rotation** scrolls the lyrics. For synced lyrics rotation should move the *view* — do not
  let it fight the automatic follow-along.
  Simplest correct behaviour: auto-follow the current line, and let rotation scroll freely;
  resume following after a few seconds of no rotation. If that is more than you want to build,
  auto-follow only and ignore rotation is acceptable — but say which you did.
- **MENU** — back, unchanged.
- **PLAY_PAUSE / NEXT / PREVIOUS** — unchanged.

### The track changing underneath

The screen stays open across track changes. When the playing track changes, the screen must
re-request for the new track and reset its scroll. Key your `remember`/`LaunchedEffect` on the
song id.

**`session.lyrics.lyrics(songId)` returns a NEW cold Flow on every call.** Never call it in a
composition body without `remember` keyed on the song id, or you will re-issue a request on
every recomposition.

## Verify

You CANNOT run Gradle in this sandbox — it fails with
`java.net.SocketException: Operation not permitted`. Do not try.

Re-read your files and check every symbol against its declaration: exact parameter names,
order, nullability, and whether something returns `Flow`, `StateFlow` or a plain value.

## Report to stdout

1. Files created and changed
2. How you handled synced vs unsynced
3. What rotation does, and how it interacts with auto-follow
4. How a track change mid-screen is handled
5. Anything in the contract or support layer that was missing or awkward
