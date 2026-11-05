# Skins backlog: classic music-player interfaces

Status: ✅ Winamp 2 approved; 🟡 iOS 6 and PSP XMB built and partly verified. Added 2026-10-20.

Each skin replicates a well-known player's interface as closely as the contract allows, as the
Touch Wheel ([5h](5h-classic-styling.md)) and Zune HD ([6.1](6.1-second-skin.md)) skins do.

## Ground rules (carried over from the first two skins)

- **Copy the look, never the assets.** No logos or bitmap assets from the originals. Everything
  is drawn in Compose or uses openly licensed fonts. Skin ids stay vendor-neutral (they are
  persisted), but since 2026-10-21 the picker shows the real product names ("PSP XMB",
  "Winamp 2", "iOS 6 Music", "iPod", "Zune HD") at the user's request, knowing these are
  trademarks.
- **Work from references, not memory.** The Zune HD skin was rejected once when built from
  memory and accepted when rebuilt from video frames. Gather frames or screenshots first, save
  them under `~/Desktop/Unruly <Name> Reference/`, and check each screen against them on device.
- **Contract only.** A skin imports nothing from `:app`, `org.oxycblt.*` or another skin.
  Missing features (ratings, equaliser, artist photos, play history) are either skipped,
  simulated visibly, or kept in the skin's own state, and the deviation is written down.

## The skins

| # | Skin | What defines it | Contract gaps to plan for | Status |
|---|------|-----------------|---------------------------|--------|
| 1 | **Winamp 2 (classic)** | 275 px main window: bevelled grey chrome, black LCD with seven-segment time, spectrum analyser, scrolling title, kbps/kHz, seek, volume/balance sliders, transport row, shuffle/repeat, EQ/PL toggles. Equaliser window with ten band sliders. Playlist editor: green text on black, blue selection, ADD/REM/SEL/MISC/LIST. Windows stacked on the phone. | No audio samples for the analyser (simulate it while playing); no equaliser (sliders are decorative); no bitrate/sample rate (show what the model has or blanks); Winamp 2 had no library browser (ADD opens a library window in playlist style). | ✅ built; user-approved 2026-10-20 |
| 2 | **iOS 6 Music app** | Skeuomorphic: brushed-aluminium bars, glossy black-gradient buttons, the bottom tab bar (Playlists, Artists, Songs, Albums, More), big cover on now playing with a glossy scrubber and volume slider, Cover Flow in landscape. | Cover Flow needs landscape handling; ratings and lyrics-in-art absent. | 🟡 built as "Glossy", partly verified on device |
| 3 | **PSP XMB** | Cross Media Bar: categories run horizontally, items vertically, over a slowly flowing wave background that tints by month; icons with soft glow; music playback screen with the bar collapsed. | No category icons (draw our own); background wave is pure animation. | 🟡 built as "Crossbar", verified on device, awaiting user review |

Considered and parked: the original Zune (2006, pad-driven). It would share visual language with
the Zune HD skin.

Dropped 2026-10-21 at the user's request: the Sony Ericsson Walkman phone (no emulator boots its
firmware, and the player is built into the OS) and Windows Media Player 11.

## 7.1 Winamp 2: built (2026-10-20, uncommitted)

`skin/src/main/java/org/unruly/skin/amp/`, registered as **Amp** (`id = "amp"`).

- **Reference**: the classic base skin's sprite sheets, sprite coordinates and window CSS from
  the open-source Webamp project, used for geometry and colours only. Nothing from them ships.
  Copies, plus a composite of the assembled windows, are in `~/Desktop/Unruly Winamp Reference/`.
- **Rendering**: each window is drawn as pixel art at native size (275 x 116 for the main and
  equaliser windows, 275 x whatever fits for the list) by `PixelPainter`, then scaled to the
  screen width with nearest-neighbour filtering. Touch maps back into native pixels, so hit
  areas match the drawing exactly. Our own 4x5 capitals (`PixelFont`) and drawn seven-segment
  digits; the list uses the system sans, unsmoothed but hinted (unhinted, "e" read as "a").
- **Main window**: title "UNRULY", options squiggle, clutter bar (O opens settings, I toggles
  lyrics), play state, time (tap toggles remaining), animated analyser, scrolling title,
  KBPS/KHZ boxes (dark: no data), MONO/STEREO (stereo lit while playing), volume (system media
  volume), balance (snaps to centre; no effect), EQ/PL toggles, gold seek bar (drag to seek),
  transport, eject (opens the library), shuffle, repeat (a "1" for repeat-one). Stop pauses
  and rewinds, and shows the stopped state.
