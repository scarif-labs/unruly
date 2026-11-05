/*
 * Copyright (c) 2026 Unruly Project
 * GlacialChrome.kt is part of Unruly.
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
 
package com.merklegroot.unruly.unruly.host

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.merklegroot.unruly.R
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Chrome for setup, styled as the first power-on of a fictional Y2K personal stereo: glacial-blue
 * print stock, a monochrome dot-matrix LCD, ink-black type.
 *
 * ## Texture is computed, never painted
 *
 * Every texture here - the gradient, the grain, the disc, the screen transition - is rendered at
 * runtime into a low-resolution bitmap and scaled up with nearest-neighbour filtering. That is what
 * keeps the look crisp: an ordered-dither pattern only reads as deliberate when every dot lands on
 * the same grid, and a pre-rendered texture scaled to an arbitrary screen smears it. It also means
 * the screens ship without a single image asset.
 *
 * Layout rules carried over from [WinampScreen]: one screen fills the viewport, the content region
 * absorbs the slack, and the primary action is pinned to the bottom as the only solid-ink fill.
 */
object Glacial {
    val Ice = Color(0xFF65A9D5)
    val Lagoon = Color(0xFF358AA9)
    val Ink = Color(0xFF0B1C2C)
    val InkSoft = Color(0xFF1F3D55)
    val Paper = Color(0xFFEAF3F8)
    val Lcd = Color(0xFFB4C4AC)
    val LcdShade = Color(0xFF94A68D)
    val LcdInk = Color(0xFF16231B)
    val LcdGhost = Color(0x1416231B)
}

/**
 * Departure Mono (Helena Zhang, SIL OFL 1.1 - see `licenses/DepartureMono-OFL.txt`), a monospace
 * drawn on a pixel grid, so body copy shares the dot-matrix headlines' construction.
 *
 * It ships one weight. Synthesis is switched off in [GText] because a faux bold smears the pixel
 * strokes; emphasis comes from ink, size and tracking instead.
 */
internal val Departure = FontFamily(Font(R.font.departure_mono))

/** The 8x8 Bayer index matrix. Thresholds are `(v + 0.5) / 64`. */
private val BAYER8 =
    intArrayOf(
        0,
        32,
        8,
        40,
        2,
        34,
        10,
        42,
        48,
        16,
        56,
        24,
        50,
        18,
        58,
        26,
        12,
        44,
        4,
        36,
        14,
        46,
        6,
        38,
        60,
        28,
        52,
        20,
        62,
        30,
        54,
        22,
        3,
        35,
        11,
        43,
        1,
        33,
        9,
        41,
        51,
        19,
        59,
        27,
        49,
        17,
        57,
        25,
        15,
        47,
        7,
        39,
        13,
        45,
        5,
        37,
        63,
        31,
        55,
        23,
        61,
        29,
        53,
        21,
    )

private fun bayer(x: Int, y: Int): Float = (BAYER8[(y and 7) * 8 + (x and 7)] + 0.5f) / 64f

/** Draws [image] across [size] with hard pixel edges. */
private fun DrawScope.drawPixels(
    image: ImageBitmap,
    dst: IntSize = IntSize(size.width.toInt(), size.height.toInt()),
) {
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset.Zero,
        dstSize = dst,
        filterQuality = FilterQuality.None,
    )
}

// ---------------------------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------------------------

/**
 * A full setup screen: model strip and step meter at the top, content taking the slack, actions
 * pinned at the bottom.
 *
 * @param step Zero-based position in the flow, or null when the screen is opened outside it (the
 *   folder editor reached from settings), in which case the meter is hidden.
 */
