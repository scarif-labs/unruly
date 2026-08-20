# Verification Device

The project has **no emulator/AVD** available — insufficient host RAM. All on-device
verification happens on this physical handset. Every phase ends with a manual checklist
in `docs/verification/phase-N-checks.md` for the user to run by hand.

## Hardware

| Property | Value |
|---|---|
| adb serial | `9b53f076` |
| Model | `M2101K6P` — Redmi Note 10 Pro |
| Codename | `sweet` / `lineage_sweet` |
| OS | LineageOS, build `BP2A.250805.005` |
| Android | **16** (API **36**) |
| RAM | 5.3 GB total |
| Audio files on device | **2,231** (`/sdcard/Music`, `/sdcard/Download`) |

Compile target: SDK Platform 35/36/36.1 all installed, so API 36 is covered.

## Why this matters to the buildspec

- Phase 0.5 requires a real device with a real library — satisfied. 2,231 tracks is a
  genuine indexer workout, not an emulator's tidy sample set.
- Phase 5 acceptance asks for "a library of 5,000+ tracks without lag". This device has
  ~2.2k. **Gap:** we cannot fully satisfy that criterion without adding more media.
  Options when we reach Phase 5: side-load additional audio, or record the criterion as
  partially verified at 2.2k and note the shortfall honestly.
- Phase 6.2 asks for a 20,000-track edge case — same gap, larger. Will need synthetic
  media generated onto the device.

## Papercuts

**`adb devices` reported empty while the device was plugged in and authorized.**
The daemon had been started earlier in the session, before the cable was connected, and
did not pick up the hotplug. Fix:

```bash
adb kill-server && adb start-server && adb devices -l
```

Do this first whenever a device "isn't there" — before touching USB debugging settings
or blaming the cable.

**`adb` is not on `PATH`.** Use the absolute path `~/Library/Android/sdk/platform-tools/adb`.

## Standing commands

```bash
A=~/Library/Android/sdk/platform-tools/adb
$A devices -l                      # confirm attached
$A install -r app/build/outputs/apk/full/debug/app-full-debug.apk
$A logcat -c                       # clear before a test run
$A logcat | grep -iE "auxio|shellac"
```
