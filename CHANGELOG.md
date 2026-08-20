# Changelog

Changes to Shellac. For the history of the upstream engine, see
[Auxio's changelog](https://github.com/OxygenCobalt/Auxio/blob/dev/CHANGELOG.md); Shellac forked
at Auxio v4.1.5.

## Unreleased

Initial skin-layer work, built on Auxio v4.1.5.

**Engine**
- Reduced Auxio to a headless audio engine. Playback, the service, and the `musikr` indexer are
  unchanged and read-only to skins.
- Added a skin contract (`:skin`) with no dependency on `:app` or `:musikr`, so the module graph
  enforces the engine boundary.
- Added an engine adapter exposing playback state, the library, commands, and lyrics to skins.
- Added lyrics support via LRCLIB — the only network-backed part of the contract, and
  user-toggleable.
- Host-side skin failure handling: a skin that throws while rendering falls back to a known-good
  skin rather than taking the app down.

**Skins**
- **Touch Wheel**: an iPod-style click wheel with a fixed cursor, rotary gesture, hierarchical
  menus, haptics, queue, search, and a now-playing view.
- **Zune**: a flat typographic skin built around album art.
- Removed the two debug skins used to validate the contract during development.

**Project**
- Renamed the app package to `com.merklegroot.shellac` and the application ID to match.
- Replaced Auxio branding with the Shellac mark and launcher icons.
