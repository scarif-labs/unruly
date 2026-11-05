/*
 * Copyright (c) 2026 Unruly Project
 * GlossyTheme.kt is part of Unruly.
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

package org.unruly.skin.glossy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import org.unruly.skin.R

/*
 * The skeuomorphic phone music app of 2012, measured off a screen recording of it. Sizes are in the
 * original's points - its screen was 320 points wide - and scaled so 320 points fill this screen's
 * width, which keeps every proportion. Colours were sampled from the recording.
 *
 * The face is TeX Gyre Heros, a free clone of the Helvetica the original used (GUST Font License;
 * see skin/LICENSE-TeXGyreHeros-GUST.txt).
 */

internal val Heros =
    FontFamily(Font(R.font.heros_regular, FontWeight.Normal), Font(R.font.heros_bold, FontWeight.Bold))

/** Dp per original point: the screen width over 320. Provided at the skin's root. */
internal val LocalPt = staticCompositionLocalOf { 1.dp }

/**
 * Room a row keeps clear at its right edge for the A-Z strip, as the original's cells pulled their
 * accessories in when a list was indexed. Zero outside indexed lists.
 */
internal val LocalIndexInset = staticCompositionLocalOf { 0.dp }

/** The A-Z strip's width in points. */
internal const val INDEX_STRIP_PT = 16

@Composable internal fun pt(v: Float): Dp = LocalPt.current * v

@Composable internal fun pt(v: Int): Dp = LocalPt.current * v

@Composable
internal fun ptSp(v: Float): TextUnit = with(LocalDensity.current) { (LocalPt.current * v).toSp() }

internal object Glossy {
    val ListBg = Color(0xFFE9E8EB)
    val RowText = Color(0xFF313033)
    val RowSub = Color(0xFF878789)
    val SepDark = Color(0xFFCFCED1)
    val SepLight = Color(0xFFF6F5F8)
    val NavTop = Color(0xFFF7F6F9)
    val NavBottom = Color(0xFFDCD9DA)
    val NavBorder = Color(0xFF8C8B8D)
    val NavTitle = Color(0xFF3D3D3D)
    val HeaderTop = Color(0xFFCECDD0)
    val HeaderBottom = Color(0xFFBEBEC0)
    val HeaderText = Color(0xFF4E4E50)
    val Index = Color(0xFF79797B)
    val SelectTop = Color(0xFF058CF5)
    val SelectBottom = Color(0xFF015EE6)
    val TabTop = Color(0xFFF3F3F3)
    val TabBottom = Color(0xFFD3D3D3)
    val TabSelTop = Color(0xFFC4C4C4)
    val TabSelBottom = Color(0xFFA9A9A9)
    val TabIcon = Color(0xFF7B7B7D)
    val TabIconSel = Color(0xFF3A3A3A)
    val TabLabel = Color(0xFF4A4A4A)

    val NpNavTop = Color(0xFF242424)
    val NpNavBottom = Color(0xFF070707)
    val NpBand = Color(0xA6000000)
    val NpTrack = Color(0xFF4C4C4C)
    val NpBarTop = Color(0xFF262321)
    val NpBarBottom = Color(0xFF151515)
    val NpArtist = Color(0xFF9A9A9A)
    val NpAlbum = Color(0xFFA6A6A6)
    val Orange = Color(0xFFF28C1B)
    val TrackRowTop = Color(0xFF4D4D4D)
    val TrackRowBottom = Color(0xFF363636)
}

/** Bold text with the original's one-point white emboss beneath it. */
@Composable
internal fun embossed(size: Float, color: Color = Glossy.NavTitle, weight: FontWeight = FontWeight.Bold): TextStyle {
    val shadowPx = with(LocalDensity.current) { pt(1f).toPx() }
    return TextStyle(
        fontFamily = Heros,
        fontWeight = weight,
        fontSize = ptSp(size),
        color = color,
        shadow = Shadow(Color.White.copy(alpha = 0.85f), Offset(0f, shadowPx), 0f),
    )
}

/** Plain text in the face, sized in points. */
@Composable
internal fun heros(size: Float, color: Color, bold: Boolean = true): TextStyle =
    TextStyle(
        fontFamily = Heros,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = ptSp(size),
        color = color,
    )

/** A clickable whose only feedback is the [pressed] flag handed to [content]. */
@Composable
internal fun Modifier.pressAware(onClick: () -> Unit, content: (Boolean) -> Unit = {}): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    content(pressed)
    return this.clickable(interactionSource = source, indication = null, onClick = onClick)
}

// ---------------------------------------------------------------------------------------------
// Navigation bar
// ---------------------------------------------------------------------------------------------

