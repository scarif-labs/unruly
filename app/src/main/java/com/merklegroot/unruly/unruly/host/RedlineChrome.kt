/*
 * Copyright (c) 2026 Unruly Project
 * RedlineChrome.kt is part of Unruly.
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

import android.graphics.Paint
import android.graphics.Rect
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.merklegroot.unruly.R
import kotlin.random.Random

/** Gloria Hallelujah, bundled under SIL OFL 1.1 (licenses/GloriaHallelujah-OFL.txt). */
internal val MarkerFont = FontFamily(Font(R.font.gloria_hallelujah))

/**
 * The setup pages after the welcome: the same red stock, grain and masthead as [VortexWelcome],
 * with a hand-lettered headline, a cut-out illustration and plain buttons.
 *
 * There is no body copy: the headline, art and buttons carry each page. In portrait the art takes
 * whatever height the headline and actions leave; wide windows put the art beside them, as the
 * welcome does.
 *
 * @param fitTo Every headline line in the flow. Each page sizes its headline to the widest of
 *   these, so all the headlines share one size.
 * @param below Content laid over the foot of the art, such as the folder list. The art keeps its
 *   size however long this grows; the content covers it instead, and a child given `weight(1f, fill
 *   = false)` scrolls once it reaches the top. Wide layouts cap it at [BelowWideMaxHeight] in the
 *   scrolling column.
 */
@Composable
internal fun RedlineScreen(
    step: Int?,
    stepCount: Int,
    headline: List<String>,
    fitTo: List<String>,
    footer: String?,
    art: @Composable BoxScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    below: @Composable ColumnScope.() -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(VortexRed)) {
        val sideBySide = maxWidth > maxHeight
        val copyWidth = min(440.dp, maxWidth * 0.46f)
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp)
        ) {
            StepProgress(step, stepCount)
            if (sideBySide) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Box(Modifier.weight(1f).fillMaxHeight(), content = art)
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.width(copyWidth).verticalScroll(rememberScrollState())) {
                        // Short windows: a full-width headline would push the actions
                        // off screen.
                        MarkerHeadline(
                            headline,
                            fitTo,
                            Modifier.padding(bottom = 10.dp),
                            cap = 76.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Column(Modifier.heightIn(max = BelowWideMaxHeight), content = below)
                        RedlineActions(actions)
                        footer?.let { Colophon(it) }
                        Spacer(Modifier.height(14.dp))
                    }
                }
            } else {
                // The headline is drawn to the width and ignores font scale, so it cannot grow
                // into the art.
                MarkerHeadline(headline, fitTo, Modifier.padding(bottom = 10.dp))
                Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp)) {
                    art()
                    // Over the art rather than above it, so adding folders never shrinks it.
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), content = below)
                }
                RedlineActions(actions)
                footer?.let { Colophon(it) }
                Spacer(Modifier.height(14.dp))
            }
        }
        Spacer(Modifier.matchParentSize().printGrain())
    }
}

/** The tallest [RedlineScreen]'s `below` content may grow in the wide layout's scrolling column. */
internal val BelowWideMaxHeight = 220.dp

@Composable
private fun RedlineActions(actions: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = actions,
    )
}

/**
 * Hand-lettered headline, one entry per line, sized so the widest line of [fitTo] fills the width.
 *
 * Drawn on a canvas rather than set as text. The marker's line box reserves room for ascenders a
 * line like "music" does not have, which left a gap above it that line-height trimming cannot
 * remove; here each line is stacked by its actual ink bounds. Filling and stroking the glyphs with
 * round joins thickens the light marker into a felt-tip without smearing it sideways.
 */
