# Unruly — Full-App Skin Layer over Auxio

**Goal:** Fork Auxio, reduce it to a headless audio engine, and build a skin layer that owns the entire UI. Every screen — browse, search, queue, now-playing, navigation itself — is defined by the active skin. Validate with a complete "Touch Wheel" skin.

**Core principle:** every phase ends in a working, installable app. Never start a phase with a broken build from the last one.

**Architectural rule:** Auxio's playback and indexer packages are read-only to you. If a skin needs something they don't expose, extend the contract — never edit the engine. This rule protects the audio quality that made Auxio the right base.

---

## Phase 0 — Toolchain and Baseline Build

Nothing here is your code. The purpose is to prove the upstream project builds unmodified on your machine before you touch anything.

### Tasks

**0.1 — Verify platform.** Auxio cannot build on Windows; its patched Media3 dependency runs unix shell scripts. On Windows, use WSL2 (Ubuntu) with Android Studio inside it, or a Linux/macOS machine.

**0.2 — Install prerequisites.**
- Android Studio (current stable)
- JDK 17
- Android SDK Platform 35+, Build Tools
- **NDK 28.2.13676358** — that exact version, via SDK Manager > SDK Tools > "Show Package Details"
- `cmake` and `ninja-build`

**0.3 — Clone with submodules.**
```bash
git clone --recurse-submodules https://github.com/OxygenCobalt/Auxio.git unruly
cd unruly
```
If already cloned without the flag: `git submodule update --init --recursive`

**0.4 — Build `fullDebug`.** First build compiles native code; 10–30 minutes is normal. Do not interrupt.

**0.5 — Install on a real device** with your real library. Emulators flatter the indexer.

**0.6 — Set up your fork.**
```bash
git checkout -b unruly
git remote rename origin upstream
```

### Phase 0 test — do not proceed until all pass

- [ ] `./gradlew assembleFullDebug` completes clean
- [ ] App launches on device
- [ ] Library scans; real albums appear with correct grouping
- [ ] A FLAC plays start to finish, audibly
- [ ] Pause, seek, next, previous all work
- [ ] Playback survives screen-off
- [ ] Notification controls work
- [ ] `./gradlew clean` then rebuild still succeeds

**If the build fails:** this is a toolchain problem, not a code problem. LLMs diagnose Gradle/AGP/NDK mismatches badly and will confidently suggest wrong fixes. Read the error, check versions against the README, search the issue tracker.

---

## Phase 1 — Map the Engine

Reading, not writing. Output is knowledge plus a document you'll reuse constantly.

### Tasks

**1.1 — Playback layer.** Around `org.oxycblt.auxio.playback`. Identify the ViewModel exposing state; how current song, position, duration, play state, queue, repeat and shuffle are surfaced; whether via `StateFlow`, `LiveData`, or custom observers.

**1.2 — Command path.** How the UI issues play, pause, seek, next, previous, shuffle, repeat, and queue manipulation.

**1.3 — Music repository.** Around `org.oxycblt.auxio.music`. This is now equally important as playback, since skins browse the library. Identify how the indexed library is exposed, the `Song`/`Album`/`Artist`/`Genre` models, how they relate, and how loading state (scanning, ready, error) is signalled.

**1.4 — Playlists and search.** How playlists are stored and mutated; whether search is a repository capability or UI-side filtering.

**1.5 — Art loading.** How Coil is configured, what identifies a song's artwork.

**1.6 — Navigation and Activity structure.** Which Activity hosts the UI, how the nav graph is wired. You'll be bypassing this, so you need to know its shape to cleanly cut it out.

**1.7 — Write `ARCHITECTURE-NOTES.md`** in the repo root: file paths, class names, exact shapes of state and commands. This is what you paste into LLM prompts. Highest-leverage artifact in the project — it prevents models inventing plausible-looking APIs that don't exist.

### Phase 1 test

- [ ] You can name the class holding playback state and the method for "skip next"
- [ ] You can name the class exposing the indexed library
- [ ] You can trace a song title from player to screen, and an album from indexer to list
- [ ] `ARCHITECTURE-NOTES.md` would let someone without repo access describe both data flows
- [ ] A `Log.d` in the playback ViewModel fires on track change
- [ ] A `Log.d` in the music repository fires when the library finishes loading

