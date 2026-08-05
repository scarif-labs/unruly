# ARCHITECTURE-NOTES.md

Engine reference for Shellac. **Paste this into every prompt that touches Auxio internals.**

Every symbol below was read in source and cited `path:line`. Anything unverified is marked
⚠️. If you need something not listed here, **read the source — do not invent it.**

- Base commit: `5bd1efe61` (`dev`), Auxio v4.1.5
- Verified: 2026-08-05

## Pin these versions in every prompt

`AGP 9.2.1` · `Kotlin 2.3.10` · `Gradle 9.4.1` · `KSP 2.3.6` · `coroutines 1.10.2`
`NDK 28.2.13676358` · `compileSdk/targetSdk 36` · `minSdk 24` · **`Java 21`**

> The buildspec says JDK 17 and `assembleFullDebug`. **Both are wrong.** The project needs
> **Java 21**, and there are **no product flavors** — the task is `assembleDebug`.

---

## 1. Module layout

| Module | Contains |
|---|---|
| `app/` | UI, playback control, service, settings. Package `org.oxycblt.auxio` |
| `musikr/` | **The indexer and all music models.** Package `org.oxycblt.musikr`. Native taglib under `musikr/src/main/cpp/` |
| `media/` | Patched Media3 fork (submodule) + nested ffmpeg decoder |

**The music models live in `musikr`, not `app`.** The buildspec's "music repository around
`org.oxycblt.auxio.music`" is only half right — `org.oxycblt.auxio.music.MusicRepository` is
a thin app-level holder; the real types are `org.oxycblt.musikr.*`.

---

## 2. Playback state

**`org.oxycblt.auxio.playback.PlaybackViewModel`** — `app/src/main/java/org/oxycblt/auxio/playback/PlaybackViewModel.kt:57`

All UI state is `StateFlow`. No `LiveData`, no `SharedFlow` (confirmed absent).

| What | Exact declaration | Line |
|---|---|---|
| Current song | `val song: StateFlow<Song?>` | `PlaybackViewModel.kt:67-70` |
| Position | `val positionDs: StateFlow<Long>` | `PlaybackViewModel.kt:80-83` |
| Playing | `val isPlaying: StateFlow<Boolean>` | `PlaybackViewModel.kt:75-78` |
| Queue + index | `val pagerQueue: StateFlow<PagerQueue>` — `PagerQueue.queue: List<Song>`, `.index: Int` | `PlaybackViewModel.kt:108-110`, `:664-666` |
| Repeat | `val repeatMode: StateFlow<RepeatMode>` | `PlaybackViewModel.kt:85-88` |
| Shuffle | `val isShuffled: StateFlow<Boolean>` | `PlaybackViewModel.kt:90-93` |

### ⚠️ Position is **deci**seconds, not milliseconds

```kotlin
/** The current position, in deci-seconds (1/10th of a second). */
val positionDs: StateFlow<Long>
```

Updated by a polling coroutine at `delay(100)` — a **10 Hz** source
(`PlaybackViewModel.kt:189-205`). Truth is `Progression.calculateElapsedPositionMs()` using
`SystemClock.elapsedRealtime()` (`playback/state/PlaybackStateHolder.kt:310-322`).

**Consequences:** the adapter must convert ds→ms (`* 100`). Phase 4.1's ~30fps ticker is
**mandatory**, not cosmetic — it interpolates a genuinely coarse source.

### Duration

There is **no** duration property on `PlaybackViewModel` or `PlaybackStateManager`. Read it
from the song: `Song.durationMs: Long` (`musikr/.../Music.kt:293`).

### `RepeatMode`
`org.oxycblt.auxio.playback.state.RepeatMode` — `NONE`, `ALL`, `TRACK`. Exactly three
(`playback/state/RepeatMode.kt:29-45`).

### Layering
`PlaybackViewModel` → `PlaybackStateManager` (central controller,
`playback/state/PlaybackStateManager.kt:30`) → `PlaybackStateHolder` (player-facing,
`playback/state/PlaybackStateHolder.kt:28`) → `ExoPlaybackStateHolder` (holds `ExoPlayer`,
`playback/service/ExoPlaybackStateHolder.kt:68`).