/** The silver navigation bar: gradient, title embossed in the middle, buttons either side. */
@Composable
internal fun NavBar(
    title: String,
    left: (@Composable () -> Unit)? = null,
    right: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(pt(44))
                .background(Brush.verticalGradient(listOf(Glossy.NavTop, Glossy.NavBottom)))
                .drawBehind {
                    drawLine(Color.White, Offset(0f, 0f), Offset(size.width, 0f), 1f)
                    drawLine(Glossy.NavBorder, Offset(0f, size.height - 1), Offset(size.width, size.height - 1), 2f)
                }
    ) {
        BasicText(
            title,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = pt(80)),
            style = embossed(20f).copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (left != null) Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = pt(5))) { left() }
        if (right != null) Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = pt(5))) { right() }
    }
}

/** Which way a bar button's edge points. */
internal enum class Point {
    NONE,
    LEFT,
    RIGHT,
}

/**
 * The bar button: a bevelled pill, square-ended or arrow-ended. [dark] is the black glossy style of
 * the Now Playing button and the player's bars; otherwise the light grey of the library.
 */
@Composable
internal fun BarButton(
    text: String,
    onClick: () -> Unit,
    point: Point = Point.NONE,
    dark: Boolean = false,
    twoLine: Boolean = false,
    textSize: Float = 12f,
) {
    var pressed = false
    val modifier = Modifier.pressAware(onClick) { pressed = it }
    val shape = barButtonShape(point)
    val fill =
        if (dark) {
            listOf(Color(0xFF8A8A8A), Color(0xFF666666), Color(0xFF4F4F4F), Color(0xFF3E3E3E))
        } else if (pressed) {
            listOf(Color(0xFFB9B9B9), Color(0xFFCACACA))
        } else {
            listOf(Color(0xFFF7F7F7), Color(0xFFE6E6E6), Color(0xFFD5D5D5))
        }
    val textStyle =
        if (dark) {
            heros(if (twoLine) 11f else 12f, Color.White).copy(
                shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, -1f), 0f),
                textAlign = TextAlign.Center,
                lineHeight = ptSp(11f),
            )
        } else {
            embossed(textSize, Color(0xFF555555)).copy(textAlign = TextAlign.Center)
        }
    Box(
        modifier =
            modifier
                .height(pt(30))
                .clip(shape)
                .background(Brush.verticalGradient(fill))
                .border(1.dp, if (dark) Color(0xFF2A2A2A) else Color(0xFF8E8E8E), shape)
                .padding(
                    start = pt(if (point == Point.LEFT) 15 else 8),
                    end = pt(if (point == Point.RIGHT) 15 else 8),
                ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = textStyle, maxLines = 2)
    }
}

/**
 * A rounded rectangle, with one end drawn to a point for back and forward buttons. The button is
 * always 30 points tall, so its height gives pixels per point for the radius and the tip.
 */
private fun barButtonShape(point: Point): androidx.compose.ui.graphics.Shape =
    androidx.compose.foundation.shape.GenericShape { size, _ ->
        val perPt = size.height / 30f
        val rad = 5f * perPt
        val tip = 11f * perPt
        when (point) {
            Point.NONE ->
                addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(rad)))
            Point.LEFT -> {
                moveTo(tip, 0f)
                lineTo(size.width - rad, 0f)
                quadraticTo(size.width, 0f, size.width, rad)
                lineTo(size.width, size.height - rad)
                quadraticTo(size.width, size.height, size.width - rad, size.height)
                lineTo(tip, size.height)
                lineTo(0f, size.height / 2f)
                close()
            }
            Point.RIGHT -> {
                moveTo(rad, 0f)
                lineTo(size.width - tip, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(size.width - tip, size.height)
                lineTo(rad, size.height)
                quadraticTo(0f, size.height, 0f, size.height - rad)
                lineTo(0f, rad)
                quadraticTo(0f, 0f, rad, 0f)
                close()
            }
        }
    }

// ---------------------------------------------------------------------------------------------
// List pieces
// ---------------------------------------------------------------------------------------------

/**
 * A 44-point list row: the light grey cell with its etched separator, blue gradient while pressed,
 * an optional leading cover, a bold title and optional grey subtitle, and a chevron or trailing text.
 */
