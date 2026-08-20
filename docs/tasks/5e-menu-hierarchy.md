# Phase 5e — Touch Wheel menu hierarchy

**Status:** implemented, compiling; paging regression fixed in follow-up (see §4)
**Agent:** codex / `gpt-5.6-luna`, reasoning `high`
**Prompt:** `docs/agents/prompts/p5-hierarchy.md`
**Log:** `docs/agents/logs/p5-hierarchy.log`

Files created:
- `skin/src/main/java/org/shellac/skin/wheel/TouchWheelSkin.kt` (598 lines)
- `skin/src/main/java/org/shellac/skin/wheel/WheelDestination.kt` (16 lines)

This was the first sub-phase to exercise the whole contract at depth: Root → Music → five
categories → drill-down → now-playing. Everything before it was flat skins with one level of
navigation, which is why this is where the design problems surfaced.

---

## 1. What worked

**The isolation gate held.** `grep -rc "org.oxycblt" skin/.../wheel/` returned 0 across all
five files. A 598-line skin doing real library browsing, playback and navigation was written
against nothing but `org.shellac.skin` plus Compose. That is the central claim of the whole
project and this is the first non-trivial evidence for it.

**Naming discipline held.** `grep -rni "ipod|apple|click wheel" skin/` is clean.

**The back stack in a field worked exactly as designed.** `SkinBackStack<WheelDestination>`
as a field on the skin object, `onBackPressed() = backStack.pop()`, and the MENU zone calling
the same `backStack.pop()`. Hardware back and wheel MENU are the same code path by
construction rather than by discipline, which is what we wanted — the failure mode here is
the two silently disagreeing.

**Per-destination selection indices** via `mutableStateMapOf<WheelDestination, Int>`, keyed
by the destination itself. Works because `WheelDestination` variants are `data object` /
`data class`, so equality and hashing are structural — `AlbumSongs("abc", "X")` recreated on
re-navigation matches the stored key. This is a case where the data-class navigation model
paid for itself.

**Scoping now-playing to text only was the right call.** It stayed text-only. Left unscoped
this would have grown a half-built version of 5f that then had to be unpicked.

---

## 2. Papercut: a trailing lambda bound to the wrong parameter, and it compiled

Two call sites, both this shape:

```kotlin
@Composable
private fun <T : Any> wheelListBody(
    title: String,
    items: List<T>,
    selectedIndex: Int,
    itemLabel: (T) -> String,
    itemKey: ((T) -> Any)? = null,
)

// call site
wheelListBody("Touch Wheel", items, selectedIndex) { item -> "Music" /* ... */ }
```

The trailing lambda binds to the **last** parameter, `itemKey`. And it *fits*: `(T) -> String`
is a subtype of `(T) -> Any`, so there is no type error at the lambda. The error surfaces
somewhere else entirely, as `No value passed for parameter 'itemLabel'`.

This one was caught by the compiler, so it cost nothing. Worth recording anyway because the
near-miss is instructive: had `itemKey` been non-nullable with a default, or had `itemLabel`
also had a default, this would have compiled cleanly and shipped a list keyed by its display
string and labelled by nothing. Kotlin's trailing-lambda sugar plus function subtyping means
"it type-checks" is a weaker signal than usual around trailing lambdas.

**Rule added to the prompt template:** pass function-typed arguments **by name** when the
callee has more than one of them.

**Fix:** named arguments at both call sites.

---

## 3. Papercut: `LazyPagingItems` has no complete list, and the contract assumed one

The agent flagged this itself, in its own words:

> Paging does not expose a complete materialized list; paged song playback uses the currently
> loaded snapshot with `playSongs`.

That is an honest report of a workaround, and it points at a genuine contract defect rather
than an implementation slip. What it actually wrote:

```kotlin
private fun <T : Any> LazyPagingItems<T>.wheelItems(): List<T> {
    if (itemCount > 0) {
        this[itemCount - 1]   // force Paging to load through to the end
    }
    return itemSnapshotList.items
}
```

Indexing the last element tells Paging to load every page up to it. So **every paged screen
pulled the entire library into memory** and passed it on as a `List`. On the test device
(~2,200 tracks) this works and looks fine. At buildspec Phase 6.2's 20,000 tracks it is a
straightforward failure, and it defeats the entire point of having used `PagingData`.

