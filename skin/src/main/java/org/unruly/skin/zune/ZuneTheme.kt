/*
 * Copyright (c) 2026 Unruly Project
 * ZuneTheme.kt is part of Unruly.
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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.unruly.skin.R
import org.unruly.skin.support.SkinColors
import org.unruly.skin.support.SkinTheme
import org.unruly.skin.support.SkinTypography

/*
 * The Zune HD's visual language, measured off a walkthrough of the hardware:
 *
 * - Black ground, white type, and one secondary colour: a pale lavender used for inactive pivots,
 *   sub-labels and metadata. There is no accent colour in the usual sense.
 * - Two voices of the same face. Words you navigate by (menu items, headings, song titles) are
 *   light, mixed or lower case. Names in lists and the now-playing artist are bold upper case.
 * - Page headings far too big for the screen, cropped by the top edge so only their lower half
 *   shows, filled with a lavender-to-aqua gradient.
 *
 * The face is Selawik, Microsoft's open-source metric match for Segoe UI, the family the hardware's
 * own typeface belonged to (OFL; see skin/LICENSE-Selawik-OFL.txt).
 */

internal val Selawik =
    FontFamily(
        Font(R.font.selawik_light, FontWeight.Light),
        Font(R.font.selawik_semilight, FontWeight.Normal),
        Font(R.font.selawik_regular, FontWeight.Medium),
        Font(R.font.selawik_semibold, FontWeight.SemiBold),
        Font(R.font.selawik_bold, FontWeight.Bold),
    )

/** Inactive pivots, sub-labels, metadata. */
internal val ZuneLavender = Color(0xFFB4B2E4)

/** Quieter still: track lists under album titles, the upcoming songs on now playing. */
internal val ZuneDim = Color(0xFF7C7A99)

/** The fill of the giant page headings. */
internal val HeadingBrush =
    Brush.horizontalGradient(listOf(Color(0xFFC3B6F4), Color(0xFFA9C8F2), Color(0xFF94E2E6)))

internal val SkinPaletteColors =
    SkinColors(
        background = Color(0xFF000000),
        surface = Color(0xFF1E1E22),
        onBackground = Color(0xFFFFFFFF),
        onSurface = ZuneLavender,
        accent = Color(0xFF94E2E6),
        onAccent = Color(0xFF000000),
    )

internal val SkinPaletteTypography =
    SkinTypography(
        title =
            TextStyle(
                fontFamily = Selawik,
                color = SkinPaletteColors.onBackground,
                fontSize = 44.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Light,
            ),
        body =
            TextStyle(
                fontFamily = Selawik,
                color = SkinPaletteColors.onBackground,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Medium,
            ),
        label =
            TextStyle(
                fontFamily = Selawik,
                color = ZuneLavender,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
    )

/** Bold upper-case names: artists, albums, the now-playing artist. Upper-case the text yourself. */
internal val CapsBold =
    TextStyle(
        fontFamily = Selawik,
        color = Color.White,
        fontSize = 19.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Bold,
    )

/** Small upper-case metadata under a name. */
internal val CapsSmall =
    TextStyle(
        fontFamily = Selawik,
        color = ZuneLavender,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
    )

/** Left margin shared by every page, so names, rings and headings hang off one line. */
internal val PageMargin = 14.dp

/** Black, full-screen, in the skin's palette - the ground every screen stands on. */
@Composable
internal fun ZuneSurface(content: @Composable () -> Unit) {
    SkinTheme(
        colors = SkinPaletteColors,
        typography = SkinPaletteTypography,
        darkSystemBars = true,
        content = {
            // Clipped: the giant headings deliberately overhang the top edge, and without a clip
            // they draw up into the system status bar.
            Box(
                modifier =
                    Modifier.fillMaxSize().clipToBounds().background(SkinPaletteColors.background)
            ) {
                content()
            }
        },
    )
}

/**
 * A page heading set far bigger than the screen and pushed up so the top edge crops it: only its
 * lower part shows, as on the hardware. [shift] slides it sideways (the hub's parallax).
 */
@Composable
internal fun GiantHeading(text: String, modifier: Modifier = Modifier, shift: Dp = 0.dp) {
    BasicText(
        text = text,
        modifier =
            modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .offset(x = PageMargin - 12.dp + shift, y = (-102).dp),
        style =
            TextStyle(
                fontFamily = Selawik,
                fontWeight = FontWeight.Light,
                fontSize = 172.sp,
                lineHeight = 172.sp,
                brush = HeadingBrush,
            ),
        maxLines = 1,
        softWrap = false,
    )
}

/** Height the cropped heading leaves visible, for whatever sits beneath it. */
internal val GiantHeadingVisible = 66.dp

/**
 * A titled page: the cropped giant heading, then the content. Back is the system gesture, as the
 * hardware's back key was.
 */
@Composable
internal fun BlackFrame(
    title: String,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") utility: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    ZuneSurface {
        Box(modifier = modifier.fillMaxSize()) {
            GiantHeading(title)
            // A Column, not a Box: screens hand in several stacked children (a title, then a
            // notice; a search field, then results), and a Box draws them on top of one another.
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(start = PageMargin, end = PageMargin, top = GiantHeadingVisible + 18.dp)
            ) {
                content()
            }
        }
    }
}