@Composable
internal fun ListRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    chevron: Boolean = true,
    trailing: String? = null,
    leading: (@Composable BoxScope.() -> Unit)? = null,
    height: Float = 44f,
    titleSize: Float = 20f,
    trailingIcon: (@Composable () -> Unit)? = null,
    dimmed: Boolean = false,
) {
    var pressed = false
    val modifier = Modifier.pressAware(onClick) { pressed = it }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(pt(height))
                .then(
                    if (pressed) Modifier.background(Brush.verticalGradient(listOf(Glossy.SelectTop, Glossy.SelectBottom)))
                    else Modifier.background(Glossy.ListBg)
                )
                .drawBehind {
                    drawLine(Glossy.SepLight, Offset(0f, 0f), Offset(size.width, 0f), 2f)
                    drawLine(Glossy.SepDark, Offset(0f, size.height - 1), Offset(size.width, size.height - 1), 2f)
                }
                .padding(end = LocalIndexInset.current),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(modifier = Modifier.size(pt(height - 1)), content = leading)
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = pt(if (leading != null) 9 else 10), end = pt(8)),
            verticalArrangement = Arrangement.Center,
        ) {
            BasicText(
                title,
                style = heros(titleSize, if (pressed) Color.White else if (dimmed) Glossy.RowSub else Glossy.RowText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                BasicText(
                    subtitle,
                    style = heros(14f, if (pressed) Color.White else Glossy.RowSub, bold = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            BasicText(trailing, style = heros(15f, if (pressed) Color.White else Glossy.RowText), modifier = Modifier.padding(end = pt(10)))
        }
        if (trailingIcon != null) Box(modifier = Modifier.padding(end = pt(10))) { trailingIcon() }
        if (chevron) Chevron(if (pressed) Color.White else Color(0xFF8E8E90))
    }
}

/** The grey ">" at the end of a row that opens a page. */
@Composable
internal fun Chevron(color: Color) {
    Canvas(modifier = Modifier.padding(end = pt(10)).size(pt(9), pt(13))) {
        val stroke = size.width * 0.33f
        val path =
            Path().apply {
                moveTo(stroke / 2, stroke / 2)
                lineTo(size.width - stroke / 2, size.height / 2)
                lineTo(stroke / 2, size.height - stroke / 2)
            }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Square))
    }
}

/** A 22-point section bar: grey gradient, letter embossed at the left. */
@Composable
internal fun SectionHeader(text: String) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(pt(23))
                .background(Brush.verticalGradient(listOf(Glossy.HeaderTop, Glossy.HeaderBottom)))
                .drawBehind {
                    drawLine(Color(0xFFDCDCDE), Offset(0f, 0f), Offset(size.width, 0f), 2f)
                    drawLine(Color(0xFFADADAF), Offset(0f, size.height - 1), Offset(size.width, size.height - 1), 2f)
                }
                .padding(start = pt(12)),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(text, style = embossed(17f, Glossy.HeaderText))
    }
}

/** The search bar at the head of a list: grey gradient with a rounded field inside. */
@Composable
internal fun SearchBar(query: String, onQuery: (String) -> Unit) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(pt(44))
                .background(Brush.verticalGradient(listOf(Color(0xFFDBDBDD), Color(0xFFBEBEC0))))
                .drawBehind {
                    drawLine(Color(0xFF9D9D9F), Offset(0f, size.height - 1), Offset(size.width, size.height - 1), 2f)
                }
                .padding(horizontal = pt(6), vertical = pt(7)),
    ) {
        val shape = RoundedCornerShape(50)
        Row(
            modifier =
                Modifier.fillMaxSize()
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFDADADC), Color(0xFFF2F2F4))))
                    .border(1.dp, Color(0xFF8D8D8F), shape)
                    .padding(horizontal = pt(9)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(modifier = Modifier.size(pt(13))) {
                val r = size.width * 0.34f
                drawCircle(Color(0xFF6D6D6F), r, Offset(r + 1, r + 1), style = Stroke(size.width * 0.13f))
                drawLine(Color(0xFF6D6D6F), Offset(r * 1.75f, r * 1.75f), Offset(size.width, size.height), size.width * 0.16f, cap = StrokeCap.Round)
            }
            Spacer(modifier = Modifier.width(pt(6)))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) BasicText("Search", style = heros(15f, Color(0xFF8E8E90), bold = false))
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = heros(15f, Color(0xFF222222), bold = false),
                    cursorBrush = SolidColor(Color(0xFF3478F6)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** A grey message in an otherwise empty list. */
@Composable
internal fun EmptyNote(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = pt(60)), contentAlignment = Alignment.Center) {
        BasicText(text, style = embossed(17f, Color(0xFF7C7C7E)))
    }
}