@Composable
internal fun MarkerHeadline(
    lines: List<String>,
    fitTo: List<String>,
    modifier: Modifier = Modifier,
    cap: TextUnit = 180.sp,
) {
    val context = LocalContext.current
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, R.font.gloria_hallelujah)
            color = VortexInk.toArgb()
            style = Paint.Style.FILL_AND_STROKE
            strokeJoin = Paint.Join.ROUND
        }
    }
    BoxWithConstraints(
        modifier.fillMaxWidth().semantics {
            heading()
            contentDescription = lines.joinToString(" ")
        }
    ) {
        val width = constraints.maxWidth.toFloat()
        // Fill the column but stop growing on wide panes, where full width would be absurd.
        val capPx = with(LocalDensity.current) { cap.toPx() }
        val layout =
            remember(lines, fitTo, width, capPx) {
                HeadlineLayout.of(lines, fitTo, width, capPx, paint)
            }
        Canvas(
            Modifier.fillMaxWidth().height(with(LocalDensity.current) { layout.height.toDp() })
        ) {
            paint.textSize = layout.textSize
            paint.strokeWidth = layout.stroke
            lines.forEachIndexed { i, line ->
                drawContext.canvas.nativeCanvas.drawText(
                    line,
                    layout.lefts[i],
                    layout.baselines[i],
                    paint,
                )
            }
        }
    }
}

private class HeadlineLayout(
    val textSize: Float,
    val stroke: Float,
    val lefts: FloatArray,
    val baselines: FloatArray,
    val height: Float,
) {
    companion object {
        fun of(
            lines: List<String>,
            fitTo: List<String>,
            width: Float,
            cap: Float,
            paint: Paint,
        ): HeadlineLayout {
            paint.textSize = 100f
            val widest = (lines + fitTo).maxOf { paint.measureText(it) }.coerceAtLeast(1f)
            val size = (100f * width * 0.95f / widest).coerceAtMost(cap)
            val stroke = size * 0.045f
            paint.textSize = size
            val ink = lines.map { Rect().also { r -> paint.getTextBounds(it, 0, it.length, r) } }
            val pad = stroke / 2
            val baselines = FloatArray(lines.size)
            for (i in lines.indices) {
                baselines[i] =
                    if (i == 0) pad - ink[0].top
                    // Each line's ink starts a little below the previous line's ink.
                    else baselines[i - 1] + ink[i - 1].bottom - ink[i].top + size * 0.08f
            }
            val lefts = FloatArray(lines.size) { pad - ink[it].left }
            return HeadlineLayout(
                size,
                stroke,
                lefts,
                baselines,
                baselines.last() + ink.last().bottom + pad,
            )
        }
    }
}

internal enum class Glyph {
    Plus,
    Arrow,
    Play,
    Rescan,
}

/** The welcome's CTA, generalised: a solid ivory primary and an ivory-outlined secondary. */
@Composable
internal fun RedlineButton(
    label: String,
    glyph: Glyph?,
    primary: Boolean = true,
    onClick: () -> Unit,
) {
    val ink = if (primary) VortexInk else VortexIvory
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = if (primary) 62.dp else 54.dp)
            .then(
                if (primary) Modifier.background(VortexIvory)
                else Modifier.border(1.5.dp, VortexIvory)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = ink,
            fontFamily = WelcomeBody,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
        )
        if (glyph != null) {
            Spacer(Modifier.width(12.dp))
            Canvas(Modifier.size(26.dp)) { drawGlyph(glyph, ink) }
        }
    }
}

private fun DrawScope.drawGlyph(glyph: Glyph, ink: Color) {
    val stroke = 1.5.dp.toPx()
    val w = size.width
    val h = size.height
    when (glyph) {
        Glyph.Plus -> {
            drawLine(ink, Offset(w * 0.12f, h / 2), Offset(w * 0.88f, h / 2), stroke)
            drawLine(ink, Offset(w / 2, h * 0.12f), Offset(w / 2, h * 0.88f), stroke)
        }
        Glyph.Arrow -> {
            drawLine(ink, Offset(1f, h / 2), Offset(w - 2f, h / 2), stroke)
            drawLine(ink, Offset(w * 0.62f, h * 0.18f), Offset(w - 2f, h / 2), stroke)
            drawLine(ink, Offset(w * 0.62f, h * 0.82f), Offset(w - 2f, h / 2), stroke)
        }
        Glyph.Play ->
            drawPath(
                Path().apply {
                    moveTo(w * 0.24f, h * 0.16f)
                    lineTo(w * 0.86f, h / 2)
                    lineTo(w * 0.24f, h * 0.84f)
                    close()
                },
                ink,
            )
        Glyph.Rescan -> {
            // A circle open at the top, with a filled arrowhead running clockwise off its end.
            val r = w * 0.34f
            drawArc(
                ink,
                startAngle = -60f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(w / 2 - r, h / 2 - r),
                size = Size(r * 2, r * 2),
                style = Stroke(stroke),
            )
            val end = Offset(w / 2 - r * 0.5f, h / 2 - r * 0.866f)
            val along = Offset(0.866f, -0.5f) * (w * 0.2f)
            val across = Offset(0.5f, 0.866f) * (w * 0.13f)
            drawPath(
                Path().apply {
                    moveTo(end.x + along.x, end.y + along.y)
                    lineTo(end.x - across.x, end.y - across.y)
                    lineTo(end.x + across.x, end.y + across.y)
                    close()
                },
                ink,
            )
        }
    }
}

