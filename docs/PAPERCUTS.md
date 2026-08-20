# Cross-Cutting Papercuts

Lessons that apply everywhere, not to one task. Per-task papercuts live in
`docs/tasks/<id>-*.md`; agent-specific ones in `docs/agents/AGENT-DISPATCH.md`.

---

## 1. Exit codes lie. Verify the artifact.

**Four separate times this session an exit code reported success or failure incorrectly.**
This is the single most expensive recurring problem so far.

| # | What happened | Why |
|---|---|---|
| 1 | `brew install --cask temurin@21` reported **exit 0**, had failed on a sudo prompt | `cmd 2>&1 \| tail -N` returns **`tail`'s** status, not the command's |
| 2 | Failed Gradle build reported **exit 0** | trailing `; echo "EXIT=$?"` became the last command in the chain |
| 3 | `BUILD SUCCESSFUL in 6s` — but no APK was produced | ran in the wrong directory (see §2) |
| 4 | Rebuild reported **exit 127** | `./gradlew` did not exist in the drifted cwd (see §2) |

**Rules:**
- Never pipe a build or install through `tail`/`head` when the exit code matters. Redirect
  to a log file and read it afterwards, or set `set -o pipefail`.
- Never append anything after the command whose status you care about.
- **Confirm the artifact exists and is fresh.** For a build that means: the APK file, its
  mtime, its size, and its contents — not the word "SUCCESSFUL".

```bash
stat -f "%Sm %z bytes" app/build/outputs/apk/debug/app-debug.apk
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -c '\.so$'   # expect 8
```

An impossibly fast build (6s for a full app assemble) is itself a red flag — it usually
means the work you intended never ran.

---

## 2. Shell cwd persists between commands and silently drifts

The working directory carries over from one command to the next, and **a `cd` inside a
compound command changes it for everything after.** Two real failures:

- `cd repo/media && git diff …` left the shell in `media/`. The next `./gradlew assembleDebug`
  then built the **Media3 fork's own Gradle project** — reporting success in 6 seconds,
  producing no APK, and even starting to download Gradle 8.13 from the submodule's separate
  wrapper. This nearly got reported as a passing build.
- A `cd /tmp` for an unrelated check left cwd at the workspace root, so `./gradlew` returned
  `127: no such file or directory`.

**Rules:**
- Use absolute paths for anything important. For Gradle, pass `-p <abs-root>` as well:
  ```bash
  /Users/0x1379/Work/shellac/repo/gradlew -p /Users/0x1379/Work/shellac/repo :app:assembleDebug
  ```
- Watch for the harness note `Shell cwd was reset to …` — it means the next relative path
  will resolve somewhere unexpected.
- Submodules (`media/`) have **their own Gradle wrapper and settings**. Running the wrong
  one fails in ways that look like success.

---

## 3. Query the repository; do not recall versions

Every version in this project was read from live Maven metadata rather than from memory.
This is fast, authoritative, and immune to training-data drift — and it has already avoided
one wrong answer and diagnosed another in seconds.

```bash
# available versions
curl -s "https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml" \
  | grep -oE "<version>[^<]+</version>" | tail

# what compileSdk does this artifact actually demand?
curl -s -o a.aar "https://dl.google.com/dl/android/maven2/androidx/lifecycle/\
lifecycle-runtime-compose-android/2.10.0/lifecycle-runtime-compose-android-2.10.0.aar"
unzip -p a.aar META-INF/com/android/build/gradle/aar-metadata.properties | grep minCompileSdk
```

That second command answered "will 2.10.0 work on compileSdk 36?" definitively in seconds,
instead of a 10-minute build to find out.

**Applies to every Compose, Media3, AndroidX and Kotlin version for the rest of the project.**
Media3 is explicitly called out by the buildspec as the worst area for stale model knowledge.

---

## 4. The buildspec itself is a source of errors

It was written against an older Auxio. Confirmed wrong so far:

| Buildspec says | Reality |
|---|---|
| JDK 17 | **JDK 21** (`app/build.gradle:33`) |
| `./gradlew assembleFullDebug` | **`:app:assembleDebug`** — no product flavors exist |
| paging prevents materialising all songs | `Library` is already a fully in-memory `Collection`; paging bounds UI memory only |
| `SkinSong.year` | no `year` property exists anywhere; derive from `Date` |

**Treat the spec as intent, not as API documentation.** Verify each claim against source
before building on it, and record the correction.

### …and so are my own corrections

This table previously carried a row claiming the engine "offers only `play(song, PlaySong)`;
context is a parameter". **That was false**, and it was mine — I repeated a Phase 1 agent's
claim without checking it. `play(album)`, `play(artist)`, `play(genre)`, `play(playlist)` and
`play(songs: List<Song>)` all exist (`PlaybackViewModel.kt:336/356/376/396/416`). A later
agent found it while building against the contract I had derived from it.

The row is removed rather than annotated, because a wrong entry in a reference table is worse
than no entry. The lesson stays: **a correction is not self-verifying just because it is a
correction.** Check the source, cite the line, and re-check when something downstream
disagrees.

---

## 6. Kotlin traps that survive compilation

**A trailing lambda binds to the last parameter — and function subtyping hides the mistake.**

```kotlin
fun <T> wheelListBody(
    title: String, items: List<T>, selectedIndex: Int,
    itemLabel: (T) -> String,
    itemKey: ((T) -> Any)? = null,     // <- trailing lambda lands HERE
)

wheelListBody("Music", items, index) { it.name }   // binds to itemKey, not itemLabel
```

`(T) -> String` is a subtype of `(T) -> Any`, so the lambda fits `itemKey` with no type
error. The diagnostic surfaces elsewhere as `No value passed for parameter 'itemLabel'` —
and only because `itemLabel` had no default. Give both defaults and this compiles clean,
labelling rows with nothing and keying them by display string.

**Rule: pass function-typed arguments by name whenever the callee takes more than one.**

**The failure class worth naming: compiles, works on the dev device, fails only at scale.**

```kotlin
private fun <T : Any> LazyPagingItems<T>.wheelItems(): List<T> {
    if (itemCount > 0) this[itemCount - 1]   // forces Paging to load through to the end
    return itemSnapshotList.items
}
```

Indexing the last element defeats paging entirely — every screen pulls the whole library into
memory. At the test device's ~2,200 tracks it is invisible. At the buildspec's 20,000 it is a
hard failure. Neither the compiler nor a device smoke test can see this; only reading the code
with the target scale in mind does. **When a change touches paging, streaming, or batching,
ask what it does at the spec's stated maximum, not at the dev device's size.**

---

## 5. macOS specifics

- **`timeout` does not exist.** Use `gtimeout` (coreutils) or the tool's own flag.
- **`/usr/libexec/java_home -v N` silently returns the wrong JDK** when N is absent — it
  returned 26 when asked for 21. Verify with `$JAVA_HOME/bin/java -version`, never with
  `java_home`'s exit status.
- **Homebrew casks that install into `/Library` need sudo** and cannot prompt from a
  non-interactive shell. Prefer user-local installs (`~/.jdks` via the Adoptium API).
- **GPG commit signing times out** — `pinentry` has no terminal here. Use `--no-gpg-sign`
  and re-sign later with `git commit --amend -S`.
