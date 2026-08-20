# Phase 2 — Device Verification Checklist

The spike is **installed and running** on `9b53f076`. Open Auxio, play something, and pull
up the now-playing panel. The Compose block sits at the top, above the cover art.

Already confirmed by screenshot — no need to re-check:

- ✅ "COMPOSE ALIVE" renders
- ✅ Song title shows and matched Auxio's own UI ("Lose Yourself to Dance")
- ✅ Button reads "Pause" while playing
- ✅ Album list populated from the real library

## What still needs you

These are the buildspec's Phase 2 tests that a screenshot cannot settle.

### 1. Title updates on track change *without reopening the screen* ⭐
- [ ] With the now-playing panel open, press **next**
- [ ] The Compose title changes **in place**

This is the one that proves live collection works rather than a one-shot read at
composition. If it only updates after closing and reopening, `collectAsStateWithLifecycle`
is not doing its job.

### 2. The Compose button actually controls audio
- [ ] Tap the Compose **Pause** button — audio stops, label flips to "Play"
- [ ] Tap again — audio resumes, label flips back

### 3. ⭐ The button reflects state changed *elsewhere* — the important one
- [ ] With the panel open and playing, pull down the notification shade
- [ ] Press **pause from the notification**
- [ ] The Compose button label flips to "Play" **on its own**

Together with #2 this is what the buildspec means by "state flows both directions". #2 alone
only proves Compose → engine. This proves engine → Compose, through a path that never
touches the Compose UI.

### 4. Rotation
- [ ] Rotate the device with the panel open
- [ ] No crash; title and button state still correct, not stale or reset

### 5. Sanity — audio is unchanged
- [ ] Playback still sounds correct with Compose present (no stutter, no dropouts)

## If something fails

```bash
A=~/Library/Android/sdk/platform-tools/adb
$A logcat -c        # clear, reproduce, then:
$A logcat -d | grep -iE "compose|fatal|androidruntime" > ~/Desktop/shellac-phase2.log
```

## Note on the spike's appearance

It is deliberately ugly and overlaps Auxio's own UI — it is drawn on top with
`translationZ` purely so it can be tapped. Phase 3.6 deletes this Activity and layout
entirely, so none of this styling survives. Do not spend time on how it looks.
