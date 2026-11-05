# User-loadable skins

Status: 🟡 Web skins built (2026-11-05), uncommitted. Runtime, install, settings UI, authoring kit
and agent skill work on the phone (Redmi, starter template, real library + folder covers). A Codex
end-to-end test ran (see below). Plan below; see "As built" for where it differs.

## As built

- Runtime: `app/.../unruly/web/` - `WebSkin` (locked-down WebView, per-skin origin
  `https://<id>.skin.unruly`, art at `/__unruly/art/<cover id>`, CSP, crash → quarantine),
  `WebSkinBridge` (wire protocol), `UserSkinStore` (zip checks, unpack, manifest).
  `SkinRegistry` lists user skins and gained `reportSkinFailure`, `installUserSkin`,
  `removeUserSkin`. `SkinLibrary` gained plain-list variants + `changes` for the bridge.
- The kit is the skill: `skills/create-unruly-skin/` (SKILL.md, `reference/unruly.d.ts`,
  `template/`, `scripts/` preview/shot/pack/push, `runtime/unruly-bridge.js`). The app packages
  `runtime/` as assets, so the app and the preview run the same bridge file. Symlinked into
  `.claude/skills/` and `.agents/skills/`.
- Debug builds: `INSTALL_SKIN` intent (used by `push.mjs`) and WebView remote debugging.
- Gotchas found on device: load the page only after the WebView has a size (a 0x0 load keeps a
  zero-height viewport); folder-cover ids are whole `fs:content://` URIs, so art is looked up
  directly via `SettingCovers`. The cover provider's one-segment match can't serve those ids,
  which probably affects the built-in skins too (not fixed here).
- Not done yet: keeping the previous version for rollback, "open with Unruly" for `.unruly`
  files, an on-screen JS error overlay, pushing volume changes made with the hardware keys.
- Decided: per-skin `localStorage` is the skin-settings store (per-skin origin keeps it isolated).

## Codex test (2026-11-05)

Fresh `codex exec -s workspace-write` in `~/Work/unruly-codex-skin` with the skill copied to
`.agents/skills/`, brief = vinyl turntable skin (crate with momentum swipe, spinning label, tonearm
seek, Side A/B, liner-note lyrics). ~4 min. Result loaded through the real Load skin picker on the
phone and worked: crate swipe, sleeve tilt, Side A/B, spinning label, tonearm drag-to-seek, real
synced lyrics, back at every level. Bugs: sleeve thumbnail cropped to a corner, "PLAY RECORD"
wraps, current synced lyric not scrolled into view.

It did no visual checks: Codex's sandbox blocks the browser. `shot.mjs` now uses no sockets
(`--remote-debugging-pipe` + Fetch interception) and fails fast with escalation advice, but even
with network allowed the sandbox stops the browser from starting. Screenshots need the agent to
run that command outside its sandbox (user approval, or `codex exec -s danger-full-access`).
Follow-up prompt for the fix round: `~/Work/unruly-codex-skin/followup.md`.

Goal: a user can load a skin they made (or an AI agent made for them) into Unruly, switch to it in
settings like any built-in skin, and get back out if it breaks. The repo ships an agent skill that
takes someone from "I want a skin that looks like X" to a loadable file.

## The format decision

Built-in skins are Kotlin/Compose classes compiled into the APK (`AppSkin.Render`). A user skin
cannot work that way:

- Nobody can compile Kotlin on a phone, and an agent can't hand a user a skin that needs our
  build toolchain to produce.
- Loading compiled code from a file (DexClassLoader) couples every skin to our exact
  Compose/Kotlin versions (every Unruly update would break every user skin) and runs a stranger's
  code with the app's full permissions.

**Recommended: web skins.** A skin is a zip (`.unruly`) of HTML/CSS/JS plus images and fonts,
rendered full-screen in a locked-down WebView and talking to the player through a JS bridge that
mirrors `SkinSession`.

- Expressive enough for what the built-ins do: menus, wheels, gestures, canvas visualisers.
- It's the format AI agents write best, and it can be previewed in a desktop browser.
- Fits F-Droid, where Unruly will ship (see below).
- Isolated: a skin's JS error can't crash the app, and the WebView has no permissions of its own.

