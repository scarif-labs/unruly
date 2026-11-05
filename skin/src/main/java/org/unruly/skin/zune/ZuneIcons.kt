/*
 * Copyright (c) 2026 Unruly Project
 * ZuneIcons.kt is part of Unruly.
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

package org.unruly.skin.zune

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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Glyphs, drawn rather than imported: a Material icon set would bring its own proportions,
// and the hardware's control language is one shape - a thin white ring with a glyph inside.

internal enum class Glyph {
    PLAY,
    PAUSE,
    BACK,
    SHUFFLE,
    REPEAT,
    REPEAT_ONE,
    HEART,
    PREVIOUS,
    NEXT,
    LYRICS,
}

/**
 * The hardware's control: a thin white ring with a glyph centred in it. [diameter] is the ring;
 * the touch target is never smaller than 48dp. [dimmed] draws it at reduced strength, for a
 * toggle that is off.
 */
@Composable
internal fun RingButton(
    glyph: Glyph,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 44.dp,
    dimmed: Boolean = false,
    ring: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val target = if (diameter < 48.dp) 48.dp else diameter
    Box(
        modifier =
            modifier
                .size(target)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                }
                .alpha(
                    when {
                        pressed -> 0.45f
                        dimmed -> 0.5f
                        else -> 1f
                    }
                ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = (size.minDimension / 22f).coerceAtLeast(1.5.dp.toPx())
            if (ring) {
                drawCircle(
                    color = Color.White,
                    radius = size.minDimension / 2f - stroke / 2f,
                    style = Stroke(width = stroke),
                )
            }
            drawGlyph(glyph, size.minDimension * if (ring) 0.42f else 0.8f, stroke)
        }
    }
}

