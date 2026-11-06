# F-Droid listing

Status: 🟡 In progress 2026-11-06. The release build passes R8 (from this tree, JDK 17) and is
installed on the phone as `com.scarif.unruly` (signed with the debug key). Nothing is committed,
tagged or submitted yet.

## Decisions

- App ID **`com.scarif.unruly`**. F-Droid treats it as permanent. Only `applicationId` changed;
  the Kotlin package stays `com.merklegroot.unruly`.
- Version **1.0.0 / versionCode 1**, tagged `v1.0.0`. Auxio's old `v4.1.x` tags were never
  pushed to scarif-labs/unruly, so they won't confuse `UpdateCheckMode: Tags`.
- Author: **Scarif Labs**. License: GPL-3.0-only. The skin kit is MIT, but F-Droid lists the app.
- Anti-features: none. Every dependency is FOSS (the same stack Auxio ships on F-Droid). LRCLIB
  lyrics are an open service and can be turned off, so no `NonFreeNet`.

## Done (uncommitted in this tree)

- `app/build.gradle`: applicationId `com.scarif.unruly`, version 1.0.0 (1).
- `Unruly.kt`: the copyleft-notice guard now checks the new IDs.
- `donottranslate.xml` (main and debug): cover-provider authority is `com.scarif.unruly[.debug].image.CoverProvider`.
- `skills/create-unruly-skin/scripts/push.mjs`: default package is `com.scarif.unruly.debug`.
  The `com.merklegroot.unruly.INSTALL_SKIN` action string is unchanged on purpose; it's just a
  name.
- `fastlane/metadata/android/en-US/`: `title.txt`, `short_description.txt`,
  `full_description.txt`, `changelogs/1.txt`, `images/icon.png` (512 px, rendered from the
  launcher vector with rsvg-convert). `images/phoneScreenshots/` is **empty**.
- fdroiddata recipe: [`fdroid/com.scarif.unruly.yml`](fdroid/com.scarif.unruly.yml) (a copy; the
  working one is in `/tmp/fdroiddata/metadata/`, a shallow clone). It passes `fdroid lint` and has
  been through `fdroid rewritemeta`. It is Auxio's recipe plus JDK 21 from trixie and one prebuild
  line that applies `patches/media-consumer-proguard-agp9.patch`.

## Next

1. ✅ 2026-11-06: `assembleRelease` succeeds from this tree with JDK 17. The default AGP rules
   already keep `@JavascriptInterface`. Signed copy: `app/build/outputs/apk/release/unruly-1.0.0-debugsigned.apk`.
   Old notes: **Release build check.** A clean clone from GitHub is at `/tmp/unruly-fdroid`. It finished,
   both submodules resolved from their public remotes, and the recipe's prebuild (the three seds
   plus the media patch) applied cleanly. It's at the pushed commit, so it still has the
   **old** app ID and version; re-clone after pushing these changes, or just build it to check
   R8. Run `cd /tmp/unruly-fdroid && ./gradlew :app:assembleRelease` with JDK 21 and
   NDK 28.2.13676358. Release has never been
   built (R8, minify, shrinkResources), so expect proguard keep-rule problems, especially for
   the WebView JS bridge (`@JavascriptInterface`) and Hilt.
2. ✅ 2026-11-06: release smoke test on 9b53f076. Onboarding, all 5 built-in skins, playback,
   LRCLIB lyrics and a loaded web skin (template, via Load skin) all work, including skin → app
   control. Phone uses System (MediaStore) mode because SAF can't pick `Download/`.
   Found: iPod skin's album track list is out of order (queue is right); `pack.mjs` fails if
   the out-dir is missing.
2b. ✅ **Removed the legacy Auxio UI** 2026-11-06, tested on 9b53f076 (fresh data): onboarding
   "Use all music on this phone" (deny + allow), library toggle both ways, VIEW audio (warm and
   cold), Shuffle All shortcut, .unruly VIEW install, all 6 skins. Also fixed: iPod album list order
   (SkinLibraryImpl sorts with albumSongSort), pack.mjs creates its out-dir. Details: 253 → ~115 Kotlin files, 84 layouts/menus/nav/xml + 440 unused resources
   (lint UnusedResources, all locales), deps dropped: navigation (+safeargs, compose), fragment,
   recyclerview, constraintlayout, viewpager2, preference, speed-dial; viewBinding off. Release
   APK 14.7 → 12.7 MB. Settings reads the same `<pkg>_preferences` file without androidx.preference.
   Not formatted: pre-existing spotless failures in skin/ and unruly/web, host/SkinHost etc. left
   alone. Known gap: after two denials Android stops showing the audio prompt; the button then
   only shows "NO ACCESS" (no jump to app settings). Original plan: Move into
   `UnrulyActivity`: the `VIEW audio/*` filter and its intent handling, and the Shuffle All
   shortcut. Repoint widget/notification intents. System mode: add a folders/system choice
   to library settings, plus a secondary "Use all music on this phone" button on the onboarding
   folder step (user picked this). Then delete MainActivity, MainFragment and every fragment,
   layout, menu, nav graph and string only they used.
3. ✅ Screenshots 1–6 taken 2026-11-06 (iPod, PSP, Winamp, Zune, iOS 6, settings). Retake if the trim changes UI. Old notes: Ask before driving the phone (9b53f076). Take 4–6 shots of different skins
   into `fastlane/.../images/phoneScreenshots/` (`1.png`, `2.png`, ...).
4. Update CHANGELOG.md with a 1.0.0 entry. Commit with `--no-gpg-sign`, push to `scarif`, tag
   `v1.0.0` and push the tag.
5. Fork fdroiddata (`glab repo fork fdroid/fdroiddata`, logged in as visheh10). Add the recipe on
   a `com.scarif.unruly` branch and open an MR using fdroiddata's MR template. Their CI builds it.
   Confirm with the user before opening the MR, since it's public.

## Notes

- `fdroid` is installed via brew (fdroidserver 2.4.5). It warns about config.yml permissions in
  fdroiddata; that's harmless.
- The phone still has the old `com.merklegroot.unruly.debug` install. A new debug build installs
  alongside it as `com.scarif.unruly.debug`, which needs onboarding again.
