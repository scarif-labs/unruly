/*
 * Copyright (c) 2026 Shellac Project
 * RecoverySkin.kt is part of Shellac.
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
 
package com.merklegroot.shellac.shellac.host

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shellac.skin.AppSkin
import org.shellac.skin.SkinSession

/**
 * The floor of the crash-recovery path. Not a skin anyone chooses - it is what renders when the
 * skin the user *did* choose crashed while composing.
 *
 * It lives in the host rather than in `:skin` on purpose. Every real skin is elaborate enough to
 * have its own way of failing; the thing that catches those failures must not be. This composable
 * reads nothing from [SkinSession] except [SkinSession.openAppSettings], has no state, no gestures,
 * no library access and no playback subscription, so there is very little of it that can go wrong
 * at the exact moment everything else has.
 *
 * It is deliberately absent from [SkinRegistry.available] - offering "Recovery" as a choice in the
 * picker would be offering the user a broken app. [SkinRegistry.isRecoveryActive] exists so
 * settings can explain the state instead.
 */
class RecoverySkin : AppSkin {
    override val id: String = ID
    override val displayName: String = "Recovery"
    override val supportsEdgeToEdge: Boolean = false

    @Composable
    override fun Render(session: SkinSession) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF101010)).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Shellac",
                    color = Color(0xFFF2F2F2),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "The skin you were using stopped working, so Shellac started here " +
                        "instead. Pick a skin to get back to normal.",
                    color = Color(0xFF9E9E9E),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Box(
                    modifier =
                        Modifier.padding(top = 28.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF8AB4F8))
                            .clickable(onClick = session.openAppSettings)
                            .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Open settings",
                        color = Color(0xFF0B1B33),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }

    companion object {
        const val ID = "recovery"
    }
}
