# Winamp Chrome — shared API contract

**Purpose:** the setup flow and the settings screen are being restyled to resemble a
classic late-90s skinnable desktop media player. Two agents are working in parallel:

- **codex** owns `RetroChrome.kt` (the primitives) **and** `OnboardingActivity.kt`
- **cline** owns `UnrulySettingsActivity.kt`

They must not edit each other's files. This document is the contract between them: codex
**implements** exactly these signatures, cline **calls** them and assumes they exist.
Neither agent may change a signature listed here.

Package for everything below: `com.merklegroot.unruly.unruly.host`
File for the primitives: `app/src/main/java/com/merklegroot/unruly/unruly/host/RetroChrome.kt`
(replace its current contents entirely; the object is renamed `Retro` -> `Winamp`).

---

## Look being targeted

A dark navy application window floating on a near-black desktop:

- Chiselled 3D bevels — one light edge (top/left) and one dark edge (bottom/right) on every
  raised surface, inverted for recessed ones. **No rounded corners, no soft shadows, no
  Material elevation.**
- Title bar: a small green LED square at the left, a centred **letter-spaced** title in the
  form `- P R E F E R E N C E S -`, and `_ □ ×` glyphs at the right.
- A menu bar under the title bar: `File  Play  Options  View`, pale blue, first letter
  underlined.
- Readouts drawn as a recessed LCD panel: near-black inset, phosphor-green monospace text.
- Everything monospace (`FontFamily.Monospace`).

## Palette (`object Winamp`)

```kotlin
val Desktop     = Color(0xFF0A0F1C)  // page background behind the window
val WindowTop   = Color(0xFF1B2C4A)  // window body gradient start
val WindowBot   = Color(0xFF0E1A2E)  // window body gradient end
val TitleTop    = Color(0xFF25406B)  // title bar gradient start
val TitleBot    = Color(0xFF16294A)  // title bar gradient end
val BevelLight  = Color(0xFF4A6E9E)  // top/left edge of a raised surface
val BevelDark   = Color(0xFF070C16)  // bottom/right edge of a raised surface
val Lcd         = Color(0xFF0B1424)  // recessed LCD panel fill
val Phosphor    = Color(0xFF4FE39B)  // primary green readout text
val PhosphorDim = Color(0xFF2E7F5B)  // inactive / secondary green
val Label       = Color(0xFF9DBBE2)  // pale blue UI text (menus, labels)
val LabelDim    = Color(0xFF5E779B)  // disabled / caption text
val SelectRow   = Color(0xFF1D3F73)  // selected list row fill
```

## Primitives codex must provide

```kotlin
/** The whole screen: Desktop background, centred window, optional bottom action area. */
@Composable
fun WinampWindow(
    title: String,                                  // e.g. "PREFERENCES"; the composable
                                                    // adds the dashes and letter spacing
    modifier: Modifier = Modifier,
    menuItems: List<String> = emptyList(),          // empty = no menu bar
    onClose: (() -> Unit)? = null,                  // wires the × glyph
    content: @Composable ColumnScope.() -> Unit,
)

/** A raised bevelled surface. The building block for buttons, panels and toolbars. */
@Composable
fun BevelPanel(
    modifier: Modifier = Modifier,
    raised: Boolean = true,                         // false = recessed (inverted bevels)
    fill: Color = Winamp.WindowTop,
    content: @Composable BoxScope.() -> Unit,
)

/** A pressable bevelled button. MUST invert its bevels while pressed - that is the
 *  entire click affordance, so it is not optional. No ripple. */
@Composable
fun WinampButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    wide: Boolean = false,                          // true = full-width primary action
    glyph: String? = null,                          // e.g. "▶" leading play triangle
    onClick: () -> Unit,
)

/** Recessed LCD panel holding phosphor-green content. */
@Composable
fun LcdPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
)

/** Phosphor-green monospace readout text. */
@Composable
fun LcdText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 14,                                 // sp
    dim: Boolean = false,
    letterSpacing: Float = 0f,                      // sp
)

/** Pale-blue monospace UI text. */
@Composable
fun WinampText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 14,
    dim: Boolean = false,
    letterSpacing: Float = 0f,
)

/** One row of a classic list window: index, label, right-aligned value, selection fill. */
@Composable
fun ListWindowRow(
    index: Int?,                                    // null = no leading number
    label: String,
    value: String? = null,                          // right-aligned, e.g. a count
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
)

/** ON/OFF segmented pair, as in the classic config panel. */
@Composable
fun OnOffToggle(checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit)

/** Small square radio with a green LED dot when selected. */
@Composable
fun WinampRadio(selected: Boolean, modifier: Modifier = Modifier)

/** Section heading inside a window: letter-spaced pale caps + a hairline rule. */
@Composable
fun WinampSectionHeader(title: String, modifier: Modifier = Modifier)
```

**Everything above must be public** (no `private`/`internal`) so `UnrulySettingsActivity`
can call it from the same package.

## Rules for both agents

1. Do **not** change any behaviour, navigation, permission handling, `SharedPreferences`
   key, or Hilt wiring. This is a restyle. Every existing callback, gate and state variable
   keeps working exactly as it does now.
2. `:app` targets Compose BOM 2026.06.01 / Material3. Use `androidx.compose.foundation`
   and `BasicText` where practical; `androidx.compose.material3.Text` is fine.
3. No new Gradle dependencies. No new resource files. No image assets - everything is drawn
   with `Canvas`, `background`, `border` and `Brush.verticalGradient`.
4. Keep `org.unruly.skin.support.UnrulyLogo` / `UnrulyMark` available for the brand mark;
   the small four-colour mark stays as the app's badge.
5. Comments explain *why* a decision was made, not what the line does. No comment narrating
   the restyle itself and no "changed X to Y" notes.
6. The build must pass `./gradlew :app:assembleDebug` from `/Users/0x1379/Work/unruly/repo`.
