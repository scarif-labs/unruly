Build the wheel component for the "Touch Wheel" skin in the Unruly Android project
(buildspec Phase 5a and 5b). Geometry and hit-testing only — no menus, no playback yet.

Working directory is the repo root.

## Naming — important

The skin is called **Touch Wheel**, never "iPod" and never "click wheel". Apple holds
trademarks and design patents here. Use `TouchWheel` / `touchwheel` in all type and package
names, and do not reference Apple anywhere in code or comments.

## Where the code goes

Create NEW files under `skin/src/main/java/org/unruly/skin/wheel/`:
- `TouchWheel.kt`      — the wheel composable
- `WheelZone.kt`       — zone enum + hit-testing

Do NOT create a skin class implementing `AppSkin` yet. Do not modify any existing file. Do
not touch any `build.gradle`.

## Hard constraints

1. **Only `org.unruly.skin` types plus Compose/Kotlin stdlib.** Do NOT import
   `org.oxycblt.auxio` or `org.oxycblt.musikr`. Do not read `app/` or `musikr/`.
2. `collectAsStateWithLifecycle` is NOT available. Use `collectAsState()`.
3. Reuse the EXISTING rotary detector — do not write another one. Read
   `skin/src/main/java/org/unruly/skin/support/RotaryGesture.kt` and use
   `Modifier.rotaryInput(...)` exactly as it is declared.
4. Read `skin/src/main/java/org/unruly/skin/support/SkinTheme.kt` and take colours from
   `SkinTheme.colors` rather than hardcoding.

Pinned: Kotlin 2.3.10, Compose BOM 2026.06.01, minSdk 24, Java 21.

---

## 5a — Wheel component, static

```kotlin
enum class WheelZone { MENU, NEXT, PREVIOUS, PLAY_PAUSE, CENTER }

@Composable
fun TouchWheel(
    modifier: Modifier = Modifier,
    onZonePressed: (WheelZone) -> Unit = {},
    onRotate: (Int) -> Unit = {},
)
```

Requirements:

1. Draw with Canvas: an outer ring (annulus) and a filled centre button.
2. Four labelled zones around the ring at the cardinal positions:
   - **MENU** at top
   - **NEXT** at right
   - **PLAY_PAUSE** at bottom
   - **PREVIOUS** at left
   Draw short text labels ("MENU", "NEXT", "PREV", "PLAY") using
   `drawText` with a `TextMeasurer` (`rememberTextMeasurer()`), positioned at those points.
3. **The wheel must stay circular in any container.** Compute `radius` from
   `min(width, height) / 2` and centre it. It must not become an ellipse when the container
   is not square — this is what buildspec 5a's proportions test checks.
4. Centre button radius = `innerRadiusFraction * outerRadius`, default fraction `0.42f`.
   That must be a comfortable touch target: at a typical phone width the centre should be
   roughly 48dp across or larger. Expose the fraction as a parameter.
5. Provide a `wheelDiameterFor(maxWidth: Dp, maxHeight: Dp): Dp` helper returning the
   diameter the wheel should occupy, so a caller can lay out "content above, wheel below"
   without the wheel overflowing on short screens.

## 5b — Button input

Hit-testing via `pointerInput` + `detectTapGestures`:

1. A tap inside the centre radius → `onZonePressed(CENTER)`.
2. A tap in the ring → resolve to MENU / NEXT / PLAY_PAUSE / PREVIOUS by angle, splitting
   the ring into four **90° quadrants centred on each cardinal direction**. So MENU owns
   315°..45° (measuring clockwise from top), NEXT 45°..135°, and so on.
3. A tap outside the outer radius → nothing.
4. **Boundary taps must resolve predictably**, never fall through and never trigger two
   zones. Make each boundary belong to exactly one zone (half-open intervals) and say which
   in a comment.
5. Rotation via the existing `Modifier.rotaryInput` → `onRotate(delta)`. Pass through the
   same inner/outer fractions so the dead zones agree with the drawn geometry.
6. Taps and rotation must not fight: a tap should not emit rotation, and a drag should not
   emit a tap. `detectTapGestures` and `detectDragGestures` in separate `pointerInput`
   blocks handle this correctly — verify the ordering you use.

Provide a pure helper so the hit-testing is checkable without gestures:

```kotlin
internal fun zoneForAngle(degreesClockwiseFromTop: Float): WheelZone
```

and

```kotlin
internal fun zoneForPoint(
    dx: Float, dy: Float, outerRadius: Float, innerRadius: Float
): WheelZone?     // null when outside the wheel entirely
```

Note the angle convention: screen +Y points down. `zoneForAngle(0f)` must be MENU (top),
`zoneForAngle(90f)` NEXT (right), `zoneForAngle(180f)` PLAY_PAUSE (bottom),
`zoneForAngle(270f)` PREVIOUS (left). Normalise input to `[0, 360)` first.

## Verify

You CANNOT run Gradle in this sandbox — it fails with
`java.net.SocketException: Operation not permitted`. Do not try.

Re-read your files and check: every symbol exists, `rotaryInput`'s real parameter list
matches your call, and `zoneForAngle` returns the right zone for 0/45/90/135/180/225/270/315
and for wrapped values like 359 and -10.

## Report to stdout

1. Files created
2. The exact signature of `rotaryInput` as you found it, and how you called it
3. Your boundary convention, and which zone owns each of 45/135/225/315
4. How you kept the wheel circular in a non-square container
5. Anything in the support layer or contract that was missing or awkward