The log statements prove you found the real classes, not plausible-looking ones.

---

## Phase 2 — Compose Interop Spike

Smallest proof that Compose renders inside Auxio and reads live state. Deliberately ugly.

### Tasks

**2.1 — Add Compose dependencies.** Compose BOM, `ui`, `material3`, `runtime`, `activity-compose`, `lifecycle-runtime-compose`, `navigation-compose`. Enable `buildFeatures { compose = true }`. With Kotlin 2.0+, apply the `org.jetbrains.kotlin.plugin.compose` plugin.

**2.2 — Build with dependencies present but unused.** Isolates version conflicts where the error is unambiguous.

**2.3 — `ComposeView` in the now-playing layout,** alongside existing UI. Hardcoded text: "COMPOSE ALIVE".

**2.4 — Wire live state.** Replace with current song title, collected lifecycle-aware.

**2.5 — One command.** A Compose play/pause button that actually controls playback.

**2.6 — Read the library from Compose.** A `LazyColumn` of album names pulled from the music repository. Proves the second data source works too.

### Phase 2 test

- [ ] Builds and runs with Compose present
- [ ] "COMPOSE ALIVE" renders
- [ ] Title updates on track change without reopening the screen
- [ ] Compose play/pause actually pauses and resumes audio
- [ ] Its icon reflects state changed elsewhere (notification, existing UI)
- [ ] Album list populates from the real library
- [ ] Rotation doesn't crash or desync

Points 4 and 5 together prove state flows both directions. Point 6 proves the library is reachable.

---

## Phase 3 — The Skin Contract

The most important design work in the project. Get it wrong and every later skin forces a refactor.

### Tasks

**3.1 — State model.** Immutable, decoupled from Auxio's internals:

```kotlin
data class SkinPlaybackState(
    val song: SkinSong?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val queue: List<SkinSong>,
    val queueIndex: Int,
    val repeatMode: SkinRepeatMode,
    val isShuffled: Boolean
)

data class SkinSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String,
    val artistId: String,
    val albumArtUri: Uri?,
    val durationMs: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?
)
```

Your own types, not Auxio's. This is what lets you pull upstream changes without every skin breaking.

**3.2 — Library access.** Lazy and paged. A skin must never be able to request "all songs" as a materialised list.

```kotlin
interface SkinLibrary {
    val loadState: StateFlow<LibraryLoadState>

    fun artists(sort: SkinSort): Flow<PagingData<SkinArtist>>
    fun albums(sort: SkinSort): Flow<PagingData<SkinAlbum>>
    fun songs(sort: SkinSort): Flow<PagingData<SkinSong>>
    fun genres(): Flow<PagingData<SkinGenre>>
    fun playlists(): Flow<PagingData<SkinPlaylist>>

    fun albumsByArtist(artistId: String): Flow<PagingData<SkinAlbum>>
    fun songsByAlbum(albumId: String): Flow<List<SkinSong>>
    fun songsByGenre(genreId: String): Flow<PagingData<SkinSong>>
    fun songsByPlaylist(playlistId: String): Flow<List<SkinSong>>

    fun search(query: String): Flow<SkinSearchResults>
}
```

**3.3 — Commands,** including queue and playlist mutation:

```kotlin
interface SkinCommands {
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeatMode()

    fun playAlbum(albumId: String, startIndex: Int = 0)
    fun playArtist(artistId: String)
    fun playPlaylist(playlistId: String)
    fun playSongs(songs: List<SkinSong>, startIndex: Int = 0)

    fun playQueueItem(index: Int)
    fun addToQueue(songs: List<SkinSong>)
    fun playNext(songs: List<SkinSong>)
    fun removeFromQueue(index: Int)
    fun moveInQueue(from: Int, to: Int)
}
```

**3.4 — The skin itself.** A single entry point. The skin owns all navigation internally — no shared nav graph, because navigation model is part of a skin's identity.

