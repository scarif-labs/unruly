# Contributing to Shellac

Shellac is a fork of [Auxio](https://github.com/OxygenCobalt/Auxio). Please file issues about
Shellac here — not upstream. If a bug reproduces in stock Auxio, it belongs upstream instead.

## Bugs and crashes

Open an issue with:

- What happened, and what you expected instead.
- The steps to reproduce it.
- Which skin was active. Most UI bugs are skin bugs.
- A logcat or stack trace if you have one. Longer is better.

## Feature requests

Open an issue describing what you want and the problem it solves. Be aware of the architectural
line the project draws: anything that changes how a *screen* looks or behaves belongs in a skin,
not in the engine. "Add a setting to reorder the queue screen" is a skin change; "expose queue
reordering in the skin contract" is an engine change.

## Code

- **Never edit the playback or indexer packages.** They are Auxio's, and the audio quality they
  provide is why Shellac forked Auxio instead of starting fresh. If a skin needs something they
  do not expose, extend the skin contract in `skin/src/main/java/org/shellac/skin/AppSkin.kt`.
- **Skins must not import `com.merklegroot.shellac.*` or `org.oxycblt.musikr.*`.** The module
  graph enforces this — if you find yourself fighting it, the contract is what needs changing.
- Kotlin only, except where a UI component has to be vendored.
- Run `./gradlew spotlessApply` before you push. CI checks formatting.
- Test on a real device with a real library. Emulators flatter the indexer.
- Keep GPLv3 headers on new files; `spotlessApply` adds them for you.

## New skins

Adding a skin is the intended way to extend Shellac. Implement `AppSkin`, register it in
`SkinRegistry`, and read the KDoc on `AppSkin` first — it documents the rules the host relies on.
Pick a stable `id`: it is persisted as the user's preference and can never change once shipped.
