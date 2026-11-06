# Changelog

Changes to Unruly. For the history of the upstream engine, see
[Auxio's changelog](https://github.com/OxygenCobalt/Auxio/blob/dev/CHANGELOG.md); Unruly forked
at Auxio v4.1.5.

## 1.0.0 (2026-11-06)

First release, built on Auxio v4.1.5.

**Engine**
- Reduced Auxio to a headless audio engine. Playback, the service, and the `musikr` indexer are
  unchanged and read-only to skins.
- Removed Auxio's own interface entirely. Opening audio files from other apps and the
  "Shuffle All" shortcut now go through the skin host.
- Added a skin contract (`:skin`) with no dependency on `:app` or `:musikr`, so the module graph
  enforces the engine boundary.
- Added an engine adapter exposing playback state, the library, commands, and lyrics to skins.
- Added lyrics support via LRCLIB — the only network-backed part of the contract, and
  user-toggleable.
- Host-side skin failure handling: a skin that throws while rendering falls back to a known-good
  skin rather than taking the app down.

**Skins**
- Five built-in skins: iPod (click wheel), Zune HD, PSP XMB, iOS 6 Music and Winamp 2.
- Web skins: load your own interface from a `.unruly` file, built with the MIT-licensed
  `create-unruly-skin` kit.

**Setup and settings**
- First-run setup picks music folders, or uses all music on the phone. The second option is the
  only way to reach music in `Download/`, which Android's folder picker refuses.
- Settings for skin, library, lyrics and the system equalizer.

**Project**
- Application ID `com.scarif.unruly`.
- Replaced Auxio branding with the Unruly mark and launcher icons.
