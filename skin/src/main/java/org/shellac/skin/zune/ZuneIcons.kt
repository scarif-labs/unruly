/*
 * Copyright (c) 2026 Shellac Project
 * ZuneIcons.kt is part of Shellac.
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
 
package org.shellac.skin.zune

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.shellac.skin.support.SkinTheme

// Transport glyphs, drawn rather than imported.
//
// The obvious route - material-icons-extended - is not a declared dependency of this
// module and adding it would pull a multi-megabyte icon set in to draw four triangles.
// Drawing them keeps the skin's "everything is BasicText, Box and Canvas" rule intact and
// lets the shapes carry the skin's own proportions instead of Material's.

/**
 * Wraps a drawn glyph in a touch target that is always at least 48dp, whatever the glyph measures.
 *
 * The glyphs are deliberately small - a 13dp skip arrow is right for this layout - and a 13dp tap
 * target is not usable, so the Canvas fills the target and the shape is drawn centred inside it.
 *
 * Press feedback is a flat alpha drop, not a ripple: a Material ripple puts a grey circle over a
 * near-black screen with one orange accent, which is the exact Material-ness this skin exists to
 * avoid.
 */
@Composable
private fun GlyphTarget(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    target: Dp = 48.dp,
    draw: DrawScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier =
            modifier
                .size(target)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .semantics {
                    contentDescription = description
                    role = Role.Button
                }
                .alpha(if (pressed) 0.5f else 1f),
        contentAlignment = Alignment.Center,
        content = { Canvas(modifier = Modifier.size(target), onDraw = draw) },
    )
}

@Composable
internal fun PreviousButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = SkinTheme.colors.accent
    GlyphTarget(description = "previous track", onClick = onClick, modifier = modifier) {
        val glyphHeight = 13.dp.toPx()
        val triangleWidth = 11.dp.toPx()
        val barWidth = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val startX = center.x - (barWidth + gap + triangleWidth) / 2f
        drawRect(
            color = accent,
            topLeft = Offset(startX, center.y - glyphHeight / 2f),
            size = Size(barWidth, glyphHeight),
        )
        drawTriangle(
            centerX = startX + barWidth + gap + triangleWidth / 2f,
            centerY = center.y,
            width = triangleWidth,
            height = glyphHeight,
            pointsRight = false,
            color = accent,
        )
    }
}

@Composable
internal fun NextButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = SkinTheme.colors.accent
    GlyphTarget(description = "next track", onClick = onClick, modifier = modifier) {
        val glyphHeight = 13.dp.toPx()
        val triangleWidth = 11.dp.toPx()
        val barWidth = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val startX = center.x - (barWidth + gap + triangleWidth) / 2f
        drawTriangle(
            centerX = startX + triangleWidth / 2f,
            centerY = center.y,
            width = triangleWidth,
            height = glyphHeight,
            pointsRight = true,
            color = accent,
        )
        drawRect(
            color = accent,
            topLeft = Offset(startX + triangleWidth + gap, center.y - glyphHeight / 2f),
            size = Size(barWidth, glyphHeight),
        )
    }
}

/**
 * The one control you hit without looking, so it is bigger than the skips and carries a ring: with
 * three bare orange glyphs in a row and nothing else to separate them, the middle one had no visual
 * claim to being the primary action.
 */
@Composable
internal fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = SkinTheme.colors.accent
    GlyphTarget(
        description = if (isPlaying) "pause" else "play",
        onClick = onClick,
        modifier = modifier,
        target = 64.dp,
    ) {
        val radius = 23.dp.toPx()
        // A translucent fill rather than a solid one: the ring sits over album art, and a
        // solid orange disc reads as a sticker pasted on the cover.
        drawCircle(color = accent.copy(alpha = 0.14f), radius = radius)
        drawCircle(color = accent, radius = radius, style = Stroke(width = 1.5.dp.toPx()))
        if (isPlaying) {
            val barWidth = 4.dp.toPx()
            val barHeight = 17.dp.toPx()
            val gap = 6.dp.toPx()
            val top = center.y - barHeight / 2f
            drawRect(
                color = accent,
                topLeft = Offset(center.x - gap / 2f - barWidth, top),
                size = Size(barWidth, barHeight),
            )
            drawRect(
                color = accent,
                topLeft = Offset(center.x + gap / 2f, top),
                size = Size(barWidth, barHeight),
            )
        } else {
            // Nudged right of true centre: a triangle's mass sits behind its point, so a
            // geometrically centred play arrow looks like it has slipped left in its ring.
            drawTriangle(
                centerX = center.x + 1.5.dp.toPx(),
                centerY = center.y,
                width = 16.dp.toPx(),
                height = 18.dp.toPx(),
                pointsRight = true,
                color = accent,
            )
        }
    }
}

private fun DrawScope.drawTriangle(
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    pointsRight: Boolean,
    color: Color,
) {
    val left = centerX - width / 2f
    val right = centerX + width / 2f
    val top = centerY - height / 2f
    val bottom = centerY + height / 2f
    val path = Path()
    if (pointsRight) {
        path.moveTo(left, top)
        path.lineTo(right, centerY)
        path.lineTo(left, bottom)
    } else {
        path.moveTo(right, top)
        path.lineTo(left, centerY)
        path.lineTo(right, bottom)
    }
    path.close()
    drawPath(path = path, color = color)
}
