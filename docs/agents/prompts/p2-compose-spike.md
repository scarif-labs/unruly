Implement the Compose interop spike (Phase 2, tasks 2.3-2.6) in the Auxio Android project.
This is a DELIBERATELY UGLY proof of interop. Do not design anything. Do not refactor.

## Non-negotiable rules

1. **NEVER edit anything under `app/src/main/java/org/oxycblt/auxio/playback/`,
   `app/src/main/java/org/oxycblt/auxio/music/`, or `musikr/`.** These are the audio engine
   and are read-only. The ONLY exception is adding a `ComposeView` to the layout XML
   `app/src/main/res/layout/fragment_playback_panel.xml` and wiring it in
   `app/src/main/java/org/oxycblt/auxio/playback/PlaybackPanelFragment.kt` — those two
   files, and only for the additions described below. Do not alter existing logic in them.
2. **Do not invent APIs.** Every Auxio symbol you use must appear in the reference below.
   If you need something not listed, STOP and report what is missing rather than guessing.
3. Build config is already done — Compose deps and the compose plugin are present. Do not
   touch `build.gradle` files.
4. Put all NEW Compose code in a new package `org.oxycblt.auxio.shellac.spike`
   (`app/src/main/java/org/oxycblt/auxio/shellac/spike/`).

## Pinned versions — do not use APIs outside these

Kotlin 2.3.10 · AGP 9.2.1 · Gradle 9.4.1 · Compose BOM 2026.06.01 · Java 21 ·
compileSdk/targetSdk 36 · minSdk 24 · activity-compose 1.13.0 ·
lifecycle-runtime-compose 2.11.0 (use `collectAsStateWithLifecycle`)

Use Material3. Do NOT use any Media3 `MediaSessionCompat` patterns — this project is
Media3-only and such code will be rejected.

## Verified engine reference — these symbols are real, cited path:line

### PlaybackViewModel — `app/src/main/java/org/oxycblt/auxio/playback/PlaybackViewModel.kt:57`
Obtain in a Fragment with `by activityViewModels()`. It is a Hilt `@HiltViewModel`.

State (ALL are `StateFlow`, no LiveData):
- `song: StateFlow<Song?>`                     `:67-70`
- `isPlaying: StateFlow<Boolean>`              `:75-78`
- `positionDs: StateFlow<Long>`  ⚠️ **DECI-seconds (1/10 s), not ms**   `:80-83`
- `repeatMode: StateFlow<RepeatMode>`          `:85-88`
- `isShuffled: StateFlow<Boolean>`             `:90-93`

Commands:
- `togglePlaying()`   `:614-618`
- `next()`            `:480-484`
- `prev()`            `:486-490`

### Music models — package `org.oxycblt.musikr` (NOT `org.oxycblt.auxio.music`)
- `Song` is an **interface** (`musikr/src/main/java/org/oxycblt/musikr/Music.kt:270`)
  - `song.name` is a `Name.Known` object, **NOT a String**. To display it, use
    `song.name.resolve(context)` if that method exists — VERIFY by reading
    `musikr/src/main/java/org/oxycblt/musikr/tag/Name.kt` before using it. Report what you find.
  - `song.durationMs: Long`, `song.album: Album`, `song.artists: List<Artist>`
- `Album` is an interface (`Music.kt:333`), `album.name` is also a `Name`.

### MusicRepository — `app/src/main/java/org/oxycblt/auxio/music/MusicRepository.kt:63`
- `library: Library?` — nullable; null until indexing completes
- `Library.albums: Collection<Album>` (`musikr/src/main/java/org/oxycblt/musikr/Library.kt:30`)
- It is **NOT a StateFlow**. Changes arrive via `UpdateListener.onMusicChanges(Changes)`
  (`MusicRepository.kt:198`).

**For reading albums, prefer an existing ViewModel over touching MusicRepository directly.**
`HomeViewModel` (`app/src/main/java/org/oxycblt/auxio/home/HomeViewModel.kt:55`) exposes
`albumList: StateFlow<List<Album>>` — VERIFY this property name and type by reading the file
first, then use it via `by activityViewModels()`. This keeps you out of the music package.

## Tasks

### 2.3 — ComposeView renders
Add a `ComposeView` to `fragment_playback_panel.xml`. The root is a
`ConstraintLayout`; constrain the ComposeView somewhere visible (below the toolbar
`@id/playback_toolbar` is fine) with a fixed height ~200dp so it cannot cover everything.
Give it `android:id="@+id/shellac_compose"`.
In `PlaybackPanelFragment.onBindingCreated` (`:90`), set its content.
It must render the literal text **COMPOSE ALIVE**.

Set `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed` on the ComposeView.

### 2.4 — Live state
Replace the hardcoded text with the current song title from
`playbackModel.song`, collected with `collectAsStateWithLifecycle()`. It must update on
track change without reopening the screen. Show "(nothing playing)" when null.

### 2.5 — One command
Add a Compose play/pause Button calling `playbackModel.togglePlaying()`. Its label/icon must
reflect `playbackModel.isPlaying` so that pausing from the notification updates it.

### 2.6 — Read the library
Add a `LazyColumn` (height-bounded, ~120dp) listing album names from the library.
Handle the not-yet-loaded case without crashing.

## Deliverable

Write the code. Then run:

```bash
export JAVA_HOME=/Users/0x1379/.jdks/jdk-21.0.12+8/Contents/Home
./gradlew :app:assembleDebug
```

Iterate until it compiles. Do not stop at the first error; fix and rebuild.

Then output a report to stdout:
1. Every file you created or modified, with the reason
2. What `Name` turned out to be and how you resolved it to a String, with file:line
3. The exact property you used for the album list, with file:line
4. Anything in the reference above that turned out to be wrong
5. Final build status