Rejected alternative: a **declarative skin** (JSON layout + sprites, Winamp-2 style). It's safer
and simpler but can only re-colour and re-arrange a fixed set of screens. None of the current
built-ins could be expressed in it, and "make it look like a PSP" would be impossible.

The rest of this plan assumes web skins.

## F-Droid

Unruly is distributed through F-Droid, not Google Play. What that means here
(<https://f-droid.org/docs/Inclusion_Policy/>, checked 2026-11-05):

- "Applications must not download additional executable binary files (e.g. add-ons, auto-updates,
  etc.) without explicit user consent." Consent must be opt-in. Loading a skin file the user
  picked themselves is that consent. An in-app gallery would need its own clear opt-in screen.
- FLOSS dependencies only. `androidx.webkit` (`WebViewAssetLoader`) is Apache-2.0, and the
  WebView is a system component, so the runtime adds nothing proprietary.
- A gallery backed by a non-free service would earn the `NonFreeNet` anti-feature. Passing files
  around (or a static, FLOSS-hosted index) avoids it.
- The authoring kit (`skins/sdk/`) is dev tooling in the repo, not part of the APK, so its own
  dependencies don't affect the F-Droid build. It must not be pulled into the Gradle build.

## The package

```
my-skin.unruly            (a zip)
├── skin.json              manifest
├── index.html             entry point
├── preview.png            shown in the settings picker
└── ...                    any css/js/images/fonts, relative paths only
```

`skin.json`:

```json
{
  "id": "com.example.neon",
  "name": "Neon",
  "author": "...",
  "version": "1.0.0",
  "unrulyApi": 1,
  "entry": "index.html",
  "edgeToEdge": true,
  "settingsGear": "top-end",
  "orientation": "portrait"
}
```

- `id` is reverse-domain so two people's "Neon" skins don't collide. Stored as `user:<id>`
  in `SkinRegistry` so a user skin can never shadow a built-in.
- `unrulyApi` is the bridge version. The app refuses newer versions with a clear message and
  keeps supporting older ones.
- `settingsGear` picks a corner but **cannot turn the gear off** (unlike built-ins, see
  `AppSkin.settingsGearAlignment`). The host-drawn gear is the guaranteed way out of a skin we
  didn't write.

## Phase 1: runtime (`WebSkin`)

A single `AppSkin` implementation, `WebSkin(pkg)`, that hosts any installed package.

- **WebView, locked down.** Files are served from `https://skin.unruly/` via
  `WebViewAssetLoader` (from the unpacked package dir). Every other request is refused in
  `shouldInterceptRequest`, plus a strict CSP: no network, no `file://`, no other origins.
  Lyrics already go through our own lrclib client, so skins never need the network.
- **Album art** is served from `https://art.unruly/<id>` by the same interceptor, read through
  the existing cover loading, so `<img src>` just works and stays offline.
- **Bridge** (`window.unruly`), mirroring the Kotlin contract one-to-one:
  - State: `unruly.playback` (snapshot of `SkinPlaybackState`) + `onPlayback(cb)` events.
    Position is pushed at ~4 Hz plus on seek; skins interpolate between pushes.
  - Commands: every `SkinCommands` method, same names and arguments.
  - Library: promise-returning, paged calls (`songs({sort, offset, limit})`, `albumsByArtist`,
    `search`, ...) instead of Kotlin `Flow<PagingData>`, plus `onLibraryChanged(cb)` and the
    `loadState`.
  - Lyrics: `lyrics(songId)` resolving to the `SkinLyricsState` shapes.
  - `openSettings()`, `setSystemBars({dark})`, `haptic()`, and `volume` (wrapping
    `support/MediaVolume`).
- **Back.** `AppSkin.onBackPressed` is synchronous but JS calls aren't, so the skin declares
  ahead of time: `unruly.setCanGoBack(true)` while it has somewhere to go back to. The host
  reads that flag, and when it's true it fires a `back` event instead of exiting.
