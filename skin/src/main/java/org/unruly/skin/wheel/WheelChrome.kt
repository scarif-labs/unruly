/*
 * Copyright (c) 2026 Unruly Project
 * WheelChrome.kt is part of Unruly.
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
 

package org.unruly.skin.wheel

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlinx.coroutines.delay
import org.unruly.skin.SkinSong
import org.unruly.skin.support.UnrulyMark
import org.unruly.skin.support.SkinAlbumArt
import org.unruly.skin.support.SkinColors
import org.unruly.skin.support.SkinTypography

/**
 * Chrome for the Touch Wheel skin, styled after the classic hard-drive music players of the
 * mid-2000s: a grey title bar, bold menu rows with a glossy blue selection and chevrons, a split
 * menu/artwork layout on the top-level menus, and full-width lists with a scrollbar below them.
 *
 * Identifiers here are deliberately vendor-neutral ("Classic", not a brand name). The visual
 * language is a widely-copied convention; the trademarks around it are not, and keeping them out of
 * the source costs nothing.
 *
 * These are presentation primitives only - no state, no engine access.
 */

/** White background, black text, deep blue selection. */
internal val ClassicColors =
    SkinColors(
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        onBackground = Color(0xFF000000),
        onSurface = Color(0xFF000000),
        accent = Color(0xFF2A5DA8),
        onAccent = Color(0xFFFFFFFF),
    )

internal val ClassicTypography =
    SkinTypography(
        title = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
        body = TextStyle(fontSize = 15.sp),
        label = TextStyle(fontSize = 12.sp),
    )

/** Menu rows are bold on the originals, at every level - it is most of their character. */
private val MenuTextStyle =
    TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)

// Vertical gradients. The selection has a gloss break: a pale upper half falling sharply to a
// saturated lower half, which is what stops it reading as a modern flat list highlight.
private val SelectionGradient =
    arrayOf(
        0.00f to Color(0xFF9CC4F2),
        0.48f to Color(0xFF5B95E2),
        0.52f to Color(0xFF3A78D3),
        1.00f to Color(0xFF2560BF),
    )
private val SelectionEdge = Color(0xFF1E4F9E)
private val TitleBarGradient = listOf(Color(0xFFFFFFFF), Color(0xFFE6E6E6), Color(0xFFC9C9C9))
private val ProgressGradient =
    arrayOf(
        0.00f to Color(0xFFA8CBF4),
        0.50f to Color(0xFF5F98E3),
        0.51f to Color(0xFF3D79D2),
        1.00f to Color(0xFF2B63BE),
    )
private val ScrollThumbGradient = listOf(Color(0xFF7FA9E6), Color(0xFF2C62BB))

private val HairlineGrey = Color(0xFF8A8A8A)
private val TrackGradient = listOf(Color(0xFFD9D9D9), Color(0xFFF7F7F7))
private val PanelGrey = Color(0xFFE9E9E9)

internal val TitleBarHeight = 24.dp

/**
 * Every menu row is the same height, as on the originals' fixed text grid. This is the minimum; a
 * list stretches it slightly so a whole number of rows fills the screen (see [ClassicRowGrid]).
 */
internal val ClassicRowHeight = 30.dp

private val LocalClassicRowHeight = staticCompositionLocalOf { ClassicRowHeight }

/**
 * Sizes the rows inside [content] so a whole number of them exactly fills the available height. The
 * originals never show a half-cut row at the foot of a list.
 */
@Composable
internal fun ClassicRowGrid(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = modifier) {
        val rows = floor(maxHeight / ClassicRowHeight).coerceAtLeast(1f)
        val rowHeight = if (maxHeight == Dp.Infinity) ClassicRowHeight else maxHeight / rows
        CompositionLocalProvider(LocalClassicRowHeight provides rowHeight, content = content)
    }
}

// Face proportions, derived from the hardware: a 61.8 x 103.5 mm body carrying a 4:3 screen
// about 38 mm tall and a wheel about 43 mm across.
//
//   screen height / face width = 0.75   (4:3, landscape)
//   wheel diameter / face width = 0.70
//   gap between them            = 0.10
//
/** Screen height as a fraction of face width - 4:3, so 3/4. */
internal const val SCREEN_ASPECT = 0.75f

