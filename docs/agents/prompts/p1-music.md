You are mapping the music library / indexer architecture of the Auxio Android music player.
This is a READ-ONLY analysis task. Do not modify any file. Produce a written report to stdout.

Repo root is the current directory. Kotlin source under `app/src/main/java/org/oxycblt/auxio/`
(see `music/`, `list/`, `search/`, `detail/`). IMPORTANT: there is a SEPARATE `musikr/` module
which contains much of the indexing logic — you must examine it, not just `app/`.

## Absolute rule on accuracy

Every class, method, property, and type you name MUST be one you actually read in a file.
Cite `path/to/File.kt:LINE` for each claim. If you cannot find something, write "NOT FOUND"
and say where you looked. Do NOT infer or invent plausible-looking API names — downstream
code will be written against this report, so an invented name is worse than an admitted gap.

## What to determine

### A. Library exposure
1. Which class exposes the indexed library to the UI? Give its fully-qualified name and
   file:line. Distinguish clearly between the repository/holder in `musikr/` and any
   ViewModel wrapper in `app/`.
2. How is the library surfaced — `StateFlow`, `LiveData`, callback interface, snapshot
   object? Give the exact type.
3. Can a consumer get "all songs" as a materialised `List`? What exactly is the accessor?
   (This matters: we intend to forbid it in our own layer, so we must know what exists.)

### B. Model types
For EACH of Song, Album, Artist, Genre, Playlist:
- fully-qualified type name, file:line, and whether it is an interface or a data class
- every property relevant to display (title/name, duration, track no, disc no, year, date)
- **how identity works** — the exact ID type. Is it a `Long`, a UUID, a custom value class?
  Give the exact type and how you obtain a stable string from it. This is critical.
- how the relationships are navigated: song→album, song→artist, album→artist,
  album→songs, artist→albums, genre→songs. Give the exact property or method for each.

### C. Load state
How is indexing/scanning state signalled? Give the exact type (likely a sealed class or
enum) and ALL its states — scanning, ready, error, and any progress representation.
Where does a consumer observe it? file:line.

### D. Playlists
How are playlists stored (Room? file?)? Give the DAO/store class and file:line.
Exact method signatures for: create, rename, delete, add songs, remove songs, reorder.
Note whether these are `suspend` functions.

### E. Search
Is search a repository capability or UI-side filtering? Give the class that performs it and
file:line. What does it search across, and what is the exact result type? If it is UI-side
filtering, say so plainly and describe where the filtering happens.

### F. Sorting
How is sort expressed? Give the exact `Sort` type, its file:line, and enumerate ALL sort
modes and directions available.

### G. Album art
How is Coil configured (fetcher/keyer/interceptor)? file:line. What EXACTLY identifies a
song's artwork — a URI, an embedded-tag read, a custom Coil model? Give the concrete type
that gets passed to Coil, and how one obtains it from a Song or Album.

## Output format

Markdown, sections A–G. Dense and factual — no preamble, no advice. Tables where they fit.
End with "## Uncertain / Not Found" listing anything unconfirmed.
