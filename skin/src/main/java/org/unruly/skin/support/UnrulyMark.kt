/*
 * Copyright (c) 2026 Unruly Project
 * UnrulyMark.kt is part of Unruly.
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
 
package org.unruly.skin.support

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Unruly mark - two rows of blocks stepping from narrow bars into full squares, like a level
 * meter caught mid-bounce.
 *
 * ## Why it is drawn rather than shipped as an asset
 *
 * The mark appears at 10-14dp in a skin's title bar. At that size a raster asset is mush and even a
 * vector drawable spends its detail budget on hinting; five rectangles per row drawn straight into
 * a Canvas land on exact pixel boundaries at any density. It also means the mark carries no
 * resource dependency, so `:skin` stays a pure-Kotlin contract module with no `R` class of its own.
 *
 * ## Geometry
 *
 * A 2-row grid on a 100 x 46 unit box, scaled to whatever height it is given. Column widths run 7,
 * 7, 24, 24, 24 with 3-unit gutters: the two narrow columns are what give the mark its read as a
 * rising meter rather than a plain checkerboard, so they must not be dropped when it is drawn
 * small.
 */
object UnrulyBrand {
    val Red = Color(0xFFE8431C)
    val Blue = Color(0xFF1B9AD9)
    val Yellow = Color(0xFFF5B310)
    val Ink = Color(0xFF1A1A2E)

    /** Width as a multiple of height. Anything laying the mark out needs this. */
    const val ASPECT = 100f / 46f
}

/** Column widths in mark units, left to right. */
private val COLUMN_WIDTHS = floatArrayOf(7f, 7f, 24f, 24f, 24f)
private const val GUTTER = 3f
private const val UNIT_WIDTH = 100f
private const val UNIT_HEIGHT = 46f
private const val ROW_HEIGHT = 21.5f
private const val ROW_GAP = 3f

/** Top row, then bottom row. The right column holds yellow through both. */
private val TOP_ROW =
    listOf(
        UnrulyBrand.Red,
        UnrulyBrand.Red,
        UnrulyBrand.Red,
        UnrulyBrand.Blue,
        UnrulyBrand.Yellow,
    )
private val BOTTOM_ROW =
    listOf(
        UnrulyBrand.Blue,
        UnrulyBrand.Blue,
        UnrulyBrand.Blue,
        UnrulyBrand.Red,
        UnrulyBrand.Yellow,
    )

/**
 * The mark alone, no wordmark. Sized by [height]; width follows [UnrulyBrand.ASPECT].
 *
 * @param tint When set, every block is drawn in this colour instead of the brand palette. Needed
 *   for the Touch Wheel's grey title bar, where five saturated colours in a 10dp glyph read as
 *   noise next to a monochrome play indicator and battery.
 */
@Composable
fun UnrulyMark(height: Dp, modifier: Modifier = Modifier, tint: Color? = null) {
    Canvas(modifier = modifier.height(height).width(height * UnrulyBrand.ASPECT)) {
        val scaleX = size.width / UNIT_WIDTH
        val scaleY = size.height / UNIT_HEIGHT

        listOf(TOP_ROW to 0f, BOTTOM_ROW to ROW_HEIGHT + ROW_GAP).forEach { (row, unitTop) ->
            var unitLeft = 0f
            row.forEachIndexed { index, color ->
                val unitWidth = COLUMN_WIDTHS[index]
                drawRect(
                    color = tint ?: color,
                    topLeft = Offset(unitLeft * scaleX, unitTop * scaleY),
                    size = Size(unitWidth * scaleX, ROW_HEIGHT * scaleY),
                )
                unitLeft += unitWidth + GUTTER
            }
        }
    }
}

/**
 * Mark plus "unruly" wordmark, for setup and settings screens where there is room to be explicit
 * about whose app this is. Skins use [UnrulyMark] on its own.
 */
@Composable
fun UnrulyLogo(
    markHeight: Dp = 18.dp,
    modifier: Modifier = Modifier,
    wordColor: Color = UnrulyBrand.Ink,
    tint: Color? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        UnrulyMark(height = markHeight, tint = tint)
        BasicText(
            text = "unruly",
            modifier = Modifier.padding(start = markHeight * 0.7f),
            style =
                TextStyle(
                    color = wordColor,
                    // Tracks the mark so the lockup keeps its proportions at any size.
                    fontSize = (markHeight.value * 1.5f).sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                ),
        )
    }
}
