/*
 * Copyright (c) 2026 Unruly Project
 * CrossbarTheme.kt is part of Unruly.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.unruly.skin.crossbar

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import org.unruly.skin.R

/*
 * The handheld's cross-shaped menu bar, measured off frames of its 6.61 system software running in
 * an emulator (native 480 x 272). Every size here is in the handheld's pixels ("px" below) and
 * scaled to the screen: in landscape 272 px fill the height, as on the device; in portrait 420 px
 * fill the width, a little larger than 480 so the type stays legible on a phone.
 *
 * The face is M PLUS Rounded 1c (SIL OFL; skin/LICENSE-MPLUSRounded1c-OFL.txt), the closest free
 * match to the wide, rounded gothic the original used. Icons are drawn in [CrossbarIcons]; nothing
 * from the original ships.
 */

internal val Rounded =
    FontFamily(
        Font(R.font.rounded_medium, FontWeight.Medium),
        Font(R.font.rounded_bold, FontWeight.Bold),
    )

/** Dp per handheld pixel. Provided at the skin's root. */
internal val LocalPx = staticCompositionLocalOf { 1.dp }

@Composable internal fun px(v: Float): Dp = LocalPx.current * v

@Composable internal fun px(v: Int): Dp = LocalPx.current * v

@Composable
internal fun pxSp(v: Float): TextUnit = with(LocalDensity.current) { (LocalPx.current * v).toSp() }

/** Dp per px for a screen of the given size. */
internal fun pxScale(widthDp: Float, heightDp: Float): Float =
    if (widthDp > heightDp) heightDp / 272f else widthDp / 420f

/** Measurements, in px. */
internal object Geo {
    /** Centre-to-centre distance between category icons. */
    const val CATEGORY_GAP = 83f

    /** Selected category's icon centre, from the left edge. */
    const val COLUMN_X = 110f

    /** Category icon box; the selected one is drawn [CATEGORY_GROW] times larger. */
    const val CATEGORY_ICON = 40f
    const val CATEGORY_GROW = 1.18f

    /** Selected item's row centre, below the category icons' centre. */
    const val FIRST_ROW = 65f

    /** An item's neighbours: the first is this far away, the rest [ROW] apart. */
    const val NEAR_ROW = 60f
    const val ROW = 42f

    /** At the top level the items before the selection sit above the bar, this far up. */
    const val ABOVE_BAR = 130f

    /** Item icon box, and where an item's text starts relative to its icon's centre. */
    const val ITEM_ICON = 30f
    const val TEXT_FROM_ICON = 32f

    /** A drilled-into list's icon column, and how far each level steps left. */
    const val LIST_X = 135f
    const val LEVEL_STEP = 87f

    const val TITLE = 15f
    const val SUBTITLE = 11.5f
    const val LABEL = 10.5f
}

/** One month's backdrop, sampled from the noon frames. */
internal data class Tint(
    val topLeft: Color,
    val topRight: Color,
    val midLeft: Color,
    val midRight: Color,
    /** The wave's glow: on the device the ribbon is lit with this. */
    val glow: Color,
) {
    /** True when the backdrop is pale enough to need dark status-bar icons. */
    val isLight: Boolean
        get() = luminance(topLeft) > 0.6f

    fun dimmed(by: Float): Tint {
        if (by >= 1f) return this
        fun Color.d() = Color(red * by, green * by, blue * by, alpha)
        return Tint(
            topLeft.d(),
            topRight.d(),
            midLeft.d(),
            midRight.d(),
            lerp(glow, glow.d(), 0.6f),
        )
    }

    private fun luminance(c: Color) = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
}

internal object Tints {
    private fun c(hex: Long) = Color(0xFF000000 or hex)

