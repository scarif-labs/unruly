/*
 * Copyright (c) 2026 Unruly Project
 * CrossbarSkin.kt is part of Unruly.
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
 
package org.unruly.skin.crossbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar
import kotlinx.coroutines.delay
import org.unruly.skin.AppSkin
import org.unruly.skin.SkinSession
import org.unruly.skin.SkinSong
import org.unruly.skin.support.SkinColors
import org.unruly.skin.support.SkinTheme
import org.unruly.skin.support.SkinTypography

/** The bar's categories, left to right. */
internal enum class Category(val label: String, val glyph: Glyph) {
    SETTINGS("Settings", Glyph.SETTINGS),
    MUSIC("Music", Glyph.MUSIC),
    PLAYLISTS("Playlists", Glyph.PLAYLISTS),
    ARTISTS("Artists", Glyph.ARTISTS),
    ALBUMS("Albums", Glyph.ALBUMS),
    SONGS("Songs", Glyph.SONGS),
    GENRES("Genres", Glyph.GENRES),
}

/** A list the menu can show. The top level is one per category; the rest are drilled into. */
internal sealed interface Level {
    data class Root(val category: Category) : Level

    data class ArtistAlbums(val id: String, val name: String) : Level

    data class AlbumSongs(val id: String, val title: String) : Level

    data class GenreSongs(val id: String, val name: String) : Level

    data class PlaylistSongs(val id: String, val name: String) : Level
}

/** One line in the side menu. */
internal class Opt(val label: String, val swatch: Tint? = null, val run: () -> Unit)

/** The side menu, opened from an entry (hold it, or tap "Options"). */
internal class OptionsMenu(val options: List<Opt>, initial: Int = 0) {
    var selected by mutableIntStateOf(initial)
}

/** Asks for a name over the menu, as the device's text entry did, then hands it to [done]. */
internal class NamePrompt(val title: String, val initial: String = "", val done: (String) -> Unit)

/**
 * A skin after the handheld game console's cross-shaped menu of 2004-2014: categories run across,
 * their contents run down, over a slowly flowing wave tinted by the month. Choosing a song opens
 * the playback screen with its control panel and visualisers.
 *
 * Built from frames of the original system software; see [CrossbarTheme] for the measurements and
 * the backlog (docs/tasks/skins-backlog.md, 7.3) for what was left out and why.
 */
class CrossbarSkin : AppSkin {
    override val id: String = "crossbar"
    override val displayName: String = "PSP XMB"
    // The XMB clock owns the top-right corner.
    override val settingsGearAlignment: Alignment = Alignment.TopStart
    override val supportsEdgeToEdge: Boolean = true

    // Navigation lives in fields so onBackPressed can act on it (see AppSkin, rule 2).
    internal var category by mutableStateOf(Category.MUSIC)
    internal val stack = mutableStateListOf<Level>()
    internal val selection = mutableStateMapOf<Level, Int>()
    internal var playerOpen by mutableStateOf(false)
    internal var options by mutableStateOf<OptionsMenu?>(null)
    internal var info by mutableStateOf<SkinSong?>(null)
    internal var naming by mutableStateOf<NamePrompt?>(null)

    /** The list a song was started from, shown across the top of the playback screen. */
    internal var group by mutableStateOf("")

    /** Bumped when the colour setting changes, so the backdrop re-reads it. */
    internal var colourSetting by mutableIntStateOf(-1)

    internal val level: Level
        get() = stack.lastOrNull() ?: Level.Root(category)

    internal fun selected(level: Level): Int = selection[level] ?: 0

    internal fun select(level: Level, index: Int) {
        selection[level] = index
    }

    internal fun drill(into: Level) {
        options = null
        stack.add(into)
    }

    internal fun openPlayer(groupName: String?) {
        if (groupName != null) group = groupName
        options = null
        playerOpen = true
    }

    override fun onBackPressed(): Boolean {
        when {
            naming != null -> naming = null
            options != null -> options = null
            info != null -> info = null
            playerOpen -> playerOpen = false
            stack.isNotEmpty() -> stack.removeAt(stack.lastIndex)
            else -> return false
        }
        return true
    }

    @Composable
    override fun Render(session: SkinSession) {
        val context = LocalContext.current
        val prefs = remember(context) { CrossbarPrefs(context) }
        if (colourSetting < 0) colourSetting = prefs.colour
        val visualiser = remember { mutableIntStateOf(prefs.visualiser) }
        // The dark visualisers take over the whole screen, system bars included.
        val black = playerOpen && Visualiser.entries.getOrNull(visualiser.intValue)?.onBlack == true
        val now by rememberNow()
        val month = if (colourSetting == 0) now.get(Calendar.MONTH) else colourSetting - 1
        val tint =
            Tints.months[month].dimmed(
                Tints.daylight(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
            )

        SkinTheme(
            colors = SkinColors.darkDefault(),
            typography = SkinTypography.default(),
            // True asks for dark icons, for a pale backdrop.
            darkSystemBars = tint.isLight && !black,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val unit = pxScale(maxWidth.value, maxHeight.value)
                CompositionLocalProvider(
                    LocalPx provides androidx.compose.ui.unit.Dp(unit),
                    LocalTint provides tint,
                ) {
                    if (black) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                    } else {
                        CrossbarBackdrop(
                            tint,
                            Modifier.fillMaxSize(),
                            energy = { if (playerOpen) playerWaveEnergy else 1f },
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        AnimatedContent(
                            targetState = playerOpen,
                            transitionSpec = {
                                fadeIn(tween(260)) togetherWith fadeOut(tween(200))
                            },
                            label = "crossbar-screen",
                        ) { player ->
                            if (player) {
                                PlayerScreen(this@CrossbarSkin, session, visualiser.intValue) {
                                    visualiser.intValue = it
                                    prefs.visualiser = it
                                }
                            } else {
                                MenuScreen(this@CrossbarSkin, session, now, colourSetting) {
                                    colourSetting = it
                                    prefs.colour = it
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Set by the playback screen while the wave visualiser runs. */
    internal var playerWaveEnergy by mutableStateOf(1f)
}

/** The wall clock, refreshed on each minute. */
@Composable
private fun rememberNow() =
    produceState(Calendar.getInstance()) {
        while (true) {
            value = Calendar.getInstance()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
