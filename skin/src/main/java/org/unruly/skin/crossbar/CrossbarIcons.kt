/*
 * Copyright (c) 2026 Unruly Project
 * CrossbarIcons.kt is part of Unruly.
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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The skin's icons, drawn in a unit square. Our own drawings in the device's manner - flat white, a
 * soft drop shadow, and a glow behind whichever is selected - not copies of its artwork.
 */
internal enum class Glyph {
    SETTINGS,
    MUSIC,
    PLAYLISTS,
    ARTISTS,
    ALBUMS,
    SONGS,
    GENRES,
    SHUFFLE,
    NOW_PLAYING,
    OPTION,
    COLOUR,
    GEAR,
    NOTE_TILE,
    PREVIOUS,
    NEXT,
    REWIND,
    FORWARD,
    PLAY,
    PAUSE,
    STOP,
    REPEAT,
    REPEAT_ONE,
    VISUAL,
    LYRICS,
}

/**
 * Draws [glyph] in a [size] box. [glow] (0..1) lights a soft halo behind it, as the device does for
 * the selection; [alpha] fades the whole icon for unselected entries.
 */
@Composable
internal fun GlyphIcon(
    glyph: Glyph,
    size: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    glow: Float = 0f,
) {
    Canvas(modifier = modifier.size(size)) {
        if (glow > 0f) {
            drawCircle(
                brush =
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.45f * glow * alpha), Color.Transparent),
                        center = center,
                        radius = this.size.minDimension * 0.75f,
                    ),
                radius = this.size.minDimension * 0.75f,
            )
        }
        val s = this.size.minDimension
        translate(s * 0.03f, s * 0.05f) {
            drawGlyph(glyph, Color.Black.copy(alpha = 0.22f * alpha))
        }
        drawGlyph(glyph, Color.White.copy(alpha = 0.95f * alpha))
    }
}

