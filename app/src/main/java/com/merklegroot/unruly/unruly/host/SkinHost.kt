/*
 * Copyright (c) 2026 Unruly Project
 * SkinHost.kt is part of Unruly.
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

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlin.math.roundToInt
import org.unruly.skin.AppSkin
import org.unruly.skin.SkinSession

/**
 * Renders the active skin behind a crash-loop breaker.
 *
 * ## Why there is no try/catch here
 *
 * There cannot be one. **Compose has no error boundary.** The compiler rejects both forms:
 * ```
 * runCatching { skin.Render(session) }
 *   e: runCatching call is not allowed to contain @Composable function invocations
 *
 * try { skin.Render(session) } catch (t: Throwable) { ... }
 *   e: Try catch is not supported around composable function invocations.
 * ```
 *
 * This is fundamental: composition can be restarted, skipped and reordered, so a `catch` cannot
 * meaningfully scope a subtree. A skin that throws **will** crash the process, and no amount of
 * host code changes that.
 *
 * ## What is done instead
 *
 * Survive the crash rather than prevent it. Before a skin composes, the registry persists
 * "currently attempting <id>". Once a frame completes, that marker is cleared. If the process dies
 * in between, the marker is still there on next launch - so the registry knows that skin crashed,
 * quarantines it, and starts in the fallback instead.
 *
 * The user therefore sees one crash, then a working app. That satisfies the buildspec's requirement
 * ("make Debug B throw in Render; you can still reach settings and switch away") without pretending
 * Compose offers isolation it does not.
 *
 * Defence in depth: UnrulySettingsActivity has its own launcher icon and never composes a skin, so
 * it stays reachable even during a crash loop.
 *
 * ## Insets
 *
 * `AppSkin.supportsEdgeToEdge` is honoured **here**, not by the Activity.
 * `WindowCompat.setDecorFitsSystemWindows` is deprecated and **ignored from API 35 onward**, where
 * edge-to-edge is enforced. On the API 36 test device it did nothing at all, so a skin declaring
 * `supportsEdgeToEdge = false` still drew its own title bar underneath the system status bar.
 * Applying `safeDrawingPadding` to the skin's root fixes it for every skin rather than making each
 * one remember to handle insets itself.
 *
 * ## The settings gear
 *
 * Every skin has its own way into settings, but each one buries it in a menu, so switching skins
 * meant finding that entry first. The host draws a [SettingsGear] over every skin, on every screen,
 * so settings (and with it the skin picker) is one tap away whatever skin is showing. A skin whose
 * own settings button is already easy to find turns the gear off instead (iOS 6 Music).
 *
 * The first time each skin opens, the gear is introduced: the skin dims, the gear is ringed and
 * captioned (or, on a gearless skin, its own settings button is). Any tap dismisses it for good; tapping the gear itself also opens settings. It is
 * remembered per skin, since every skin is a new interface and the gear may sit in a new corner.
 * The recovery skin is skipped - it is a single "Open settings" button already.
 */
@Composable
fun SkinHost(skin: AppSkin, session: SkinSession, onRenderSucceeded: (String) -> Unit) {
    val context = LocalContext.current
    val hints = remember(context) { context.getSharedPreferences(HINT_PREFS, Context.MODE_PRIVATE) }
    var hinting by
        remember(skin.id) {
            mutableStateOf(skin.id != RecoverySkin.ID && !hints.getBoolean(hintKey(skin.id), false))
        }
    // The session outlives a skin switch; forget the last skin's button before this one places its own.
    remember(skin.id) { session.settingsButtonAnchor.bounds = null }
    fun dismissHint() {
        hinting = false
        hints.edit { putBoolean(hintKey(skin.id), true) }
    }

    Box(Modifier.fillMaxSize()) {
        if (skin.supportsEdgeToEdge) {
            skin.Render(session)
        } else {
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) { skin.Render(session) }
        }
        val corner = skin.settingsGearAlignment
        val anchor = session.settingsButtonAnchor.bounds
        // A gearless skin is hinted only once its own button is on screen to point at.
        if (hinting && (corner != null || anchor != null)) {
            // Swallows the tap, so dismissing the hint never also presses whatever is beneath.
            Box(
                Modifier.fillMaxSize()
                    .background(Color(0xB3000000))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = ::dismissHint,
                    )
            )
        }
        if (corner != null) {
            GearCorner(corner, hinting, onClick = {
                if (hinting) dismissHint()
                session.openAppSettings()
            })
        } else if (hinting && anchor != null) {
            AnchorHint(anchor, onClick = {
                dismissHint()
                session.openAppSettings()
            })
        }
    }

    // Reached only if Render returned without throwing. LaunchedEffect runs after the frame
    // is applied, so this genuinely means "this skin composed successfully at least once".
    LaunchedEffect(skin.id) { onRenderSucceeded(skin.id) }
}