/** Wheel diameter as a fraction of face width. */
internal const val WHEEL_WIDTH_RATIO = 0.70f

/** Vertical gap between screen and wheel, as a fraction of face width. */
internal const val FACE_GAP_RATIO = 0.10f

/** Total face height as a multiple of face width - the three values above summed. */
internal const val FACE_HEIGHT_RATIO = SCREEN_ASPECT + FACE_GAP_RATIO + WHEEL_WIDTH_RATIO

/**
 * Anodised silver body. A shallow diagonal falloff rather than a flat grey: the real face catches
 * light across its width, and a flat fill reads as a background rather than a material.
 */
internal val BodyBrush =
    Brush.linearGradient(
        colors = listOf(Color(0xFFF0F1F3), Color(0xFFDADBDE), Color(0xFFC8C9CD)),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

/** The black glass mask around the LCD. */
internal val ScreenSurround = Color(0xFF0C0C0D)

/** Width of the black mask between the face and the lit area of the screen. */
internal val ScreenSurroundInset = 7.dp

/**
 * Grey gradient title bar: play state at the left, centred title, battery at the right.
 *
 * The title is centred within the whole bar rather than in the leftover space, so it does not drift
 * as the side elements change width.
 */
@Composable
internal fun ClassicTitleBar(
    title: String,
    isPlaying: Boolean,
    hasSong: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(TitleBarHeight)
                .background(Brush.verticalGradient(TitleBarGradient))
    ) {
        BasicText(
            title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 44.dp).align(Alignment.Center),
            style = ClassicTypography.title.copy(color = Color.Black, textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left cluster: brand mark, then the play indicator when there is a song. The
            // mark is tinted to the same ink as the rest of the bar's glyphs - the full
            // five-colour palette at 9dp reads as a smudge of noise beside a monochrome
            // battery, and the bar is meant to be the quietest thing on screen.
            UnrulyMark(height = 9.dp, tint = Color(0xFF3C3C3C))
            if (hasSong) {
                Box(modifier = Modifier.width(6.dp))
                PlayStateGlyph(isPlaying = isPlaying)
            }
            Box(modifier = Modifier.weight(1f))
            BatteryGlyph()
        }
        // Hairline under the bar, as on the originals.
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.BottomCenter)
                    .background(HairlineGrey)
        )
    }
}

/** Glossy blue play triangle or pause bars, matching the originals' status indicator. */
@Composable
private fun PlayStateGlyph(isPlaying: Boolean) {
    Canvas(modifier = Modifier.width(10.dp).height(11.dp)) {
        val gloss = Brush.verticalGradient(*ProgressGradient, startY = 0f, endY = size.height)
        val edge = SelectionEdge
        if (isPlaying) {
            val path =
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(0f, size.height)
                    close()
                }
            drawPath(path, brush = gloss)
            drawPath(path, color = edge, style = Stroke(width = 0.8.dp.toPx()))
        } else {
            val barWidth = size.width * 0.36f
            listOf(0f, size.width - barWidth).forEach { left ->
                drawRect(brush = gloss, topLeft = Offset(left, 0f), size = Size(barWidth, size.height))
                drawRect(
                    color = edge,
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, size.height),
                    style = Stroke(width = 0.8.dp.toPx()),
                )
            }
        }
    }
}

/** Outlined battery with a nub and a glossy full charge, drawn rather than shipped as an asset. */
@Composable
private fun BatteryGlyph() {
    Canvas(modifier = Modifier.width(22.dp).height(11.dp)) {
        val nubWidth = size.width * 0.08f
        val bodyWidth = size.width - nubWidth
        val stroke = 1.dp.toPx()
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFD0D0D0))),
            size = Size(bodyWidth, size.height),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
        drawRoundRect(
            color = Color(0xFF4A4A4A),
            size = Size(bodyWidth, size.height),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(width = stroke),
        )
        drawRect(
            color = Color(0xFF4A4A4A),
            topLeft = Offset(bodyWidth, size.height * 0.3f),
            size = Size(nubWidth, size.height * 0.4f),
        )
        val inset = stroke * 2f
        val fill =
            Size(
                (bodyWidth - inset * 2f).coerceAtLeast(0f),
                (size.height - inset * 2f).coerceAtLeast(0f),
            )
        drawRoundRect(
            brush =
                Brush.verticalGradient(
                    0.00f to Color(0xFFB9F09A),
                    0.50f to Color(0xFF69C94A),
                    0.51f to Color(0xFF3FAF2A),
                    1.00f to Color(0xFF4CBF33),
                    startY = inset,
                    endY = inset + fill.height,
                ),
            topLeft = Offset(inset, inset),
            size = fill,
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }
}

