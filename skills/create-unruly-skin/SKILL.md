---
name: create-unruly-skin
description: Build a custom skin (a complete music-player interface) for the Unruly Android music player, as HTML/CSS/JS packed into a .unruly file the user loads in Unruly's settings. Use when someone asks to make, design, change or fix an Unruly skin or theme, e.g. "make me an Unruly skin that looks like a Walkman".
---

# Creating an Unruly skin

An Unruly skin is the whole interface of the music player: browsing, now playing, queue,
lyrics, navigation. Unruly plays the music; the skin is a web page that draws everything and talks
to the player through `window.unruly`. You deliver a `.unruly` file (a zip), and the user loads it
from **Unruly → settings → skin → Load skin**.

Everything you need is in this folder. Paths below are relative to it (`<kit>`).

| Path | What |
| --- | --- |
| `reference/unruly.d.ts` | The complete API with docs. Read it before writing code; it is the only API. |
| `template/` | A small working skin (albums → album → play, mini player). Copy it to start. |
| `scripts/preview.mjs` | Serves a skin with a mock player, for a desktop browser. |
| `scripts/shot.mjs` | Headless phone-sized screenshot of a skin, with scripted taps. |
| `scripts/pack.mjs` | Validates like the app does and writes `<id>-<version>.unruly`. |
| `scripts/push.mjs` | Installs on a USB-connected phone (Unruly debug builds only). |
| `runtime/unruly-bridge.js` | The bridge the app injects. Don't include it; don't edit it. |

Requirements: Node 18+, and Chrome, Chromium, Brave or Edge for screenshots (`CHROME=/path` if it
isn't found). `shot.mjs` uses no network, but agent sandboxes usually stop browsers from starting.
If it says "The browser didn't start", re-run that command with escalated permissions (outside the
sandbox). Don't skip the visual checks.

## Workflow

1. **Pin down the brief.** If the user hasn't said, ask (briefly) what it should look and feel like,
   or what device or app it should evoke, which screens matter most, and how it's driven (taps, a
   wheel, swipes, d-pad). Fill the rest with good defaults; don't over-interview.
2. **Start from the template.** Copy `<kit>/template` to a new folder named after the skin, outside
   the kit. Set `skin.json`: a unique reverse-domain `id` (`com.<author>.<skin>`, lowercase), `name`
   (≤ 40 chars), `author`, `version`.
3. **Build it.** Plain HTML/CSS/JS, no build step needed. If you want a framework or bundler, bundle
   it into files inside the skin folder. Nothing can be loaded from the network at runtime.
4. **Check every screen in the preview** with `shot.mjs`, look at each image, and fix what's wrong.
   Read the console output it prints; `UNCAUGHT` lines and CSP refusals are bugs. Check at least:
   - the main screens, after real navigation (`--run` clicks)
   - `--query library=empty`, `library=scanning`, `playing=0`, and `lyrics=disabled` / `notFound` if
     you show lyrics
   - long titles (the mock library has some) and albums with no art (every 7th)
   - a small phone (`--size 360x740`) and a tall one (`--size 412x915`)
   - back: `--run "__unrulyPreview.back()"` must pop one level, and do nothing at the root
5. **Pack:** `node <kit>/scripts/pack.mjs <skin-dir> <out-dir>`. Fix every error; take warnings
   seriously.
6. **Deliver.** If a phone with an Unruly *debug* build is connected (`adb devices`), install with
   `node <kit>/scripts/push.mjs <file>.unruly` and screenshot it with
   `adb exec-out screencap -p > shot.png`. Otherwise hand the user the `.unruly` file and say: send
   it to the phone and open it (Unruly offers "Install skin"), or open Unruly, tap the gear →
   skin → Load skin, and pick the file.

### shot.mjs examples

```sh
K=<kit>/scripts
node $K/shot.mjs my-skin /tmp/home.png
node $K/shot.mjs my-skin /tmp/album.png --run "document.querySelector('.album').click()"
# several steps, 700 ms apart (--step), then the shot:
node $K/shot.mjs my-skin /tmp/np.png --run "openLibrary()" --run "document.querySelector('.song').click()"
node $K/shot.mjs my-skin /tmp/empty.png --query "library=empty"
```

`--run` code runs in the page, so it can call your own functions or dispatch pointer/touch events.
`--wait` (default 1500 ms) is the time before the first step. `node $K/preview.mjs my-skin` serves
the same thing for a real browser at phone size (devtools device mode).

