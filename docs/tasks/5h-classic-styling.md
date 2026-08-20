# Phase 5h — Classic styling (visual match to the reference hardware)

**Status:** 🟡 visual match, haptics and transitions complete and verified on device; font
treatment and a release-build profiler pass outstanding
**Owner:** Claude (design work, not delegated)
**Commits:** `145a0b0c8`, `209c1fd0d`, `3284b785c`

User supplied a photo of a 5th/6th-generation hard-drive music player and asked to match its
UI, then to match its dimensions and shrink the wheel.

---

## 1. Naming

Identifiers are vendor-neutral throughout — `ClassicColors`, `ClassicTitleBar`,
`ClassicMenuRow`, `BodySilver`. The visual language is a widely-copied convention; the
trademarks around it are not, and keeping them out of the source costs nothing. The grep gate
(`ipod|click wheel`) still passes.

Worth stating plainly: a pixel-exact clone carrying another company's trade dress is the kind
of thing that gets pulled from app stores. For a personal device this is a non-issue, but the
naming discipline is cheap insurance if the project ever moves.

---

## 2. What was built

`WheelChrome.kt` (new) holds the whole visual vocabulary as stateless presentation
primitives:

| Component | Notes |
|---|---|
| `ClassicTitleBar` | Grey gradient, play/pause glyph left, centred title, drawn battery right |
| `ClassicMenuRow` | White / blue-gradient row with an optional chevron |
| `ClassicSplitLayout` | Menu left (56%), artwork panel right, hairline divider |
| `ClassicArtPanel` | Current track's cover |
| `ClassicProgressBar` | Grey rounded track, blue gradient fill |

Details that carried the look more than expected:

- **Both gradients are vertical**, light top falling to a saturated base. A flat fill reads as
  a modern list highlight; the gradient is most of what makes it read as period-correct.
- **The title is centred in the whole bar**, not in the space left over between the side
  elements, so it does not drift as the play glyph appears and disappears.
- **Chevrons mark rows that descend**, not rows that act. `Shuffle Songs` has none; `Music`,
  `Queue` and `Now Playing` do. It is the only affordance separating the two.
- **The wheel is a shallow radial gradient** darkening outward with a paler centre button and
  a hairline between. Two flat discs read as a diagram; the gradient reads as a dished
  physical control.

---

## 3. Face proportions

First attempt filled the screen: content on top, wheel occupying the lower 45%. On a
1080×2400 phone that made the wheel a **full 1080px across** — the user's "the scroll wheel is
too big".

The mistake was sizing against the phone instead of against the hardware. Measured from the
device:

| Ratio | Value |
|---|---|
| screen height / body width | 0.75 (4:3, landscape) |
| wheel diameter / body width | 0.70 |
| gap between them | 0.10 |
| **total face height / body width** | **1.55** |

A phone is roughly 2.2:1 where that hardware is 1.67:1, so filling the height necessarily
stretches both elements. Instead the face is sized to whichever of width or height binds
first, then centred, with a silver body colour filling the remainder. The wheel went from
100% of screen width to 70% of face width.

Constants live in `WheelChrome.kt` with the derivation in a comment, so the relationship is
recoverable rather than a set of magic numbers.

---

## 4. Reversing the fixed-cursor decision

`WheelMenuList` was built in 5d around a **fixed centre cursor**: the selection pinned
mid-screen, the list sliding beneath it. That was my design call, specified emphatically in
the agent prompt ("Get this right — it is the whole character of the component"), and it took
a bug fix in 5f to make it actually work.

It is not what the hardware does. The real device anchors the list to the top and walks the
highlight down it, scrolling only when the selection would leave the viewport.

Because this reversed a deliberate, verified decision rather than fixing a defect, it went to
the user rather than being changed silently. They chose to match the hardware.

### The implementation trap

The obvious implementation is wrong:

```kotlin
LaunchedEffect(index) { listState.scrollToItem(index) }   // WRONG
```

That snaps the selected row to the top of the viewport on every click, so the highlight never
appears to move — the list just scrolls under a stationary highlight, which is the fixed-cursor
behaviour again by accident. Walking a menu requires the list to stay **still** while the
selection travels, and to move only at the edge:

```kotlin
private suspend fun LazyListState.revealItem(index: Int) {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) { scrollToItem(index); return }
    val overshootBelow = item.offset + item.size - info.viewportEndOffset
    if (overshootBelow > 0) { scrollBy(overshootBelow.toFloat()); return }
    val overshootAbove = item.offset - info.viewportStartOffset
    if (overshootAbove < 0) scrollBy(overshootAbove.toFloat())
}
```

`scrollBy`, not `animateScrollBy` — at one item per click the move must feel immediate, and
queued animations lag behind fast rotation.

Note `userScrollEnabled = false` does **not** block programmatic scrolling; it only disables
gestures. The whole component depends on that.

---

## 5. Papercut: `supportsEdgeToEdge` was silently dead on API 35+

The skin's title bar drew underneath the Android status bar, hiding the drawn battery behind
the system one — despite `TouchWheelSkin.supportsEdgeToEdge = false`.

`ShellacActivity` honoured the flag with:

```kotlin
WindowCompat.setDecorFitsSystemWindows(window, !skin.supportsEdgeToEdge)
```

