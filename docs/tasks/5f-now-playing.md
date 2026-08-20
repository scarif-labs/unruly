# Phase 5f — Now-playing view (and the 5d centring bug)

**Status:** ✅ implemented and verified on device
**Agent:** codex / `gpt-5.6-luna`, reasoning `high`
**Prompt:** `docs/agents/prompts/p5-nowplaying.md`
**Commit:** `19f69496a`

Files: `skin/src/main/java/org/shellac/skin/wheel/WheelNowPlaying.kt` (new),
`TouchWheelSkin.kt` (delegate + rotation routing), `WheelMenuList.kt` (centring fix).

---

## 1. What worked

Art, title, artist, album, `1 of 2052`, progress bar, elapsed and remaining — all correct on
device against a real track. Elapsed + remaining stayed consistent with total across every
reading, which is the cheap check that the two are computed from the same source.

The pure helpers were correct on every specified vector without a fix:

| Input | `formatTime` | | Input | `progressFraction` |
|---|---|---|---|---|
| `0` | `0:00` | | `(0, 0)` | `0f` |
| `7000` | `0:07` | | `(50, 0)` | `0f` (no divide by zero) |
| `67000` | `1:07` | | `(50, 100)` | `0.5f` |
| `3723000` | `1:02:03` | | `(200, 100)` | `1f` |
| `-5` | `0:00` | | `(-5, 100)` | `0f` |

**The scrub design came back better than specified.** The prompt asked for a local target
committed after idle; the agent added a `seekHoldMs` state that keeps displaying the target
*after* the seek is issued, released only once the engine's raw position confirms arrival.
The tolerance chosen — 100 ms — is exactly one decisecond, which is the engine's actual
reporting resolution (finding F1). That is the right constant for the right reason.

Scrub step is `max(1s, duration/100)`, so a 20-minute track moves in ~12 s steps and a
90-second one in 1 s steps. Verified: 2.8 s/click on a 4:40 track.

---

## 2. The 5d centring bug — the defining behaviour never worked

`WheelMenuList`'s entire character is: **the selection is pinned to a fixed vertical centre
and the list moves under it.** It did not do this. It was:

```kotlin
val centreOffset = (viewportHeight - rowHeight) / 2
listState.scrollToItem(effectiveIndex, centreOffset)
```

`scrollToItem(index, scrollOffset)` scrolls so the item's top sits `scrollOffset` pixels
**above** the viewport start. A positive offset moves content *up* — away from centre, not
toward it. Worse, a list cannot scroll before its first item, and there was no
`contentPadding`, so there was nowhere to scroll *to*.

Observed on device:

- **Root menu (3 items):** the list is shorter than the viewport, so it cannot scroll at all.
  The selection sat pinned to the very top with a large empty region below it, permanently.
- **Artists (long, paged):** the selected row was scrolled off the top of the viewport
  entirely. Opening Artists showed rows starting partway into the C's with **no visible
  highlight anywhere**.

### Fix

```kotlin
val centrePadding = with(LocalDensity.current) {
    ((viewportHeight - rowHeight) / 2).coerceAtLeast(0).toDp()
}
LazyColumn(contentPadding = PaddingValues(vertical = centrePadding), …)
…
listState.scrollToItem(effectiveIndex, 0)
```

Half a viewport of padding at each end is what makes centring possible at all: it gives the
list somewhere to scroll to when the selection is the first or last item. Applied to both the
`List` and `LazyPagingItems` overloads. Verified on device — selection now centres, with real
blank space above item 0.

### Why this survived so long

The code's own comments described the correct behaviour in detail — *"push its top edge down
by half the leftover space"*, *"Blank space above item 0 is intentional"*. The intent was
understood and documented; only the mechanism was wrong, and `scrollToItem`'s offset sign
convention is exactly the kind of thing that reads as correct.

It also compiled, it never crashed, and 5d's stated acceptance test ("a 2,000-item list
scrolls without stutter") passed — the list *did* scroll smoothly, just to the wrong place.
**Three sub-phases and two agent reviews went by without catching it. Ten seconds of looking
at it on a device did.**

---

## 3. Papercut: a confounded measurement, twice

First device scrub reading suggested a ~90° rotation moved 43 s — implying the rotary was
over-counting by ~2×. It was not. A clean repeat with playback confirmed paused gave **+17 s
for 90°, twice running**, i.e. 6 clicks at 2.8 s — exactly `90/13` truncated, which is the
configured 13°/click.

This is the **second** time an apparent rotary over-count turned out to be a measurement
artefact rather than a defect (the first was during Phase 4). The pattern both times: a
reading taken while some other state was still changing underneath.

**Rule: never report a rotary/timing anomaly from a single reading.** Pause playback, take a
baseline, confirm it is stable, then apply exactly one known-size input and repeat it. Had the
first reading been trusted, the "fix" would have been to halve a correct constant.

---

## 4. Improvement made during review

The seek handover released its held target only when an engine sample landed within one
decisecond of it. Nothing guarantees one does — a seek can settle slightly off, or 10 Hz
sampling can step straight over the window while playing. The hold would then never clear and
**the progress bar would sit frozen at the target while audio played on**, which is a worse
artefact than the backwards jump the hold exists to prevent.

Added a 1 s bounded timeout keyed on the hold alone (so engine samples do not restart it).
Verified on device: after a seek commits, position resumes advancing normally
(1:28 → 1:34 → 1:41).

---

## 5. Tooling papercuts

- **`adb install -r` fails with an empty error message.** No reason, no code — just
  `adb: failed to install …:` and a blank line. `adb push` + `adb shell pm install -r` from
  `/data/local/tmp` worked immediately on the same APK. Use the push route when a streamed
  install fails silently.
- **`ioreg -p IOUSB -w 0` is the right diagnostic for a missing device**, not `adb devices`.
  It showed the two host controllers with **zero child nodes**, proving nothing was
  enumerating over USB at all — the fault was below adb, so no amount of
  `kill-server`/`start-server` could have helped. (`system_profiler SPUSBDataType` produced no
  output at all here.) Root cause: a loose cable.
- **`uiautomator dump` reads Compose text far more cheaply than screenshots.** Compose
  exposes `BasicText` through semantics, so
  `adb shell uiautomator dump && adb shell cat /sdcard/ui.xml` yields exact on-screen strings
  as text. Used it for the timing measurements above instead of reading images.
- **Do not put a phase id like `5f` in an agent's search terms.** The agent searched for
  `"…|scrub|5f|now-playing|…"` without excluding `media/`, and `5f` is a hex digit pair, so it
  matched essentially every checksum in ffmpeg's test-vector suite — 413 KB of truncated
  garbage into its context. Scope agent searches to `skin/` explicitly.

---

## 6. Verification performed on device

- [x] Art renders square and undistorted from a real cover
- [x] Title / artist / album correct
- [x] `1 of 2052` queue position correct after `shuffleAll`
- [x] Elapsed and remaining consistent with total at multiple positions
- [x] Scrub moves position; magnitude correct and reproducible (6 clicks / 90°)
- [x] Position resumes advancing after a seek commits — no frozen bar
- [x] Selection centring (the 5d fix)
- [x] No crashes across the session
- [ ] Audible seek accuracy — cannot verify remotely, needs the user
- [ ] Scrub target renders in accent colour mid-gesture — timing too tight to catch by
      screenshot; needs a human eye
