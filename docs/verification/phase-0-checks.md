# Phase 0 — Device Verification Checklist

**Run these by hand on the Redmi Note 10 Pro.** They are the buildspec's Phase 0 exit
tests. Claude can verify the build ones automatically; the *audible* and *tactile* ones
are yours — no tooling can confirm that a FLAC actually sounded right.

Do not let Phase 1 start until every box is ticked.

---

## Automated (Claude runs these — reported back, no action from you)

- [ ] `./gradlew assembleFullDebug` completes clean
- [ ] `./gradlew clean` then rebuild still succeeds
- [ ] APK installs to `9b53f076` via `adb install -r`

## Manual — you run these on the device

### 0. ⚠️ FIRST: grant a music folder — nothing works without this

The app is installed and launched, but indexing found **zero songs**:

```
IndexingNotification: Updating state to Songs(loaded=0, explored=0)
Musikr: Indexing took 252ms
```

Auxio indexes via **SAF** (`org.oxycblt.musikr.fs.saf.SAF`), not `MediaStore`. It cannot
see your 2,231 files until you explicitly hand it a folder.

- [ ] Open Auxio → **Settings → Music → Music folders** (or the on-boarding prompt)
- [ ] Add the folder(s) holding your library (e.g. `/sdcard/Music`)
- [ ] Trigger a rescan and let it finish

Everything below is meaningless until this is done and the song count is non-zero.

### 1. Launch and index
- [ ] App launches without crashing
- [ ] Library scan completes (may take a while on 2,231 tracks — let it finish)
- [ ] **Real albums appear with correct grouping.** Look specifically for: multi-disc
      albums grouped as one, compilations not fragmented into one-album-per-track, and
      album artist vs track artist handled sensibly.
- [ ] Track counts per album look right against what you know of your library

### 2. Audible playback — the point of the whole phase
- [ ] **A FLAC plays start to finish, audibly.** Pick a long one. Listen to the end —
      truncation at the tail is a real failure mode of a broken native build.
- [ ] No clicks, dropouts, or distortion

### 3. Transport controls
- [ ] Pause actually stops sound
- [ ] Resume continues from the same position
- [ ] Seek — drag the scrubber, audio jumps to the right place
- [ ] Next skips to the next track
- [ ] Previous behaves correctly (restart-then-previous, or straight previous)

### 4. Background survival
- [ ] Screen off → playback continues uninterrupted
- [ ] Screen on again → UI shows correct current position, not desynced
- [ ] Leave the app (home button) → playback continues

### 5. Notification
- [ ] Media notification appears with correct title / artist / album art
- [ ] Play/pause from the notification works
- [ ] Next/previous from the notification works
- [ ] Notification state stays in sync with the in-app UI

---

## How to report back

For anything that fails, the useful information is:

1. Which box
2. What happened instead
3. Logcat around the failure:

```bash
A=~/Library/Android/sdk/platform-tools/adb
$A logcat -c            # clear, then reproduce the problem, then:
$A logcat -d > ~/Desktop/unruly-phase0.log
```

**If the build itself fails,** the buildspec is emphatic and correct: that is a toolchain
problem, not a code problem. LLMs diagnose Gradle/AGP/NDK mismatches badly and will
suggest confidently wrong fixes. Claude will read the actual error and check versions
against the README rather than guessing — and will not dispatch a codex/cline agent at it.

---

## Baseline to record

Phase 5 acceptance requires "audio unchanged from baseline". So while you are here, note:

- [ ] Does ReplayGain apply? (Settings → check it is on, then compare a loud and quiet track)
- [ ] Is gapless playback working on an album that runs tracks together?

Write the answers into `docs/verification/baseline-audio.md` — these become the reference
that Phase 5 is measured against, and they are much harder to reconstruct later.