```kotlin
interface AppSkin {
    val id: String
    val displayName: String
    val supportsEdgeToEdge: Boolean

    @Composable
    fun Render(session: SkinSession)
}

class SkinSession(
    val playback: StateFlow<SkinPlaybackState>,
    val library: SkinLibrary,
    val commands: SkinCommands,
    val openAppSettings: () -> Unit
)
```

**3.5 — Engine adapter.** One class translating Auxio's real ViewModels and repository into the contract types. The only file that knows both worlds. Isolating it here is what keeps skins portable and upstream merges cheap.

**3.6 — Host Activity.** Replaces Auxio's nav-graph Activity. Hosts exactly one thing: the active skin's `Render`. Handles system back by delegating to the skin, exiting only when the skin reports it's at its root.

**3.7 — Non-skinnable settings.** *Build this before any real skin.* An app-native settings Activity holding the skin picker, library rescan, and audio options. Must be reachable even if the active skin is broken or crashes on launch — otherwise a bad skin means reinstalling. Wire it to a system-level entry point, not only an in-skin button.

**3.8 — Registry and persistence.** Skin ID to `AppSkin` map, persisted active-skin preference, safe fallback to a known-good skin if the stored ID is missing or its skin throws during composition.

**3.9 — Two debug skins.** "Debug A" and "Debug B": a plain list of albums, tap to play, a text now-playing view, one back level. Ugly on purpose — you're testing the mechanism, not the design.

### Phase 3 test

- [ ] Both debug skins appear in app-native settings
- [ ] Switching swaps the entire UI, not just one screen
- [ ] Choice persists across restart
- [ ] Both browse the real library and play correctly
- [ ] Both show live playback state and control it
- [ ] System back works inside each skin and exits at root
- [ ] App-native settings is reachable while each skin is active
- [ ] **Set the stored skin ID to a nonexistent value; app falls back gracefully instead of crashing**
- [ ] **Make Debug B throw in `Render`; you can still reach settings and switch away**
- [ ] Grep confirms neither debug skin imports `org.oxycblt.auxio.*` — only your skin package
- [ ] Scroll a large library list; no jank, memory stays flat (proves paging works)

The two bolded tests are what make the system survivable while you develop skins. The grep is the architectural gate — if a skin needs an Auxio type, extend the contract instead. Enforce it now, while there are two trivial skins and the fix is cheap.

---

## Phase 4 — Skin Support Infrastructure

Shared machinery every skin needs. Build once here so skins don't each reimplement it differently.

**4.1 — Position ticker.** Interpolated ~30fps position for animation, distinct from the player's coarser updates. Pauses with playback, resyncs on seek.

**4.2 — Album art composable.** Wraps Coil; per-skin fallback for missing art; no reload flicker on recomposition.

**4.3 — Skin-scoped theming.** Each skin controls colours, typography, and system bar appearance without leaking into app-native settings.

**4.4 — Gesture helpers.** Reusable rotary/angular detection with configurable quantisation. The wheel needs it; later skins likely will too.

**4.5 — Navigation helpers.** An optional back-stack primitive skins may use for hierarchical navigation. Optional — a flat skin shouldn't be forced into it.

**4.6 — Loading and empty states.** Standard hooks for library-scanning, empty-library, and no-results conditions, styled per skin.

### Phase 4 test

- [ ] Debug A shows interpolated position advancing smoothly, not in jumps
- [ ] Position freezes on pause, resumes correctly, snaps on seek
- [ ] Art displays; missing art shows fallback without crashing
- [ ] Debug A and B have visibly different themes with no bleed into app settings
- [ ] Rotary helper logs sensible angle deltas in a test composable
- [ ] Back-stack helper handles push/pop/reset correctly under rotation
- [ ] Empty-library state renders instead of a blank screen
- [ ] 10+ rapid skin switches leak nothing (check the profiler)

---

## Phase 5 — Touch Wheel Skin

The validation target: a complete UI, not a themed screen. Build in strict sub-phases, each independently testable.

Naming: **Touch Wheel**, not iPod. Apple holds trademarks and design patents here. Irrelevant privately, but the habit is free and matters if you ever share it.

### 5a — Wheel component, static
Screen split: content region above, wheel below. Circular wheel, centre button, four labelled zones (menu, next, previous, play/pause). No interaction.