/** Draws [glyph] centred, fitting a box [extent] across. */
internal fun DrawScope.drawGlyph(glyph: Glyph, extent: Float, stroke: Float) {
    val c = center
    val h = extent / 2f
    val white = Color.White
    val line = Stroke(width = stroke * 1.15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (glyph) {
        Glyph.PLAY -> {
            // Nudged right: a triangle's mass sits behind its point.
            val x = c.x + h * 0.12f
            drawPath(
                Path().apply {
                    moveTo(x - h * 0.55f, c.y - h * 0.68f)
                    lineTo(x + h * 0.62f, c.y)
                    lineTo(x - h * 0.55f, c.y + h * 0.68f)
                    close()
                },
                white,
            )
        }
        Glyph.PAUSE -> {
            val w = h * 0.3f
            drawRect(white, Offset(c.x - h * 0.45f, c.y - h * 0.62f), Size(w, h * 1.24f))
            drawRect(white, Offset(c.x + h * 0.15f, c.y - h * 0.62f), Size(w, h * 1.24f))
        }
        Glyph.BACK -> {
            val w = stroke * 1.9f
            val arrow = Stroke(width = w, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
            drawLine(white, Offset(c.x - h * 0.7f, c.y), Offset(c.x + h * 0.75f, c.y), w)
            drawPath(
                Path().apply {
                    moveTo(c.x - h * 0.05f, c.y - h * 0.62f)
                    lineTo(c.x - h * 0.68f, c.y)
                    lineTo(c.x - h * 0.05f, c.y + h * 0.62f)
                },
                white,
                style = arrow,
            )
        }
        Glyph.SHUFFLE -> {
            // Two paths that run level, cross in an S, and run level again, each ending in an
            // arrowhead - straight tails are what stop it reading as an X.
            val l = c.x - h * 0.8f
            val r = c.x + h * 0.55f
            val t = c.y - h * 0.42f
            val b = c.y + h * 0.42f
            val bend = h * 0.32f
            listOf(t to b, b to t).forEach { (from, to) ->
                drawPath(
                    Path().apply {
                        moveTo(l, from)
                        lineTo(l + bend, from)
                        cubicTo(c.x, from, c.x - h * 0.1f, to, r - bend, to)
                        lineTo(r, to)
                    },
                    white,
                    style = line,
                )
            }
            arrowHead(Offset(r + h * 0.12f, t), h * 0.5f, white)
            arrowHead(Offset(r + h * 0.12f, b), h * 0.5f, white)
        }
        Glyph.REPEAT,
        Glyph.REPEAT_ONE -> {
            val r = h * 0.68f
            drawArc(
                color = white,
                startAngle = -60f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2f, r * 2f),
                style = line,
            )
            val tip = Offset(c.x + r * 0.5f, c.y - r * 0.87f)
            drawPath(
                Path().apply {
                    moveTo(tip.x - h * 0.05f, tip.y - h * 0.36f)
                    lineTo(tip.x + h * 0.3f, tip.y + h * 0.02f)
                    lineTo(tip.x - h * 0.12f, tip.y + h * 0.26f)
                    close()
                },
                white,
            )
            if (glyph == Glyph.REPEAT_ONE) {
                drawLine(white, Offset(c.x, c.y - h * 0.3f), Offset(c.x, c.y + h * 0.3f), stroke * 1.3f)
            }
        }
        Glyph.HEART -> {
            val s = h * 0.78f
            drawPath(
                Path().apply {
                    moveTo(c.x, c.y + s * 0.8f)
                    cubicTo(c.x - s * 1.3f, c.y - s * 0.1f, c.x - s * 0.7f, c.y - s * 1.1f, c.x, c.y - s * 0.4f)
                    cubicTo(c.x + s * 0.7f, c.y - s * 1.1f, c.x + s * 1.3f, c.y - s * 0.1f, c.x, c.y + s * 0.8f)
                    close()
                },
                white,
            )
        }
        Glyph.LYRICS -> {
            // Three ragged lines of text: the words page.
            listOf(-0.42f to 0.75f, 0f to 0.55f, 0.42f to 0.68f).forEach { (dy, len) ->
                drawLine(
                    white,
                    Offset(c.x - h * 0.62f, c.y + h * dy),
                    Offset(c.x - h * 0.62f + h * 1.24f * len / 0.75f * 0.75f, c.y + h * dy),
                    stroke * 1.2f,
                    cap = StrokeCap.Round,
                )
            }
        }
        Glyph.PREVIOUS,
        Glyph.NEXT -> {
            // |<< and >>| as on the hardware's transport overlay: a bar and two triangles.
            val forward = glyph == Glyph.NEXT
            val tw = h * 0.62f
            val bar = h * 0.16f
            val total = tw * 2f + bar
            val left = c.x - total / 2f
            fun tri(x: Float, right: Boolean) =
                Path().apply {
                    if (right) {
                        moveTo(x, c.y - h * 0.6f)
                        lineTo(x + tw, c.y)
                        lineTo(x, c.y + h * 0.6f)
                    } else {
                        moveTo(x + tw, c.y - h * 0.6f)
                        lineTo(x, c.y)
                        lineTo(x + tw, c.y + h * 0.6f)
                    }
                    close()
                }
            if (forward) {
                drawPath(tri(left, true), white)
                drawPath(tri(left + tw, true), white)
                drawRect(white, Offset(left + tw * 2f, c.y - h * 0.6f), Size(bar, h * 1.2f))
            } else {
                drawRect(white, Offset(left, c.y - h * 0.6f), Size(bar, h * 1.2f))
                drawPath(tri(left + bar, false), white)
                drawPath(tri(left + bar + tw, false), white)
            }
        }
    }
}

private fun DrawScope.arrowHead(tip: Offset, size: Float, color: Color) {
    drawPath(
        Path().apply {
            moveTo(tip.x + size * 0.55f, tip.y)
            lineTo(tip.x - size * 0.35f, tip.y - size * 0.6f)
            lineTo(tip.x - size * 0.35f, tip.y + size * 0.6f)
            close()
        },
        color,
    )
}
