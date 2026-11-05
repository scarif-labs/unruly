# Onboarding redesign: "Redline"

Status: 🟡 in progress. Last worked on 2026-10-20. Nothing is committed yet. The art was switched
from medieval creatures to photocopied audio gear (round 11, R11-A); that build is not yet seen on
device.

The first-run flow is three pages on the same red stock (#F32B16, ivory #F4EFDF, ink #131310,
print grain): the vortex welcome, music folders and lock screen controls. A segmented progress bar
replaces the old "PERSONAL FREQUENCY ── SIDE B / 00x" masthead on every page.

## Where things are

| Piece | File |
|---|---|
| Welcome (type-tunnel vortex), palette, grain, `StepProgress`, system bars | `app/src/main/java/com/merklegroot/unruly/unruly/host/VortexWelcome.kt` |
| Shared page chrome: `RedlineScreen`, `MarkerHeadline`, `RedlineButton`, `VellumScrap`, `PaperCutout`, `NowPlayingCard` | `.../host/RedlineChrome.kt` (new) |
| Shared with settings: `FolderStrips`, `rememberFolderPicker`, `MusicSettings.saveFolders`, `folderLabel` | `.../host/OnboardingActivity.kt` |
| Flow logic and the folders and lock-screen steps | `.../host/OnboardingActivity.kt` |
| Headline font: Gloria Hallelujah (SIL OFL) | `app/src/main/res/font/gloria_hallelujah.ttf`, `licenses/GloriaHallelujah-OFL.txt` |
| Audio gear art (cassette case, Walkman, boombox) | `app/src/main/res/drawable-nodpi/onboarding_*.webp` |
| Welcome motion and verification notes | `docs/verification/vortex-welcome.md` |
| Design concepts, briefs and raw assets (outside the repo) | `~/Desktop/Unruly Onboarding Concepts/`; gear sources in `App Assets - Audio Gear/` |

## Current design

- **Progress bar:** one ivory segment per step. The current segment fills in as the page appears.
  It has three segments, or two when the notification step is skipped (already granted, or below
  API 33). The folder editor opened from settings (`EXTRA_MANAGE`) shows no bar.
- **Headlines:** hand-lettered Gloria Hallelujah, lowercase, two lines: "music / folders" and
  "lock / screen".
  - Drawn on a canvas, stacked by the glyphs' actual ink bounds, because the font's line box left
    a big gap above x-height-only lines.
  - Thickened with a fill-and-stroke paint.
  - Every page sizes its headline to the widest line across all of them (`AllHeadlines`), so the
    sizes match. The cap is 180 sp, or 76 sp in the wide layout.
- **No body copy.** It was removed at the user's request: headline, art and buttons carry each
  page.
- **Art:** black-and-white photocopied cut-outs of audio gear with torn ivory borders, the same
  style as the settings screen. They replaced the medieval creatures on 2026-10-20.
- **Music folders, empty:** an open, empty cassette case, a torn vellum scrap reading "NOTHING TO
  PLAY / 0 FOLDERS · WAITING", the "Choose a folder +" button and the footer "FEED ME FOLDERS.".
- **Music folders, chosen:**
  - A Walkman with a cassette going in.
  - The folders as torn strips with the name in Gloria Hallelujah and a × to remove (no red
    initial any more). The label is just the folder name on internal storage; other volumes keep
    their name ("SD card/Albums").
  - **The list is laid over the art, not above it,** so the art keeps its size as folders are
    added and the strips cover it. Past the art's height the list scrolls. `RedlineScreen` does
    this for any `below` content; wide layouts cap it at `BelowWideMaxHeight` (220 dp).
  - "N FOLDERS · READY", then "Add another +" and "Scan & play ▶" ("Save & rescan" when opened
    from settings).
- **Lock screen:** a boombox with a drawn "NOW PLAYING" card (⏮ ▶ ⏭), then "Allow →" and
  "Not now", and the footer "BUTTONS WHERE YOUR THUMB IS.".
- **Layout:** wide windows (width > height) put the art beside the headline and actions. Steps
  cross-fade (the old `DitherSwap` is gone).