**Test:** proportions hold in portrait and on a tablet or resized window; centre button is a comfortable touch target.

### 5b — Button input
Five zones trigger correct commands against a stub.

**Test:** all five fire correctly; targets don't overlap; boundary taps resolve predictably.

### 5c — Rotary gesture
Circular drags produce directional deltas quantised to discrete clicks (~12–15° each).

**Test:** clockwise and counter-clockwise give opposite signs; a full circle yields consistent click counts both ways; centre and outside-ring touches ignored; slow drags don't jitter direction.

### 5d — Menu list component
The reusable list primitive for the whole skin: selection fixed vertically, items move under it, wheel drives selection, centre button activates.

**Test:** one item per click; clamps at both ends; a 2,000-item list scrolls without stutter; selection stays visually centred.

### 5e — Menu hierarchy
Root → Music → Artists / Albums / Songs / Genres / Playlists → drill down → now playing. Plus Shuffle Songs and Now Playing at root. Menu button ascends.

**Test:** every level navigates correctly; menu button always ascends and never dead-ends; deep drill-down then playback works; rotation preserves the full stack; back at root exits.

### 5f — Now-playing view
Art, title, artist, album, progress bar, "3 of 12", elapsed and remaining. Wheel scrubs position here.

**Test:** metadata correct against a known album; progress smooth; scrubbing seeks accurately and audibly; times correct at 0:00, mid-track, and end.

### 5g — Queue and extras
Queue view with reordering, add-to-queue and play-next from list context, search screen (wheel-driven character selection, or a pragmatic keyboard fallback).

**Test:** queue reflects reality and reorders correctly; play-next inserts at the right index; search returns correct results and plays from them.

### 5h — Polish
Haptics per click. Selection styling. Transition animations between menu levels. Font and colour treatment. Optional backlight dimming.

**Test:** haptics fire once per click, not per touch event; no dropped frames during scroll (profiler); coherent rather than assembled.

### Phase 5 acceptance

- [ ] A full session — launch, browse by artist, drill to album, play, skip, scrub, queue something, return to root — entirely via the wheel
- [ ] Switch to Debug B and back; both still work
- [ ] Audio unchanged from baseline: FLAC gapless, ReplayGain still applies
- [ ] No crashes across a 30-minute session
- [ ] Queue and playback position survive app restart
- [ ] Works on a library of 5,000+ tracks without lag

---

## Phase 6 — Hardening

- **6.1 — Second real skin** (Cassette or Minimal), ideally with a *different* navigation model — flat or single-page. This is what proves the contract generalises rather than having been shaped around the wheel. If it forces contract changes, that information arrives cheaply now.
- **6.2 — Edge cases:** empty library, single-song queue, missing metadata, corrupt art, very long titles, non-Latin scripts, 20,000-track library.
- **6.3 — Upstream merge test:** `git fetch upstream && git merge upstream/dev`. If only the engine adapter breaks, your architecture is correct.
- **6.4 — Performance:** battery over an hour, memory over a long session, cold start.
- **6.5 — Licensing:** GPLv3 requires that a distributed fork be marked as modified. README credits Auxio, links upstream, states changes. Good manners regardless.

---

## Working Notes for LLM-Assisted Development

**Paste `ARCHITECTURE-NOTES.md` into every prompt touching Auxio internals.** Without it, models invent plausible-looking class and method names.

**Paste the skin contract into every skin prompt.** Once Phase 3 is done, most skin work needs *only* the contract — not the Auxio codebase. That's the payoff of the boundary: small, self-contained context per task.

**Pin versions in prompts** — Media3, Compose BOM, Kotlin. Media3 is the worst area for stale training data; models mix deprecated `MediaSessionCompat` patterns into Media3 code with total confidence.

**One sub-phase per session.** "Build 5c: rotary gesture detection returning quantised click deltas" is a good task. "Build the iPod skin" is not.

**Commit after every passing test.** Working states are cheap to create and expensive to recover.

**Never let a model edit the playback or music packages.** If a change seems to need it, the contract is missing something — extend `SkinPlaybackState` or `SkinLibrary` instead.