Note what kind of bug this is: it compiles, it passes every check on the dev device, and it
only fails at a scale nobody tests interactively. Compilation was never going to catch it and
neither was a device smoke test.

### Root cause was in the contract, not the skin

The skin had no better option. `SkinCommands` offered:

| Method | Can start mid-collection? |
|---|---|
| `playAlbum(albumId, startIndex)` | yes |
| `playArtist(artistId)` | **no** — index 0 only |
| `playGenre(genreId)` | **no** — index 0 only |
| `playPlaylist(playlistId)` | **no** — index 0 only |
| `playSongs(songs, startIndex)` | yes, but needs the whole list materialised |

To play track #500 of the paged all-songs list, the only route was `playSongs` — which means
building a 20,000-element list. The contract left no alternative.

The engine never had this limitation. `PlaybackViewModel.play(song: Song, with: PlaySong)`
takes **one song plus a browsing context**, which is precisely "start here, in this
collection". The contract had documented that overload and then failed to expose it.

### Fix — contract extension

Added to `SkinCommands`:

```kotlin
fun playSongFromAll(songId: String)
fun playSongFromAlbum(songId: String, albumId: String)
fun playSongFromArtist(songId: String, artistId: String)
fun playSongFromGenre(songId: String, genreId: String)
fun playSongFromPlaylist(songId: String, playlistId: String)
```

A 1:1 mapping onto `PlaySong`'s variants. A skin now names a starting point with two ids and
never materialises anything.

Implemented in `SkinCommandsImpl` as `play(song, PlaySong.FromX(...))`. One detail worth
noting: `PlaySong.FromArtist` and `FromGenre` take a **nullable** `which`. Passing null lets
Auxio pick the song's own artist/genre — which is wrong for a song belonging to more than one
artist, since the queue would be built from a collection the user was not browsing. The
adapter resolves and passes the real object.

`playSongs` remains, correctly scoped to genuinely ad-hoc lists (search results) that are
small and already in hand.

---

## 4. Follow-up dispatch

The contract fix removes the *need* to materialise for playback, but the *rendering* path
still went through `wheelItems()`. Dispatched as a separate task
(`docs/agents/prompts/p5-paged-list.md`) to:

- delete `wheelItems()` entirely
- add a `LazyPagingItems` overload of `WheelMenuList` (`item: T?`, since an unloaded row is
  null; `isSelected` by **index**, not by `===`, because null placeholders would otherwise all
  compare equal to each other)
- resolve CENTER with `peek(index)` rather than `items[index]`, so activating a row does not
  itself trigger a page load
- rewire song playback onto the new `playSongFrom*` commands

---

## 5. Contract feedback, consolidated

Beyond the paging gap:

1. **No settings entry point.** The fixed root hierarchy has nowhere to surface
   `openAppSettings`. Not a defect for this skin, but a skin with no settings affordance
   cannot reach app settings at all. Worth a decision in Phase 6.
2. **`SkinLibraryGate` re-entrancy.** `statusScreen` and the ready path both call
   `wheelScaffold` with the same destination, so the scanning state writes
   `selectionIndices[destination] = 0` and discards a saved index if the library reloads while
   a screen is open. Cosmetic, low priority, noted.
3. Paged vs plain-`List` asymmetry in `SkinLibrary` (`songsByAlbum`/`songsByPlaylist` return
   `Flow<List<SkinSong>>`, everything else returns `Flow<PagingData<...>>`) forced two parallel
   code paths through the skin — `pagedLibraryScreen` and `listLibraryScreen`, near-identical.
   The asymmetry is still the right call (paging a 12-track album is ceremony), but the cost
   lands on every skin, not on the contract. Revisit if a second skin pays it again.

---

## 6. Verification status

- [x] `:skin:compileDebugKotlin` — passes
- [x] `:app:compileDebugKotlin` — passes with contract extension
- [x] isolation gate (0 `org.oxycblt` references)
- [x] naming gate
- [x] registered in `SkinRegistry` alongside DebugSkinA/B (`DebugSkinA` stays the crash-loop
      fallback — the recovery target should be the simplest thing that can render)
- [ ] paged-list refactor merged
- [ ] on-device navigation
