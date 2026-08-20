/*
 * Copyright (c) 2026 Shellac Project
 * AppSkin.kt is part of Shellac.
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
 
package org.shellac.skin

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything a skin is given. Constructed by the host and handed to [AppSkin.Render].
 *
 * A skin should treat this as its entire universe. If something is missing, the contract is
 * incomplete - extend it here rather than reaching into the engine.
 */
class SkinSession(
    val playback: StateFlow<SkinPlaybackState>,
    val library: SkinLibrary,
    val commands: SkinCommands,
    /**
     * Lyrics for the playing track.
     *
     * The only part of the contract backed by a network call, so a skin should expect
     * [SkinLyricsState.Failed] and [SkinLyricsState.NotFound] to be ordinary outcomes rather than
     * edge cases, and must handle [SkinLyricsState.Disabled] - the lookup is user-toggleable
     * because this engine is otherwise entirely offline.
     */
    val lyrics: SkinLyricsSource,
    /**
     * Opens the app-native settings screen.
     *
     * A skin SHOULD surface this somewhere, but the host also exposes settings through a
     * system-level entry point, so a skin that omits it - or crashes before drawing it - cannot
     * lock the user out (buildspec 3.7).
     */
    val openAppSettings: () -> Unit,
)

/**
 * A complete user interface.
 *
 * A skin owns **every** screen: browsing, search, queue, now-playing, and navigation itself. There
 * is no shared navigation graph, because the navigation model is part of a skin's identity - a
 * wheel-driven hierarchy and a flat single-page skin have nothing useful to share.
 *
 * ## Rules
 * 1. A skin MUST NOT import `org.oxycblt.auxio.*` or `org.oxycblt.musikr.*`. This module cannot see
 *    those packages, so the compiler enforces it - but the Phase 3 grep gate still checks, to catch
 *    a skin placed in the wrong module.
 * 2. [Render] may be called again at any time (configuration change, skin switch). Hold durable
 *    *content* state in `rememberSaveable`.
 *
 *    **Navigation state is the exception**, and it is deliberate. [onBackPressed] is a plain
 *    method, not a composable, so it cannot read a `rememberSaveable`. Navigation state that back
 *    must act on therefore has to live in a field the skin object can see — a `mutableStateOf` /
 *    `SnapshotStateList` field is the correct pattern here, not a workaround. Mirror it into
 *    `rememberSaveable` if it must survive process death.
 *
 *    (An earlier revision of this doc told skins never to use fields, which directly contradicted
 *    [onBackPressed]'s signature. Reported by the first skin written against this contract. A
 *    reusable back-stack primitive arrives in the skin support layer, buildspec Phase 4.5.)
 * 3. [Render] must not throw. If it does, the host catches it, falls back to a known-good skin, and
 *    surfaces the failure - but the user still loses the skin.
 */
interface AppSkin {
    /** Stable identifier, persisted as the user's preference. Never change it once shipped. */
    val id: String

    /** Human-readable name, shown in app-native settings. */
    val displayName: String

    /**
     * Whether the host should draw edge-to-edge behind the system bars for this skin. A skin that
     * wants full control of the screen returns true and handles insets itself.
     */
    val supportsEdgeToEdge: Boolean

    @Composable fun Render(session: SkinSession)

    /**
     * Handle a system back press.
     *
     * Return true if the skin consumed it (e.g. popped its own stack). Return false when at its
     * root, which lets the host exit the app.
     *
     * Default is false: a flat skin with no hierarchy needs no back handling at all.
     */
    fun onBackPressed(): Boolean = false
}
