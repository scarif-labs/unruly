# Shellac — Progress Tracker

Master status across all phases. One line per task. Detail lives in `docs/tasks/<id>-*.md`.

**Legend:** ⬜ not started · 🟡 in progress · ✅ done · 🔴 blocked · ⏭️ skipped

Last updated: 2026-08-05

---

## Blockers (live)

| # | Blocker | Blocks | Owner | Status |
|---|---------|--------|-------|--------|
| B1 | No Android device connected | 0.5 + all on-device tests | User | ✅ cleared again — loose cable. `9b53f076`, 2052-song queue. Diagnosis that worked: `ioreg -p IOUSB` showed host controllers with **zero child devices**, proving the fault was below adb |
| B2 | NDK `28.2.13676358` not installed | 0.4 build | Claude | ✅ cleared — installed via `sdkmanager` |
| B3 | No AVD/emulator possible (host RAM) | automated UI testing in all phases | — | ⚠️ permanent constraint. Every phase ends with a **manual** checklist in `docs/verification/phase-N-checks.md` |
| B4 | Device has 2,231 tracks; spec asks for 5,000+ (Ph 5) and 20,000 (Ph 6.2) | 5 acceptance, 6.2 | User | ⚠️ open — needs side-loaded or synthetic media before Phase 5 sign-off |

---

## Phase 0 — Toolchain and Baseline Build

| Task | Description | Status | Doc |
|------|-------------|--------|-----|
| 0.1 | Verify platform | ✅ macOS arm64 (Darwin 25.5.0) — supported, no WSL needed | — |
| 0.2 | Install prerequisites | ✅ JDK17, SDK 35/36, cmake 3.22.1, NDK 28.2.13676358 | [0.2](tasks/0.2-prerequisites.md) |
| 0.3 | Clone with submodules | ✅ media 609M + ffmpeg + taglib, all clean | — |
| 0.4 | Build APK — ⚠️ task is **`:app:assembleDebug`**, not `assembleFullDebug` (no flavors exist) | ✅ 24.4 MB APK, 8 native `.so` across 4 ABIs | [0.4](tasks/0.4-build-toolchain.md) |
| 0.5 | Install on real device | ✅ installed + launched, no crash (pid 12080) | [checks](verification/phase-0-checks.md) |
| 0.6 | Set up fork | ✅ branch `shellac`, remote renamed to `upstream` | — |
| 0.T | Clean rebuild test | ✅ 232/232 tasks executed, 11m29s, identical APK | — |
| 0.C | Commit working state | ✅ `856bddcc3` (unsigned — see papercut) | — |
| 0.M | **Manual device verification** | ✅ user-confirmed 2026-08-05 ("tested the rest and they are good") | [checks](verification/phase-0-checks.md) |
| 0.B | Audio baseline (ReplayGain / gapless) | ⏭️ **skipped — user declined.** Ph 5 acceptance "audio unchanged from baseline" becomes a judgement call, not a comparison. See B5 |

### Phase 0 exit tests
- [ ] `./gradlew assembleFullDebug` clean
- [ ] App launches on device
- [ ] Library scans, correct grouping
- [ ] FLAC plays start to finish, audibly
- [ ] Pause/seek/next/previous
- [ ] Playback survives screen-off
- [ ] Notification controls
- [ ] `clean` then rebuild succeeds

---

## Phase 1 — Map the Engine

| Task | Description | Status | Agent | Doc |
|------|-------------|--------|-------|-----|
| 1.1 | Playback layer | ✅ mapped, citations verified | codex/luna-high | [1.1-1.2](tasks/1.1-1.2-playback-map.md) |
| 1.2 | Command path | ✅ mapped | codex/luna-high | [1.1-1.2](tasks/1.1-1.2-playback-map.md) |
| 1.3 | Music repository | ✅ mapped | codex/luna-high | [1.3-1.5](tasks/1.3-1.5-music-map.md) |
| 1.4 | Playlists and search | ✅ mapped | codex/luna-high | [1.3-1.5](tasks/1.3-1.5-music-map.md) |
| 1.5 | Art loading | ✅ mapped | codex/luna-high | [1.3-1.5](tasks/1.3-1.5-music-map.md) |
| 1.6 | Navigation / Activity structure | ✅ mapped, citations verified | cline/deepseek-v4-flash | [1.6](tasks/1.6-navigation-map.md) |
| 1.7 | Write `ARCHITECTURE-NOTES.md` | ⬜ Claude — synthesis, not delegated | — | — |
| 1.T | Phase 1 test: `Log.d` proof in real classes | ⬜ needs build | — | — |

