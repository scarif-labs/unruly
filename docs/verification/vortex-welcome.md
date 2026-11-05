# Vortex welcome screen

The first-run welcome page uses the selected red, ivory and black Vortex direction. A bundled
Anton wordmark is mapped onto nested text rings using Android Canvas bitmap meshes. Rings move
outward from the dark center to create continuous forward travel. Each phase wrap shifts the
ring indices; the entering ring fades in at the aperture and the exiting ring is offscreen.
The grain, headline and controls remain stationary. The wordmark now reads “unruly”; Android's
app, setup and settings labels use Unruly as well. Existing package IDs and saved preferences
retain their original identifiers so the update preserves installed data.

Each ring's centre slides away from the aperture in proportion to its radius, as the mouth of a
tilted tube would, so the rings crowd together on the aperture's right side as in the reference.
The offset saturates, so the large rings bend back toward the viewer instead of drifting
offscreen. Rings shade toward the black aperture. Of the six words per ring, the two on the lower
arc are turned over so they read upright, left to right.

The rings wobble on a separate clock: each bulges into a slowly turning two- and three-lobed
blob, letters stretch and squeeze along the ring, a rotation wave rolls down the tunnel so
neighbouring rings counter-rotate and letters shear, and the tunnel's axis snakes slightly. The
aperture stays steady. Every term depends on absolute radius, not ring index, so bands still meet
and the phase wrap stays seamless; the clock wraps at a common period of its frequencies. The
per-vertex math uses lookup tables and angle-addition identities: per-vertex trig cost about 10 ms
of UI-thread time per frame.

Landscape, tablet and unfolded layouts place the tunnel beside the copy. In portrait the copy is
capped at half the screen and scrolls at very large font scales, rather than collapsing the
tunnel. The banner's height follows the pane, and its text only condenses or widens slightly.

“Find your way in” accelerates the tunnel for 650 ms, then opens the existing folder-selection
step. It does not complete onboarding or bypass storage permissions. Opening Music folders from
settings still skips the welcome screen.

Animation pauses below the resumed lifecycle state. Android's animator duration scale controls
its speed; a zero scale retains the artwork without motion and makes the CTA continue immediately.
The preference is observed while the screen is open. Fonts work offline; Anton's license is in
`licenses/Anton-OFL.txt`.

## Verified on 2026-10-20

Same device. The debug APK installs. Screenshots were checked in portrait, landscape, at a 2× font
scale and at a simulated 1600 × 2560 / 320 dpi tablet size. Over 10 s of animation there were
0 janky frames out of 1,214 (p50 12 ms, p99 14 ms at 120 Hz).

## Verified on 2026-10-19

Device: Redmi Note 10 Pro, Android 16 / API 36, 1080 × 2400.

- Debug APK assembles successfully.
- Spotless formatting/check passes for the changed Kotlin files.
- On-device screenshots confirm the layout and persistent CTA.
- Screenshots taken 1.2 seconds apart with animations disabled have identical tunnel pixels;
  the ivory artwork remains present.
- After restoring the animation preference, tunnel pixels change again.
- Both the animated CTA and the reduced-motion CTA reach folder selection.
- Initial frame sample: 21 janky frames out of 2,554 (0.82%).
- Full `:app:lintDebug` runs but fails on 68 existing errors elsewhere in the repository,
  including the media-search manifest filter, restricted Material APIs and DiffUtil checks.
  Neither changed Kotlin file has lint errors; Canvas KTX suggestions are advisory.

## Manual regression checks

1. Start a fresh setup and watch several cycles: there should be no visible reset.
2. Background and resume the app: the tunnel should resume without jumping ahead.
3. Toggle Android's Remove animations setting while the welcome page is open. The artwork must
   remain visible and resume when the setting is turned off.
4. Tap the CTA quickly several times: only one transition to folder selection should occur.
5. Pick a music folder, return from the system picker, and finish setup. The chosen folder and
   current step must survive Activity recreation.
6. Open Music folders from settings: folder management should open directly.
