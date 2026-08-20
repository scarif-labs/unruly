/*
 * Copyright (c) 2026 Shellac Project
 * ZuneTheme.kt is part of Shellac.
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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shellac.skin.SkinSong
import org.shellac.skin.support.ShellacMark
import org.shellac.skin.support.SkinColors
import org.shellac.skin.support.SkinTheme
import org.shellac.skin.support.SkinTypography

internal val SkinPaletteColors =
    SkinColors(
        background = Color(0xFF050505),
        surface = Color(0xFF171717),
        onBackground = Color(0xFFF5F5F5),
        onSurface = Color(0xFF888888),
        accent = Color(0xFFF0640A),
        onAccent = Color(0xFF050505),
    )

internal val SkinPaletteTypography =
    SkinTypography(
        title =
            TextStyle(
                color = SkinPaletteColors.onBackground,
                fontSize = 52.sp,
                lineHeight = 50.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-2.2).sp,
            ),
        body =
            TextStyle(
                color = SkinPaletteColors.onBackground,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Light,
            ),
        label =
            TextStyle(
                color = SkinPaletteColors.onSurface,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.4.sp,
            ),
    )

@Composable
internal fun BlackFrame(
    title: String,
    modifier: Modifier = Modifier,
    utility: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    SkinTheme(
        colors = SkinPaletteColors,
        typography = SkinPaletteTypography,
        darkSystemBars = true,
        content = {
            Column(
                modifier =
                    modifier
                        .fillMaxSize()
                        .background(SkinPaletteColors.background)
                        .padding(start = 28.dp, end = 20.dp, top = 24.dp, bottom = 18.dp)
            ) {
                // The heading gets the full width and a line of its own.
                //
                // It used to share a Row with the utility links, with weight(1f) - so the
                // links took their space first and the heading was squeezed into what was
                // left, clipping "music" to "mus" and cutting the g off "now playing". Short
                // headings should simply fit; the deliberate bleed is for genuinely long
                // ones running off the screen edge, not for a four-letter word losing a
                // fight with a row of links.
                // Brand mark above the heading, at the small end of legible. It sits on its
                // own line rather than beside the heading because the heading deliberately
                // takes the full width and bleeds off the edge when long - anything sharing
                // that row gets run over.
                ShellacMark(height = 10.dp, modifier = Modifier.padding(bottom = 6.dp))
                BasicText(
                    text = title,
                    style = SkinTheme.typography.title,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                if (utility != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        utility()
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                content()
            }
        },
    )
}

@Composable
internal fun AccentLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier =
            modifier.clickable(onClick = onClick).padding(horizontal = 7.dp, vertical = 5.dp),
        style = SkinTheme.typography.label.copy(color = SkinTheme.colors.accent),
    )
}

/**
 * An [AccentLink] with a guaranteed 48dp touch target.
 *
 * Kept separate from [AccentLink] rather than folded into it: the library screens stack these links
 * directly under the heading, where forcing every one to 48dp would push the content down by half a
 * heading's worth of empty black. This variant is for the links that float over album art, where
 * they are hit blind and the padded-text hit box is not big enough.
 */
@Composable
internal fun AccentLinkTarget(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = text,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val description = contentDescription
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .semantics(mergeDescendants = true) {
                    this.contentDescription = description
                    role = Role.Button
                }
                .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style =
                SkinTheme.typography.label.copy(
                    // Flat alpha drop instead of a ripple - see the note in ZuneIcons.kt.
                    color = SkinTheme.colors.accent.copy(alpha = if (pressed) 0.5f else 1f)
                ),
        )
    }
}

/**
 * A dark rounded capsule to sit behind controls that overlay album art.
 *
 * The now-playing scrim is fully transparent at the top by design - it is shaped for the text block
 * at the bottom - so anything in the top corner has no protection at all, and orange-on-pale-cover
 * disappears entirely. Widening the scrim to cover the top would dim the artwork across the whole
 * screen to fix three links, so the links carry their own background instead.
 */
@Composable
internal fun UtilityIsland(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .background(color = Color(0xD9000000), shape = RoundedCornerShape(26.dp))
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun SectionLabel(text: String) {
    BasicText(
        text = text,
        style = SkinTheme.typography.label.copy(color = SkinTheme.colors.accent),
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
internal fun LibraryRow(
    title: String,
    subtitle: String = "",
    onClick: (() -> Unit)? = null,
    trailing: String? = null,
) {
    val rowModifier =
        Modifier.fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(vertical = 10.dp)
    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(text = title, style = SkinTheme.typography.body)
            if (subtitle.isNotEmpty()) {
                BasicText(
                    text = subtitle,
                    style = SkinTheme.typography.label,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            BasicText(
                text = trailing,
                style = SkinTheme.typography.label.copy(color = SkinTheme.colors.accent),
            )
        }
    }
}

@Composable
internal fun SongRow(
    song: SkinSong,
    index: Int? = null,
    onClick: () -> Unit,
    trailing: String? = null,
) {
    val prefix = if (index == null) "" else "${index + 1}. "
    LibraryRow(
        title = "$prefix${song.title}",
        subtitle = "${song.artist}  ·  ${song.album}",
        onClick = onClick,
        trailing = trailing ?: formatDuration(song.durationMs),
    )
}

@Composable
internal fun EmptyMessage(text: String) {
    BasicText(
        text = text,
        style = SkinTheme.typography.body.copy(color = SkinTheme.colors.onSurface),
        modifier = Modifier.padding(vertical = 20.dp),
    )
}

/** "1 song" / "2 songs" - counts are user-visible, so they should read correctly. */
internal fun plural(count: Int, noun: String): String =
    if (count == 1) "1 $noun" else "$count ${noun}s"

internal fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000L).coerceAtLeast(0L)
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}

internal fun formatSignedDuration(durationMs: Long): String {
    val prefix = if (durationMs < 0L) "-" else ""
    return prefix + formatDuration(kotlin.math.abs(durationMs))
}
