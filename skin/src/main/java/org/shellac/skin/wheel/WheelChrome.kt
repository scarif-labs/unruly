package org.shellac.skin.wheel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shellac.skin.SkinSong
import org.shellac.skin.support.SkinAlbumArt
import org.shellac.skin.support.SkinColors
import org.shellac.skin.support.SkinTypography

/**
 * Chrome for the Touch Wheel skin, styled after the classic hard-drive music players of the
 * mid-2000s: a grey title bar, a white menu list with a blue gradient selection and chevrons,
 * and a split menu/artwork layout.
 *
 * Identifiers here are deliberately vendor-neutral ("Classic", not a brand name). The visual
 * language is a widely-copied convention; the trademarks around it are not, and keeping them
 * out of the source costs nothing.
 *
 * These are presentation primitives only - no state, no engine access.
 */

/** White background, black text, deep blue selection. */
internal val ClassicColors = SkinColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    accent = Color(0xFF2A5DA8),
    onAccent = Color(0xFFFFFFFF),
)

internal val ClassicTypography = SkinTypography(
    title = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
    body = TextStyle(fontSize = 15.sp),
    label = TextStyle(fontSize = 12.sp),
)

// Vertical gradients. The originals were subtle - a light top edge falling to a saturated
// base - which is what stops a flat fill from looking like a modern list highlight.
private val SelectionGradient = listOf(Color(0xFF8FB6EC), Color(0xFF2454A0))
private val TitleBarGradient = listOf(Color(0xFFF6F6F6), Color(0xFFC2C2C2))
private val ProgressGradient = listOf(Color(0xFF8FB6EC), Color(0xFF2454A0))

private val HairlineGrey = Color(0xFF8A8A8A)
private val TrackGrey = Color(0xFFE4E4E4)

internal val TitleBarHeight = 26.dp

/**
 * Grey gradient title bar: play state at the left, centred title, battery at the right.
 *
 * The title is centred within the whole bar rather than in the leftover space, so it does
 * not drift as the side elements change width.
 */
@Composable
internal fun ClassicTitleBar(
    title: String,
    isPlaying: Boolean,
    hasSong: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TitleBarHeight)
            .background(Brush.verticalGradient(TitleBarGradient)),
    ) {
        BasicText(
            title,
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            style = ClassicTypography.title.copy(
                color = Color.Black,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasSong) {
                PlayStateGlyph(isPlaying = isPlaying)
            }
            Box(modifier = Modifier.weight(1f))
            BatteryGlyph()
        }
        // Hairline under the bar, as on the originals.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.BottomCenter)
                .background(HairlineGrey),
        )
    }
}

/** Small blue play triangle or pause bars, matching the originals' status indicator. */
@Composable
private fun PlayStateGlyph(isPlaying: Boolean) {
    Canvas(modifier = Modifier.width(10.dp).height(10.dp)) {
        val blue = ProgressGradient.last()
        if (isPlaying) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path, color = blue)
        } else {
            val barWidth = size.width / 3f
            drawRect(color = blue, topLeft = Offset(0f, 0f), size = Size(barWidth, size.height))
            drawRect(
                color = blue,
                topLeft = Offset(size.width - barWidth, 0f),
                size = Size(barWidth, size.height),
            )
        }
    }
}

/** Outlined battery with a nub and a full charge, drawn rather than shipped as an asset. */
@Composable
private fun BatteryGlyph() {
    Canvas(modifier = Modifier.width(20.dp).height(10.dp)) {
        val nubWidth = size.width * 0.08f
        val bodyWidth = size.width - nubWidth
        val stroke = 1.dp.toPx()
        drawRoundRect(
            color = Color(0xFF3C3C3C),
            topLeft = Offset(0f, 0f),
            size = Size(bodyWidth, size.height),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
        )
        drawRect(
            color = Color(0xFF3C3C3C),
            topLeft = Offset(bodyWidth, size.height * 0.3f),
            size = Size(nubWidth, size.height * 0.4f),
        )
        val inset = stroke * 2f
        drawRoundRect(
            color = Color(0xFF5BA85B),
            topLeft = Offset(inset, inset),
            size = Size((bodyWidth - inset * 2f).coerceAtLeast(0f), (size.height - inset * 2f).coerceAtLeast(0f)),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }
}

/**
 * One menu row: white with black text, or the blue gradient with white text when selected.
 *
 * [showChevron] marks rows that descend into another screen, as the originals did - it is
 * the only affordance distinguishing "opens a submenu" from "does something".
 */
@Composable
internal fun ClassicMenuRow(
    label: String,
    isSelected: Boolean,
    showChevron: Boolean,
    modifier: Modifier = Modifier,
) {
    val background = if (isSelected) {
        Brush.verticalGradient(SelectionGradient)
    } else {
        Brush.verticalGradient(listOf(Color.White, Color.White))
    }
    val contentColor = if (isSelected) Color.White else Color.Black
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            label,
            modifier = Modifier.weight(1f),
            style = ClassicTypography.body.copy(color = contentColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showChevron) {
            BasicText(
                "›",
                style = ClassicTypography.title.copy(color = contentColor, fontSize = 17.sp),
            )
        }
    }
}

/**
 * Menu on the left, artwork panel on the right, divided by a hairline.
 *
 * The split is the signature of the later models. [menuWeight] is the menu's share; the
 * panel takes the rest.
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
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(HairlineGrey))
        Box(
            modifier = Modifier.weight(1f - menuWeight).fillMaxHeight(),
            content = panel,
        )
    }
}

/**
 * The artwork panel beside the menu. Shows the current track's cover, falling back to a flat
 * grey square so the split does not collapse when nothing is playing.
 */
@Composable
internal fun ClassicArtPanel(song: SkinSong?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Color(0xFFEDEDED)),
        contentAlignment = Alignment.Center,
    ) {
        if (song == null) {
            BasicText(
                "",
                style = ClassicTypography.label.copy(color = Color(0xFF9A9A9A)),
            )
        } else {
            SkinAlbumArt(
                uri = song.albumArtUri,
                contentDescription = song.album,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                fallback = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(Color(0xFFD5D5D5)),
                    )
                },
            )
        }
    }
}

/**
 * The progress bar: a grey rounded track with a blue gradient fill.
 *
 * [fraction] is expected already clamped to `0f..1f` by [progressFraction].
 */
@Composable
internal fun ClassicProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(10.dp)) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = TrackGrey, size = size, cornerRadius = radius)
        val filled = size.width * fraction.coerceIn(0f, 1f)
        if (filled > 0f) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = ProgressGradient,
                    startY = 0f,
                    endY = size.height,
                ),
                size = Size(filled, size.height),
                cornerRadius = radius,
            )
        }
        drawRoundRect(
            color = HairlineGrey,
            size = size,
            cornerRadius = radius,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
        )
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
        style = ClassicTypography.label.copy(color = Color(0xFF5A5A5A)),
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
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(elapsed, style = ClassicTypography.label.copy(color = elapsedColor))
        BasicText(remaining, style = ClassicTypography.label.copy(color = Color(0xFF5A5A5A)))
    }
}