/**
 * One menu row: white with bold black text, or the glossy blue gradient with white text when
 * selected.
 *
 * [showChevron] marks rows that descend into another screen, as the originals did - it is the only
 * affordance distinguishing "opens a submenu" from "does something". [isNowPlaying] adds the small
 * speaker that marks the playing track in a list.
 */
@Composable
internal fun ClassicMenuRow(
    label: String,
    isSelected: Boolean,
    showChevron: Boolean,
    modifier: Modifier = Modifier,
    isNowPlaying: Boolean = false,
) {
    val contentColor = if (isSelected) Color.White else Color.Black
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(LocalClassicRowHeight.current)
                .then(
                    if (isSelected) {
                        Modifier.background(Brush.verticalGradient(*SelectionGradient))
                            .drawWithContent {
                                drawContent()
                                // A darker line along the top and bottom edges seats the bar.
                                val line = 1.dp.toPx()
                                drawRect(SelectionEdge, size = Size(size.width, line))
                                drawRect(
                                    SelectionEdge,
                                    topLeft = Offset(0f, size.height - line),
                                    size = Size(size.width, line),
                                )
                            }
                    } else {
                        Modifier
                    }
                )
                .padding(start = 8.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            label,
            modifier = Modifier.weight(1f),
            style = MenuTextStyle.copy(color = contentColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isNowPlaying) {
            SpeakerGlyph(color = if (isSelected) Color.White else ClassicColors.accent)
        }
        if (showChevron) {
            Box(modifier = Modifier.width(if (isNowPlaying) 7.dp else 4.dp))
            ChevronGlyph(color = contentColor)
        }
    }
}

/** A drawn `>`: heavier and squarer than any font's guillemet, like the originals'. */
@Composable
private fun ChevronGlyph(color: Color) {
    Canvas(modifier = Modifier.width(7.dp).height(12.dp)) {
        val stroke = 2.2.dp.toPx()
        val path =
            Path().apply {
                moveTo(stroke / 2f, stroke / 2f)
                lineTo(size.width - stroke / 2f, size.height / 2f)
                lineTo(stroke / 2f, size.height - stroke / 2f)
            }
        drawPath(
            path,
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Square, join = StrokeJoin.Miter),
        )
    }
}

/** Small speaker with two sound arcs, marking the playing track. */
@Composable
private fun SpeakerGlyph(color: Color) {
    Canvas(modifier = Modifier.padding(start = 4.dp).width(15.dp).height(12.dp)) {
        val h = size.height
        val body =
            Path().apply {
                moveTo(0f, h * 0.34f)
                lineTo(h * 0.26f, h * 0.34f)
                lineTo(h * 0.58f, 0f)
                lineTo(h * 0.58f, h)
                lineTo(h * 0.26f, h * 0.66f)
                lineTo(0f, h * 0.66f)
                close()
            }
        drawPath(body, color = color)
        val stroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
        listOf(0.30f, 0.52f).forEach { r ->
            val radius = h * r
            drawArc(
                color = color,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(h * 0.62f - radius, h / 2f - radius),
                size = Size(radius * 2f, radius * 2f),
                style = stroke,
            )
        }
    }
}

/**
 * Menu on the left, artwork panel on the right. The menu casts a soft shadow onto the panel, as on
 * the originals - without it the two halves read as adjacent boxes rather than layered sheets.
 *
 * The split is used on the top-level menus only; deeper lists run the full width.
 */