`PlaybackStateManager.Listener` (`:256-317`) — `onIndexMoved`, `onQueueChanged`,
`onQueueReordered`, `onNewPlayback`, `onProgressionChanged`, `onRepeatModeChanged`,
`onSessionEnded`. Register via `addListener` / `removeListener` (`:72-88`); registration
immediately replays current state.

---

## 3. Commands

All on `PlaybackViewModel`:

| Command | Signature | Line |
|---|---|---|
| Play | `play(song: Song, with: PlaySong)` | `:217-224` |
| Toggle play/pause | `togglePlaying()` | `:614-618` |
| Seek | `seekTo(positionDs: Long)` ⚠️ **deciseconds** | `:448-456` |
| Next | `next()` | `:480-484` |
| Previous | `prev()` | `:486-490` |
| Shuffle | `toggleShuffled()` | `:620-624` |
| Repeat | `toggleRepeatMode()` | `:626-634` |

### ⚠️ `PlaySong` — playback context is a *parameter*, not a set of methods

`org.oxycblt.auxio.playback.PlaySong` — `playback/PlaySong.kt:38`

```kotlin
sealed interface PlaySong {
    data object FromAll                          // :47
    data object FromAlbum                        // :52
    data class  FromArtist(val which: Artist?)   // :62
    data class  FromGenre(val which: Genre?)     // :72
    data class  FromPlaylist(val which: Playlist)// :81
    data object ByItself                         // :86
}
```

This differs structurally from the buildspec's `SkinCommands`, which assumes
`playAlbum(albumId)`, `playArtist(artistId)`, etc. **The engine has no such methods.** You
always play *a song* with *a context*. The adapter must translate: resolve the id → find
the first song → call `play(song, PlaySong.FromAlbum)`.

---

## 4. Music library

**`org.oxycblt.musikr.Library`** — `musikr/src/main/java/org/oxycblt/musikr/Library.kt:28`

```kotlin
interface Library {
    val songs: Collection<Song>
    val albums: Collection<Album>
    val artists: Collection<Artist>
    val genres: Collection<Genre>
    val playlists: Collection<Playlist>
    fun empty(): Boolean
}
```

⚠️ **Everything is a materialised in-memory `Collection`.** There is no lazy or paged
source anywhere in the engine — see decision **D3**.

App-level holder: `org.oxycblt.auxio.music.MusicRepository.library: Library?`
(`music/MusicRepository.kt:63-65`). It is **not** a `StateFlow`; changes arrive via
`UpdateListener.onMusicChanges(Changes)` where
`data class Changes(val deviceLibrary: Boolean, val userLibrary: Boolean)`
(`music/MusicRepository.kt:198`). Bridge with `callbackFlow`.

### Load state

`org.oxycblt.auxio.music.IndexingState` — sealed interface, `music/MusicRepository.kt:223`

```kotlin
sealed interface IndexingState {
    data class Indexing(val progress: IndexingProgress) : IndexingState
    data class Completed(val error: Exception?) : IndexingState   // error == null means success
}
```

Observed via a separate `IndexingListener.onIndexingStateChanged()` (`:201-204`).
Note there is **no explicit `Ready` state** — success is `Completed(error = null)`.

### Identity — ⚠️ not a `Long`

`org.oxycblt.musikr.Music.UID` (`musikr/.../Music.kt:76`) is a custom class holding a
format, an item kind, and a `UUID`. Every model has `uid: Music.UID` (`:45`).

Stable string is `uid.toString()` → compressed `u...` form (`:96`). Round-trip helpers used
by Room: `fromMusicUID(uid): String?` / `toMusicUid(string): UID?` (`:122-127`).

**All Skin ids are `uid.toString()`. Never assume numeric ids.**

### Models — all **interfaces**, not data classes (no `copy()`)

| Type | Key properties | Relationships |
|---|---|---|
| `Song` `Music.kt:270` | `name: Name.Known`, `track: Int?`, `disc: Disc?`, `date: Date?`, `uri: Uri`, `path: Path`, `format`, `size`, `durationMs`, `bitrateKbps`, `cover: Cover?` | `album: Album`, `artists: List<Artist>`, `genres: List<Genre>` |
| `Album` `:333` | `dates: Date.Range?`, `releaseType`, `durationMs`, `covers: CoverCollection` | `songs: Collection<Song>`, `artists: List<Artist>` |
| `Artist` `:361` | `durationMs: Long?`, `covers`, `genres` | `songs`, **`explicitAlbums`** + **`implicitAlbums`** |
| `Genre` `:382` | `durationMs`, `covers` | `songs`, `artists` |
| `Playlist` `:396` | `name: Name.Known`, `durationMs`, `covers` | `songs: List<Song>` |

