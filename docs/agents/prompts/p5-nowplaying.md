Build the now-playing view for the Touch Wheel skin in the Unruly Android project
(buildspec Phase 5f).

Working directory is the repo root.

## Naming

**Touch Wheel** — never "iPod", never "click wheel". Do not reference Apple anywhere in code
or comments.

## What exists now

`skin/src/main/java/org/unruly/skin/wheel/TouchWheelSkin.kt` has a placeholder
`nowPlayingScreen(session)` that renders title/artist/album/`n of m` as plain text. Replace
its body. Phase 5e deliberately scoped art, progress and scrubbing out; this is that work.

## Where the code goes

- New file: `skin/src/main/java/org/unruly/skin/wheel/WheelNowPlaying.kt`
- Edit `TouchWheelSkin.kt` only to delegate `nowPlayingScreen` to it and to route rotation
  (see "Scrubbing" below).

Do NOT modify `TouchWheel.kt`, `WheelZone.kt`, `WheelMenuList.kt`, `WheelDestination.kt`,
the contract, anything under `support/`, or any `build.gradle`.

## Hard constraints

1. **Only `org.unruly.skin` types plus Compose/Kotlin stdlib.** Do NOT import
   `org.oxycblt.auxio` or `org.oxycblt.musikr`. Do not read `app/` or `musikr/` at all.
2. `collectAsStateWithLifecycle` is NOT available. Use `collectAsState()`.
3. Pass function-typed arguments **by name** when a function takes more than one — a
   trailing lambda binds to the LAST parameter and function subtyping hides the mistake.

Pinned: Kotlin 2.3.10, Compose BOM 2026.06.01, paging 3.5.0, minSdk 24, Java 21.

## Read these first and use their REAL signatures

- `skin/src/main/java/org/unruly/skin/SkinModels.kt` — `SkinPlaybackState`, `SkinSong`
- `skin/src/main/java/org/unruly/skin/SkinCommands.kt` — note `seekTo(positionMs: Long)`
- `skin/src/main/java/org/unruly/skin/support/PositionTicker.kt` —
  `rememberInterpolatedPosition(state: SkinPlaybackState): State<Long>`
- `skin/src/main/java/org/unruly/skin/support/SkinAlbumArt.kt` —
  `SkinAlbumArt(uri, contentDescription, modifier, contentScale, fallback)`
- `skin/src/main/java/org/unruly/skin/support/SkinTheme.kt`
- `skin/src/main/java/org/unruly/skin/wheel/TouchWheelSkin.kt`

## What to display

Above the wheel: album art, title, artist, album, a progress bar, `n of m` for the queue
position, and elapsed / remaining times.

- Art via `SkinAlbumArt` with `song.albumArtUri`. Supply a `fallback` — a plain filled
  rectangle in `SkinTheme.colors.surface`. Art must stay **square** and must not distort.
- **Use `rememberInterpolatedPosition(playback)` for the progress bar and elapsed time**, not
  `playback.positionMs` directly. The engine reports position at only 10 Hz (deciseconds), so
  the raw value visibly steps. The ticker interpolates against the frame clock.
- Draw the progress bar yourself with `Canvas` — do not pull in Material's.
- No song loaded: show "Nothing playing" and no progress bar.

### Time formatting

Write a pure, testable helper:

```kotlin
internal fun formatTime(ms: Long): String
```

`m:ss` under an hour (`0:00`, `4:07`), `h:mm:ss` at or over one (`1:02:03`). Negative or
nonsense input clamps to `0:00`. Show elapsed as `formatTime(position)` and remaining as
`-` + `formatTime(duration - position)`.

**`durationMs` can be 0 or unknown.** Guard every division — a zero-duration track must not
produce a NaN progress fraction or crash. Write the fraction helper pure and testable too:

```kotlin
internal fun progressFraction(positionMs: Long, durationMs: Long): Float  // always in 0f..1f
```

## Scrubbing — the substantial part

On the now-playing screen the wheel **scrubs position** instead of moving a selection.

Naively calling `seekTo` on every rotation click would fire tens of seeks a second at the
engine. Don't. Instead:

1. While rotating, keep a **local scrub target** in state and move it by a sensible amount
   per click. Use a percentage of track duration, floored to at least ~1 second per click, so
   scrubbing a 20-minute track is not unusably slow and a 90-second one is not jumpy.
2. **Display the scrub target** while scrubbing — the progress bar and elapsed time follow
   the scrub, not the still-playing engine position. Otherwise the two fight visibly.
3. **Commit with a single `seekTo(target)`** once rotation has been idle for ~300 ms. Use a
   `LaunchedEffect` keyed on the scrub target with a `delay`, so each new click cancels and
   restarts the timer.
4. After committing, return to following the engine. Take care around the handover: the
   engine needs a moment to report the new position, and dropping straight back to the live
   value will make the bar jump backwards to where it was before the seek. Handle it and
   explain in a comment how.
5. Clamp the target to `0..durationMs`.

Scrubbing must be visibly distinct — show the elapsed time differently (accent colour) while
scrubbing so it is clear the number is a target, not the current position.

### Wheel wiring on this screen

- **Rotation** → scrub, as above.
- **PLAY_PAUSE / NEXT / PREVIOUS** → unchanged (`playPause()`, `next()`, `previous()`).
- **MENU** → back, unchanged.
- **CENTER** → nothing for now. Do not invent a mode toggle.

`TouchWheelSkin.wheelScaffold` currently routes rotation into `WheelMenuState`. Route it to
the scrub handler for `NowPlaying` instead. Keep the change small and obvious; do not
restructure `wheelScaffold` for every other screen.

## Verify

You CANNOT run Gradle in this sandbox — it fails with
`java.net.SocketException: Operation not permitted`. Do not try.

Re-read your files and check every symbol against its declaration: exact parameter names,
order, nullability, and whether something returns `Flow`, `StateFlow` or a plain value.

Check `formatTime` for: `0 -> "0:00"`, `7000 -> "0:07"`, `67000 -> "1:07"`, `247000 -> "4:07"`,
`3600000 -> "1:00:00"`, `3723000 -> "1:02:03"`, `-5 -> "0:00"`.
Check `progressFraction` for: `(0, 0) -> 0f`, `(50, 0) -> 0f` (no divide by zero),
`(0, 100) -> 0f`, `(50, 100) -> 0.5f`, `(200, 100) -> 1f`, `(-5, 100) -> 0f`.

## Report to stdout

1. Files created and changed
2. Your scrub step size, and why it behaves for both a 90-second track and a 20-minute one
3. Exactly how you avoid the bar jumping backwards after a seek commits (point 4 above)
4. Every place you guarded against `durationMs == 0`
5. Anything in the contract or support layer that was missing, awkward, or forced a
   workaround