### Contract-shaping findings (feed directly into Phase 3)

These came out of the mapping and each one contradicts a naive reading of the buildspec's
example types. Resolve them **before** writing `SkinPlaybackState` / `SkinLibrary`.

| # | Finding | Consequence for Phase 3 |
|---|---------|------------------------|
| F1 | Position is **deci**seconds — `PlaybackViewModel.positionDs: StateFlow<Long>`, polled on `delay(100)` | Spec says `positionMs`. Adapter converts. Ph 4.1's "~30fps interpolation" sits on a **10 Hz** source — interpolation is mandatory, not cosmetic |
| F2 | Identity is `Music.UID` — a custom class (format + item kind + `UUID`), **not** `Long` | `SkinSong.id: String` = `uid.toString()`; parse back via `Music.UID.fromString` equivalent. Never assume numeric ids |
| F3 | `Song`/`Album`/`Artist`/`Genre`/`Playlist` are **interfaces**, not data classes | Cannot `copy()`; must construct own Skin types explicitly in the adapter |
| F4 | **No `year` property exists.** Song has `date: Date?`; Album has `dates: Date.Range?` | Spec's `SkinSong.year: Int?` must be derived (`date?.year`). Album needs a decision: `min`, `max`, or a range |
| F5 | Artist albums split into `explicitAlbums` / `implicitAlbums` | `albumsByArtist()` must decide: union, or expose the distinction |
| F6 | `MusicRepository` is **not** a `StateFlow` — changes arrive via `UpdateListener.onMusicChanges(Changes)` | `SkinLibrary.loadState: StateFlow<…>` requires a `callbackFlow` bridge in the adapter |
| F7 | `Library` exposes `Collection`/`Set`, fully materialised in memory. There is no lazy source | ⚠️ Spec wants `Flow<PagingData<…>>` so a skin "can never request all songs". **Paging can only ever be presentational here** — the engine already holds everything in RAM. Worth building anyway (keeps skins honest, keeps UI flat in memory), but the stated memory benefit is not real. Flag before committing to the design |
| F8 | `MainActivity` is a pure NavHost shell — `activity_main.xml` is one `FragmentContainerView` | Ph 3.6 replacement is clean; Activity holds no UI logic beyond theme + edge-to-edge |

## Phase 2 — Compose Interop Spike

| Task | Status | Evidence |
|---|---|---|
| 2.1 Compose deps | ✅ | BOM 2026.06.01, plugin 2.3.10; [doc](tasks/2.1-2.2-compose-deps.md) |
| 2.2 Build with deps unused | ✅ | **caught a real conflict** — lifecycle 2.11.0 needs compileSdk 37; pinned 2.10.0 |
| 2.3 ComposeView renders | ✅ | "COMPOSE ALIVE" on device |
| 2.4 Live state | ✅ | "Lose Yourself to Dance" matches Auxio's own UI simultaneously |
| 2.5 One command | ✅ | Button reads "Pause" while `isPlaying` — state flows engine → Compose |
| 2.6 Library read | ✅ | `LazyColumn` of real albums from a 1664-song library |
| 2.M | **Manual checks** | 🔴 awaiting user — [checks](verification/phase-2-checks.md) |

Agent: codex/luna-high. Three defects found in review — see [doc](tasks/2.3-2.6-compose-spike.md).
Commits `032f1a705`, `4c205d7d4`, `389d66803`.

## Phase 3 — The Skin Contract