## The rules

These are what separate a skin that works on a phone from one that only looks right in a browser.

- **Viewport meta is required:**
  `<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">`.
  One CSS px is one Android dp.
- **Insets.** With `"edgeToEdge": true` (the default) you draw under the status and navigation
  bars. Keep text and controls clear of them with `var(--unruly-inset-top)` / `-bottom` / `-left` /
  `-right` (CSS px, set by the app; fall back to `0px`). Backgrounds should still run to the edges.
- **The settings gear.** Unruly draws a small round gear (about 48 px with its margin) in one
  corner of every screen. You can't remove it. Choose the corner with `"settingsGear"`:
  `top-end` (default), `top-start`, `bottom-start`, `bottom-end`. Keep that corner free of your own
  controls, below the inset. You may also offer your own settings entry with
  `unruly.openSettings()`.
- **System back.** Keep `unruly.setCanGoBack(depth > 0)` truthful after every navigation, and pop
  one level on `unruly.on("back", ...)`. At the root, back must leave the app, so it has to be
  false there.
- **Render from events.** Subscribe with `unruly.on("playback" | "queue" | "loadState" | ...)`.
  These fire at once with the current value. Commands like `playPause()` just send a request, and
  the new state arrives as an event. Use `unruly.position()` inside `requestAnimationFrame` for
  smooth progress; `playback.positionMs` only updates about 4 times a second.
- **Big libraries.** People have 50,000 songs. Use `offset`/`limit` and load more on scroll, or
  virtualise. Never fetch everything to draw one screen, and never keep the whole library in the
  DOM. Album and playlist song lists come back whole and are fine.
- **Every state is a screen.** `loadState` is `scanning` on first launch, and `ready` with
  `isEmpty: true` when there's no music ("Add a music folder in settings" + `openSettings()`). Also
  design for nothing playing (`playback.song === null`), `art: null` (draw a placeholder), and long
  names (ellipsis or marquee, never overflowing the layout).
- **Lyrics** come from `unruly.lyrics(songId)`. `notFound` is normal. `disabled` means the user
  turned lookups off, so say so and link to settings. Synced lines have `startMs`; highlight the current one using
  `unruly.position()` and keep it scrolled into view.
- **Offline and self-contained.** No CDNs, web fonts from the internet, analytics or `fetch` to
  other hosts; they're blocked by policy and fail silently. Bundle fonts (check their licence
  allows it) and images in the skin. Relative paths only.
- **Touch, not mouse.** Targets at least 44 px. No hover-only affordances. Use `pointer` events,
  `touch-action` where you handle gestures yourself, `user-select: none` on chrome, and
  `overscroll-behavior: contain` on scrollers. Give quiet feedback with `unruly.haptic()`.
- **Battery.** On `on("visibility", false)` stop `requestAnimationFrame` loops, canvases and
  timers, and start them again when it's true.
- **System bar icons:** `unruly.setSystemBars({ icons: "dark" })` on a light skin, `"light"` on
  a dark one.
- **Your own settings** (theme colour, layout choice) go in `localStorage`. Each skin gets its own
  storage.
- **Look-alike skins** should be original drawings in the spirit of the device. Don't copy
  trademarked logos or ripped artwork.

## Debugging on a phone

Add `"developer": true` to `skin.json` while you work: Unruly then shows a red "N errors" pill
in a bottom corner whenever the skin's JavaScript throws or logs `console.error`, and tapping it
lists them. (Debug builds of Unruly always show it.) Remove the flag before sharing; `pack.mjs`
warns while it's set.

With a debug build, open `chrome://inspect` in desktop Chrome or Brave while the phone is
connected, then inspect the `<id>.skin.unruly` page for its console, DOM and network.
`adb logcat | grep "\[skin"` shows the skin's console too. If the skin fails to load or its
renderer crashes, Unruly falls back to a recovery screen and marks it "CRASHED LAST TIME" in the
picker. Re-loading the file clears that.

## Updating a skin

Bump `version` and load the new file. The same `id` replaces the installed copy, and its
`localStorage` survives. A different `id` installs a second skin. Unruly keeps the replaced
version until the new one has loaded once; if the new one fails to load or its page crashes, it
goes back to the old one and says so in the skin picker.

Users can also open a `.unruly` file straight from a file manager or chat app: Unruly shows its
name and author and installs it when they tap Install.
