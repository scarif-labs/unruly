# Settings redesign: "Redline" menu stack

Status: 🟡 portrait checked on device (Redmi Note 10 Pro). Started 2026-10-20. Nothing is committed yet.

> **2026-11-05: restyled.** The look below is superseded by concept M4-N ("sleeve index",
> `~/Desktop/Unruly Menu Concepts/Round 4 - Red Border Weights/M4-N-settings.png`): off-white
> paper, red Inter Bold headlines (all four the same size), orange caps statuses, black hairline
> rules, no art. Same menu stack and behaviour. Inter Bold added (`res/font/inter_bold.otf`,
> `licenses/Inter-OFL.txt`). Checked in portrait on the `horus35` emulator only. The
> `settings_*.webp` cut-outs are now unused.

The old "PREFERENCES" Winamp window is replaced by **settings** on onboarding's red stock: a menu
of torn ivory strips (skin, library, lyrics, each with its current state in tracked caps), where
each option pushes its own page. Back (or "← settings") pops to the menu; × on the menu closes.
The chosen concept is `S2-A` (photocopy collage) in
`~/Desktop/Unruly Settings Concepts/Round 2 - Menu Stack/`.

## Where things are

| Piece | File |
|---|---|
| Menu, pages, page scaffold, back stack | `app/src/main/java/com/merklegroot/unruly/unruly/host/UnrulySettingsActivity.kt` |
| Shared with onboarding: `FolderStrips`, `rememberFolderPicker`, `MusicSettings.saveFolders`, `folderLabel` | `.../host/OnboardingActivity.kt` |
| `Glyph.Rescan` for the Rescan button | `.../host/RedlineChrome.kt` |
| Photocopy cut-outs (cassette, headphones, record, microphone) | `app/src/main/res/drawable-nodpi/settings_*.webp` |
| Source PNGs and brief (outside the repo) | `~/Desktop/Unruly Settings Concepts/App Assets/` |

## Behaviour

- **Skin:** torn cards with a marker check on the active skin. Picking one still leaves settings
  to show it, as before. Recovery mode and quarantined skins get a caps status line.
- **Library:** folders are edited in place (no longer via `OnboardingActivity` with
  `EXTRA_MANAGE`). Adding or removing a folder writes through at once, which starts a scan.
  "Rescan" calls `requestIndex(true)` and shows "Rescanning…" for a fixed 1.8 s.
  The folder strips are laid over the record (`SettingsScaffold`'s `overArt`), so the art keeps
  its size as folders are added; the list scrolls once it fills the space.
- **Lyrics:** the whole strip toggles the lrclib lookup; the switch is drawn in ink.
- **Art scale:** `artScale` shrinks a page's cut-out while keeping its overhang past the right
  edge; the microphone uses 0.62 because, tall and narrow, it filled the page at full size.
- **Headlines** match in size across all four pages ("settings", "the / skin", "music / library",
  "lyrics"). Wide windows put the art beside the column.

## Open items

1. Verified in portrait: the menu, all three pages, and system back from each page to the menu.
   Still to check: landscape, 2× font, the SAF round-trip from "Add folder", removing a folder,
   and the recovery/quarantine labels. The list-over-art change is built but not yet seen.
2. `OnboardingActivity`'s `EXTRA_MANAGE` mode is now unused. Delete it, or keep it.
3. `WinampChrome.kt` is now used only by the also-unused `GlacialChrome.kt`.
4. The cut-outs are AI-generated placeholders, like the onboarding creatures.
5. Strings are hard-coded, as in onboarding.
