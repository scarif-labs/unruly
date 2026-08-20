You are restyling the Shellac Android app's first-run setup flow to resemble a classic
late-1990s skinnable desktop media player (Winamp-era). Kotlin + Jetpack Compose.

Working directory: /Users/0x1379/Work/shellac/repo

READ FIRST, IN FULL:
  /Users/0x1379/Work/shellac/docs/tasks/winamp-chrome-api.md
That file is a binding contract. Another agent is editing ShellacSettingsActivity.kt in
parallel against the exact signatures listed there. Implement them verbatim - same names,
same parameter names, same order, same defaults, all public.

YOU OWN EXACTLY TWO FILES. Do not touch any others:
  app/src/main/java/com/merklegroot/shellac/shellac/host/RetroChrome.kt
  app/src/main/java/com/merklegroot/shellac/shellac/host/OnboardingActivity.kt

TASK 1 - RetroChrome.kt
Replace its entire contents with the Winamp chrome primitives from the contract. Rename the
palette object from `Retro` to `Winamp` with the exact colour values given. Implement every
composable in the contract's "Primitives codex must provide" section.

Bevel technique: a raised surface draws a light 2dp edge along its top and left and a dark
2dp edge along its bottom and right; a recessed surface swaps them. Draw these with Canvas
or with two Box overlays - not with `border`, which cannot do asymmetric edges.
`WinampButton` MUST swap raised->recessed while pressed, using
`MutableInteractionSource` + `collectIsPressedAsState`, with `indication = null`. That
inversion is the click animation and it is required.

TASK 2 - OnboardingActivity.kt
Restyle the three step composables. Keep ALL existing logic byte-for-byte: the
`rememberSaveable` state, the `Step` enum and `StepSaver`, the SAF `OpenDocumentTree`
launcher and its `Location.Unopened.from(context, uri).open(context)` call, the
notification-permission launcher, `isNeeded`, `markComplete`, `commit`, and the
`EXTRA_MANAGE` behaviour. Only the presentation changes. Delete `SpinningRecord` usage and
delete the file VinylRecord.kt is NOT yours - just stop calling it; leave the file alone.

Screen 1, WelcomeStep - "boot-up", the classic player face as the welcome:
  A WinampWindow titled "SHELLAC" with menuItems File/Play/Options/View. Inside, an
  LcdPanel containing: a large phosphor time readout "21:26"; small pale "KBPS 33  KHZ 22"
  labels and a boxed "MONO" tag to its right; a row of ~14 green visualiser bars of varied
  heights under those labels; and below, "PLAY" at the left with a scrolling-marquee-style
  title "★ NOW READING YOUR LIBRARY ★" clipped at the right edge. Under the LCD, a recessed
  seek trough with a raised bevelled thumb about a third of the way along. Under that, a row
  of five bevelled transport buttons with glyphs ◀◀ ▶ ❚❚ ■ ▶▶, and the four-colour
  ShellacMark at the right end of that row. Below the window, centred: the `shellac`
  wordmark in phosphor green, then "YOUR FILES · YOUR STEREO" in letter-spaced pale caps,
  then a wide WinampButton "POWER ON" with a ▶ glyph that calls the existing onContinue.
  The visualiser bars must animate - drive them from a `rememberInfiniteTransition` so the
  face looks alive. Keep it cheap: a handful of bars on one shared transition, not one
  animation per bar.

Screen 2, FoldersStep - "playlist editor", the folder picker as the classic list window:
  A WinampWindow titled "PLAYLIST EDITOR" with menuItems File/Sources/Sort/Help, filling
  most of the screen. Inside, the chosen folders as numbered ListWindowRows - index, the
  path from `location.path.resolve(context)`, and the first row rendered selected. Under the
  last row, a dim "+ add another folder…" row that calls the existing onAdd. The list area
  keeps its height when short - it is a window, not a wrapping card. A bottom toolbar inside
  the window holds a "+ Add" WinampButton (onAdd), a "− Rem" WinampButton that removes the
  last folder via the existing onRemove, and at the right an LcdPanel readout of the form
  "3 · folders". Below the window, a wide WinampButton "SCAN & PLAY" with a ▶ glyph, wired
  to the existing onContinue and disabled when the folder list is empty - that gate already
  exists and must be preserved. When the list is empty show a single dim row reading
  "no folders — add one to begin".

Screen 3, NotificationsStep - same window treatment, titled "NOTIFICATIONS", one LcdPanel
  explaining that a notification is what puts transport controls on the lock screen and that
  playback works without it, then a wide "ALLOW" WinampButton (onAllow) and a narrower
  "NOT NOW" WinampButton (onSkip).

The Activity's Scaffold containerColor becomes Winamp.Desktop. The status bar icons must be
LIGHT on this dark background - the current code sets `isAppearanceLightStatusBars = true`
for the old cream design; flip both that and `isAppearanceLightNavigationBars` to false.

CONSTRAINTS
- No new Gradle dependencies, no new resources, no image assets.
- Comments explain why, never what. Do not write comments about this restyle or about what
  the code used to be.
- You MUST leave the build green. Run `./gradlew :app:assembleDebug` from
  /Users/0x1379/Work/shellac/repo and fix every compile error before you finish. Do not
  report success without a passing build.
