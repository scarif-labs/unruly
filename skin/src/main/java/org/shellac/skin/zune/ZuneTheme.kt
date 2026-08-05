package org.shellac.skin.zune

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shellac.skin.SkinSong
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
        title = TextStyle(
            color = SkinPaletteColors.onBackground,
            fontSize = 52.sp,
            lineHeight = 50.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-2.2).sp,
        ),
        body = TextStyle(
            color = SkinPaletteColors.onBackground,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Light,
        ),
        label = TextStyle(
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
                modifier = modifier
                    .fillMaxSize()
                    .background(SkinPaletteColors.background)
                    .padding(start = 28.dp, end = 20.dp, top = 24.dp, bottom = 18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Oversized headings must CLIP at the right edge, never wrap. Wrapping
                    // turned "music" into "mu / sic" and "playlists" into "playli / sts".
                    // The bleed past the frame is the signature of this visual language;
                    // a wrapped heading just reads as a layout bug.
                    BasicText(
                        text = title,
                        style = SkinTheme.typography.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                    if (utility != null) {
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
internal fun AccentLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        modifier = modifier.clickable(onClick = onClick).padding(horizontal = 7.dp, vertical = 5.dp),
        style = SkinTheme.typography.label.copy(color = SkinTheme.colors.accent),
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
        Modifier
            .fillMaxWidth()
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
