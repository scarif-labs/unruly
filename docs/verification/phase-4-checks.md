# Phase 4 — Device Verification Checklist

The support layer is wired into both debug skins specifically so it can be tested by hand.
Launch **Shellac**, play an album, open now-playing.

## 1. Interpolated position ⭐ — the headline test

Now-playing shows two readouts side by side:

```
raw    01:23      <- engine, 10 Hz
smooth 01:23.4    <- interpolated
```

- [ ] **`smooth` advances continuously**; `raw` visibly steps ~10x/sec
- [ ] The progress bar moves smoothly, not in jumps
- [ ] **Pause** → `smooth` freezes immediately and does not drift
- [ ] **Resume** → continues from the right place, no jump
- [ ] **Seek** (the ±10s buttons) → `smooth` **snaps** to the new position; it must NOT
      glide or lag behind
- [ ] At a track boundary, `smooth` never exceeds the track length

The whole reason this exists: the engine reports position at only 10 Hz, so anything
animated from it directly looks steppy.

## 2. Rotary gesture ⭐

A bordered box on now-playing shows `ROTARY: +N` and the last delta.

- [ ] Drag **clockwise** in the ring → number **increases**
- [ ] Drag **counter-clockwise** → number **decreases**
- [ ] A full slow circle gives a consistent count both ways (~27 clicks at 13°)
- [ ] **Crossing the top (12 o'clock) does not produce a huge jump** — this is the
      wraparound case and the most common bug in rotary detectors
- [ ] Touching the **centre** does nothing (dead zone — a centre press is a button)
- [ ] Touching **outside the ring** does nothing
- [ ] Slow drags do not jitter direction back and forth

## 3. Album art

- [ ] Art appears in album rows (small) and now-playing (large)
- [ ] Albums with **no art** show that skin's fallback, and do not crash
- [ ] **Debug A and Debug B show visibly DIFFERENT fallbacks** (per-skin requirement)
- [ ] Scrolling the album list fast does not flicker or reload already-visible art

## 4. Theming with no leakage ⭐

- [ ] Debug A and Debug B look clearly different (colours and type)
- [ ] Debug A: **light** status/nav bar icons. Debug B: **dark**
- [ ] Open **Shellac Settings** from inside each skin — settings looks **identical** either
      way. No skin colour bleeds into it
- [ ] Switch A → B → A: the system bars update each time and never keep the previous
      skin's styling (`onDispose` restore)

## 5. Back stack — two approaches on purpose

Debug A uses `SkinBackStack`; Debug B uses a plain enum field. 4.5 is optional, and a flat
skin must not be forced into it.

- [ ] Both: albums → now-playing → queue, and back pops correctly through all three
- [ ] Both: back at the album list exits the app
- [ ] **Rotate on the queue screen** — the stack survives, you are not dumped to the root

## 6. Loading and empty states

- [ ] Trigger **Rescan music** in Shellac Settings, return to a skin: the scanning state
      renders (not a blank screen)
- [ ] Each skin styles scanning differently

## 7. Leak check

- [ ] Switch skins **10+ times**; watch the Android Studio profiler
- [ ] Memory returns to baseline; no steady climb

## How to report a failure

```bash
A=~/Library/Android/sdk/platform-tools/adb
$A logcat -c      # clear, reproduce, then:
$A logcat -d | grep -iE "shellac|rotary|fatal" > ~/Desktop/shellac-phase4.log
```
