You are restyling ONE screen of the Shellac Android app to resemble a classic late-1990s
skinnable desktop media player (Winamp-era) config panel. Kotlin + Jetpack Compose.

Working directory: /Users/0x1379/Work/shellac/repo

READ FIRST, IN FULL:
  /Users/0x1379/Work/shellac/docs/tasks/winamp-chrome-api.md
That file is a binding contract. Another agent (codex) is implementing those primitives in
RetroChrome.kt RIGHT NOW, in parallel with you. Assume every composable listed in its
"Primitives codex must provide" section already exists and call them exactly as specified.
Do NOT define your own copies of them, and do NOT edit RetroChrome.kt.

YOU OWN EXACTLY ONE FILE:
  app/src/main/java/com/merklegroot/shellac/shellac/host/ShellacSettingsActivity.kt

Do not create, edit or delete any other file. In particular do not touch
OnboardingActivity.kt, RetroChrome.kt or VinylRecord.kt - codex owns those and you will
collide with it.

TARGET DESIGN - "PREFERENCES", the settings screen as the classic config panel:

A full-screen dark navy desktop (Winamp.Desktop) with one WinampWindow titled
"PREFERENCES", no menu bar, filling the screen. Inside the window, a two-pane layout:

LEFT PANE, about 40% width, a recessed BevelPanel acting as the category list. Entries:
  ▸ Skin      (selected)
    Library
    Lyrics
    Playback
    About     (dim - not implemented yet, must not be clickable)
Selection uses Winamp.SelectRow behind the row and a "▸" marker. Tapping Skin / Library /
Lyrics scrolls or switches the right pane to that section; Playback and About are inert.
Simplest correct approach: hold the selected category in `remember { mutableStateOf(...) }`
and render only the matching section on the right.

RIGHT PANE, the settings themselves, using WinampSectionHeader for each heading:

  DISPLAY SKIN
    The skins from `registry.available()` as two side-by-side bevelled option boxes, each
    with a WinampRadio and the skin's displayName. Selected = the one whose id equals the
    collected `active.id`. Tapping calls the existing `onSkinChosen(skin.id)`. Keep the
    existing `registry.isQuarantined(skin.id)` warning text and the existing
    `registry.isRecoveryActive` explanatory message.

  LIBRARY
    "Music folders" with a right-aligned phosphor value showing the existing folderSummary
    logic (keep `folderRevision` and the `remember(folderRevision)` block exactly as they
    are - they exist because the folder list is edited in another Activity and comes back
    through onResume). The row keeps launching OnboardingActivity with
    `OnboardingActivity.EXTRA_MANAGE`.
    "Rescan music" with a WinampButton labelled "GO" wired to the existing
    `musicRepository.requestIndex(true)` + `rescanning` feedback state. Preserve the
    RESCAN_FEEDBACK_MS LaunchedEffect.

  LYRICS
    "Fetch from lrclib.net" with an OnOffToggle bound to the existing `lyricsEnabled` state
    and the existing `lyricsPreferences.edit().putBoolean(...)` write. Under it, dim caption
    "Sends title, artist & album only."

BOTTOM BAR inside the window, right-aligned: a "Cancel" WinampButton and an "OK"
WinampButton. Both simply leave the screen by the route the existing back handling already
uses - call the same thing the existing `OnBackPressedCallback` does, i.e. `goToHost()` when
`isStandaloneLaunch` is true and `finish()` otherwise. Extract that into a private method
and use it from both buttons. Every setting on this screen already writes through
immediately, so Cancel and OK are the same action; that is correct and intended - do not
invent a staging/rollback layer.

HARD CONSTRAINTS
- This is a restyle ONLY. Do not change behaviour, navigation semantics, Hilt injection,
  SharedPreferences keys, the `isStandaloneLaunch` / `isTaskRoot` logic, or `onSkinChosen`.
  Every existing state variable, callback and comment explaining *why* those work the way
  they do must survive.
- Everything monospace, square corners, bevels not shadows. No Material Card, Switch,
  RadioButton, Scaffold colours from MaterialTheme, or ripples.
- No new Gradle dependencies, no new resources, no image assets.
- The status bar icons must be LIGHT on this dark background: the file currently sets
  `isAppearanceLightStatusBars = true` and `isAppearanceLightNavigationBars = true` for the
  old cream design - set both to false.
- Comments explain why, never what. Do not write comments about this restyle or about what
  the code used to be.
- Your file must compile against the contract. You cannot run a green build until codex
  lands RetroChrome.kt, so if `./gradlew :app:assembleDebug` fails ONLY with unresolved
  references to the contract primitives, that is expected - report it and stop. Any other
  error is yours to fix.
