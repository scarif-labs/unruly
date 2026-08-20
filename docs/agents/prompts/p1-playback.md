You are mapping the playback architecture of the Auxio Android music player. This is a
READ-ONLY analysis task. Do not modify any file. Produce a written report to stdout.

Repo root is the current directory. Source is Kotlin under `app/src/main/java/org/oxycblt/auxio/`.
There is also a separate `musikr/` module.

## Absolute rule on accuracy

Every single class, method, property, and type you name MUST be one you have actually read
in a file. For each claim, cite `path/to/File.kt:LINE`. If you cannot find something, write
"NOT FOUND" and say where you looked. Do NOT infer, guess, or produce plausible-looking
API names. A confidently-invented method name is the single most damaging output you can
produce here, because downstream code will be written against it. Accuracy over completeness.

## What to determine

### A. Playback state exposure
1. Which class holds/exposes playback state to the UI? (Look around `playback/`, expect a
   ViewModel, plus whatever service/state-holder backs it — check `AuxioService.kt` too.)
2. For EACH of these, give the exact property name, its declared type, and file:line:
   - current song
   - playback position (and note: is it a polled value, a Flow, or read on demand?)
   - duration
   - is-playing / play state
   - the queue, and the index into it
   - repeat mode (give the exact enum type and ALL its constant names)
   - shuffle state
3. Is state surfaced via `StateFlow`, `SharedFlow`, `LiveData`, or a custom observer
   interface? If custom, describe the listener interface and how a consumer registers and
   unregisters.
4. How does position update? Exact mechanism and update frequency if discoverable.

### B. Command path
For EACH of: play, pause (or a combined toggle), seek, next, previous, toggle shuffle,
cycle repeat — give the exact method signature and the class that declares it, with file:line.

Then trace ONE command end to end: from the UI call site, through every intermediate layer,
to where it reaches the Media3 player. List each hop as `Class.method() (file:line)`.

### C. Queue manipulation
Exact method signatures for: jump to queue index, add to queue, play next, remove from
queue, move/reorder within queue. Note which class declares them. If a capability does not
exist, say so explicitly.

### D. Starting playback from a collection
How does the app start playing an album, an artist, a genre, a playlist? Give exact method
signatures. Note what type the "start playback here" call takes — a song, a list, a parent
object? This matters a great deal.

### E. Model types
Give the exact declaration and fully-qualified name of the Song type used in playback, and
which module/package it lives in. List its properties relevant to display: title, artist,
album, duration, track number, disc number, year, and how album art is identified.

## Output format

Markdown. Sections A–E. Dense and factual — no preamble, no summary of what you're about
to do, no advice. Use tables where a table fits. End with a section
"## Uncertain / Not Found" listing anything you could not confirm.
