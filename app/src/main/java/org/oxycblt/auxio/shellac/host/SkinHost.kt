package org.oxycblt.auxio.shellac.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import org.shellac.skin.AppSkin
import org.shellac.skin.SkinSession

/**
 * Renders the active skin behind a crash-loop breaker.
 *
 * ## Why there is no try/catch here
 *
 * There cannot be one. **Compose has no error boundary.** The compiler rejects both forms:
 *
 * ```
 * runCatching { skin.Render(session) }
 *   e: runCatching call is not allowed to contain @Composable function invocations
 *
 * try { skin.Render(session) } catch (t: Throwable) { ... }
 *   e: Try catch is not supported around composable function invocations.
 * ```
 *
 * This is fundamental: composition can be restarted, skipped and reordered, so a `catch`
 * cannot meaningfully scope a subtree. A skin that throws **will** crash the process, and no
 * amount of host code changes that.
 *
 * ## What is done instead
 *
 * Survive the crash rather than prevent it. Before a skin composes, the registry persists
 * "currently attempting <id>". Once a frame completes, that marker is cleared. If the
 * process dies in between, the marker is still there on next launch - so the registry knows
 * that skin crashed, quarantines it, and starts in the fallback instead.
 *
 * The user therefore sees one crash, then a working app. That satisfies the buildspec's
 * requirement ("make Debug B throw in Render; you can still reach settings and switch away")
 * without pretending Compose offers isolation it does not.
 *
 * Defence in depth: ShellacSettingsActivity has its own launcher icon and never composes a
 * skin, so it stays reachable even during a crash loop.
 *
 * ## Insets
 *
 * `AppSkin.supportsEdgeToEdge` is honoured **here**, not by the Activity.
 * `WindowCompat.setDecorFitsSystemWindows` is deprecated and **ignored from API 35 onward**,
 * where edge-to-edge is enforced. On the API 36 test device it did nothing at all, so a skin
 * declaring `supportsEdgeToEdge = false` still drew its own title bar underneath the system
 * status bar. Applying `safeDrawingPadding` to the skin's root fixes it for every skin
 * rather than making each one remember to handle insets itself.
 */
@Composable
fun SkinHost(skin: AppSkin, session: SkinSession, onRenderSucceeded: (String) -> Unit) {
    if (skin.supportsEdgeToEdge) {
        skin.Render(session)
    } else {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) { skin.Render(session) }
    }

    // Reached only if Render returned without throwing. LaunchedEffect runs after the frame
    // is applied, so this genuinely means "this skin composed successfully at least once".
    LaunchedEffect(skin.id) { onRenderSucceeded(skin.id) }
}