@Composable
internal fun ClassicSplitLayout(
    modifier: Modifier = Modifier,
    menuWeight: Float = 0.56f,
    panel: @Composable BoxScope.() -> Unit,
    menu: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier = modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.weight(menuWeight).fillMaxHeight(), content = menu)
        Box(modifier = Modifier.weight(1f - menuWeight).fillMaxHeight().clipToBounds()) {
            panel()
            Box(
                modifier =
                    Modifier.width(7.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                            )
                        )
            )
        }
    }
}

/** How long each cover holds in the panel slideshow. */
private const val SLIDE_MS = 7_000

/**
 * The artwork panel beside the menu.
 *
 * Shows [featured] when given (the playing track, while "Now Playing" is highlighted); otherwise a
 * slow pan-and-zoom slideshow through [slideshow], crossfading between covers. With neither, a
 * plain placeholder with a note, so the split never collapses.
 */
@Composable
internal fun ClassicArtPanel(
    featured: Uri?,
    slideshow: List<Uri>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(PanelGrey).clipToBounds()) {
        when {
            featured != null -> Crossfade(featured, animationSpec = tween(500)) { KenBurnsArt(it) }
            slideshow.isNotEmpty() -> {
                var index by remember(slideshow) { mutableIntStateOf(0) }
                LaunchedEffect(slideshow) {
                    while (true) {
                        delay(SLIDE_MS.toLong())
                        index = (index + 1) % slideshow.size
                    }
                }
                Crossfade(slideshow[index % slideshow.size], animationSpec = tween(900)) {
                    KenBurnsArt(it)
                }
            }
            else -> ArtPlaceholder()
        }
    }
}

/**
 * One cover, zoomed in and drifting diagonally. The drift reverses rather than restarting so a
 * cover held for longer than one pass never jumps.
 */
@Composable
private fun KenBurnsArt(uri: Uri) {
    val transition = rememberInfiniteTransition(label = "ken-burns")
    val t by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    tween(durationMillis = SLIDE_MS + 2_000, easing = LinearEasing),
                    RepeatMode.Reverse,
                ),
            label = "ken-burns-drift",
        )
    SkinAlbumArt(
        uri = uri,
        contentDescription = null,
        modifier =
            Modifier.fillMaxSize().graphicsLayer {
                val scale = 1.12f + 0.12f * t
                scaleX = scale
                scaleY = scale
                translationX = (t - 0.5f) * size.width * 0.10f
                translationY = (0.5f - t) * size.height * 0.05f
            },
        contentScale = ContentScale.Crop,
        fallback = { ArtPlaceholder() },
    )
}

/** Light grey square with a faint note: the originals' stand-in for missing artwork. */
@Composable
private fun ArtPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFFF4F4F4), Color(0xFFCFCFCF)))),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            "♫",
            style = TextStyle(fontSize = 54.sp, color = Color(0xFFB4B4B4), textAlign = TextAlign.Center),
        )
    }
}

/**
 * Cover art with the originals' mirrored reflection beneath it, fading out over [reflection] of the
 * cover's height.
 */
@Composable
internal fun ClassicReflectedArt(
    uri: Uri?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    reflection: Float = 0.28f,
) {
    Column(modifier = modifier) {
        SkinAlbumArt(
            uri = uri,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentScale = ContentScale.Crop,
            fallback = { ArtPlaceholder(Modifier.aspectRatio(1f)) },
        )
        Box(
            modifier =
                Modifier.padding(top = 1.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f / reflection)
                    .clipToBounds()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush =
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent)
                                ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
        ) {
            // The whole cover, flipped about its own centre, so its bottom edge meets the
            // original's; the box above shows only the top slice of it.
            SkinAlbumArt(
                uri = uri,
                contentDescription = null,
                modifier =
                    Modifier.fillMaxWidth()
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .aspectRatio(1f)
                        .graphicsLayer { scaleY = -1f },
                contentScale = ContentScale.Crop,
                fallback = {
                    ArtPlaceholder(
                        Modifier.wrapContentHeight(Alignment.Top, unbounded = true)
                            .aspectRatio(1f)
                            .graphicsLayer { scaleY = -1f }
                    )
                },
            )
        }
    }
}

