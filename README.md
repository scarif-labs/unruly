<p align="center"><img src="docs/logo.png" width="150"></p>
<h1 align="center"><b>Shellac</b></h1>
<h4 align="center">A music player whose entire interface is a swappable skin.</h4>
<p align="center">
    <a href="https://www.gnu.org/licenses/gpl-3.0">
        <img src="https://img.shields.io/badge/license-GPL%20v3-2B6DBE.svg?style=flat">
    </a>
    <img alt="Minimum SDK Version" src="https://img.shields.io/badge/API-24%2B-1450A8?style=flat">
</p>
<h4 align="center"><a href="/CHANGELOG.md">Changelog</a> | <a href="/ARCHITECTURE-NOTES.md">Architecture</a> | <a href="/buildspec.md">Buildspec</a></h4>

## About

Shellac is a fork of [Auxio](https://github.com/OxygenCobalt/Auxio) reduced to a headless audio
engine, with a skin layer on top that owns the entire user interface.

A skin here is not a theme. It is not a colour palette or an icon pack laid over a fixed set of
screens. A skin defines **every** screen — browsing, search, queue, now-playing — and the
navigation model that connects them, because the way you move through a music library is part of
that interface's identity. An iPod-style wheel and a flat scrolling page have nothing useful to
share, so Shellac does not force them to.

Two skins ship today:

| Skin | What it is |
|---|---|
| **Touch Wheel** | An iPod-style click wheel: a fixed cursor, a rotary gesture, a hierarchical menu, and haptics on each tick. |
| **Zune** | A flat, typographic interface built around large headings and album art. |

Underneath, playback and music indexing are Auxio's, untouched. That was the point of forking
Auxio rather than starting fresh: the audio path and the tag indexer are the parts that are hard
to get right, and they are the parts Shellac does not rewrite.

## Architecture

```
app/      Host: playback control, service, indexer glue, app-native settings.
          Package com.merklegroot.shellac
skin/     The skin contract, the support layer, and the shipped skins.
          Package org.shellac.skin — no dependency on :app or :musikr
musikr/   The indexer and all music models, with native taglib under src/main/cpp
media/    Patched Media3 fork (submodule), plus a nested ffmpeg decoder
```

The engine is **read-only** to skins, and the module graph enforces it: `:skin` cannot see
`com.merklegroot.shellac` or `org.oxycblt.musikr`, so a skin physically cannot reach into
playback or indexing. When a skin needs something the engine does not expose, the fix is to
extend the contract in [`AppSkin.kt`](skin/src/main/java/org/shellac/skin/AppSkin.kt) — never to
edit the engine.

A skin is handed a `SkinSession` — playback state, the library, a command surface, lyrics, and a
way to open app-native settings — and returns a composable. That is its whole universe.
[`ARCHITECTURE-NOTES.md`](ARCHITECTURE-NOTES.md) documents the engine surface it draws from,
every symbol cited to a source line.

If a skin throws while rendering, the host catches it, falls back to a known-good skin, and
surfaces the failure. Settings are also reachable from a system-level entry point, so a broken
skin can never lock you out of switching away from it.

## Building

Shellac cannot be built on Windows — the patched Media3 dependency runs unix shell scripts. Use
Linux, macOS, or WSL2.

Requirements:

- **JDK 21** (not 17)
- Android SDK Platform 36, Build Tools
- **NDK 28.2.13676358** — that exact version, via SDK Manager → SDK Tools → "Show Package Details"
- `cmake` and `ninja`

```bash
git clone --recurse-submodules https://github.com/merkle-groot/shellac.git
cd shellac
(cd media && git apply ../patches/media-consumer-proguard-agp9.patch)
./gradlew assembleDebug
```

There are **no product flavors** — the task is `assembleDebug`, not `assembleFullDebug`. The
first build compiles native code and takes 10–30 minutes; do not interrupt it.

The patch step is required. See [`patches/README.md`](patches/README.md) for why, and
re-apply it after any `git submodule update`.

Install on a real device with a real library. Emulators flatter the indexer.

## Writing a skin

1. Add a class in `skin/src/main/java/org/shellac/skin/` implementing `AppSkin`.
2. Give it a stable `id` — it is persisted as the user's preference and must never change once
   shipped — and a `displayName` for the settings list.
3. Register it in `SkinRegistry`.

Read the KDoc on `AppSkin` before you start. It documents the rules the host relies on,
including the one exception to "hold state in `rememberSaveable`": navigation state that
`onBackPressed` must act on has to live in a field, because `onBackPressed` is not a composable.

## License

Shellac is licensed under the [GNU General Public License v3](LICENSE), inherited from Auxio.

Auxio is copyright the Auxio Project, and remains the source of everything under `musikr/`, the
`media/` submodule, and the playback engine in `app/`. Shellac is not affiliated with or endorsed
by the Auxio project — please direct issues with Shellac here, not upstream.