- **Equaliser**: ON, AUTO, RESET (flattens the curve; renamed from PRESETS at the user's request), graph, preamp and ten bands that drag and are
  remembered. A model only: the engine has no equaliser.
- **List window**: bottom buttons are SEL, LYRICS (widened) and a gear that opens settings (it
  replaced LIST OPTS, so settings is no longer only behind the tiny title-bar squiggle and the
  clutter-bar O); ADD, REM and MISC were removed at the user's request. Playlist (tap selects, tap the selected line to play;
  running time is selected/total), library browser (eject on the main window or the mini
  transport; categories, then artists, albums,
  songs, genres, playlists; "[..]" or back goes up; a song plays and returns to the playlist)
  and lyrics (LYRICS or clutter I; follows the sung line). Drag to scroll; drag the thumb for long
  lists.

Verified on device: rendering of all three windows, equaliser drag and graph, time toggle,
eject into the library, browsing albums, playlist selection and running time. Not yet
exercised: play/pause/stop/seek/volume (avoided starting audio on the user's phone), lyrics
mode, long-list scrolling, landscape.

## 7.2 iOS 6 Music app: built (2026-10-20, uncommitted)

`skin/src/main/java/org/unruly/skin/glossy/`, registered as **Glossy** (`id = "glossy"`).

- **Reference**: the user's screen recording of the iOS 6 Music app. Key frames, contact sheets
  and built-vs-reference shots are in `~/Desktop/Unruly iOS6 Reference/`.
- **Scale**: everything is sized in the original's points and scaled so its 320-point width fills
  the screen (`LocalPt`, `pt()`), so proportions match the recording exactly.
- **Type**: TeX Gyre Heros, a free Helvetica clone (GUST Font License,
  `skin/LICENSE-TeXGyreHeros-GUST.txt`), regular and bold, in `skin/src/main/res/font/`.
- **Library**: silver nav bar with embossed title, Settings in place of Store, black arrow-ended
  "Now Playing" button; search bar at the head of each list; 44-point grey rows with etched
  separators and a blue gradient on press; grey sticky letter bars and the A-Z strip; tab bar
  with Playlists, Artists, Songs, Albums and More (Compilations is not in the contract, so Songs
  takes its slot; More holds Genres, Lyrics and Settings). Album page with the grey header band,
  cover and reflection, and numbered track rows. Pages slide in.
- **Now playing** (reworked from a tall-screen reference the user supplied, after the first
  layout left a black void and then stretched the transport keys): black nav bar with artist /
  title / album; below it an artwork stage - the cover square and centred, a blurred enlarged
  copy filling the rest (clipped, or the blur smears over the nav bar). The control band (count,
  blue scrubber, repeat / lyrics / shuffle, orange when on) and the transport (three keys
  clustered at the centre, no cell dividers) are translucent and float at the stage's top and
  bottom. The list button flips the cover to the track list: plain numbers, mixed-case titles,
  a blue arrow on the playing track. A solid black volume bar with a full-width blue slider
  (system media volume) closes the screen.
- **Deviations**: no Store, Genius, ratings, Compilations or Cover Flow (the recording does not
  show it); the band's centre control opens lyrics over the cover instead of Genius.
- **Bugs found on device and fixed**: the nav bar's third line was clipped (font metrics ignore
  `lineHeight`; each line now has a fixed 13-point slot); a black gap under the cover on a tall
  screen; album tracks out of order (`songsByAlbum` is unsorted; `inAlbumOrder` sorts by disc
  and track).

Verified on device: Playlists (empty), Albums list, Now Playing (stage, band, transport,
volume) and the flipped track list, against the user's reference.

Second pass (2026-10-20, portrait unless noted). Working: Songs with the A-Z jump, Artists, the
artist page, the album page (disc/track order), More, Genres, More > Lyrics, search (grouped
Artists/Songs results that open), play from a row, scrub (engine position matched), pause, next,
previous, repeat off/all/one, shuffle, lyrics over the cover, dragging volume, system back.

Open defects:

