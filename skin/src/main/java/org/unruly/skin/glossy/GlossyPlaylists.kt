/*
 * Copyright (c) 2026 Unruly Project
 * GlossyPlaylists.kt is part of Unruly.
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * The playlist-editing pieces of the 2012 music app: the blue alert that names a playlist, the
 * action sheet that confirms a destructive step, the Edit / Clear / Delete band at the head of a
 * playlist, and the red and blue badges of its edit mode and song picker.
 */

/** A modal over the whole skin. Only one shows at a time; back dismisses it. */
internal sealed interface GOverlay {
    /** The naming alert: [onSave] gets the trimmed text, which may be blank. */
    data class Name(
        val title: String,
        val message: String,
        val initial: String,
        val onSave: (String) -> Unit,
    ) : GOverlay

    /** The action sheet: one red [action] button over Cancel. */
    data class Confirm(val action: String, val onConfirm: () -> Unit) : GOverlay
}

/** The black veil behind an alert or sheet. Taps on it fall through to nothing. */
@Composable
private fun Veil(alpha: Float, onTap: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = alpha))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        content = content,
    )
}

/**
 * The navy glass alert with a text field, as the original asked for a new playlist's name. Cancel
 * and Save sit side by side; the keyboard's Done saves as well.
 */
@Composable
internal fun NameAlert(overlay: GOverlay.Name, onDismiss: () -> Unit) {
    var field by remember {
        mutableStateOf(TextFieldValue(overlay.initial, TextRange(0, overlay.initial.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val save = {
        onDismiss()
        overlay.onSave(field.text.trim())
    }
    val shape = RoundedCornerShape(pt(10))
    Veil(alpha = 0.45f, onTap = {}) {
        Box(modifier = Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            Column(
                modifier =
                    Modifier.width(pt(284))
                        .clip(shape)
                        .background(Brush.verticalGradient(listOf(Color(0xF2304677), Color(0xF20E1C45), Color(0xF2091538))))
                        .border(pt(2), Color(0xCCDDE2EE), shape)
                        // The glass: a pale highlight across the top third.
                        .drawBehind {
                            drawRoundRect(
                                Brush.verticalGradient(
                                    listOf(Color(0x55FFFFFF), Color(0x10FFFFFF)),
                                    endY = size.height * 0.32f,
                                ),
                                size = size.copy(height = size.height * 0.32f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                            )
                        }
                        .padding(horizontal = pt(12), vertical = pt(14)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(overlay.title, style = alertText(18f, bold = true))
                Spacer(Modifier.height(pt(4)))
                BasicText(overlay.message, style = alertText(15f, bold = false))
                Spacer(Modifier.height(pt(12)))
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(pt(31))
                            .clip(RoundedCornerShape(pt(4)))
                            .background(Color.White)
                            .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(pt(4)))
                            .padding(horizontal = pt(6)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = field,
                        onValueChange = { field = it },
                        singleLine = true,
                        textStyle = heros(16f, Color(0xFF111111), bold = false),
                        cursorBrush = SolidColor(Color(0xFF3478F6)),
                        keyboardOptions =
                            KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                Spacer(Modifier.height(pt(14)))
                Row(horizontalArrangement = Arrangement.spacedBy(pt(8))) {
                    AlertButton("Cancel", default = false, onClick = onDismiss, modifier = Modifier.weight(1f))
                    AlertButton("Save", default = true, onClick = save, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun alertText(size: Float, bold: Boolean) =
    heros(size, Color.White, bold).copy(
        textAlign = TextAlign.Center,
        shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, -2f), 0f),
    )

/** A glassy button in the alert; the default action is a shade lighter, as on the original. */
@Composable
private fun AlertButton(text: String, default: Boolean, onClick: () -> Unit, modifier: Modifier) {
    var pressed = false
    val press = Modifier.pressAware(onClick) { pressed = it }
    val shape = RoundedCornerShape(pt(6))
    val fill =
        when {
            pressed -> listOf(Color(0xFF1E2C55), Color(0xFF16224A))
            default -> listOf(Color(0xFF8E9BBC), Color(0xFF55648F), Color(0xFF3E4E7E))
            else -> listOf(Color(0xFF6C7899), Color(0xFF3A4670), Color(0xFF26325C))
        }
    Box(
        modifier =
            modifier
                .then(press)
                .height(pt(43))
                .clip(shape)
                .background(Brush.verticalGradient(fill))
                .border(1.dp, Color(0xFF0B1433), shape),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = alertText(17f, bold = true))
    }
}

/** The action sheet: a dark glass panel from the bottom with the red action over Cancel. */
@Composable
internal fun ConfirmSheet(overlay: GOverlay.Confirm, onDismiss: () -> Unit) {
    Veil(alpha = 0.4f, onTap = onDismiss) {
        Column(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xE6545A66), Color(0xF0262A33), Color(0xF5181B22))))
                    .drawBehind { drawLine(Color(0xFF8C919C), Offset(0f, 0f), Offset(size.width, 0f), 2f) }
                    // Swallow taps so they don't reach the veil.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(horizontal = pt(20), vertical = pt(18)),
            verticalArrangement = Arrangement.spacedBy(pt(10)),
        ) {
            SheetButton(overlay.action, destructive = true) {
                onDismiss()
                overlay.onConfirm()
            }
            Spacer(Modifier.height(pt(4)))
            SheetButton("Cancel", destructive = false, onClick = onDismiss)
        }
    }
}

@Composable
private fun SheetButton(text: String, destructive: Boolean, onClick: () -> Unit) {
    var pressed = false
    val press = Modifier.pressAware(onClick) { pressed = it }
    val shape = RoundedCornerShape(pt(7))
    val fill =
        when {
            pressed -> listOf(Color(0xFF0E5AD6), Color(0xFF0A44B0))
            destructive -> listOf(Color(0xFFF08A8A), Color(0xFFD62020), Color(0xFFB10E0E))
            else -> listOf(Color(0xFF7C7F86), Color(0xFF3E4148), Color(0xFF2A2D33))
        }
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .then(press)
                .height(pt(44))
                .clip(shape)
                .background(Brush.verticalGradient(fill))
                .border(1.dp, Color(0xFF111111), shape),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style =
                heros(19f, Color.White).copy(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, -2f), 0f)),
        )
    }
}

/**
 * The grey band at the head of a playlist. Browsing, it holds Edit, Clear and Delete; editing, it
 * holds the playlist's name in a field, so a rename is just typing over it.
 */
@Composable
internal fun PlaylistBand(
    editing: Boolean,
    name: String,
    onName: (String) -> Unit,
    onEdit: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(pt(48))
                .background(Brush.verticalGradient(listOf(Color(0xFFDBDBDD), Color(0xFFBEBEC0))))
                .drawBehind {
                    drawLine(Color(0xFF9D9D9F), Offset(0f, size.height - 1), Offset(size.width, size.height - 1), 2f)
                }
                .padding(horizontal = pt(10), vertical = pt(9)),
        contentAlignment = Alignment.Center,
    ) {
        if (editing) {
            val shape = RoundedCornerShape(pt(5))
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .clip(shape)
                        .background(Color.White)
                        .border(1.dp, Color(0xFF8D8D8F), shape)
                        .padding(horizontal = pt(8)),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = onName,
                    singleLine = true,
                    textStyle = heros(16f, Color(0xFF222222)),
                    cursorBrush = SolidColor(Color(0xFF3478F6)),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(pt(10)), verticalAlignment = Alignment.CenterVertically) {
                BarButton("  Edit  ", onEdit)
                BarButton("  Clear  ", onClear)
                BarButton("  Delete  ", onDelete)
            }
        }
    }
}