- **Unchanged:** the SAF picker and persistable grant, `rememberSaveable` state across Activity
  recreation, the notification permission, the settings entry point, and the commit and rescan
  path.

## Verified on device (Redmi Note 10 Pro, Android 16, 1080 × 2400)

- Builds, installs and passes `spotlessApply`.
- **Welcome:** on 2026-10-19 the vortex was 0 janky frames out of 1,214 at 120 Hz, before the
  wobble was toned down. The calmer version was not re-measured cleanly.
- **Every page:** the progress bar shows and animates.
- **Folders, empty:**
  - Portrait, landscape and 2× font all checked.
  - These checks predate the latest changes: bigger headlines, removed copy, two-line "lock /
    screen".
- **Folders, chosen:** checked in portrait with the snail. The cat was checked via settings →
  Music folders, but before the latest headline and copy changes.
- **Lock screen:** portrait and landscape checked, before the latest changes.

## Not yet verified (the latest build is installed but unseen)

- The audio gear art on all three pages, and the folder list laid over the Walkman with several
  folders (the art must not shrink). That build is not installed yet.
- `EXTRA_MANAGE` (`am start` with the extra) opens the chosen-folders page without wiping data;
  back out without saving.
- Matching headline sizes and the two-line "lock / screen".
- The removed body copy.
- The folder-name-only labels.
- An SD-card folder label ("SD card/Albums"); no SD card was available.
- To re-run first-run onboarding, clear the app data:
  `adb shell pm clear com.merklegroot.unruly.debug`. This also resets the notification
  permission and releases folder grants.

## Open items

1. **Placeholder art.** The gear cut-outs are AI-generated (Codex image generation, chroma-keyed
   from flat green). Replace them with licensed or commissioned photocopies before release. The
   sources are in `~/Desktop/Unruly Onboarding Concepts/App Assets - Audio Gear/`; the old
   creature sources stay in `App Assets/`.
2. **Landscape:** the actions column scrolls; the bottom button is partly cut off on short
   windows.
3. **`GlacialChrome.kt` is now unused** (the old blue LCD theme). The user hasn't decided whether
   to delete it. `departure_mono.otf` may become unused with it.
4. **Strings are hard-coded**, like the rest of onboarding; they are not in `strings.xml`.
5. **Commit:** nothing is committed. The working tree also holds earlier uncommitted rebrand work.

## Design history (concepts on the Desktop)

Each round is in its own folder with its `brief.md`; earlier PNGs are read-only.

1. **Round 1:** vortex everywhere. Rejected: the void stays on the welcome only.
2. **Rounds 2–3:**
   - A: Ransom Zine.
   - B: Type Riot.
   - C: Illuminated, after the *idea* magazine manuscript cover.
3. **Rounds 4–5:** "Illuminated Punk" (manuscript fragments in a punk zine), first too sparse,
   then scaled up.
4. **Round 6:** blackletter headlines. The user liked them but found them hard to read.
5. **Rounds 7–9:** font swaps on round 6:
   - Rotunda, Anton.
   - Wood type, extended grotesk, Cooper.
   - Swiss grotesk, ink-trap, geometric.
6. **Round 10:** Gloria Hallelujah, the user's pick, now implemented.
7. **Round 11:** the creatures replaced by photocopied audio gear, matching settings.
   - A: one hero object per page (empty cassette case, Walkman, boombox). The user's pick, now
     implemented.
   - B: a pile of gear per page.

## 2026-11-05: tunnel type

The vortex tunnel word is now Inter Black (bundled `res/font/inter_black.otf`, OFL) tracked -0.04em,
to match the picked logo direction L2-D (`~/Desktop/Unruly Logo Concepts/Round 2 - Icon + Loading
Directions/`). Checked on the phone. `anton_regular.ttf` is now unused (kept for comparison).
Debug builds open onboarding on a set-up phone with
`am start -n <pkg>/com.merklegroot.unruly.unruly.host.UnrulyActivity --ez debugOnboarding true`.