internal fun DrawScope.drawGlyph(glyph: Glyph, c: Color) {
    val s = size.minDimension
    val ox = (size.width - s) / 2f
    val oy = (size.height - s) / 2f
    fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    fun rect(l: Float, t: Float, r: Float, b: Float, radius: Float = 0f) =
        drawRoundRect(c, p(l, t), Size((r - l) * s, (b - t) * s), CornerRadius(radius * s))
    fun stroke(w: Float) = Stroke(width = w * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun poly(vararg pts: Float): Path =
        Path().apply {
            moveTo(ox + pts[0] * s, oy + pts[1] * s)
            var i = 2
            while (i < pts.size) {
                lineTo(ox + pts[i] * s, oy + pts[i + 1] * s)
                i += 2
            }
            close()
        }
    fun oval(cx: Float, cy: Float, rx: Float, ry: Float, angle: Float = -20f) {
        rotate(angle, p(cx, cy)) { drawOval(c, p(cx - rx, cy - ry), Size(rx * 2 * s, ry * 2 * s)) }
    }

    when (glyph) {
        Glyph.SETTINGS -> {
            // A toolbox: handle, lid, body, latch.
            drawRoundRect(
                c,
                p(0.36f, 0.16f),
                Size(0.28f * s, 0.2f * s),
                CornerRadius(0.05f * s),
                style = stroke(0.07f),
            )
            rect(0.08f, 0.34f, 0.92f, 0.5f, 0.06f)
            rect(0.08f, 0.54f, 0.92f, 0.86f, 0.06f)
            rect(0.44f, 0.46f, 0.56f, 0.6f, 0.02f)
        }
        Glyph.MUSIC -> {
            // Two beamed quavers.
            drawPath(poly(0.30f, 0.20f, 0.80f, 0.10f, 0.80f, 0.22f, 0.30f, 0.32f), c)
            rect(0.30f, 0.22f, 0.37f, 0.74f)
            rect(0.73f, 0.12f, 0.80f, 0.64f)
            oval(0.24f, 0.75f, 0.14f, 0.10f)
            oval(0.67f, 0.65f, 0.14f, 0.10f)
        }
        Glyph.PLAYLISTS -> {
            drawPath(poly(0.22f, 0.10f, 0.22f, 0.48f, 0.66f, 0.29f), c)
            for (y in listOf(0.58f, 0.72f, 0.86f)) {
                rect(0.12f, y - 0.035f, 0.2f, y + 0.035f, 0.035f)
                rect(0.30f, y - 0.035f, 0.88f, y + 0.035f, 0.035f)
            }
        }
        Glyph.ARTISTS -> {
            drawCircle(c, 0.17f * s, p(0.5f, 0.3f))
            drawPath(
                Path().apply {
                    moveTo(ox + 0.16f * s, oy + 0.9f * s)
                    cubicTo(
                        ox + 0.16f * s,
                        oy + 0.5f * s,
                        ox + 0.84f * s,
                        oy + 0.5f * s,
                        ox + 0.84f * s,
                        oy + 0.9f * s,
                    )
                    close()
                },
                c,
            )
        }
        Glyph.ALBUMS -> {
            val disc =
                Path().apply {
                    fillType = PathFillType.EvenOdd
                    addOval(Rect(p(0.08f, 0.08f), Size(0.84f * s, 0.84f * s)))
                    addOval(Rect(p(0.42f, 0.42f), Size(0.16f * s, 0.16f * s)))
                }
            drawPath(disc, c)
            drawArc(
                Color.Black.copy(alpha = c.alpha * 0.18f),
                200f,
                70f,
                false,
                p(0.22f, 0.22f),
                Size(0.56f * s, 0.56f * s),
                style = stroke(0.05f),
            )
        }
        Glyph.SONGS -> {
            rect(0.56f, 0.12f, 0.63f, 0.72f)
            drawPath(
                Path().apply {
                    moveTo(ox + 0.63f * s, oy + 0.12f * s)
                    cubicTo(
                        ox + 0.70f * s,
                        oy + 0.28f * s,
                        ox + 0.86f * s,
                        oy + 0.30f * s,
                        ox + 0.80f * s,
                        oy + 0.52f * s,
                    )
                    cubicTo(
                        ox + 0.78f * s,
                        oy + 0.38f * s,
                        ox + 0.70f * s,
                        oy + 0.34f * s,
                        ox + 0.63f * s,
                        oy + 0.32f * s,
                    )
                    close()
                },
                c,
            )
            oval(0.47f, 0.74f, 0.15f, 0.11f)
        }
        Glyph.GENRES -> {
            val tag =
                Path().apply {
                    fillType = PathFillType.EvenOdd
                    moveTo(ox + 0.12f * s, oy + 0.18f * s)
                    lineTo(ox + 0.50f * s, oy + 0.18f * s)
                    lineTo(ox + 0.88f * s, oy + 0.56f * s)
                    lineTo(ox + 0.56f * s, oy + 0.88f * s)
                    lineTo(ox + 0.12f * s, oy + 0.48f * s)
                    close()
                    addOval(Rect(p(0.24f, 0.28f), Size(0.12f * s, 0.12f * s)))
                }
            drawPath(tag, c)
        }
        Glyph.SHUFFLE -> {
            val st = stroke(0.08f)
            drawPath(
                Path().apply {
                    moveTo(ox + 0.1f * s, oy + 0.3f * s)
                    cubicTo(
                        ox + 0.45f * s,
                        oy + 0.3f * s,
                        ox + 0.45f * s,
                        oy + 0.7f * s,
                        ox + 0.78f * s,
                        oy + 0.7f * s,
                    )
                },
                c,
                style = st,
            )
            drawPath(
                Path().apply {
                    moveTo(ox + 0.1f * s, oy + 0.7f * s)
                    cubicTo(
                        ox + 0.45f * s,
                        oy + 0.7f * s,
                        ox + 0.45f * s,
                        oy + 0.3f * s,
                        ox + 0.78f * s,
                        oy + 0.3f * s,
                    )
                },
                c,
                style = st,
            )
            drawPath(poly(0.74f, 0.18f, 0.92f, 0.3f, 0.74f, 0.42f), c)
            drawPath(poly(0.74f, 0.58f, 0.92f, 0.7f, 0.74f, 0.82f), c)
        }
        Glyph.NOW_PLAYING -> {
            drawCircle(c, 0.4f * s, p(0.5f, 0.5f), style = stroke(0.07f))
            drawPath(poly(0.41f, 0.32f, 0.41f, 0.68f, 0.70f, 0.5f), c)
        }
        Glyph.OPTION -> {
            // The settings entry: a ring with a small spanner across it.
            drawCircle(c, 0.36f * s, p(0.5f, 0.5f))
            drawLine(
                Color.Black.copy(alpha = c.alpha * 0.35f),
                p(0.34f, 0.66f),
                p(0.62f, 0.38f),
                0.09f * s,
                StrokeCap.Round,
            )
            drawCircle(Color.Black.copy(alpha = c.alpha * 0.35f), 0.09f * s, p(0.64f, 0.36f))
        }
        Glyph.COLOUR -> {
            drawCircle(c, 0.4f * s, p(0.5f, 0.5f), style = stroke(0.07f))
            for (i in 0 until 3) {
                val a = (-PI / 2 + i * 2 * PI / 3).toFloat()
                drawCircle(c, 0.1f * s, p(0.5f + 0.2f * cos(a), 0.5f + 0.2f * sin(a)))
            }
        }
        Glyph.GEAR -> {
            for (i in 0 until 8) {
                rotate(i * 45f, p(0.5f, 0.5f)) { rect(0.44f, 0.08f, 0.56f, 0.24f, 0.02f) }
            }
            drawCircle(c, 0.3f * s, p(0.5f, 0.5f), style = stroke(0.12f))
        }
        Glyph.NOTE_TILE -> {
            drawRoundRect(
                c,
                p(0.08f, 0.08f),
                Size(0.84f * s, 0.84f * s),
                CornerRadius(0.1f * s),
                style = stroke(0.06f),
            )
            rect(0.52f, 0.24f, 0.58f, 0.64f)
            drawPath(poly(0.58f, 0.24f, 0.74f, 0.34f, 0.74f, 0.42f, 0.58f, 0.34f), c)
            oval(0.45f, 0.66f, 0.12f, 0.08f)
        }
        Glyph.PREVIOUS -> {
            rect(0.14f, 0.28f, 0.2f, 0.72f)
            drawPath(poly(0.5f, 0.28f, 0.5f, 0.72f, 0.2f, 0.5f), c)
            drawPath(poly(0.82f, 0.28f, 0.82f, 0.72f, 0.52f, 0.5f), c)
        }
        Glyph.NEXT -> {
            drawPath(poly(0.18f, 0.28f, 0.18f, 0.72f, 0.48f, 0.5f), c)
            drawPath(poly(0.5f, 0.28f, 0.5f, 0.72f, 0.8f, 0.5f), c)
            rect(0.8f, 0.28f, 0.86f, 0.72f)
        }
        Glyph.REWIND -> {
            drawPath(poly(0.5f, 0.28f, 0.5f, 0.72f, 0.16f, 0.5f), c)
            drawPath(poly(0.84f, 0.28f, 0.84f, 0.72f, 0.5f, 0.5f), c)
        }
        Glyph.FORWARD -> {
            drawPath(poly(0.16f, 0.28f, 0.16f, 0.72f, 0.5f, 0.5f), c)
            drawPath(poly(0.5f, 0.28f, 0.5f, 0.72f, 0.84f, 0.5f), c)
        }
        Glyph.PLAY -> drawPath(poly(0.26f, 0.2f, 0.26f, 0.8f, 0.82f, 0.5f), c)
        Glyph.PAUSE -> {
            rect(0.26f, 0.22f, 0.4f, 0.78f, 0.02f)
            rect(0.6f, 0.22f, 0.74f, 0.78f, 0.02f)
        }
        Glyph.STOP -> rect(0.24f, 0.24f, 0.76f, 0.76f, 0.04f)
        Glyph.REPEAT,
        Glyph.REPEAT_ONE -> {
            drawRoundRect(
                c,
                p(0.12f, 0.3f),
                Size(0.76f * s, 0.4f * s),
                CornerRadius(0.2f * s),
                style = stroke(0.07f),
            )
            drawPath(poly(0.52f, 0.2f, 0.66f, 0.3f, 0.52f, 0.4f), c)
            if (glyph == Glyph.REPEAT_ONE) rect(0.46f, 0.42f, 0.53f, 0.6f)
        }
        Glyph.VISUAL -> {
            drawCircle(c, 0.36f * s, p(0.5f, 0.5f), style = stroke(0.07f))
            for (i in 0 until 4) {
                val a = (i * PI / 2 + PI / 4).toFloat()
                drawLine(
                    c,
                    p(0.5f + 0.1f * cos(a), 0.5f + 0.1f * sin(a)),
                    p(0.5f + 0.26f * cos(a), 0.5f + 0.26f * sin(a)),
                    0.07f * s,
                    StrokeCap.Round,
                )
            }
        }
        Glyph.LYRICS -> {
            drawRoundRect(
                c,
                p(0.12f, 0.16f),
                Size(0.76f * s, 0.62f * s),
                CornerRadius(0.1f * s),
                style = stroke(0.07f),
            )
            drawPath(poly(0.28f, 0.76f, 0.28f, 0.92f, 0.44f, 0.76f), c)
            for (y in listOf(0.36f, 0.5f, 0.62f)) rect(
                0.26f,
                y - 0.03f,
                if (y == 0.62f) 0.56f else 0.74f,
                y + 0.03f,
                0.03f,
            )
        }
    }
}
