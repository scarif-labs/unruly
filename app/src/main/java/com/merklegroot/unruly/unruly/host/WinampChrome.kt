/*
 * Copyright (c) 2026 Unruly Project
 * WinampChrome.kt is part of Unruly.
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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Chrome for the app's own screens - setup and settings - styled after a late-90s skinnable desktop
 * media player: chiselled bevels, a phosphor-green LCD readout, monospace type throughout.
 *
 * ## The three rules this exists to enforce
 *
 * These are layout rules, not decoration, and every screen that skipped one looked broken:
 * 1. **A screen is one window that fills the viewport.** An earlier version sized each window to
 *    its content, which left every screen as a cluster of controls jammed against the status bar
 *    above a large empty void. [WinampScreen] always fills, and its content region takes the slack.
 * 2. **The primary action is pinned to the bottom and is the only saturated fill on the screen.**
 *    Buried in a scrolling column among five other bevelled panels, a call to action is
 *    indistinguishable from a toolbar button. [WinampScreen] gives it a fixed action bar and
 *    [WinampButton]'s `primary` flag gives it the only phosphor-green face.
 * 3. **No side-by-side panes.** A phone is ~360dp wide; a desktop config panel's category list plus
 *    content pane leaves each column too narrow to hold a word, and labels wrap one character per
 *    line. The desktop feel comes from the chrome - title bars, bevels, the LCD - not from copying
 *    a desktop's *layout*. Sections stack vertically.
 *
 * The window is also inset-aware ([WindowInsets.safeDrawing]); without it the title bar renders
 * underneath the status bar and the action bar under the gesture pill.
 */
object Winamp {
    val Desktop = Color(0xFF080D18)
    val WindowTop = Color(0xFF1E3152)
    val WindowBot = Color(0xFF101D33)
    val TitleTop = Color(0xFF2B4977)
    val TitleBot = Color(0xFF182C4E)
    val BevelLight = Color(0xFF5279AC)
    val BevelDark = Color(0xFF050912)
    val Lcd = Color(0xFF071120)
    val Phosphor = Color(0xFF4FE39B)
    val PhosphorDim = Color(0xFF2E7F5B)
    val Label = Color(0xFFB6CEEA)
    val LabelDim = Color(0xFF6D86A8)
    val SelectRow = Color(0xFF23508F)

    /** Text colour on a phosphor-filled primary button. Dark, for contrast against green. */
    val OnPhosphor = Color(0xFF04140C)
}

internal val Mono = FontFamily.Monospace

/** Bevel thickness. Two device-independent pixels reads as chiselled without turning chunky. */
private val BEVEL = 2.dp

/**
 * A full-screen application window: title bar, optional menu bar, a content region that absorbs all
 * remaining height, and an optional fixed action bar at the bottom.
 *
 * @param actions The primary call to action. Rendered in a bar that never scrolls, so it is
 *   reachable and visible no matter how long the content is.
 */
@Composable
fun WinampScreen(
    title: String,
    modifier: Modifier = Modifier,
    menuItems: List<String> = emptyList(),
    onClose: (() -> Unit)? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Winamp.Desktop)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(10.dp)
            .bevel(raised = true)
            .background(Brush.verticalGradient(listOf(Winamp.WindowTop, Winamp.WindowBot)))
    ) {
        WindowTitleBar(title = title, onClose = onClose)
        if (menuItems.isNotEmpty()) MenuBar(menuItems)

        // weight(1f) is what makes the window fill instead of hugging its content: the
        // content region takes every pixel the title and action bars do not.
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 12.dp),
            content = content,
        )

        if (actions != null) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Winamp.WindowBot)
                    .drawWithContent {
                        drawContent()
                        // A single light hairline separates the action bar from the content
                        // above it, so the primary button reads as fixed furniture rather
                        // than as the last item in the list.
                        drawRect(Winamp.BevelLight, Offset.Zero, Size(size.width, 1.dp.toPx()))
                    }
                    .padding(12.dp),
                content = actions,
            )
        }
    }
}

/**
 * Draws a chiselled edge: light along the top and left, dark along the bottom and right, swapped
 * when [raised] is false.
 *
 * A draw modifier rather than child Boxes on purpose. `fillMaxWidth()` children of a Box
 * participate in the parent's measurement, so bevels built that way silently inflated every panel
 * to its maximum constraint - a toolbar's first button then took the whole row and pushed the rest
 * off screen. Drawing leaves the panel sized purely by its content.
 */
fun Modifier.bevel(raised: Boolean = true): Modifier =
    this.drawWithContent {
        drawContent()
        val light = if (raised) Winamp.BevelLight else Winamp.BevelDark
        val dark = if (raised) Winamp.BevelDark else Winamp.BevelLight
        val e = BEVEL.toPx()
        drawRect(light, Offset.Zero, Size(size.width, e))
        drawRect(light, Offset.Zero, Size(e, size.height))
        drawRect(dark, Offset(0f, size.height - e), Size(size.width, e))
        drawRect(dark, Offset(size.width - e, 0f), Size(e, size.height))
    }

/** A raised or recessed bevelled surface. */
@Composable
fun BevelPanel(
    modifier: Modifier = Modifier,
    raised: Boolean = true,
    fill: Color = Winamp.WindowTop,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.background(fill).bevel(raised), content = content)
}