/**
 * A rectangle with torn edges: each side is walked in short steps and nudged inwards at random, so
 * the outline stays inside its bounds. [seed] keeps a scrap's tear stable across recomposition.
 */
private class TornEdge(private val seed: Int, private val tear: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val random = Random(seed)
        val depth = with(density) { tear.toPx() }
        val step = with(density) { 6.dp.toPx() }
        val path = Path()
        fun side(x0: Float, y0: Float, x1: Float, y1: Float, nx: Float, ny: Float) {
            val length = kotlin.math.hypot(x1 - x0, y1 - y0)
            val count = (length / step).toInt().coerceAtLeast(1)
            for (i in 0 until count) {
                val t = i.toFloat() / count
                val bite = random.nextFloat() * depth
                path.lineTo(x0 + (x1 - x0) * t + nx * bite, y0 + (y1 - y0) * t + ny * bite)
            }
        }
        path.moveTo(0f, 0f)
        side(0f, 0f, size.width, 0f, 0f, 1f)
        side(size.width, 0f, size.width, size.height, -1f, 0f)
        side(size.width, size.height, 0f, size.height, 0f, -1f)
        side(0f, size.height, 0f, 0f, 1f, 0f)
        path.close()
        return Outline.Generic(path)
    }
}

/** An ivory scrap of torn vellum, slightly tilted, lifted off the page by a soft shadow. */
@Composable
internal fun VellumScrap(
    modifier: Modifier = Modifier,
    seed: Int,
    tilt: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = remember(seed) { TornEdge(seed, 2.5.dp) }
    Box(
        modifier
            .graphicsLayer { rotationZ = tilt }
            .shadow(4.dp, shape)
            .background(VortexIvory, shape),
        content = content,
    )
}

/**
 * A cut-out illustration with a soft offset shadow, scaled to fit. The shadow is the same image
 * tinted black: the cut-outs are irregular, so a box shadow would show a rectangle.
 */
@Composable
internal fun PaperCutout(
    @DrawableRes image: Int,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
) {
    val painter = painterResource(image)
    Box(modifier) {
        Image(
            painter,
            contentDescription = null,
            modifier = Modifier.matchParentSize().offset(3.dp, 5.dp),
            alignment = alignment,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.28f)),
        )
        Image(
            painter,
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            alignment = alignment,
            contentScale = ContentScale.Fit,
        )
    }
}

/** The lock-screen notification the permission buys, drawn rather than set in symbol glyphs. */
@Composable
internal fun NowPlayingCard(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier
            .shadow(8.dp, shape)
            .background(VortexIvory, shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "NOW PLAYING",
            modifier = Modifier.weight(1f),
            color = VortexInk,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            letterSpacing = 1.5.sp,
            maxLines = 1,
        )
        Canvas(Modifier.width(108.dp).height(24.dp)) {
            val h = size.height
            val bar = h * 0.16f
            fun triangle(tipX: Float, baseX: Float) =
                drawPath(
                    Path().apply {
                        moveTo(baseX, 0f)
                        lineTo(tipX, h / 2)
                        lineTo(baseX, h)
                        close()
                    },
                    VortexInk,
                )
            // Previous: bar, then a triangle pointing left.
            drawRect(VortexInk, Offset(0f, 0f), Size(bar, h))
            triangle(tipX = bar, baseX = bar + h * 0.8f)
            // Play, centred.
            val mid = size.width / 2
            triangle(tipX = mid + h * 0.45f, baseX = mid - h * 0.45f)
            // Next: a triangle pointing right, then a bar.
            val end = size.width
            triangle(tipX = end - bar, baseX = end - bar - h * 0.8f)
            drawRect(VortexInk, Offset(end - bar, 0f), Size(bar, h))
        }
    }
}