/** The A-Z strip down the right edge of an indexed list. */
@Composable
internal fun IndexStrip(letters: List<String>, onLetter: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxHeight().width(pt(INDEX_STRIP_PT)).padding(vertical = pt(6)),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { l ->
            BasicText(
                l,
                style = heros(10f, Glossy.Index),
                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onLetter(l) },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tab bar
// ---------------------------------------------------------------------------------------------

internal enum class Tab(val label: String) {
    PLAYLISTS("Playlists"),
    ARTISTS("Artists"),
    SONGS("Songs"),
    ALBUMS("Albums"),
    MORE("More"),
}

/** The 49-point tab bar: silver, the selected tab sunk into a darker inset. */
@Composable
internal fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(pt(49))
                .background(Brush.verticalGradient(listOf(Glossy.TabTop, Glossy.TabBottom)))
                .drawBehind {
                    drawLine(Color(0xFF7E7E80), Offset(0f, 0f), Offset(size.width, 0f), 2f)
                    drawLine(Color.White, Offset(0f, 2f), Offset(size.width, 2f), 1f)
                }
                .padding(horizontal = pt(2), vertical = pt(2)),
    ) {
        Tab.entries.forEach { tab ->
            val isSelected = tab == selected
            Column(
                modifier =
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (isSelected) {
                                Modifier.clip(RoundedCornerShape(pt(3)))
                                    .background(Brush.verticalGradient(listOf(Glossy.TabSelTop, Glossy.TabSelBottom)))
                                    .border(1.dp, Color(0x55000000), RoundedCornerShape(pt(3)))
                            } else {
                                Modifier
                            }
                        )
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            onSelect(tab)
                        },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Canvas(modifier = Modifier.size(pt(30), pt(26))) {
                    drawTabIcon(tab, if (isSelected) Glossy.TabIconSel else Glossy.TabIcon)
                }
                BasicText(tab.label, style = heros(10f, if (isSelected) Color(0xFF2E2E2E) else Glossy.TabLabel))
            }
        }
    }
}

/** The tab icons, drawn in one flat grey with a lighter top, as the original's were embossed. */
private fun DrawScope.drawTabIcon(tab: Tab, color: Color) {
    val w = size.width
    val h = size.height
    val brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.75f), color))
    when (tab) {
        Tab.PLAYLISTS -> {
            for (i in 0..3) drawRoundRect(brush, Offset(w * 0.12f, h * (0.18f + i * 0.17f)), Size(w * (if (i == 3) 0.4f else 0.62f), h * 0.09f), CornerRadius(2f))
            drawCircle(brush, h * 0.13f, Offset(w * 0.72f, h * 0.8f))
            drawRect(brush, Offset(w * 0.79f, h * 0.18f), Size(w * 0.06f, h * 0.62f))
            drawRect(brush, Offset(w * 0.79f, h * 0.18f), Size(w * 0.16f, h * 0.08f))
        }
        Tab.ARTISTS -> {
            drawCircle(brush, h * 0.2f, Offset(w * 0.55f, h * 0.3f))
            drawRoundRect(brush, Offset(w * 0.3f, h * 0.52f), Size(w * 0.52f, h * 0.48f), CornerRadius(h * 0.25f))
            drawLine(color, Offset(w * 0.12f, h * 0.2f), Offset(w * 0.25f, h * 0.98f), h * 0.07f)
            drawCircle(brush, h * 0.1f, Offset(w * 0.12f, h * 0.18f))
        }
        Tab.SONGS -> {
            drawCircle(brush, h * 0.16f, Offset(w * 0.36f, h * 0.78f))
            drawRect(brush, Offset(w * 0.45f, h * 0.08f), Size(w * 0.08f, h * 0.7f))
            drawPath(
                Path().apply {
                    moveTo(w * 0.53f, h * 0.08f)
                    quadraticTo(w * 0.78f, h * 0.25f, w * 0.68f, h * 0.48f)
                    quadraticTo(w * 0.7f, h * 0.28f, w * 0.53f, h * 0.24f)
                    close()
                },
                brush,
            )
        }
        Tab.ALBUMS -> {
            drawRoundRect(brush, Offset(w * 0.22f, h * 0.06f), Size(w * 0.58f, h * 0.88f), CornerRadius(2f))
            drawCircle(Color(0xFFE0E0E0), h * 0.12f, Offset(w * 0.47f, h * 0.66f))
            drawRect(Color(0xFFE0E0E0), Offset(w * 0.55f, h * 0.24f), Size(w * 0.05f, h * 0.42f))
        }
        Tab.MORE -> for (i in 0..2) drawCircle(brush, h * 0.11f, Offset(w * (0.22f + i * 0.28f), h * 0.5f))
    }
}

/** Square album art cell for list rows: the cover, or a grey note when there is none. */
@Composable
internal fun BoxScope.RowArt(uri: android.net.Uri?) {
    org.unruly.skin.support.SkinAlbumArt(
        uri = uri,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        fallback = {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFCFCFD1), Color(0xFFA9A9AB)))), contentAlignment = Alignment.Center) {
                BasicText("♪", style = heros(22f, Color(0xFF7A7A7C)))
            }
        },
    )
}

/** Provides [LocalPt] for a root that is [width] wide. */
@Composable
internal fun ProvidePoints(width: Dp, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPt provides width / 320f, content = content)
}
