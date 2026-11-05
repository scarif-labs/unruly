Implement four pieces of skin support infrastructure for the Unruly Android project
(buildspec Phase 4.2, 4.3, 4.5, 4.6). These are mechanical; keep them small and obvious.

Working directory is the repo root.

## Where the code goes

Create NEW files under `skin/src/main/java/org/unruly/skin/support/`:
- `SkinAlbumArt.kt`
- `SkinTheme.kt`
- `SkinBackStack.kt`
- `SkinStates.kt`

Do NOT create `PositionTicker.kt` or `RotaryGesture.kt` - another agent is writing those
right now. Do not touch them.

## Hard constraints

1. **Only use `org.unruly.skin` types plus Compose/Coil/Kotlin stdlib.** Do NOT import
   `org.oxycblt.auxio` or `org.oxycblt.musikr` - the `skin` module cannot see them. Do not
   read `app/` or `musikr/` at all.
2. Do not modify existing files. Do not touch any `build.gradle`.
3. Read these first:
   - `skin/src/main/java/org/unruly/skin/SkinModels.kt`
   - `skin/src/main/java/org/unruly/skin/SkinLibrary.kt` (for `LibraryLoadState`)
4. `collectAsStateWithLifecycle` is NOT available. Use `collectAsState()`.
5. Available: androidx.compose runtime/ui/foundation/material3, androidx.paging.compose,
   and Coil 3 (`io.coil-kt.coil3:coil-compose`, so `coil3.compose.AsyncImage`).

Pinned: Kotlin 2.3.10, Compose BOM 2026.06.01, Coil 3.4.0, minSdk 24, Java 21.

---

## 4.2 - SkinAlbumArt

A composable wrapping Coil for album art.

```kotlin
@Composable
fun SkinAlbumArt(
    uri: Uri?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit = { }
)
```

Requirements:
1. Use `coil3.compose.AsyncImage`.
2. **No reload flicker on recomposition.** Build the `ImageRequest` inside a `remember`
   keyed on the uri, so an unrelated recomposition does not restart the load. This is the
   whole point of the wrapper - state it in a comment.
3. `uri == null` renders `fallback` and never touches Coil.
4. A load error also renders `fallback`. Use AsyncImage's `error` slot; do not let a
   missing or corrupt file crash the skin.
5. Default `ContentScale.Crop`, overridable via a parameter.
6. The fallback is a caller-supplied composable so each skin styles its own - do not
   hardcode a placeholder image.

---

## 4.3 - SkinTheme

Per-skin theming that does not leak into app-native settings.

```kotlin
data class SkinColors(
    val background: Color, val surface: Color, val onBackground: Color,
    val onSurface: Color, val accent: Color, val onAccent: Color
)

data class SkinTypography(
    val title: TextStyle, val body: TextStyle, val label: TextStyle
)

@Composable
fun SkinTheme(
    colors: SkinColors,
    typography: SkinTypography,
    darkSystemBars: Boolean = true,
    content: @Composable () -> Unit
)
```

Requirements:
1. Provide `colors` and `typography` through `CompositionLocal`s (`LocalSkinColors`,
   `LocalSkinTypography`) with sensible defaults, so a skin can read them anywhere.
2. Expose accessors `SkinTheme.colors` and `SkinTheme.typography` via an `object SkinTheme`
   with `@Composable @ReadOnlyComposable` getters.
3. `darkSystemBars` controls status/navigation bar icon appearance. Do it with
   `androidx.core.view.WindowCompat` / `WindowInsetsControllerCompat` obtained from the
   current window via `LocalView.current`. **Restore the previous value in a
   `DisposableEffect` `onDispose`** - otherwise switching skins leaves the bars styled by
   the previous skin, which is exactly the leak this task exists to prevent.
4. Provide two ready-made palettes: `SkinColors.darkDefault()` and `SkinColors.lightDefault()`.

---

## 4.5 - SkinBackStack

An OPTIONAL back-stack primitive. A flat skin must not be forced to use it.

```kotlin
class SkinBackStack<T>(initial: T) {
    val current: T
    val canPop: Boolean
    fun push(destination: T)
    fun pop(): Boolean      // false when already at root
    fun resetTo(destination: T)
    val size: Int
}

@Composable
fun <T : Any> rememberSkinBackStack(initial: T): SkinBackStack<T>
```

Requirements:
1. Back it with a `SnapshotStateList` so Compose observes changes.
2. `pop()` at the root returns false and leaves the stack alone - never empties it.
3. `rememberSkinBackStack` uses `remember`. **Add a KDoc note** explaining that the stack
   must also be reachable from `AppSkin.onBackPressed()`, which is NOT composable, so a skin
   typically holds this in a field on the skin object rather than only in composition.
   Reference `AppSkin`'s rule 2.
4. Keep it generic over `T` - destinations are the skin's own type.

---

## 4.6 - SkinStates

Standard hooks for library-scanning / empty / no-results, styled per skin.

```kotlin
@Composable
fun SkinLibraryGate(
    loadState: LibraryLoadState,
    scanning: @Composable (loaded: Int?, explored: Int?) -> Unit,
    failed: @Composable (message: String) -> Unit,
    empty: @Composable () -> Unit,
    ready: @Composable () -> Unit
)

@Composable
fun SkinNoResults(query: String, content: @Composable (String) -> Unit)
```

Requirements:
1. `SkinLibraryGate` exhaustively `when`s over `LibraryLoadState` and calls the matching
   slot. `Idle` should render the same as `scanning(null, null)` - a skin should not have to
   care about the distinction.
2. `Ready(isEmpty = true)` calls `empty`, otherwise `ready`.
3. Every branch is a caller-supplied composable - render nothing of your own. This is a
   control-flow helper, not a UI.
4. Add a KDoc note that `LibraryLoadState` outranks paging's own load state, per
   `SkinLibrary.loadState`'s documentation.

---

## Verify

You CANNOT run Gradle in this sandbox - do not try. Re-read your files and check every
symbol you used actually exists, especially Coil 3 API names (`coil3.compose.AsyncImage`,
`coil3.request.ImageRequest`) which differ from Coil 2.

## Report to stdout

1. Files created
2. The exact Coil 3 API you used
3. How you restore system bar appearance on dispose
4. Anything in the contract that was missing for this work
