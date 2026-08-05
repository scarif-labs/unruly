package org.shellac.skin.support

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Colour palette a skin can style itself with. Handed to [SkinTheme] and read back through
 * [SkinTheme.colors].
 */
data class SkinColors(
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val onSurface: Color,
    val accent: Color,
    val onAccent: Color,
) {
    companion object {
        /** Ready-made dark palette. */
        fun darkDefault() = SkinColors(
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onBackground = Color(0xFFE0E0E0),
            onSurface = Color(0xFFBDBDBD),
            accent = Color(0xFFBB86FC),
            onAccent = Color(0xFF000000),
        )

        /** Ready-made light palette. */
        fun lightDefault() = SkinColors(
            background = Color(0xFFFAFAFA),
            surface = Color(0xFFFFFFFF),
            onBackground = Color(0xFF1C1B1F),
            onSurface = Color(0xFF49454F),
            accent = Color(0xFF6750A4),
            onAccent = Color(0xFFFFFFFF),
        )
    }
}

/**
 * Typography a skin can style itself with. Handed to [SkinTheme] and read back through
 * [SkinTheme.typography].
 */
data class SkinTypography(
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
) {
    companion object {
        /** Sensible neutral defaults; most skins override these. */
        fun default() = SkinTypography(
            title = TextStyle.Default,
            body = TextStyle.Default,
            label = TextStyle.Default,
        )
    }
}

/** Default [SkinColors] used when a skin does not override them. */
val LocalSkinColors = staticCompositionLocalOf { SkinColors.darkDefault() }

/** Default [SkinTypography] used when a skin does not override them. */
val LocalSkinTypography = staticCompositionLocalOf { SkinTypography.default() }

/**
 * Per-skin theming that deliberately does not leak into app-native settings.
 *
 * Colors and typography are pushed through [LocalSkinColors] / [LocalSkinTypography] so a
 * skin can read them anywhere inside [content] via [SkinTheme.colors] and
 * [SkinTheme.typography].
 *
 * [darkSystemBars] controls whether the status and navigation bars use dark (light-on-dark
 * icon) appearance. The previous value is captured and restored when this composable
 * leaves the composition - otherwise switching skins leaves the bars styled by the
 * previous skin, which is exactly the leak this wrapper exists to prevent.
 */
@Composable
fun SkinTheme(
    colors: SkinColors,
    typography: SkinTypography,
    darkSystemBars: Boolean = true,
    content: @Composable () -> Unit,
) {
    SystemBarAppearance(darkSystemBars)
    CompositionLocalProvider(
        LocalSkinColors provides colors,
        LocalSkinTypography provides typography,
    ) {
        content()
    }
}

/**
 * Sets the status/navigation bar icon appearance for [darkSystemBars], and restores the
 * previous appearance on dispose so a later skin is not stuck with our styling.
 */
@Composable
private fun SystemBarAppearance(darkSystemBars: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window

    DisposableEffect(darkSystemBars, window) {
        val controller = window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
        // Capture the values this skin is about to overwrite, so onDispose can restore them.
        val previousStatusBars = controller?.isAppearanceLightStatusBars
        val previousNavigationBars = controller?.isAppearanceLightNavigationBars

        controller?.isAppearanceLightStatusBars = darkSystemBars
        controller?.isAppearanceLightNavigationBars = darkSystemBars

        onDispose {
            controller?.let {
                it.isAppearanceLightStatusBars = previousStatusBars ?: false
                it.isAppearanceLightNavigationBars = previousNavigationBars ?: false
            }
        }
    }
}

/** Accessors for the current skin's theme values, read from the composition locals. */
object SkinTheme {
    /** The active [SkinColors], read from [LocalSkinColors]. */
    val colors: SkinColors
        @Composable @ReadOnlyComposable get() = LocalSkinColors.current

    /** The active [SkinTypography], read from [LocalSkinTypography]. */
    val typography: SkinTypography
        @Composable @ReadOnlyComposable get() = LocalSkinTypography.current
}
