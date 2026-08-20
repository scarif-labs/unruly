Implement the engine-side lyrics lookup for the Shellac Android project, backed by LRCLIB.

Working directory is the repo root.

## Searching this repo

Scope every search to `app/src/main/java` or `skin/src/main/java`. The repo vendors a 609 MB
`media/` submodule containing ffmpeg's test-vector suite; an unscoped search drowns in it.

## What already exists

The contract is written and compiles. **Read it first and implement exactly it — do not change it:**

- `skin/src/main/java/org/shellac/skin/SkinLyrics.kt` — `SkinLyricLine`, `SkinLyrics`,
  `SkinLyricsState`, `SkinLyricsSource`
- `skin/src/main/java/org/shellac/skin/AppSkin.kt` — `SkinSession` now takes a
  `lyrics: SkinLyricsSource` parameter, which is currently unsatisfied

`AndroidManifest.xml` already declares `INTERNET` and `ACCESS_NETWORK_STATE`.

## Your job

Everything under `app/src/main/java/org/oxycblt/auxio/shellac/`. Do NOT modify anything in
`skin/`. Do NOT touch `org.oxycblt.auxio.playback` or `org.oxycblt.auxio.music` — the engine is
read-only (project invariant 1).

Suggested files:
- `engine/lyrics/LrcLibClient.kt`  — HTTP + JSON
- `engine/lyrics/LrcParser.kt`     — LRC timestamp parsing
- `engine/lyrics/LyricsRepository.kt` — caching, state machine
- `engine/SkinLyricsSourceImpl.kt` — the contract implementation

Then wire it into `EngineAdapter.kt:48`, which currently reads:

```kotlin
return SkinSession(binding.playback, library, binding.commands, openAppSettings)
```

## No new dependencies

Use `java.net.HttpURLConnection` and `org.json` (both in the Android SDK). The project has no
HTTP client — only `coil-core`, which does not expose one — and adding OkHttp or Ktor for a
single GET is not worth the dependency surface. Do not edit any `build.gradle`.

## The LRCLIB API

Base URL `https://lrclib.net`.

**Primary lookup** — exact match, preferred:

```
GET /api/get?artist_name={artist}&track_name={title}&album_name={album}&duration={seconds}
```

- `200` → JSON object with `plainLyrics` (string, may be null), `syncedLyrics` (string in LRC
  format, may be null), `instrumental` (boolean)
- `404` → no match

**Fallback** when the exact lookup 404s:

```
GET /api/search?track_name={title}&artist_name={artist}
```

Returns a JSON **array** of the same shape. Take the first entry whose duration is within a
couple of seconds of the track's, if any; otherwise treat as not found. Do not blindly take
element 0 — a wrong-length match is usually a different recording and its synced timings will
drift badly.

**Required:** send a `User-Agent` header identifying the application, e.g.
`Shellac/<versionName> (https://github.com/oxycblt/auxio)`. LRCLIB asks for this; requests
without one may be rejected.

URL-encode every query parameter. Titles routinely contain `&`, `?`, `#` and `+`.

`duration` is in **seconds**, and the engine reports position/duration in milliseconds — check
the units at the boundary.

## Behaviour required

1. **`instrumental: true` → `SkinLyricsState.NotFound`.** It is not an error and there is
   nothing to show.
2. **Prefer synced over plain.** When `syncedLyrics` parses to at least one line, populate
   `SkinLyrics.synced`. Always populate `plain` as well — from `plainLyrics` when present,
   otherwise from the synced lines' text — because a skin may choose to render unsynced.
3. **Cache per song id, in memory, for the process.** Re-collecting the flow for a track that
   has already been looked up must NOT re-issue a request. Cache negative results
   (`NotFound`) too — otherwise every revisit re-requests tracks that will never have lyrics,
   which is most of a typical library. Do **not** cache `Failed`: a transient network error
   should be retried on the next visit.
4. **Never block the main thread.** The lookup runs on `Dispatchers.IO`. Connect and read
   timeouts of a few seconds — a hung socket must not leave the UI on `Loading` forever.
5. **Emit `Loading` first**, then the terminal state.
6. **Concurrent collectors for the same track must share one request**, not issue several.
7. **Unknown song id → `SkinLyricsState.NoTrack`.**
8. Any exception (`IOException`, JSON, timeout) → `SkinLyricsState.Failed` with a short
   user-presentable message. Never let one escape to crash the app.

## LRC parsing

Lines look like:

```
[00:12.34]Some line of text
[00:15.00][01:42.00]A line that repeats at two timestamps
[ar:Artist]
```

Requirements:
- `[mm:ss.xx]` and `[mm:ss.xxx]` and `[mm:ss]` all occur. Handle all three.
- A single line may carry **several** timestamps and must be emitted once per timestamp.
- Metadata tags (`[ar:]`, `[ti:]`, `[al:]`, `[by:]`, `[offset:]`, `[length:]`) are not lyric
  lines — skip them. Note `[offset:+250]` shifts all timings; applying it is a bonus, ignoring
  it is acceptable, but do not mistake it for a lyric.
- Empty text after a timestamp is a legitimate gap line — keep it, so a skin's "current line"
  can correctly be blank during an instrumental break.
- **Output must be sorted ascending by `startMs`** — `activeLyricIndexAt` in the support layer
  binary-searches it and will return nonsense otherwise.

Put the parser in a pure, non-Android function so it is checkable by reading:

```kotlin
internal fun parseLrc(raw: String): List<SkinLyricLine>
```

## The user-facing toggle

This engine is otherwise **entirely offline**. A lyrics lookup sends the playing track's
title, artist and album to a third party, which is a real change in what the app does. So:

- Add a boolean preference (e.g. `lyrics_lookup_enabled`) in the existing
  `shellac_skins` SharedPreferences, or a sibling file. **Default it to enabled** — the user
  asked for the feature — but it must be switchable.
- When disabled, `lyrics(...)` emits `SkinLyricsState.Disabled` and **makes no request at
  all**.
- Add a toggle row to `app/src/main/java/org/oxycblt/auxio/shellac/host/ShellacSettingsActivity.kt`,
  under a new "Lyrics" section next to the existing "Library" section. Match the surrounding
  style. Make the row's text state plainly that lyrics are fetched from lrclib.net and that
  track details are sent there.

## Verify

You CANNOT run Gradle in this sandbox — it fails with
`java.net.SocketException: Operation not permitted`. Do not try.

Re-read your files and check every symbol against its declaration — exact parameter names,
order, nullability, and whether something returns `Flow`, `StateFlow` or a plain value.
Check `parseLrc` by hand against: a single timestamp, a multi-timestamp line, `[mm:ss]` with
no fraction, a metadata tag, and an empty-text line.

## Report to stdout

1. Files created and changed
2. How caching works, and exactly what is and is not cached
3. How concurrent collectors for the same track share one request
4. Your `parseLrc` handling of multi-timestamp lines and metadata tags
5. Where the units convert between milliseconds and seconds
6. Anything in the contract that was missing, awkward, or forced a workaround