**That API is deprecated and ignored from API 35 onward**, where edge-to-edge is enforced. On
the API 36 test device it did nothing whatsoever. The flag had been part of the contract since
Phase 3 and had never actually worked; no skin before this one drew anything near the top
edge, so nothing surfaced it.

Fixed in `SkinHost` rather than in the skin, so every skin benefits:

```kotlin
if (skin.supportsEdgeToEdge) skin.Render(session)
else Box(Modifier.fillMaxSize().safeDrawingPadding()) { skin.Render(session) }
```

**Generalisable lesson: a contract flag that no consumer exercises is not implemented, it is
merely declared.** Worth auditing the rest of the contract for options nothing has used yet.

---

## 6. Papercut: do not compile while an agent is writing the module

Compiled mid-flight while the 5g agent was working and got 25 errors — `Unresolved reference
'WheelQueue'`, cascading type-inference failures. All spurious: the agent had already updated
`TouchWheelSkin.kt` to call into `WheelQueue`/`WheelSearch` but had not finished creating
them.

Nearly diagnosed as a real integration failure. **An agent's working tree is not a consistent
state.** Wait for the completion notification before compiling, and treat any error touching
files the agent owns as suspect until it has finished.

---

## 7. Verified on device

- [x] Title bar renders below the system status bar, drawn battery visible
- [x] Blue gradient selection with chevrons on descending rows only
- [x] Split menu/artwork layout; panel shows the playing track's cover
- [x] Play/pause glyph in the title bar tracks real playback state
- [x] Root and Music menus sit flush at the top — no blank half-screen
- [x] On Albums, the list stays still while the highlight walks item 0 → 6
- [x] Wheel is circular, ~70% of face width, silver with transport glyphs
- [x] No crashes

## 8. Haptics and transitions (commit `3284b785c`)

**Haptics** hang off `rotaryInput`'s per-detent callback, which already accumulates angle and
emits once per click. That is what makes "once per click, not per touch event" fall out for
free — driving them from pointer movement instead would fire dozens of times a second and
feel like a buzz rather than a wheel.

`SEGMENT_FREQUENT_TICK` (API 34+) exists precisely for a control passing through discrete
positions and is tuned to stay comfortable when repeated fast; `CLOCK_TICK` is the closest
older equivalent. Zone presses use the firmer `CONTEXT_CLICK`. Neither passes
`FLAG_IGNORE_VIEW_SETTING` — a user who turned haptics off means it.

**Transitions** slide the screen in the direction of travel. Two things worth recording:

1. *Only the screen slides.* The wheel is a physical control; animating the whole face would
   slide the wheel too, which reads as wrong immediately.
2. *It is an enter animation only.* `AnimatedContent` would have needed the wheel hoisted out
   of the per-destination composable, since each screen currently builds its own face — a
   sizeable refactor of `wheelScaffold` for a polish feature. The outgoing screen has already
   left the composition by the time the new one runs, so there is nothing to animate out
   anyway, and a slide-in alone reads correctly. `Animatable` seeded from the travel direction
   via `remember(destination)` gets this in about ten lines.

Direction needs knowing which way we went, so navigation goes through `navigateTo` /
`navigateBack` wrappers. The flag is a **plain field**, not remembered state — `onBackPressed`
is not composable and has to set it too, the same constraint that put the back stack in a
field (AppSkin rule 2).

---

## 9. Profiling — partial, and honestly so

**The debug build is not a valid profiling target.** Compose debug composition is several
times slower than release, so numbers from it cannot answer the buildspec's "no dropped frames
during scroll".

Debug figures, for what little they are worth (scrolling Albums, synthetic input):

| | |
|---|---|
| Janky frames | 18.75% (6 of 32) |
| 90th / 95th percentile | 93 ms / 133 ms |
| GPU 90th / 95th | 8 ms / 9 ms |
| **Number High input latency** | **50** |

Two things stand out. GPU time is comfortable, so nothing is over-drawing — the cost is UI
thread, which is exactly what debug mode inflates. And `High input latency` exceeding the
frame count means most of the "jank" is `adb shell input swipe` delivering bursts at
unnatural rates, not rendering.

### What was established

A **release build was produced and verified** (`:app:assembleRelease`, 12 MB minified vs 40 MB
debug), signed with the debug key, and installed. It launches, the settings activity renders,
and there are no `ClassNotFound` / `NoSuchMethod` failures — so **the skin survives R8**, which
was a genuine risk worth retiring given Compose plus a reflective DI graph.

### What is still blocked

Release installs under a different application id (`org.oxycblt.auxio` vs
`org.oxycblt.auxio.debug`), so it has its own storage grants and its library is empty.
Indexing is SAF-based, and the folder picker cannot be scripted. A real release profile
therefore needs a one-time manual step: open the release app, grant the music folder, then
re-run

```bash
adb shell dumpsys gfxinfo org.oxycblt.auxio reset
# scroll a long list by hand
adb shell dumpsys gfxinfo org.oxycblt.auxio
```

Scrolling **by hand** also removes the synthetic-input artefact above.

**Not claiming the performance criterion is met.** It is unmeasured, not passed.

---

## 10. Outstanding for 5h

- Font treatment — currently the platform default at classic sizes
- Release-build profiler pass (see §9; needs the manual folder grant)