- **Insets.** The host passes the safe-area insets as CSS variables
  (`--unruly-inset-top`, ...), so it works the same on every WebView version.
- **Lifecycle.** Pause timers and JS when the app is in the background; resume and re-push state on
  return. Visualisers get a `visible` event so they stop drawing.
- **Crash handling.** A WebView renderer crash (`onRenderProcessGone`) or a page that fails to
  load quarantines the skin the same way a built-in's crash does (`SkinRegistry` has no
  in-process failure call yet; `reportSkinFailure` is only named in its doc comment, so add one). An uncaught JS error alone doesn't quarantine; it's logged to a debug overlay.

## Phase 2: install, storage, registry

- **Import:** settings → skin page → "Load skin" (SAF picker for `.unruly`/`.zip`), and
  "open with Unruly" for `.unruly` files from a file manager or a chat app.
- **Validation** before anything is unpacked: zip-slip and symlink checks, size caps (e.g.
  50 MB package, 2000 files), `skin.json` schema, `entry` exists, `unrulyApi` supported.
  Errors are short and specific ("This skin needs a newer Unruly").
- **Storage:** unpacked to `filesDir/skins/<id>/<version>/`; the previous version is kept until
  the new one renders once, so a bad update can roll back.
- **Registry:** `SkinRegistry` gets a dynamic list: built-ins + installed user skins.
  Re-importing an installed `id` is an update.

## Phase 3: settings UI

- Skin page lists built-ins, then user skins with their preview, author and a remove ×.
- "Load skin" button at the end of the page (same `SettingsButton` style as library).
- A crashed user skin shows the existing "CRASHED LAST TIME" label.

## Phase 4: authoring kit (in repo, under `skins/sdk/`)

What a person or agent needs to build a skin without reading app code:

- `unruly.d.ts`: the bridge's types with doc comments. The source of truth for agents. A unit
  test checks every `SkinCommands`/`SkinLibrary` member has a bridge method, so it can't drift.
- `template/`: a minimal working skin (now playing + library list + queue) to copy.
- `preview/`: a browser harness that loads a skin folder with a **mock bridge** (fake library of
  ~2000 songs with art, a fake player that ticks, lyrics), phone-sized frames and inset
  simulation. `npx serve`-level simple: an agent can screenshot it and iterate without a phone.
- `pack`: a script that validates exactly as the app does (same rules, ideally the same schema
  file) and produces the `.unruly` zip.
- **Live reload on device** (debug builds): `adb push` a folder +
  `am start ... --es devSkin <path>` loads it unpacked and reloads on change.
- A reference skin built only with the kit, shipped as a sample, to prove the kit is enough.

## Phase 5: the agent skill

`skills/create-unruly-skin/SKILL.md` (Agent Skills format, readable by Claude Code, Codex and
others; symlinked into `.claude/skills/`). It teaches the agent to:

1. Pin down the look with the user (reference device/app, screens, interactions).
2. Copy `template/`, build against `unruly.d.ts`, and check every screen in `preview/` with
   screenshots, including empty library, long titles and no art.
3. Run `pack`, then hand over the `.unruly` file with load instructions; or use the dev-reload path
   if a device is connected.
4. Follow the hard rules: no network, relative paths only, honour insets, keep `setCanGoBack`
   truthful, stay smooth with large libraries (page, don't load everything).

Acceptance test: a fresh agent session with only the skill builds a working skin from a one-line
brief, and it loads and plays on the phone first time.

## Order

1 → 2 → 3 gives a working path with a hand-written test skin. 4 and 5 follow, with the bridge
frozen as `unrulyApi: 1` before the kit ships.

## Open questions

- Web skins vs declarative (above). This is the decision that blocks everything else.
- Sharing: is a gallery/download inside the app in scope, or only files people pass around?
  (Files only is the F-Droid-simplest; see above.)
- Should user skins be able to store their own small settings (e.g. a colour choice)? Proposed:
  a namespaced key-value store in the bridge, capped in size.
- Licence for user skins: they're the author's own work, not part of Unruly. The template and
  reference skin should be permissively licensed (e.g. CC0/MIT) so derived skins aren't forced
  under the GPL.