/** The red minus disc at the start of a row in edit mode. */
@Composable
internal fun MinusBadge() {
    Canvas(modifier = Modifier.size(pt(23))) {
        drawCircle(Color.White)
        drawCircle(
            Brush.verticalGradient(listOf(Color(0xFFF26A6A), Color(0xFFD01414))),
            radius = size.minDimension / 2 - 2.dp.toPx(),
        )
        val bar = size.width * 0.13f
        drawLine(
            Color.White,
            Offset(size.width * 0.26f, size.height / 2),
            Offset(size.width * 0.74f, size.height / 2),
            bar,
        )
    }
}

/** The blue plus disc on a song in the picker; grey once the song has been added. */
@Composable
internal fun PlusBadge(added: Boolean) {
    Canvas(modifier = Modifier.size(pt(26))) {
        val fill =
            if (added) listOf(Color(0xFFC9C9CB), Color(0xFFA3A3A5))
            else listOf(Color(0xFF5DA6F8), Color(0xFF1C6FE8))
        drawCircle(Color.White)
        drawCircle(Brush.verticalGradient(fill), radius = size.minDimension / 2 - 2.dp.toPx())
        val bar = size.width * 0.12f
        drawLine(Color.White, Offset(size.width * 0.28f, size.height / 2), Offset(size.width * 0.72f, size.height / 2), bar)
        drawLine(Color.White, Offset(size.width / 2, size.height * 0.28f), Offset(size.width / 2, size.height * 0.72f), bar)
    }
}

/** The red "Delete" button that slides in at a row's end once its minus disc is tapped. */
@Composable
internal fun RowDeleteButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(pt(5))
    Box(
        modifier =
            Modifier.padding(end = pt(8))
                .height(pt(30))
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFFE96B6B), Color(0xFFC81717), Color(0xFFB30D0D))))
                .border(1.dp, Color(0xFF7A0A0A), shape)
                .clickable(onClick = onClick)
                .padding(horizontal = pt(10)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            "Delete",
            style = heros(14f, Color.White).copy(shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, -1f), 0f)),
        )
    }
}

/** A song row in a playlist's edit mode: minus disc, title and artist, and Delete once armed. */
@Composable
internal fun EditableSongRow(title: String, subtitle: String, armed: Boolean, onArm: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(pt(44))
                .background(Glossy.ListBg)
                .drawBehind {
                    drawLine(Glossy.SepLight, Offset(0f, 0f), Offset(size.width, 0f), 2f)
                    drawLine(Glossy.SepDark, Offset(0f, size.height - 1), Offset(size.width, size.height - 1), 2f)
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(pt(44)).clickable(onClick = onArm),
            contentAlignment = Alignment.Center,
        ) {
            MinusBadge()
        }
        Column(modifier = Modifier.weight(1f).padding(end = pt(8))) {
            BasicText(title, style = heros(18f, Glossy.RowText), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(subtitle, style = heros(14f, Glossy.RowSub, bold = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (armed) RowDeleteButton(onDelete)
    }
}

/** A thin grey line of prompt text under a nav bar, as the picker's "Add songs to …". */
@Composable
internal fun PromptBand(text: String) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Glossy.HeaderTop, Glossy.HeaderBottom)))
                .padding(horizontal = pt(12), vertical = pt(5)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = embossed(13f, Glossy.HeaderText).copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