@Composable
fun GlacialScreen(
    step: Int?,
    stepCount: Int,
    label: String,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        GlacialBackdrop(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GText("UNRULY SL-2000", size = 11, letterSpacing = 1.5f)
                Spacer(Modifier.width(10.dp))
                GText(
                    label,
                    size = 11,
                    letterSpacing = 1.5f,
                    soft = true,
                    modifier = Modifier.weight(1f),
                )
                if (step != null) {
                    GText("%02d/%02d".format(step + 1, stepCount), size = 11, letterSpacing = 1.5f)
                }
            }
            Spacer(Modifier.height(10.dp))
            StepMeter(step, stepCount)
            Spacer(Modifier.height(24.dp))

            Column(Modifier.fillMaxWidth().weight(1f), content = content)

            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = actions,
            )
        }
    }
}

/** Solid segments for steps reached, dithered ones for steps still ahead. */
@Composable
private fun StepMeter(step: Int?, count: Int) {
    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
        val cell = 2.dp.toPx()
        if (step == null) {
            drawRect(Glacial.Ink, size = Size(size.width, 1.dp.toPx()))
            return@Canvas
        }
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (count - 1)) / count
        repeat(count) { i ->
            val x0 = i * (w + gap)
            if (i <= step) {
                drawRect(Glacial.Ink, Offset(x0, 0f), Size(w, size.height))
            } else {
                // A 50% checker: the same "not yet" language as the dithered transitions.
                var cx = 0
                while (cx * cell < w) {
                    for (cy in 0 until 2) {
                        if ((cx + cy) % 2 == 0) {
                            drawRect(
                                Glacial.Ink,
                                Offset(x0 + cx * cell, cy * cell),
                                Size(minOf(cell, w - cx * cell), cell),
                            )
                        }
                    }
                    cx++
                }
            }
        }
    }
}

/**
 * The page stock: flat glacial blue that Bayer-dithers into lagoon over the bottom ~45%, with a
 * live film grain on top.
 *
 * The gradient is computed once per size. The grain cycles through a few pre-rolled tiles at
 * roughly 12fps - fast enough to shimmer like a scan, slow enough not to read as video noise - and
 * only the draw phase observes the frame clock, so nothing recomposes.
 */
@Composable
private fun GlacialBackdrop(modifier: Modifier) {
    val frame = rememberFrameNanos()
    Spacer(
        modifier.drawWithCache {
            val cell = 2.dp.toPx()
            val w = max(1, ceil(size.width / cell).toInt())
            val h = max(1, ceil(size.height / cell).toInt())
            val ice = Glacial.Ice.toArgb()
            val lagoon = Glacial.Lagoon.toArgb()
            val px = IntArray(w * h)
            for (y in 0 until h) {
                val t = ((y.toFloat() / h) - 0.55f) / 0.45f
                val v = t.coerceIn(0f, 1f).pow(1.4f)
                for (x in 0 until w) {
                    px[y * w + x] = if (v > bayer(x, y)) lagoon else ice
                }
            }
            val image = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
            val dst = IntSize((w * cell).toInt(), (h * cell).toInt())
            val grainCell = 1.dp.toPx()
            val tilePx = (GRAIN_TILE * grainCell).toInt()
            onDrawBehind {
                drawPixels(image, dst)
                val tile = grainTiles[((frame.longValue / 83_000_000L) % GRAIN_FRAMES).toInt()]
                var y = 0
                while (y < size.height) {
                    var x = 0
                    while (x < size.width) {
                        drawImage(
                            tile,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(GRAIN_TILE, GRAIN_TILE),
                            dstOffset = IntOffset(x, y),
                            dstSize = IntSize(tilePx, tilePx),
                            filterQuality = FilterQuality.None,
                        )
                        x += tilePx
                    }
                    y += tilePx
                }
            }
        }
    )
}

private const val GRAIN_TILE = 96
private const val GRAIN_FRAMES = 4

