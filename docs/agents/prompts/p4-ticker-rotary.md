Implement two pieces of skin support infrastructure for the Unruly Android project
(buildspec Phase 4.1 and 4.4). Both are subtle; correctness matters far more than features.

Working directory is the repo root.

## Where the code goes

Create NEW files under `skin/src/main/java/org/unruly/skin/support/`:
- `PositionTicker.kt`
- `RotaryGesture.kt`

## Hard constraints

1. **Only use `org.unruly.skin` types plus Compose/Kotlin stdlib.** Do NOT import
   `org.oxycblt.auxio` or `org.oxycblt.musikr` — the `skin` module cannot see them. Do not
   read `app/` or `musikr/` at all.
2. Do not modify existing files. Do not touch any `build.gradle`.
3. Read `skin/src/main/java/org/unruly/skin/SkinModels.kt` first — you need
   `SkinPlaybackState` (`positionMs`, `durationMs`, `isPlaying`).
4. `collectAsStateWithLifecycle` is NOT available here. Use `collectAsState()`.

Pinned: Kotlin 2.3.10, Compose BOM 2026.06.01, minSdk 24, Java 21.

---

## 4.1 — PositionTicker

**The problem.** The engine reports playback position at only **10 Hz** (Auxio polls every
100 ms). Rendering that directly gives visibly steppy progress bars and animations. Skins
need a smooth ~30–60 fps position that stays *honest* about the real value.

Provide a composable, roughly:

```kotlin
@Composable
fun rememberInterpolatedPosition(state: SkinPlaybackState): State<Long>
```

Requirements, all of which matter:

1. **Interpolate between engine updates.** Between the coarse updates, advance the position
   using elapsed wall-clock time. Use `withFrameMillis` inside a `LaunchedEffect` so it is
   driven by the frame clock, not a fixed `delay` loop — that gives smooth output at
   whatever rate the device actually renders.
2. **Freeze when paused.** When `isPlaying` is false, hold the last engine value exactly.
   Do not keep advancing.
3. **Resync on seek — the hard part.** When the engine reports a position that differs from
   the interpolated one by more than a small threshold (say 500 ms) in *either* direction,
   snap immediately to the engine value. Do not ease into it. A user scrubbing needs the
   readout to track the scrub, not glide behind it.
4. **Never run past the end.** Clamp to `durationMs`. The interpolator must not report a
   position beyond the track length while waiting for the next engine update at a track
   boundary.
5. **Never go backwards during normal playback.** Small engine jitter should not make the
   displayed position stutter backwards; only a genuine seek (over the threshold) may move
   it back.
6. Handle `durationMs == 0` and a null song without dividing by zero or crashing.

Also provide a small pure helper so the logic is testable without Compose:

```kotlin
internal fun interpolate(
    lastEnginePositionMs: Long,
    lastEngineUpdateUptimeMs: Long,
    nowUptimeMs: Long,
    isPlaying: Boolean,
    durationMs: Long
): Long
```

---

## 4.4 — RotaryGesture

A reusable circular-drag detector. The Touch Wheel skin needs it; later skins likely will.

Provide a `Modifier` extension, roughly:

```kotlin
fun Modifier.rotaryInput(
    degreesPerClick: Float = 13f,
    innerRadiusFraction: Float = 0.30f,
    outerRadiusFraction: Float = 1.0f,
    onClick: (delta: Int) -> Unit
): Modifier
```

Use `pointerInput` + `detectDragGestures` (or `awaitPointerEventScope`). Behaviour:

1. **Compute the angle** of each touch point relative to the centre of the composable, and
   accumulate the *change* in angle across the drag.
2. **⚠️ Handle wraparound.** Crossing the 0°/360° boundary must produce a small delta, not a
   ~360° jump. Normalise every step into `(-180, 180]`. **This is the single most common bug
   in rotary detectors — get it right.**
3. **Quantise** into discrete clicks of `degreesPerClick`. Emit `onClick(+1)` / `onClick(-1)`
   per click crossed, carrying the remainder forward so slow drags accumulate rather than
   being discarded. A drag of 3.5 clicks emits 3 and keeps 0.5.
4. **Clockwise is positive**, counter-clockwise negative. Screen coordinates have Y pointing
   *down*, so verify the sign rather than assuming.
5. **Ignore touches in the dead zone**: closer to the centre than `innerRadiusFraction`, or
   beyond `outerRadiusFraction`, of the composable's half-size. A centre press is a button,
   not a rotation.
6. **Reset accumulation on each new gesture** so a released and re-pressed finger does not
   inherit a stale remainder.
7. If a drag starts in the valid ring and moves into the dead zone, stop emitting until it
   returns — do not emit a huge delta on re-entry.

Provide a pure helper too:

```kotlin
internal fun angleDeltaDegrees(fromDegrees: Float, toDegrees: Float): Float
```
returning the shortest signed angular distance, normalised to `(-180, 180]`.

---

## Verify

You CANNOT run Gradle in this sandbox — it fails with
`java.net.SocketException: Operation not permitted`. Do not try.

Instead re-read your files and check: every imported symbol exists, the wraparound helper is
correct for cases like 359° → 1° (should be +2, not -358) and 1° → 359° (should be -2), and
the interpolator handles pause, seek, and track end.

## Report to stdout

1. Files created
2. The exact wraparound normalisation you used, and why it is correct
3. How you determined the sign convention for clockwise
4. Every edge case you handled in the interpolator, and any you knowingly did not
5. Anything in the contract that was missing for this work
