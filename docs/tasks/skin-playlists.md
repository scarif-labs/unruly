# Playlist creation in skins

Status: 🟡 API, PSP XMB and iOS 6 Music done and checked on device (2026-11-05). Nothing is
committed yet. Winamp, iPod and Zune can browse and play playlists but still cannot create or edit them.

## Done

**Skin API** (`skin/.../SkinCommands.kt`): `createPlaylist(name?, songs)`, `addToPlaylist`,
`renamePlaylist`, `deletePlaylist`, `removeFromPlaylist(playlistId, songId, index)`.
- A null/blank name takes the next free "Playlist N" (`R.string.fmt_def_playlist`, same rule as
  `PlaylistPickerViewModel`), so skins without a keyboard can skip naming.
- `removeFromPlaylist` takes the song id as well as the index and is a no-op if they no longer
  match, so a second quick removal can't hit a shifted list.

**Engine** (`SkinCommandsImpl.kt`, `EngineAdapter.kt`): calls the existing `MusicRepository`
playlist methods on the `Binding` scope, serialised through one `Mutex` (each repository write is
read-modify-write of the whole library; parallel edits would lose one). Lists refresh on their own
via `SkinLibraryImpl.changes`.

**PSP XMB / Crossbar** (`CrossbarMenu.kt`, `CrossbarSkin.kt`):
- Songs (hold): "Add to Playlist" → second menu of "New Playlist" + existing playlists (the
  playlist being browsed is left out). Albums (hold) get the same, adding in album order.
- Playlists category starts with a "New Playlist" entry. Playlist (hold): Play, Rename, Delete
  (confirm menu, cursor on Cancel). Inside a playlist, songs get "Remove from Playlist".
- `NamePrompt` / `NameEntry`: text field + keyboard over an 80% black scrim; Back or tap outside
  cancels.
- Side menu now scrolls (it ran off-screen past ~12 lines).
- Fixed while testing: selection underline struck through subtitle-less titles; selection left
  past the end after a delete (now clamped in `MenuScreen`).

Device walkthrough (Redmi, 2026-11-05) passed: create, add songs, add album to unnamed new
playlist ("Playlist 1"), remove, rename, delete. Test playlists were deleted afterwards and the
active skin restored to Zune.

**iOS 6 Music / Glossy** (`GlossySkin.kt`, new `GlossyPlaylists.kt`):
- Playlists tab starts with "Add Playlist..." → navy glass alert ("New Playlist", name field,
  Cancel / Save) → song picker (`GDest.PickSongs`): prompt band, search filter, A-Z list with a
  blue "+" per song, greyed once picked (tap again to unpick). Done creates the playlist with the
  picked songs in pick order; a blank name takes the default.
- A playlist page has its own screen (`PlaylistScreen`): an Edit / Clear / Delete band under the
  bar. Edit mode: the band becomes a name field (Done renames), each song gets a red minus disc
  that arms a red Delete button, and the bar holds "+" (picker, appends) and Done. Clear and
  Delete confirm in a red/grey action sheet (`GOverlay`); back dismisses an overlay first.
- The bar title reads the live name from `playlists()`, so a rename sticks after the picker.
- Playlist rows are keyed by index + id (a playlist may repeat a song; the old id-only key would
  have crashed). `ListRow` gained `dimmed`, `BarButton` gained `textSize`.
- Not done: reordering (no move in the API); returning from the picker leaves edit mode.

Device walkthrough (Redmi, 2026-11-05) passed: create named playlist with 3 songs, remove one,
rename, add one from edit mode, Clear, Delete. Test playlist deleted; active skin restored to
PSP XMB.

## Next, per skin

| Skin | Has | Plan |
|---|---|---|
| **Winamp 2** (`amp/`) | "Playlist" window is the queue; bottom bar SEL, LYRICS, gear | LIST button with "Save list" → `createPlaylist(null, playback.queue)`. Smallest change. |
| **iPod** (`wheel/`) | Center press only; `QueueActions` screen pattern; wheel alphabet in `WheelSearch.kt` | On-The-Go: hold center on a song adds to it; "Save Playlist" makes a real one with the default name. Needs hold detection in `TouchWheel.kt`. Rename could reuse the alphabet. |
| **Zune HD** (`zune/`) | Tap only (`pressable` in `ZuneTheme.kt`); search `BasicTextField` | Long-press on rows → "add to playlist"; "new playlist" at top of the playlists pivot. |

Suggested order: Winamp, iPod, Zune.

## Known, not fixed

- Opening the name keyboard shifts the XMB up (safe-drawing insets include the IME). Looked
  acceptable on device.
- PSP XMB: the side menu covers the selected row's length chip (pre-existing).
- Winamp library counter says "1 ITEMS" (pre-existing).
- On re-entering Playlists the selection can reset to the top: the "New Playlist" lead entry
  makes the count 1 before the paged list arrives, and the new clamp kicks in. Not seen on device.