    /** January to December. */
    val months =
        listOf(
            Tint(c(0xCDD0DA), c(0xC9CCD7), c(0xBFC3CF), c(0xC2C5D1), c(0xFBFFFF)),
            Tint(c(0xEBAD13), c(0xEBBE17), c(0xEBB216), c(0xEBC212), c(0xFFFF10)),
            Tint(c(0x6BBE31), c(0x9BD448), c(0x5BB32B), c(0x8BCA35), c(0xFFFF3B)),
            Tint(c(0xFB8EAC), c(0xF4B8C6), c(0xFD7EA4), c(0xF4BBC7), c(0xFFFFD0)),
            Tint(c(0x014619), c(0x09691A), c(0x07581A), c(0x137C1B), c(0xE6FF50)),
            Tint(c(0x6F469F), c(0xB47AED), c(0x724EA8), c(0xAC79EB), c(0xFFEAFF)),
            Tint(c(0x0698BA), c(0x13B8DC), c(0x069BB6), c(0x10B8DA), c(0xCBFFFF)),
            Tint(c(0x000653), c(0x005FB8), c(0x002186), c(0x0187C6), c(0xA2FFFF)),
            Tint(c(0x28054D), c(0x3C0176), c(0x4B0C6D), c(0x510782), c(0xFF65FF)),
            Tint(c(0xFFA825), c(0xFFBC53), c(0xFF9B29), c(0xFFAC3A), c(0xFFFF68)),
            Tint(c(0x4F190F), c(0x692C1B), c(0x3F0E07), c(0x501A10), c(0xFF7511)),
            Tint(c(0x97120D), c(0xB81712), c(0xAF1306), c(0xC4180C), c(0xFFE63B)),
        )

    val monthNames =
        listOf(
            "January",
            "February",
            "March",
            "April",
            "May",
            "June",
            "July",
            "August",
            "September",
            "October",
            "November",
            "December",
        )

    /**
     * How bright the backdrop is at [hour] (0-23): full by day, dimming through the evening to
     * about half at night, as the original did.
     */
    fun daylight(hour: Int, minute: Int): Float {
        val h = hour + minute / 60f
        val night = 0.55f
        return when {
            h < 5f -> night
            h < 8f -> night + (1f - night) * (h - 5f) / 3f
            h < 17f -> 1f
            h < 21f -> 1f - (1f - night) * (h - 17f) / 4f
            else -> night
        }
    }
}

/** The backdrop's current colours, for the few things drawn in them. */
internal val LocalTint = compositionLocalOf { Tints.months[9] }

internal object Ink {
    val Text = Color(0xFFFFFFFF)
    val Shadow = Color(0x66000000)

    /** Elapsed time and the progress fill on the playback screen. */
    val Blue = Color(0xFF3B63F0)

    /** The translucent band across the top of the playback screen. */
    val Band = Color(0x40000020)

    /** The side menu's panel. */
    val Panel = Color(0x38000000)
}

/** White text with the soft drop shadow every label on the device carries. */
@Composable
internal fun crossText(
    size: Float,
    weight: FontWeight = FontWeight.Medium,
    color: Color = Ink.Text,
): TextStyle {
    val unit = LocalPx.current.value * LocalDensity.current.density
    return TextStyle(
        fontFamily = Rounded,
        fontWeight = weight,
        fontSize = pxSp(size),
        color = color,
        shadow = Shadow(Ink.Shadow, Offset(unit * 0.8f, unit * 1.2f), unit * 2.5f),
    )
}

/**
 * The skin's own settings: which colour the backdrop takes. "Original" follows the month, as the
 * device did out of the box.
 */
internal class CrossbarPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("crossbar_skin", Context.MODE_PRIVATE)

    /** 0 = by month; 1-12 = that month's colour always. */
    var colour: Int
        get() = prefs.getInt(KEY_COLOUR, 0).coerceIn(0, 12)
        set(value) = prefs.edit { putInt(KEY_COLOUR, value) }

    /** Index into [Visualiser.entries]. */
    var visualiser: Int
        get() = prefs.getInt(KEY_VISUALISER, 0)
        set(value) = prefs.edit { putInt(KEY_VISUALISER, value) }

    private companion object {
        const val KEY_COLOUR = "colour"
        const val KEY_VISUALISER = "visualiser"
    }
}