⚠️ **There is no `year` property anywhere.** Song has `date: Date?` (`Date.year`,
`musikr/.../tag/Date.kt:34`); Album has `dates: Date.Range?` with `.min` / `.max`
(`tag/Date.kt:88-90`). Disc number is `song.disc?.number` (`tag/Disc.kt:27`).

### Search — app-side filtering, not a repository query

`org.oxycblt.auxio.search.SearchEngine` — `search/SearchEngine.kt:40`

```kotlin
suspend fun search(items: Items, query: String): Items
data class Items(songs, albums, artists, genres, playlists)  // each Collection<T>?, null if empty
```

It filters a collection **you supply**. It does not own or query the library.

---

## 5. UI structure (the layer being replaced)

`MainActivity` — `MainActivity.kt:54`, `@AndroidEntryPoint`, the **only** UI Activity
(manifest `.MainActivity`, `AndroidManifest.xml:46-52`, `launchMode="singleTask"`).

`activity_main.xml` is a **single `FragmentContainerView`** (`@id/nav_host`,
`app:navGraph="@navigation/outer"`) — the Activity is a pure NavHost shell. `onCreate` does
only: `setupTheme()` → inflate → `setupEdgeToEdge()`.

Two nav graphs: `res/navigation/outer.xml` (start `main_fragment`, plus settings/dialogs)
and `res/navigation/inner.xml` (start `home_fragment`), nested via `explore_nav_host` in
`fragment_main.xml:22`.

**Phase 3.6 is therefore clean:** the Activity holds no UI logic beyond theme and
edge-to-edge. Note `onResume` also starts `AuxioService` and issues
`DeferredPlayback.RestoreState` (`MainActivity.kt:68-81`) — **that must be preserved** in
any replacement Activity, or playback state won't restore.

---

## 6. Contract decisions

Approved by the user 2026-08-05. Each is a **decision, not a fact** — overturnable, but
recorded here so downstream prompts stay consistent.

### D1 (F4) — `year` is derived
```kotlin
SkinSong.year  = song.date?.year
SkinAlbum.year = album.dates?.min?.year
```
Rationale: `dates` is a range; `.min` is the original release, which is what a listener
means by "year". Note `SkinAlbum.year` therefore loses reissue information.

### D2 (F5) — `albumsByArtist` unions both sets, exposes the distinction
```kotlin
fun albumsByArtist(artistId: String): Flow<PagingData<SkinAlbum>>  // explicit + implicit
// SkinAlbum.isExplicitForArtist: Boolean
```
Rationale: a skin that ignores the flag gets sensible behaviour (everything the artist
appears on); a skin that cares can separate "albums by" from "appears on".

### D3 (F7) — keep paging, but the documented rationale is wrong
Keep `Flow<PagingData<T>>` in `SkinLibrary`. **But record honestly:**

> The buildspec justifies paging with "a skin must never request all songs as a
> materialised list". **That benefit does not exist.** `Library` already holds every song in
> RAM as a `Collection` (`Library.kt:29`). Paging here bounds **UI/adapter memory only** —
> it cannot reduce engine memory, because the engine has already paid that cost.

Kept anyway because it bounds composable/list memory (real, and what Phase 3's "scroll a
large list, memory stays flat" test actually measures), keeps skins honest about not
materialising their own copies, and preserves the option if musikr ever goes lazy.

### D4 — `SkinCommands.playAlbum/playArtist/...` need adapter translation
The engine has no such methods (§3). Each becomes: resolve id → pick start song →
`play(song, PlaySong.FromX)`. Empty collections must be handled — decide per-command
whether "play an empty album" is a no-op or an error.

---

## 7. Rules

1. **Never edit `app/.../playback/**`, `app/.../music/**`, or `musikr/**`.** Missing
   capability → extend the contract, not the engine.
2. Skins must not import `org.oxycblt.auxio.*` or `org.oxycblt.musikr.*`. Only the engine
   adapter knows both worlds.
3. Positions crossing the boundary are **milliseconds**. The ds↔ms conversion lives in the
   adapter and nowhere else.
4. All ids crossing the boundary are `String` (`uid.toString()`).