/** The gear in its corner, with the first-run caption beside it while [hinting]. */
@Composable
private fun BoxScope.GearCorner(corner: Alignment, hinting: Boolean, onClick: () -> Unit) {
    val bias = corner as? BiasAlignment
    val atBottom = (bias?.verticalBias ?: -1f) > 0f
    val atStart = (bias?.horizontalBias ?: 1f) < 0f
    Column(
        Modifier.align(corner).safeDrawingPadding().padding(2.dp),
        horizontalAlignment = if (atStart) Alignment.Start else Alignment.End,
    ) {
        if (hinting && atBottom) {
            HintCaption(atStart)
            Spacer(Modifier.height(10.dp))
        }
        SettingsGear(onClick, ringed = hinting)
        if (hinting && !atBottom) {
            Spacer(Modifier.height(10.dp))
            HintCaption(atStart)
        }
    }
}

/**
 * The first-run hint for a skin with no gear: a ring around the skin's own settings button, at
 * [bounds], captioned beneath. The ringed button stays live through the scrim.
 */
@Composable
private fun AnchorHint(bounds: Rect, onClick: () -> Unit) {
    val density = LocalDensity.current
    val ring = with(density) { 6.dp.toPx() }
    val ringed = bounds.inflate(ring)
    val corner = with(density) { (ringed.height / 2).toDp() }
    Box(
        Modifier.offset { IntOffset(ringed.left.roundToInt(), ringed.top.roundToInt()) }
            .size(with(density) { DpSize(ringed.width.toDp(), ringed.height.toDp()) })
            .border(2.dp, Color.White, RoundedCornerShape(corner))
            .clip(RoundedCornerShape(corner))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Settings" }
    )
    Box(
        Modifier.offset {
            IntOffset(ringed.left.roundToInt(), (ringed.bottom + 10.dp.toPx()).roundToInt())
        }
    ) {
        HintCaption(atStart = true)
    }
}

@Composable
private fun HintCaption(atStart: Boolean) {
    Text(
        "Change skin or settings here",
        Modifier.widthIn(max = 220.dp).padding(horizontal = 6.dp),
        color = Color.White,
        fontSize = 18.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = if (atStart) TextAlign.Start else TextAlign.End,
    )
}

/**
 * A small gear on a translucent dark disc. The disc is what keeps it legible over any skin, light or
 * dark, without it borrowing any one skin's look.
 */
@Composable
private fun SettingsGear(onClick: () -> Unit, ringed: Boolean) {
    // The ring's room is kept even when it is not drawn, so the gear does not shift on dismissal.
    Box(
        Modifier.border(2.dp, if (ringed) Color.White else Color.Transparent, CircleShape)
            .padding(4.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0x99000000))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Settings" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val ink = Color(0xFFF2F2F2)
            val c = center
            val r = size.minDimension / 2
            // Eight teeth around a ring with a hole, the usual cog.
            repeat(8) { i ->
                rotate(i * 45f, c) {
                    drawLine(ink, Offset(c.x, c.y - r * 0.6f), Offset(c.x, c.y - r), r * 0.32f)
                }
            }
            drawCircle(ink, radius = r * 0.5f, center = c, style = Stroke(r * 0.3f))
        }
    }
}

private const val HINT_PREFS = "unruly_hints"

private fun hintKey(skinId: String) = "settings_gear_seen_$skinId"
