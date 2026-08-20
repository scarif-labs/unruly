Write two deliberately ugly debug skins for the Shellac project, an Android music player.

Working directory is the repo root. Create files under:
skin/src/main/java/org/shellac/skin/debug/

## Critical constraint

You may ONLY use types from the `org.shellac.skin` package. Read these four files first and use nothing outside them:

- skin/src/main/java/org/shellac/skin/AppSkin.kt
- skin/src/main/java/org/shellac/skin/SkinModels.kt
- skin/src/main/java/org/shellac/skin/SkinLibrary.kt
- skin/src/main/java/org/shellac/skin/SkinCommands.kt

You must NOT import anything from `org.oxycblt.auxio` or `org.oxycblt.musikr`. The `skin` module cannot even see those packages, so such an import will not compile. Do not attempt it. Do not read the app/ or musikr/ directories at all - you do not need them, and that is the entire point of this exercise.

Do not modify any existing file. Do not touch any build.gradle.

## Pinned versions

Kotlin 2.3.10, Compose BOM 2026.06.01, Material3, androidx.paging 3.5.0, minSdk 24, Java 21.

Use `androidx.paging.compose.collectAsLazyPagingItems()` for `Flow<PagingData<T>>`. Use `androidx.lifecycle.compose.collectAsStateWithLifecycle()` is NOT available in this module - use `collectAsState()` from `androidx.compose.runtime` instead.

## What to build

Two skins, `DebugSkinA` and `DebugSkinB`, each implementing `AppSkin`. They exist to test the mechanism, not to look good. Ugly is correct. Keep each under about 150 lines.

Each skin needs:
1. An album list as the root screen, from `session.library.albums()`. Use `collectAsLazyPagingItems()`.
2. Tapping an album calls `session.commands.playAlbum(album.id)` and navigates to a now-playing screen.
3. A text-only now-playing screen: title, artist, album, whether it is playing, and position/duration as raw milliseconds. Read from `session.playback` via `collectAsState()`.
4. A play/pause button calling `session.commands.playPause()`.
5. One level of back navigation: now-playing returns to the album list. Implement `onBackPressed()` to return true when on the now-playing screen and false when at the album list root.
6. A button calling `session.openAppSettings()`.
7. Handle `session.library.loadState` - show something sensible for `Scanning`, `Failed`, and an empty `Ready`.

Hold the current screen in a `rememberSaveable`. Note `onBackPressed()` is a plain interface method, not composable, so you will need to keep the navigation state somewhere the skin object can read and mutate - a simple `mutableStateOf` field on the skin class is fine for a debug skin.

Make the two skins visibly different so a person can tell at a glance which is active:
- DebugSkinA: id "debug-a", displayName "Debug A", light-ish colours, album list as a plain LazyColumn of text rows.
- DebugSkinB: id "debug-b", displayName "Debug B", dark colours with a different font size/weight, album list showing "index. title" prefixed with the row number.

Set `supportsEdgeToEdge` to false for both.

## Verify

You cannot run Gradle in this sandbox - do not try. Instead, re-read your own files and check every symbol you used actually exists in the four contract files listed above. Pay attention to exact property names, nullability, and whether something returns a Flow, a StateFlow, or a plain value.

## Report to stdout

1. Files created
2. Every contract symbol you used, and which file it came from
3. Anything in the contract that was missing, ambiguous, or that made the skins awkward to write - this feedback is genuinely valuable, the contract is new and unproven
4. Anything you were tempted to reach into the engine for