- **Landscape is unusable** (skipped at the user's request, 2026-10-20). `LocalPt` scales 320 pt to the screen *width*, so landscape is ~2.2x
  too big: the library shows only nav, search and tab bars; Now Playing loses the cover, the
  control row and the transport.

Fixed the same day and checked on device:

- **Volume followed only the skin's own slider.** Each skin read the stream volume once. All three
  now share `support/MediaVolume.kt`, which listens for the system's volume broadcast; checked
  with the hardware keys in Glossy and Amp (Zune builds, not rechecked).
- **Accessories collided with the A-Z strip.** Indexed lists provide `LocalIndexInset`, and
  `ListRow` pulls its content in by the strip's width (the background stays full width).
- **Lyrics over the cover were hard to read.** Scrim raised from 62% to 80% black, plus a soft
  shadow under each line.

Still open:

- Minor: Genres are not alphabetical; the album page does not mark the playing track; the tab bar
  rides above the keyboard (iOS 6 covers it); the search query clears on a tab switch.

## 7.3 PSP XMB: built (2026-10-21, uncommitted)

Reference set in `~/Desktop/Unruly PSP Reference/` (see its `README.md`; `board.png` first).

- **Best sources**: Sony's 6.50 manual has greyscale screenshots at native 480x272, enough to
  measure the XMB layout exactly; the online guide has the annotated playback-screen diagram. In
  colour: a JPCSP emulator capture of the real firmware (no watermark), a blue stock tour, and the
  music lists, player, control panel and visualiser from "The PSP Experience, Media Edition".
- **Downloading**: yt-dlp 2026.06.09 gets HTTP 403 from YouTube by default;
  `--extractor-args "youtube:player_client=mweb"` works but caps at 360p.
- **Playback screen** (from Sony's diagram): group name top left with a note glyph, track n/total
  top right, cover at left, title and artist, codec box (MP3), control panel on triangle (prev/next
  group, prev/next track, rewind/forward, play, pause, stop, group mode, A-B, repeat, shuffle,
  clear, visual player, sleep timer, display, help), status icons bottom left, elapsed / total
  bottom right in blue.
- **Emulator captures (`emu/`)**: Sony's 6.61 XMB running in JPCSP (VSH mode), grabbed from the
  framebuffer as lossless 480x272 PNGs: every category, music folder and track lists, the player,
  every control-panel button, the four visualisers, the options menu, track info, Theme Settings,
  all 12 month tints at noon (`months-sheet.png`) and a 20 s wave clip. Setup and how to re-run
  it are in the folder's `README.md`.
- **Emulator limits**: JPCSP draws the wave as a solid fill, not the translucent ribbon of real
  hardware (use the videos for the wave's look; the geometry and motion are right); no night
  dimming; the Color picker hangs; SensMe channels needs an app that isn't in the firmware.
- **Visualisers**: skins still get no audio samples, so they would be simulated, as in Amp.

### The skin

`skin/src/main/java/org/unruly/skin/crossbar/`, registered as **Crossbar** (`id = "crossbar"`).

- **Scale**: sizes are in the handheld's pixels (`px()`), measured off the emulator frames. In
  landscape 272 px fill the height, as on the device; in portrait 420 px fill the width, so the
  type stays legible. The bar sits at the device's height in landscape and about a third of the
  way down in portrait, leaving room above it for the entries before the selection.
- **Type**: M PLUS Rounded 1c Medium and Bold (SIL OFL, `skin/LICENSE-MPLUSRounded1c-OFL.txt`),
  subset to Latin (`res/font/rounded_*.ttf`, ~70 KB each); the closest free match to the
  original's wide rounded gothic out of six candidates compared against the frames.
- **Backdrop**: each month's gradient sampled from the noon frames; a translucent ribbon wave
  (lighter band, fine bright lines, lit swell under the crest) that drifts continuously, as on
  real hardware rather than the emulator's solid fill. Dims through the evening to about half at
  night. Settings > Color picks Original (by month) or any month, from a side panel with swatches.
- **Menu**: categories Settings, Music (Now Playing, Shuffle All), Playlists, Artists, Albums,
  Songs, Genres, with our own drawn icons; the selected one grows, glows and is labelled. Swipe
  across for categories (one per swipe), drag up and down for entries, tap to open, hold or tap
  the Options chip for the side menu (Play, Play Next, Add to Queue, Information on songs; Play on
  collections). Drilling in steps the lists left: the parent shrinks to a faded icon column with
  ◂ arrows, as on the device. Album and song rows show cover thumbnails; the selected song shows
  its length in the box where the device showed the codec. Clock and battery top right; empty
  lists say so ("There are no playlists.").
- **Playback screen**: band with the group name and (n/total), cover, title, rule and artist,
  length box, shuffle/repeat marks, play state, blue elapsed time and progress bar (drag or tap to
  seek). Tap for the control panel: previous/next track, rewind/forward 10 s, play, pause, stop,
  repeat, shuffle, Visual Player and lyrics, with the touched control's label underneath. Visual
  Player opens the strip: Off, Wave (the backdrop's wave swells), Bars (cyan and orange dots),
  Cloud and Scope, all simulated and still while paused; the last three go black edge to edge.
- **Deviations**: no previous/next group, A-B repeat, sleep timer or help (no contract support);
  no codec (the box shows the length); no SensMe or search yet; lyrics replace the device's
  Display button.
- **Contract bug found and fixed**: `SkinCommands.shuffleAll()` threw "Invalid playback
  parameters" when tapped before the library had loaded (it must be a no-op). Fixed in
  `SkinCommandsImpl` for every skin.

Verified on device (portrait unless noted): the menu in October and August colours, category
swipes, scrolling, drilling Albums > album > tracks, the options side menu, the Color panel,
Information, the playback screen with the control panel, the visualiser strip, Bars, lyrics, and
the menu in landscape (Playlists empty state). Not exercised: starting playback from the skin (the
phone's music was left paused), the visualisers in motion, Artists/Genres drill-down, search.