| Task | Status | Notes |
|---|---|---|
| 3.1 State model | ✅ | `SkinModels.kt` |
| 3.2 Library access | ✅ | `SkinLibrary.kt` |
| 3.3 Commands | ✅ | `SkinCommands.kt` |
| 3.4 AppSkin | ✅ | `AppSkin.kt` |
| 3.5 Engine adapter | ✅ verified on device | codex/luna-high |
| 3.6 Host Activity | ✅ verified on device | `ShellacActivity` |
| 3.7 Non-skinnable settings | ✅ verified on device | own LAUNCHER entry |
| 3.8 Registry + fallback | ✅ | crash-loop breaker, not an error boundary |
| 3.9 Debug skins | ✅ verified on device | cline/deepseek-v4-flash |
| 3.T Transport coverage | 🟡 | cline extending both skins so every contract command runs |

### Phase 3 exit tests

| Test | Result |
|---|---|
| Both debug skins appear in app-native settings | ✅ |
| Switching swaps the **entire** UI | ✅ A light/plain vs B dark/numbered |
| Choice persists across restart | ✅ `active_skin_id=debug-b` survived |
| Both browse the real library and play correctly | ✅ tap → `Playing PlaybackCommand(song=Burn the Witch, parent=Album(A Moon Shaped Pool), queue=11 songs)` |
| Both show live playback state and control it | 🟡 play/pause only until 3.T lands |
| System back works, exits at root | ⬜ needs manual check |
| App-native settings reachable while each skin active | ✅ separate launcher icon |
| **Nonexistent stored skin id → graceful fallback** | ✅ `Stored skin id 'skin-that-does-not-exist' is unknown; falling back to 'debug-a'`, no crash |
| **Debug B throws in Render → settings still reachable** | ⬜ blocked on 3.T finishing (agent holds those files) |
| Grep: no skin imports `org.oxycblt.*` | ✅ 0 hits |
| Large list scroll, flat memory | ⬜ needs manual check |

### Architecture note

Contract lives in its own `:skin` Gradle module with **no dependency on `:app` or `:musikr`**.
The grep gate is therefore structural — a skin *cannot* import an Auxio type, the module
cannot see one. Grep remains as a backstop against a skin placed in the wrong module.

## Phase 4 — Skin Support Infrastructure

All in `skin/src/main/java/org/shellac/skin/support/`. Commit `8fc0d62cb`.

| Task | File | Status | Agent |
|---|---|---|---|
| 4.1 Position ticker | `PositionTicker.kt` | ✅ builds | codex/luna-high |
| 4.2 Album art | `SkinAlbumArt.kt` | ✅ builds | cline/flash |
| 4.3 Theming | `SkinTheme.kt` | ✅ builds | cline/flash |
| 4.4 Rotary gesture | `RotaryGesture.kt` | ✅ builds | codex/luna-high |
| 4.5 Back stack | `SkinBackStack.kt` | ✅ builds | cline/flash |
| 4.6 Loading states | `SkinStates.kt` | ✅ builds | cline/flash |
| 4.W Wire into debug skins | — | 🟡 running | cline/flash |
| 4.M Manual device checks | — | ⬜ [checks](verification/phase-4-checks.md) |

### Verified by reading, before any consumer existed

- **Rotary wraparound is correct.** `359°→1° = +2` (not −358); `1°→359° = −2`. Works because
  Kotlin's `%` keeps the dividend's sign.
- **Interpolator** freezes on pause, snaps on >500 ms divergence in either direction, clamps
  to `durationMs`, and uses `maxOf` so engine jitter cannot move the display backwards.
- **`SkinTheme` restores system bar appearance in `onDispose`** — without that, one skin's
  bar styling leaks into the next.

### Papercuts

| Issue | Cause |
|---|---|
| `detectDragGestures` unresolved | it lives in `foundation.gestures`, not `ui.input.pointer` |
| `LocalContext.current` inside `remember {}` | a `remember` lambda is not composable scope; hoist it out |
| `WindowCompat` unresolved | **my omission** — `:skin` lacked `androidx.core`. Second time I under-specified this module's deps after `foundation`/`material3` |

**Standing lesson:** when writing an agent prompt that names an API, check the target module
actually has the dependency. Three of the Phase 4 build failures were this, not agent error.