private val grainTiles: List<ImageBitmap> by lazy {
    val ink = Glacial.Ink.copy(alpha = 0.16f).toArgb()
    val paper = Glacial.Paper.copy(alpha = 0.22f).toArgb()
    val rng = Random(1999)
    List(GRAIN_FRAMES) {
        val px =
            IntArray(GRAIN_TILE * GRAIN_TILE) {
                val r = rng.nextFloat()
                when {
                    r < 0.07f -> ink
                    r < 0.13f -> paper
                    else -> 0
                }
            }
        Bitmap.createBitmap(px, GRAIN_TILE, GRAIN_TILE, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/**
 * Frame time since first composition. Read it only in draw lambdas, so frames redraw, not
 * recompose.
 */
@Composable
private fun rememberFrameNanos(): MutableLongState {
    val state = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { state.longValue = it - start }
    }
    return state
}

// ---------------------------------------------------------------------------------------------
// Type
// ---------------------------------------------------------------------------------------------

/** Ink monospace text, the body face of the whole flow. */
@Composable
fun GText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 14,
    soft: Boolean = false,
    color: Color = if (soft) Glacial.InkSoft else Glacial.Ink,
    letterSpacing: Float = 0f,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        fontFamily = Departure,
        style = LocalTextStyle.current.copy(fontSynthesis = FontSynthesis.None),
        fontSize = size.sp,
        lineHeight = (size * 1.5f).sp,
        letterSpacing = letterSpacing.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Headline lines set in the 5x7 matrix as solid ink pixels. The pixel pitch shrinks to fit the
 * widest line, capped at [maxPitch] so short words do not balloon.
 */
@Composable
fun PixelHeadline(lines: List<String>, modifier: Modifier = Modifier, maxPitch: Dp = 6.dp) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cols = lines.maxOf { dotColumns(it, proportional = true) }
        val pitch = min(maxPitch, maxWidth / cols)
        Column(verticalArrangement = Arrangement.spacedBy(pitch * 2)) {
            lines.forEach {
                DotText(it, pitch = pitch, on = Glacial.Ink, dot = 1f, proportional = true)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Dot matrix
// ---------------------------------------------------------------------------------------------

/** Rows of a 5x7 glyph, `#` lit. Lowercase falls back to uppercase where a glyph is missing. */
private val FONT: Map<Char, List<String>> =
    mapOf(
        ' ' to listOf(".....", ".....", ".....", ".....", ".....", ".....", "....."),
        'A' to listOf(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'B' to listOf("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."),
        'C' to listOf(".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."),
        'D' to listOf("###..", "#..#.", "#...#", "#...#", "#...#", "#..#.", "###.."),
        'E' to listOf("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
        'F' to listOf("#####", "#....", "#....", "####.", "#....", "#....", "#...."),
        'G' to listOf(".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####"),
        'H' to listOf("#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'I' to listOf(".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."),
        'J' to listOf("..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."),
        'K' to listOf("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
        'L' to listOf("#....", "#....", "#....", "#....", "#....", "#....", "#####"),
        'M' to listOf("#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"),
        'N' to listOf("#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#"),
        'O' to listOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'P' to listOf("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
        'Q' to listOf(".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"),
        'R' to listOf("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
        'S' to listOf(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
        'T' to listOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
        'U' to listOf("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'V' to listOf("#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."),
        'W' to listOf("#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#."),
        'X' to listOf("#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"),
        'Y' to listOf("#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."),
        'Z' to listOf("#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"),
        '0' to listOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
        '1' to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
        '2' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to listOf("#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."),
        '4' to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        '5' to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        '6' to listOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to listOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        '8' to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to listOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
        ':' to listOf(".....", ".##..", ".##..", ".....", ".##..", ".##..", "....."),
        '.' to listOf(".....", ".....", ".....", ".....", ".....", ".##..", ".##.."),
        '-' to listOf(".....", ".....", ".....", "#####", ".....", ".....", "....."),
        '/' to listOf(".....", "....#", "...#.", "..#..", ".#...", "#....", "....."),
        '+' to listOf(".....", "..#..", "..#..", "#####", "..#..", "..#..", "....."),
        '·' to listOf(".....", ".....", ".....", "..#..", ".....", ".....", "....."),
        '!' to listOf("..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#.."),
        '&' to listOf(".##..", "#..#.", "#.#..", ".#...", "#.#.#", "#..#.", ".##.#"),
        '▶' to listOf("#....", "##...", "###..", "####.", "###..", "##...", "#...."),
        '◀' to listOf("....#", "...##", "..###", ".####", "..###", "...##", "....#"),
        '⏮' to listOf("#...#", "#..##", "#.###", "#####", "#.###", "#..##", "#...#"),
        '⏭' to listOf("#...#", "##..#", "###.#", "#####", "###.#", "##..#", "#...#"),
        'a' to listOf(".....", ".....", ".###.", "....#", ".####", "#...#", ".####"),
        'c' to listOf(".....", ".....", ".###.", "#....", "#....", "#...#", ".###."),
        'e' to listOf(".....", ".....", ".###.", "#...#", "#####", "#....", ".###."),
        'h' to listOf("#....", "#....", "#.##.", "##..#", "#...#", "#...#", "#...#"),
        'l' to listOf(".##..", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."),
        's' to listOf(".....", ".....", ".####", "#....", ".###.", "....#", "####."),
    )

private fun glyph(c: Char): List<String> = FONT[c] ?: FONT[c.uppercaseChar()] ?: FONT.getValue(' ')

/**
 * Column spans of each glyph in [text]. Monospaced, every glyph is five columns. Proportional,
 * blank edge columns are trimmed so narrow letters like `l` and `i` stop floating in their cells -
 * the difference between a readout and a wordmark. A space keeps three columns either way.
 */
private fun spans(text: String, proportional: Boolean): List<IntRange> =
    text.map { c ->
        val rows = glyph(c)
        if (!proportional) return@map 0..4
        val lit = (0..4).filter { k -> rows.any { it[k] == '#' } }
        if (lit.isEmpty()) 0..2 else lit.first()..lit.last()
    }

/** Matrix columns for [text], with one blank column between glyphs. */
fun dotColumns(text: String, proportional: Boolean = false): Int =
    if (text.isEmpty()) 0 else spans(text, proportional).sumOf { it.last - it.first + 2 } - 1

/** Rasterises [text] into column-major on/off cells, seven rows tall. */
private fun rasterise(text: String, proportional: Boolean = false): Array<BooleanArray> {
    val cols = Array(dotColumns(text, proportional)) { BooleanArray(7) }
    var x = 0
    val glyphSpans = spans(text, proportional)
    text.forEachIndexed { i, c ->
        val rows = glyph(c)
        val span = glyphSpans[i]
        for (k in span) {
            for (r in 0 until 7) if (rows[r][k] == '#') cols[x][r] = true
            x++
        }
        x++
    }
    return cols
}

/**
 * Draws a grid of square dots. [dot] is the dot's share of the pitch: under 1 leaves the hairline
 * gaps of a real LCD, 1 gives solid pixels. [ghost], when set, paints the unlit cells - the faint
 * resting segments a passive LCD shows, which is most of what makes it read as hardware.
 */
private fun DrawScope.drawDots(
    cols: Int,
    pitch: Float,
    on: Color,
    ghost: Color?,
    dot: Float,
    lit: (col: Int, row: Int) -> Boolean,
) {
    // Solid pixels overlap by half a pixel so antialiased edges never open a seam between them.
    val s = if (dot >= 1f) pitch + 0.5f else pitch * dot
    for (c in 0 until cols) {
        for (r in 0 until 7) {
            val isOn = lit(c, r)
            if (!isOn && ghost == null) continue
            drawRect(if (isOn) on else ghost!!, Offset(c * pitch, r * pitch), Size(s, s))
        }
    }
}

/**
 * [text] in the 5x7 matrix.
 *
 * @param allOn Lights every cell, for the power-on segment test.
 */
@Composable
fun DotText(
    text: String,
    pitch: Dp,
    modifier: Modifier = Modifier,
    on: Color = Glacial.LcdInk,
    ghost: Color? = null,
    dot: Float = 0.86f,
    allOn: Boolean = false,
    proportional: Boolean = false,
) {
    val cells = remember(text, proportional) { rasterise(text, proportional) }
    Canvas(modifier.size(pitch * cells.size, pitch * 7)) {
        drawDots(cells.size, pitch.toPx(), on, ghost, dot) { c, r -> allOn || cells[c][r] }
    }
}

/**
 * A full-width matrix line that scrolls [text] right to left one column at a time, the way a
 * hardware marquee steps rather than glides.
 */
@Composable
fun DotMarquee(
    text: String,
    pitch: Dp,
    modifier: Modifier = Modifier,
    allOn: Boolean = false,
    blank: Boolean = false,
) {
    val loop = remember(text) { rasterise(text + "   ") }
    var offset by remember { mutableIntStateOf(0) }
    LaunchedEffect(loop) {
        while (true) {
            delay(MARQUEE_STEP_MS)
            offset = (offset + 1) % loop.size
        }
    }
    Canvas(modifier.fillMaxWidth().height(pitch * 7)) {
        val p = pitch.toPx()
        val cols = (size.width / p).toInt()
        drawDots(cols, p, Glacial.LcdInk, Glacial.LcdGhost, 0.86f) { c, r ->
            when {
                allOn -> true
                blank -> false
                else -> loop[(c + offset) % loop.size][r]
            }
        }
    }
}

private const val MARQUEE_STEP_MS = 110L

/**
 * The recessed monochrome panel. A dark lip along the top and left sells the recess; the border is
 * a hard ink line because the whole flow is drawn in ink on paper.
 */
@Composable
fun LcdWell(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .drawBehind {
                val b = 2.dp.toPx()
                val lip = 4.dp.toPx()
                drawRect(Glacial.Ink)
                drawRect(Glacial.Lcd, Offset(b, b), Size(size.width - 2 * b, size.height - 2 * b))
                drawRect(Glacial.LcdShade, Offset(b, b), Size(size.width - 2 * b, lip))
                drawRect(Glacial.LcdShade, Offset(b, b), Size(lip, size.height - 2 * b))
            }
            .padding(start = 18.dp, end = 14.dp, top = 16.dp, bottom = 14.dp),
        content = content,
    )
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

/**
 * A flat button that casts a hard, dithered offset shadow and presses down into it.
 *
 * The press is a physical translation onto the shadow - the same affordance a printed sticker or a
 * rubber key gives - which is why there is no ripple. Only the [primary] button is solid ink.
 */
@Composable
fun GlacialButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    glyph: String? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val drop = 5.dp
    Box(
        modifier
            .fillMaxWidth()
            .padding(end = drop, bottom = drop)
            .drawBehind {
                if (pressed) return@drawBehind
                val d = drop.toPx()
                val cell = 1.5.dp.toPx()
                // Shadow strips to the right and below, as a 50% checker on the cell grid.
                fun strip(x0: Float, y0: Float, w: Float, h: Float) {
                    val cx0 = (x0 / cell).toInt()
                    val cy0 = (y0 / cell).toInt()
                    val cx1 = ((x0 + w) / cell).toInt()
                    val cy1 = ((y0 + h) / cell).toInt()
                    for (cy in cy0 until cy1) for (cx in cx0 until cx1) {
                        if ((cx + cy) % 2 == 0) {
                            drawRect(Glacial.Ink, Offset(cx * cell, cy * cell), Size(cell, cell))
                        }
                    }
                }
                strip(size.width, d, d, size.height)
                strip(d, size.height, size.width - d, d)
            }
            .graphicsLayer {
                val d = if (pressed) drop.toPx() else 0f
                translationX = d
                translationY = d
            }
            .background(if (primary) Glacial.Ink else Glacial.Paper)
            .drawBehind {
                if (!primary) {
                    val b = 2.dp.toPx()
                    drawRect(Glacial.Ink, size = Size(size.width, b))
                    drawRect(Glacial.Ink, Offset(0f, size.height - b), Size(size.width, b))
                    drawRect(Glacial.Ink, size = Size(b, size.height))
                    drawRect(Glacial.Ink, Offset(size.width - b, 0f), Size(b, size.height))
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .heightIn(min = if (primary) 58.dp else 50.dp)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        val ink = if (primary) Glacial.Paper else Glacial.Ink
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) {
                GText(glyph, size = 15, color = ink)
                Spacer(Modifier.width(12.dp))
            }
            GText(label.uppercase(), size = 15, color = ink, letterSpacing = 3f, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Disc
// ---------------------------------------------------------------------------------------------

private const val DISC_CELLS = 132

/**
 * A compact disc, shaded analytically per pixel and reduced to three inks with an ordered dither.
 *
 * Nothing about it is an asset: radius and angle are tabulated once, and each frame only
 * re-evaluates the lighting and thresholds it, so ~17k pixels cost a few hundred microseconds. The
 * reflection bow-tie drifts slowly as if the disc were tilting under a lamp, and the moulded ring
 * of ticks on the clear hub turns - the only part of a real spinning disc you can see move.
 */
@Composable
fun DitherDisc(modifier: Modifier = Modifier) {
    val n = DISC_CELLS
    val tables = remember {
        val r = FloatArray(n * n)
        val a = FloatArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f) / n * 2f - 1f
            val dy = (y + 0.5f) / n * 2f - 1f
            r[y * n + x] = hypot(dx, dy) / 0.94f
            a[y * n + x] = atan2(dy, dx)
        }
        r to a
    }
    val bitmap = remember { Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888) }
    val image = remember { bitmap.asImageBitmap() }
    val pixels = remember { IntArray(n * n) }
    val frame = rememberFrameNanos()

    Canvas(modifier) {
        val t = frame.longValue / 1e9f
        val (rt, at) = tables
        val ink = Glacial.Ink.toArgb()
        val ice = Glacial.Ice.toArgb()
        val paper = Glacial.Paper.toArgb()
        val light = 0.6f + sin(t * 0.35f) * 0.5f
        val spin = t * 0.9f
        val sx = 0.07f / 0.94f
        val sy = 0.09f / 0.94f
        for (y in 0 until n) for (x in 0 until n) {
            val i = y * n + x
            val r = rt[i]
            val th = at[i]
            val lum: Float =
                when {
                    r > 1f -> {
                        // Hard dithered drop shadow, offset down-right.
                        val dx = (x + 0.5f) / n * 2f - 1f - sx * 0.94f
                        val dy = (y + 0.5f) / n * 2f - 1f - sy * 0.94f
                        if (hypot(dx, dy) / 0.94f < 1f) 0.36f else -1f
                    }
                    r > 0.965f -> 0.04f
                    r > 0.36f -> {
                        val bow = abs(cos(th - light)).pow(7f)
                        val ripple = sin(r * 70f + th * 0.5f) * 0.05f
                        val leadOut = if (r > 0.86f) 0.1f else 0f
                        val inner = if (r < 0.40f) -0.12f else 0f
                        (0.44f + bow * 0.62f + ripple + leadOut + inner).coerceIn(0f, 1f)
                    }
                    r > 0.345f -> 0.12f
                    r > 0.16f -> {
                        val a = (th - spin).mod(TAU)
                        val tick = (a / (TAU / 24f)).mod(1f) < 0.22f
                        val notch = a < 0.22f
                        when {
                            r in 0.25f..0.30f && (tick || notch) -> 0.08f
                            r in 0.21f..0.25f && notch -> 0.08f
                            else -> 0.66f + abs(cos(th - light)).pow(3f) * 0.2f
                        }
                    }
                    r > 0.145f -> 0.1f
                    else -> -1f
                }
            pixels[i] =
                when {
                    lum < 0f -> 0
                    lum < 0.5f -> if (lum * 2f > bayer(x, y)) ice else ink
                    else -> if ((lum - 0.5f) * 2f > bayer(x, y)) paper else ice
                }
        }
        bitmap.setPixels(pixels, 0, n, 0, 0, n, n)
        val side = minOf(size.width, size.height).toInt()
        val left = ((size.width - side) / 2).toInt()
        val top = ((size.height - side) / 2).toInt()
        drawImage(
            image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(n, n),
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(side, side),
            filterQuality = FilterQuality.None,
        )
    }
}

private const val TAU = (Math.PI * 2).toFloat()

// ---------------------------------------------------------------------------------------------
// Transition
// ---------------------------------------------------------------------------------------------

/**
 * Swaps [target]'s content behind an ordered-dither dissolve: ink fills the screen in Bayer order
 * rising from the bottom, the content changes under full cover, and the ink drains away again.
 */
@Composable
fun <T> DitherSwap(target: T, content: @Composable (T) -> Unit) {
    var shown by remember { mutableStateOf(target) }
    val cover = remember { Animatable(0f) }
    LaunchedEffect(target) {
        if (target == shown) return@LaunchedEffect
        cover.animateTo(1f, tween(240, easing = LinearEasing))
        shown = target
        cover.animateTo(0f, tween(340, easing = LinearEasing))
    }
    val cache = remember { arrayOfNulls<Bitmap>(1) }
    Box(Modifier.fillMaxSize()) {
        content(shown)
        Canvas(Modifier.fillMaxSize()) {
            val c = cover.value
            if (c <= 0f) return@Canvas
            val cell = 6.dp.toPx()
            val w = max(1, ceil(size.width / cell).toInt())
            val h = max(1, ceil(size.height / cell).toInt())
            val bmp =
                cache[0]?.takeIf { it.width == w && it.height == h }
                    ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { cache[0] = it }
            val ink = Glacial.Ink.toArgb()
            val px = IntArray(w * h)
            for (y in 0 until h) {
                val rise = y.toFloat() / h
                for (x in 0 until w) {
                    val v = bayer(x, y) * 0.7f + (1f - rise) * 0.3f
                    px[y * w + x] = if (v < c) ink else 0
                }
            }
            bmp.setPixels(px, 0, w, 0, 0, w, h)
            drawPixels(bmp.asImageBitmap(), IntSize((w * cell).toInt(), (h * cell).toInt()))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// ASCII
// ---------------------------------------------------------------------------------------------

/** Density ramp, sparse to dense. On the LCD ink is dark, so dense characters read as shadow. */
private const val ASCII_RAMP = " .:-=+*#%@"

/**
 * The same disc as [DitherDisc], lit by the same model, but set in type: each character cell
 * samples the shading and picks a glyph from [ASCII_RAMP].
 *
 * Rows are fewer than columns because a monospace cell is roughly 0.6 as wide as it is tall; the
 * ratio keeps the disc round. Rebuilt ~8 times a second as the reflection drifts, which at ~735
 * characters is nothing.
 */
@Composable
fun AsciiDisc(modifier: Modifier = Modifier, color: Color = Glacial.LcdInk, size: Int = 9) {
    val rows = 21
    val cols = 35
    val light by
        produceState(0.6f) {
            while (true) {
                delay(120)
                value += 0.09f
            }
        }
    val art = buildString {
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val dx = (x + 0.5f) / cols * 2f - 1f
                val dy = (y + 0.5f) / rows * 2f - 1f
                val r = hypot(dx, dy)
                val th = atan2(dy, dx)
                val ink: Float =
                    when {
                        r > 1f -> 0f
                        r > 0.88f -> 0.95f
                        r > 0.38f -> 0.75f - abs(cos(th - light)).pow(5f) * 0.7f
                        r > 0.3f -> 0.6f
                        r > 0.16f -> 0.18f
                        else -> 0f
                    }
                append(
                    ASCII_RAMP[
                        (ink * (ASCII_RAMP.length - 1)).toInt().coerceIn(0, ASCII_RAMP.lastIndex)]
                )
            }
            if (y < rows - 1) append('\n')
        }
    }
    Text(
        art,
        modifier = modifier,
        color = color,
        fontFamily = Departure,
        fontSize = size.sp,
        lineHeight = size.sp,
        letterSpacing = 0.sp,
        softWrap = false,
    )
}