@Composable
private fun WindowTitleBar(title: String, onClose: (() -> Unit)?) {
    Box(
        Modifier.fillMaxWidth()
            .height(34.dp)
            .background(Brush.verticalGradient(listOf(Winamp.TitleTop, Winamp.TitleBot)))
    ) {
        Box(
            Modifier.padding(start = 9.dp)
                .size(10.dp)
                .align(Alignment.CenterStart)
                .background(Winamp.Phosphor)
        )
        Text(
            // Letter-spaced and dash-wrapped, the way every one of these title bars was.
            text = "- ${title.uppercase().toCharArray().joinToString(" ")} -",
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 44.dp),
            color = Winamp.Label,
            fontSize = 11.sp,
            fontFamily = Mono,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("_", color = Winamp.Label, fontFamily = Mono, fontSize = 12.sp)
            Text("□", color = Winamp.Label, fontFamily = Mono, fontSize = 10.sp)
            Text(
                "×",
                modifier =
                    if (onClose == null) Modifier
                    else
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClose,
                        ),
                color = Winamp.Label,
                fontFamily = Mono,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun MenuBar(items: List<String>) {
    Row(
        Modifier.fillMaxWidth()
            .background(Winamp.WindowBot)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items.forEach { item ->
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append(item.take(1))
                    }
                    append(item.drop(1))
                },
                color = Winamp.Label,
                fontFamily = Mono,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * A pressable bevelled button.
 *
 * The bevel inverts while held - that inversion *is* the click affordance, which is why there is no
 * ripple. [primary] gives the phosphor-green face reserved for the one action the screen is asking
 * for.
 */
@Composable
fun WinampButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    glyph: String? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val active = enabled
    val face =
        when {
            !active -> Winamp.WindowBot
            primary && pressed -> Winamp.PhosphorDim
            primary -> Winamp.Phosphor
            else -> Winamp.WindowTop
        }
    val ink =
        when {
            !active -> Winamp.LabelDim
            primary -> Winamp.OnPhosphor
            else -> Winamp.Label
        }
    Box(
        modifier
            .background(face)
            .bevel(raised = !pressed && active)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .heightIn(min = if (primary) 56.dp else 44.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) {
                Text(
                    glyph,
                    color = ink,
                    fontFamily = Mono,
                    fontSize = if (primary) 16.sp else 14.sp,
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                label.uppercase(),
                color = ink,
                fontFamily = Mono,
                fontSize = if (primary) 16.sp else 13.sp,
                letterSpacing = if (primary) 2.sp else 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Recessed near-black panel holding phosphor-green content. */
@Composable
fun LcdPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(Winamp.Lcd).bevel(raised = false).padding(12.dp), content = content)
}

/** Phosphor-green monospace readout text. */
@Composable
fun LcdText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 14,
    dim: Boolean = false,
    letterSpacing: Float = 0f,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text,
        modifier = modifier,
        color = if (dim) Winamp.PhosphorDim else Winamp.Phosphor,
        fontFamily = Mono,
        fontSize = size.sp,
        lineHeight = (size * 1.45f).sp,
        letterSpacing = letterSpacing.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Pale-blue monospace interface text. */
@Composable
fun WinampText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 14,
    dim: Boolean = false,
    letterSpacing: Float = 0f,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text,
        modifier = modifier,
        color = if (dim) Winamp.LabelDim else Winamp.Label,
        fontFamily = Mono,
        fontSize = size.sp,
        lineHeight = (size * 1.45f).sp,
        letterSpacing = letterSpacing.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Section heading: letter-spaced phosphor caps over a full-width rule. */
@Composable
fun WinampSectionHeader(title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)) {
        LcdText(title.uppercase(), size = 12, letterSpacing = 2.5f, maxLines = 1)
        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(1.dp).background(Winamp.BevelLight))
    }
}

/**
 * A settings or list row: label and optional caption on the left, a control on the right.
 *
 * The label column takes `weight(1f)` and the trailing control sizes to its own content. The
 * reverse - a fixed label column - is what produced labels wrapping one character per line, because
 * on a narrow screen the leftover space was a few pixels wide.
 */
@Composable
fun WinampRow(
    label: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && enabled)
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                else Modifier
            )
            .heightIn(min = 56.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            WinampText(label, size = 15, dim = !enabled)
            if (caption != null) {
                WinampText(caption, size = 12, dim = true, modifier = Modifier.padding(top = 2.dp))
            }
        }
        trailing?.invoke(this)
    }
}

/** Segmented ON/OFF pair. The active half carries the phosphor fill. */
@Composable
fun OnOffToggle(checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(modifier) {
        listOf(true, false).forEach { on ->
            val active = on == checked
            Box(
                Modifier.background(if (active) Winamp.Phosphor else Winamp.WindowBot)
                    .bevel(raised = !active)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        onChange(on)
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    if (on) "ON" else "OFF",
                    color = if (active) Winamp.OnPhosphor else Winamp.LabelDim,
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

/** Square indicator with a phosphor LED when selected. */
@Composable
fun WinampRadio(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(18.dp).background(Winamp.Lcd).bevel(raised = false),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(8.dp).background(Winamp.Phosphor))
    }
}
