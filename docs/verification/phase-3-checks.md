# Phase 3 — Device Verification Checklist

Two launcher icons now exist: **Shellac** (the skin host) and **Shellac Settings**.
The original **Auxio Debug** icon still works and is untouched.

## Already verified automatically — no action needed

- ✅ Both skins appear in settings; switching swaps the entire UI; choice persists
- ✅ Album list populates from the real library (1664 songs) in both skins
- ✅ Tapping an album plays it with the correct queue
  (`song=Burn the Witch, parent=Album(A Moon Shaped Pool), queue=11 songs`)
- ✅ Nonexistent stored skin id falls back gracefully instead of crashing
- ✅ Zero `org.oxycblt.*` imports in either skin
- ✅ Live indexing progress renders (`SCANNING... loaded=1370 explored=1508`)

## What still needs you

### 1. System back ⭐
- [ ] In a skin: album list → tap album → now playing → **back** returns to the album list
- [ ] At the album list, **back exits the app** (does not trap you)
- [ ] Same in both Debug A and Debug B

Back is delegated to the skin: the host only exits when the skin reports it is at its root.
A skin that never returns false traps the user; one that always returns false exits from
everywhere.

### 2. Large-list scroll and memory ⭐
- [ ] Scroll the album list fast, top to bottom, repeatedly
- [ ] No stutter, no blank rows that never fill
- [ ] Memory stays flat in the Android Studio profiler

This is the test that actually measures whether paging works. Note the honest caveat: the
engine already holds the whole library in RAM, so this measures **UI/adapter** memory only.

### 3. Rotation
- [ ] Rotate in each skin — no crash, navigation position preserved

### 4. Repeated skin switching
- [ ] Switch A → B → A ten times via Shellac Settings
- [ ] No leak in the profiler, no slowdown

### 5. Audio unchanged
- [ ] Play via a skin, confirm it sounds identical to playing via the original Auxio UI

## Known gaps, deliberately

- **Now-playing is text only.** Debug skins are ugly on purpose (buildspec 3.9).
- **`ShellacActivity` has not replaced `MainActivity`.** Both launcher entries coexist so the
  original UI stays testable. `MainActivity` is removed in Phase 5 once a real skin exists.
- **A throwing skin still crashes the process once.** Compose has no error boundary; the
  registry quarantines the offender so the *next* launch is fine. One crash, then recovery.
