/*
 * Copyright (c) 2026 Unruly Project
 * SkinErrorPanel.kt is part of Unruly.
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

package com.merklegroot.unruly.unruly.web

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Most errors kept; a skin throwing every frame would otherwise grow this without bound. */
internal const val MAX_SKIN_ERRORS = 50

/**
 * For skin authors: the skin's JavaScript errors, on the phone, without a desktop debugger. A red
 * pill with the count; tap it for the list. Shown only in debug builds of Unruly, or for a skin
 * whose skin.json says `"developer": true` - never to someone just using a skin.
 *
 * It sits in a bottom corner, on the opposite side from the settings gear.
 */
@Composable
internal fun SkinErrorPanel(errors: SnapshotStateList<String>, gearAtStart: Boolean, modifier: Modifier) {
    if (errors.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val side = if (gearAtStart) Alignment.BottomEnd else Alignment.BottomStart
    Box(modifier.fillMaxWidth().safeDrawingPadding().padding(8.dp), contentAlignment = side) {
        if (!open) {
            Text(
                "⚠ ${errors.size} error${if (errors.size == 1) "" else "s"}",
                Modifier.clip(RoundedCornerShape(50))
                    .background(ErrorRed)
                    .clickable(role = Role.Button) { open = true }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xF0141414))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Skin errors",
                        Modifier.weight(1f),
                        color = ErrorRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    PanelButton("Clear") {
                        errors.clear()
                        open = false
                    }
                    PanelButton("Hide") { open = false }
                }
                Column(
                    Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Spacer(Modifier.padding(top = 2.dp))
                    // Newest first: the one that just happened is the one being looked for.
                    errors.asReversed().forEach {
                        Text(
                            it,
                            color = Color(0xFFE8E8E8),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier.clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

private val ErrorRed = Color(0xFFE5484D)