/**
 * The scrollbar beside a full-width list: a pale track between hairlines with a blue thumb.
 *
 * [first] is the index of the top visible row, fractional while a row is part-scrolled; [visible]
 * how many rows fit; [total] the list length.
 */
@Composable
internal fun ClassicScrollbar(
    first: Float,
    visible: Float,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.width(9.dp).fillMaxHeight()) {
        val line = 1.dp.toPx()
        drawRect(Brush.horizontalGradient(TrackGradient))
        drawRect(HairlineGrey, size = Size(line, size.height))
        if (total <= 0) return@Canvas
        val minThumb = 12.dp.toPx()
        val thumbHeight = (size.height * visible / total).coerceIn(minThumb, size.height)
        val travel = size.height - thumbHeight
        val maxFirst = (total - visible).coerceAtLeast(1f)
        val top = travel * (first / maxFirst).coerceIn(0f, 1f)
        val inset = line * 2f
        drawRoundRect(
            brush = Brush.horizontalGradient(ScrollThumbGradient, startX = inset, endX = size.width),
            topLeft = Offset(inset, top + line),
            size = Size(size.width - inset - line, thumbHeight - line * 2f),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
        )
    }
}

/**
 * The progress bar: an inset grey track with a glossy blue fill.
 *
 * [fraction] is expected already clamped to `0f..1f` by [progressFraction]. While [scrubbing], a
 * diamond marks the target, as on the originals.
 */
@Composable
internal fun ClassicProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    scrubbing: Boolean = false,
) {
    val barHeight: Dp = 10.dp
    Canvas(modifier = modifier.fillMaxWidth().height(if (scrubbing) 14.dp else barHeight)) {
        val bar = barHeight.toPx()
        val top = (size.height - bar) / 2f
        val barSize = Size(size.width, bar)
        val radius = CornerRadius(2.dp.toPx())
        drawRoundRect(
            brush = Brush.verticalGradient(TrackGradient, startY = top, endY = top + bar),
            topLeft = Offset(0f, top),
            size = barSize,
            cornerRadius = radius,
        )
        val filled = size.width * fraction.coerceIn(0f, 1f)
        if (filled > 0f) {
            drawRoundRect(
                brush = Brush.verticalGradient(*ProgressGradient, startY = top, endY = top + bar),
                topLeft = Offset(0f, top),
                size = Size(filled, bar),
                cornerRadius = radius,
            )
        }
        drawRoundRect(
            color = HairlineGrey,
            topLeft = Offset(0f, top),
            size = barSize,
            cornerRadius = radius,
            style = Stroke(width = 1.dp.toPx()),
        )
        if (scrubbing) {
            val half = size.height / 2f
            val x = filled.coerceIn(half, size.width - half)
            val diamond =
                Path().apply {
                    moveTo(x, 0f)
                    lineTo(x + half, half)
                    lineTo(x, size.height)
                    lineTo(x - half, half)
                    close()
                }
            drawPath(diamond, brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFBDBDBD))))
            drawPath(diamond, color = Color(0xFF3C3C3C), style = Stroke(width = 1.dp.toPx()))
        }
    }
}

/** Placeholder shown in the menu column when a list has nothing in it. */
@Composable
internal fun ClassicEmptyLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier = modifier.padding(8.dp),
        style = ClassicTypography.body.copy(color = Color(0xFF5A5A5A)),
    )
}

/** `n of m` in the originals' small grey caption style. */
@Composable
internal fun ClassicCaption(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier = modifier,
        style = ClassicTypography.label.copy(color = Color(0xFF5A5A5A), fontWeight = FontWeight.Bold),
    )
}

/** Row of elapsed (left) and remaining (right) times under the progress bar. */
@Composable
internal fun ClassicTimeRow(
    elapsed: String,
    remaining: String,
    elapsedColor: Color,
    modifier: Modifier = Modifier,
) {
    val style = ClassicTypography.label.copy(fontWeight = FontWeight.Bold)
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        BasicText(elapsed, style = style.copy(color = elapsedColor))
        BasicText(remaining, style = style.copy(color = Color(0xFF5A5A5A)))
    }
}