## Phase 5 — Touch Wheel Skin

| Task | File(s) | Status | Agent | Doc |
|---|---|---|---|---|
| 5a Wheel geometry | `TouchWheel.kt` | ✅ builds | codex/luna-high | — |
| 5b Button zones | `WheelZone.kt` | ✅ builds | codex/luna-high | — |
| 5c Rotary | reuses `support/RotaryGesture.kt` | ✅ | — | — |
| 5d Menu list | `WheelMenuList.kt` | ✅ **verified on device** — centring was broken, fixed in 5f commit | codex/luna-high | [5f](tasks/5f-now-playing.md) §2 |
| 5e Menu hierarchy | `TouchWheelSkin.kt`, `WheelDestination.kt` | ✅ **verified on device** | codex/luna-high | [5e](tasks/5e-menu-hierarchy.md) |
| 5e′ Paging fix | `WheelMenuList.kt`, `TouchWheelSkin.kt` | ✅ builds | codex/luna-high | [5e](tasks/5e-menu-hierarchy.md) §3–4 |
| 5f Now playing | `WheelNowPlaying.kt` | ✅ **verified on device** | codex/luna-high | [5f](tasks/5f-now-playing.md) |
| 5g Queue/search | `WheelQueue.kt`, `WheelSearch.kt` | ✅ **verified on device** — search was unusable as built, redesigned around a MENU-switched focus | codex/luna-high + Claude | [5g](tasks/5g-queue-search.md) |
| 5h Polish | `WheelChrome.kt`, `WheelHaptics.kt` | 🟡 visuals + haptics + transitions done; fonts and release profiling outstanding | Claude | [5h](tasks/5h-classic-styling.md) |

Commits `6122a0cdc` (5a/5b/5d), `05b44135a` (5e + paging), `19f69496a` (5f), `9e5e27d93` (selection memory), `145a0b0c8` (classic styling + insets), `209c1fd0d` (hardware proportions).

### 5e found a real contract defect — which is what it was for

The first skin to browse the library at depth hit a gap the debug skins never could:
`SkinCommands` had **no way to start playback mid-collection from a paged flow**.
`playArtist`/`playGenre`/`playPlaylist` start at index 0; `playSongs(list, startIndex)` needs
the whole collection materialised, which `PagingData` by construction never gives you.

The skin's only escape was to force Paging to load the entire library — invisible at the
device's 2,231 tracks, a hard failure at the spec's 20,000.

Fix followed **invariant 1** (engine read-only; missing capability → extend the contract):
five `playSongFrom*(songId, contextId)` methods mapping 1:1 onto Auxio's existing
`play(song, PlaySong.FromX)`. The engine always supported this; the contract just never
exposed it. Full write-up in [5e](tasks/5e-menu-hierarchy.md) §3.

**This is the argument for building a real skin before declaring the contract done.** Three
sub-phases of geometry and list primitives found nothing. The first one to actually navigate
found a design hole in an afternoon.

## Phase 6 — Hardening

| Task | Status | Notes |
|---|---|---|
| 6.1 Second skin | ✅ **verified on device** | Zune-styled, `org.shellac.skin.zune`. Imports nothing from `wheel/` or `org.oxycblt`. **Needed no contract changes** — see [6.1](tasks/6.1-second-skin.md) |
| 6.2 Edge cases | ⬜ | Blocked on B4 (20,000-track library) |
| 6.3 Upstream merge | ⬜ | |
| 6.4 Performance | 🟡 | Release build verified to survive R8; profiling still needs a manual folder grant — see [5h](tasks/5h-classic-styling.md) §9 |
| 6.5 Licensing / README | ⬜ | GPLv3 obligations |

---

## Invariants (never violate)

1. **Engine is read-only.** `org.oxycblt.auxio.playback` and `org.oxycblt.auxio.music` are never edited. Missing capability → extend the contract.
2. **Every phase ends installable.** Never begin a phase on a broken build.
3. **Skins never import `org.oxycblt.auxio.*`.** Enforced by grep from Phase 3 onward.
4. **Commit after every passing test.**