/** Lavender lower-case link text, for the odd action a notice needs. */
@Composable
internal fun AccentLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier.pressable(onClick).padding(vertical = 8.dp),
        style = SkinTheme.typography.body.copy(color = ZuneLavender),
    )
}

/** Clickable with the skin's press feedback: a flat dim, never a ripple. */
@Composable
internal fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    return this.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        .then(if (pressed) Modifier.background(Color(0x22FFFFFF)) else Modifier)
}

@Composable
internal fun SectionLabel(text: String) {
    BasicText(
        text = text.uppercase(),
        style = CapsSmall,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

/**
 * A song row, as in the hardware's queue and song lists: the title in semibold mixed case, artist
 * and album in small lavender capitals beneath, the duration in the gutter when [showTime].
 */
@Composable
internal fun SongRow(
    song: org.unruly.skin.SkinSong,
    onClick: () -> Unit,
    isCurrent: Boolean = false,
    showTime: Boolean = false,
    timeVisible: Boolean = showTime,
) {
    Box(modifier = Modifier.fillMaxWidth().pressable(onClick).padding(vertical = 9.dp)) {
        if (showTime && timeVisible) {
            BasicText(
                text = formatDuration(song.durationMs),
                style = SkinTheme.typography.label.copy(color = Color(0xFFBDBDBD), fontSize = 15.sp),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Column(modifier = Modifier.padding(start = if (showTime) 48.dp else 0.dp)) {
            BasicText(
                text = song.title,
                style =
                    SkinTheme.typography.body.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCurrent) SkinPaletteColors.accent else Color.White,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
            )
            BasicText(
                text = "${displayArtist(song.artist).uppercase()}    ${song.album.uppercase()}",
                style = CapsSmall,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
            )
        }
    }
}

@Composable
internal fun EmptyMessage(text: String) {
    BasicText(
        text = text,
        style = SkinTheme.typography.body.copy(color = ZuneLavender, fontWeight = FontWeight.Light),
        modifier = Modifier.padding(vertical = 20.dp),
    )
}

/** Untagged files come through with a blank artist; a blank row is not a usable row. */
internal fun displayArtist(name: String): String = name.ifBlank { "unknown artist" }

/** "1 song" / "2 songs" - counts are user-visible, so they should read correctly. */
internal fun plural(count: Int, noun: String): String =
    if (count == 1) "1 $noun" else "$count ${noun}s"

internal fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}

internal fun formatSignedDuration(durationMs: Long): String {
    val prefix = if (durationMs < 0L) "-" else ""
    return prefix + formatDuration(kotlin.math.abs(durationMs))
}
