Wire the newly-built skin support layer into the two existing debug skins in the Shellac
Android project. Nothing in `org.shellac.skin.support` has a consumer yet, so none of it has
ever executed. This task is that consumer, and doubles as the Phase 4 test harness.

Working directory is the repo root.

## Files to modify — ONLY these two

- `skin/src/main/java/org/shellac/skin/debug/DebugSkinA.kt`
- `skin/src/main/java/org/shellac/skin/debug/DebugSkinB.kt`

Do not create files. Do not modify anything under `support/` or the contract. Do not touch
any `build.gradle`.

## Hard constraints

1. **Only `org.shellac.skin` types plus Compose/Kotlin stdlib.** Do NOT import
   `org.oxycblt.auxio` or `org.oxycblt.musikr`. Do not read `app/` or `musikr/` at all.
2. `collectAsStateWithLifecycle` is NOT available. Use `collectAsState()`.

Pinned: Kotlin 2.3.10, Compose BOM 2026.06.01, Coil 3.4.0, minSdk 24, Java 21.

## Read these first — use their real signatures, do not guess

- `skin/src/main/java/org/shellac/skin/support/PositionTicker.kt`
- `skin/src/main/java/org/shellac/skin/support/RotaryGesture.kt`
- `skin/src/main/java/org/shellac/skin/support/SkinAlbumArt.kt`
- `skin/src/main/java/org/shellac/skin/support/SkinTheme.kt`
- `skin/src/main/java/org/shellac/skin/support/SkinBackStack.kt`
- `skin/src/main/java/org/shellac/skin/support/SkinStates.kt`

## What to change

### 1. Position ticker (4.1) — both skins
On the now-playing screen, replace the raw `playback.positionMs` readout with the
interpolated value from `PositionTicker`. Show BOTH, labelled, e.g.:

```
raw    01:23    (10 Hz engine)
smooth 01:23.4  (interpolated)
```

Showing them together is deliberate: it makes the difference visible during the manual test,
and makes a seek resync obvious.

Also render a progress bar driven by the interpolated value (a simple `Box` with a
fractional width is fine — no need for a Slider).

### 2. Album art (4.2) — both skins
- On the album list, render `SkinAlbumArt` at a small size (~48dp) per row.
- On now-playing, render it large (~200dp).
- Supply a DIFFERENT fallback composable in each skin (e.g. a coloured box with the first
  letter of the title) so the per-skin fallback requirement is visibly exercised.

### 3. Theming (4.3) — both skins
Wrap each skin's whole UI in `SkinTheme` with its own `SkinColors` / `SkinTypography`.
DebugSkinA uses a light palette with `darkSystemBars = false`; DebugSkinB uses a dark
palette with `darkSystemBars = true`. Read colours from `SkinTheme.colors` inside the skin
rather than the hardcoded values currently in these files, so the CompositionLocal path is
actually exercised.

### 4. Back stack (4.5) — DebugSkinA ONLY
Replace DebugSkinA's `screen` field with a `SkinBackStack`. Keep DebugSkinB on its existing
plain enum field. This is deliberate: 4.5 is an OPTIONAL primitive and a flat skin must not
be forced into it, so the two skins should demonstrate both approaches.

Remember `onBackPressed()` is not composable, so the back stack must live in a field on the
skin object, not only in composition.

### 5. Loading/empty states (4.6) — both skins
Replace the hand-rolled `when (loadState)` blocks with `SkinLibraryGate`, supplying each
skin's own styling for scanning / failed / empty / ready.

### 6. Rotary test area (4.4) — both skins
Add a bordered box (~150dp tall) on the now-playing screen using `Modifier.rotaryInput`.
It must:
- accumulate the total click delta and display it, e.g. `ROTARY: +7`
- display the last delta received (`+1` / `-1`)
- have a "reset" control zeroing the accumulator

This is the only way to verify 4.4 by hand: your spec's test is "rotary helper logs sensible
angle deltas". Clockwise must increase the number, counter-clockwise decrease it.

## Performance requirement

Every `session.library.xxx()` returns a NEW Flow. Never call one directly in a composition
body — wrap in `remember` keyed appropriately. This app previously churned repository
listeners several times a second exactly that way.

## Keep the skins visually distinct

A stays light/plain; B stays dark with numbered rows and heavier type.

## Verify

You CANNOT run Gradle in this sandbox — do not try. Re-read your files and check every
symbol against the six support files. Check exact parameter names, ordering, and whether
each returns `State<T>` (needing `by`) or a plain value.

## Report to stdout

1. Files changed
2. Each support API used, with its exact signature as you found it
3. Anything in the support layer that was awkward, missing, or badly named — this is its
   first real consumer, so this feedback is valuable
4. Anything you could not wire, and why
