# Phase 5e — device checks (Touch Wheel navigation)

Build: commit `05b44135a`, APK 40 MB at `app/build/outputs/apk/debug/app-debug.apk`.

**The device was disconnected when this build finished** (`adb devices` empty), so nothing
below has been run. Reconnect and install:

```bash
adb devices                      # confirm 9b53f076 is listed
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then open **Shellac**, go to the app-native settings launcher icon, and select **Touch
Wheel**. Debug A and B are still there — switching back is the escape hatch if the wheel
misbehaves.

---

## The wheel

Content is on top, the wheel occupies roughly the lower 45%.

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | Wheel shape | A circle, not an ellipse, and centred | |
| 2 | Rotate a finger around the ring | Selection moves one item per click, smoothly | |
| 3 | Rotate slowly past the top (12 o'clock) | Selection keeps moving the same direction — **no jump or reversal** as it crosses 0°/360° | |
| 4 | Rotate hard to the top of the list | Stops at the first item; does **not** wrap to the bottom | |
| 5 | Rotate hard to the bottom | Stops at the last item; does not wrap | |
| 6 | Tap the centre button | Activates the selected row | |
| 7 | Tap MENU (top of ring) | Goes back one level | |
| 8 | Tap PLAY (bottom) | Toggles play/pause from any screen | |
| 9 | Tap NEXT / PREV (right / left) | Skips track from any screen, including inside menus | |
| 10 | Tap dead centre of the screen but *outside* the wheel circle | Nothing happens | |

**Check 3 is the one to do carefully.** The rotary maths was verified by reading, and an
earlier on-device reading suggested over-counting before it turned out to be a confounded
measurement. This is its first real use, so a genuine result matters. Rotate at a steady,
*slow* pace through 12 o'clock and watch whether the selection ever stutters or reverses.

---

## Navigation

| # | Path | Expected | Result |
|---|---|---|---|
| 11 | Root menu | `Music`, `Shuffle Songs`, `Now Playing` | |
| 12 | Music | `Artists`, `Albums`, `Songs`, `Genres`, `Playlists` | |
| 13 | Artists → an artist | That artist's albums | |
| 14 | → an album → a song | Plays, and lands on Now Playing | |
| 15 | Albums → album → song | Plays | |
| 16 | Songs → a song | Plays | |
| 17 | Genres → genre → song | Plays | |
| 18 | Playlists → playlist → song | Plays (skip if you have no playlists) | |
| 19 | `Shuffle Songs` at root | Starts shuffled playback, goes to Now Playing | |
| 20 | `Now Playing` at root | Opens Now Playing **without changing what is playing** | |

### Selection memory — the subtle one

| # | Check | Expected | Result |
|---|---|---|---|
| 21 | In Albums, rotate down to roughly the 10th album. Enter it, then press MENU | You return to Albums **on that same album**, not scrolled back to the top | |
| 22 | Repeat one level deeper (album → song → MENU → MENU) | Each level remembers where you were | |

This is per-destination index preservation. A single shared index would silently fail it, and
it reads as "the app forgot where I was" rather than as an obvious bug.

### Back button parity

| # | Check | Expected | Result |
|---|---|---|---|
| 23 | Navigate down 3 levels, then use the **system back gesture** repeatedly | Behaves identically to tapping MENU — same levels, same order | |
| 24 | System back at the **root** menu | App exits | |
| 25 | MENU at the root menu | Nothing happens (does not exit) | |

23 is worth doing deliberately: MENU and system back are wired to the same `backStack.pop()`,
so they should be indistinguishable. If they differ, the field-held back stack is wrong.

---

## Paging — what this build actually fixed

Before this commit, opening any paged list dragged the whole library into memory. Songs is
the screen that shows it (~2,231 tracks).

| # | Check | Expected | Result |
|---|---|---|---|
| 26 | Music → Songs | Opens **immediately**, no multi-second freeze | |
| 27 | Rotate fast, continuously, down through a few hundred songs | Stays responsive; rows may briefly read `Loading…` then fill in | |
| 28 | If a row shows `Loading…`, press centre on it | Nothing happens — no crash | |
| 29 | Rows that say `Loading…` | Same height as loaded rows, so the list does **not** jump when they resolve | |

Check 27's `Loading…` flashes are correct behaviour, not a defect — it means paging is
working. Seeing *no* `Loading…` ever, on a 2,000-track list scrolled fast, would suggest
something is still materialising eagerly.

⚠️ **Blocker B4 still applies.** At 2,231 tracks this exercises paging but not the spec's
20,000. Passing here does not prove Phase 6.2.

---

## Robustness

| # | Check | Expected | Result |
|---|---|---|---|
| 30 | Rotate the device to landscape mid-list | Wheel stays circular; selection and position preserved | |
| 31 | Background the app during playback, return | Playback continued; Now Playing reflects reality | |
| 32 | Switch to Debug A, then back to Touch Wheel | Both render correctly; no colour bleed between skins | |
| 33 | Play a song, then open the app-native settings launcher icon | Reachable while Touch Wheel is active | |

Check 32 targets system-bar leakage — `SkinTheme` restores bar appearance in `onDispose`,
and the Touch Wheel palette is dark where Debug A is light, so a leak would be visible.

---

## Known-incomplete (do not report these)

- **Now Playing is text only** — title, artist, album, `n of m`. No album art, no progress
  bar, no scrubbing. That is Phase 5f, deliberately scoped out.
- **No queue screen and no search.** Phase 5g.
- **No haptics, transitions, or custom fonts.** Phase 5h.
- **No settings entry inside the wheel menu** — use the separate launcher icon. Open
  contract question, see `docs/tasks/5e-menu-hierarchy.md` §5.1.
