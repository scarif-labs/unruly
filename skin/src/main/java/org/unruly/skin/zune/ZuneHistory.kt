/*
 * Copyright (c) 2026 Unruly Project
 * ZuneHistory.kt is part of Unruly.
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

package org.unruly.skin.zune

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** How many recently played albums the quickplay "History" section keeps. */
private const val HISTORY_SIZE = 8

private const val PREFS_NAME = "zune_skin_history"
private const val KEY_ALBUMS = "albums"

/**
 * The albums most recently played, newest first, for quickplay's "History".
 *
 * The skin contract has no play history, so the skin keeps its own: one line per album id in a
 * private preferences file. It is the skin's state, not the library's - switching skins leaves it
 * untouched and nothing else reads it.
 */
internal class ZuneHistory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var albumIds by mutableStateOf(load())
        private set

    /** Moves [albumId] to the front, dropping the oldest past [HISTORY_SIZE]. */
    fun record(albumId: String) {
        if (albumIds.firstOrNull() == albumId) return
        val next = (listOf(albumId) + albumIds.filter { it != albumId }).take(HISTORY_SIZE)
        albumIds = next
        prefs.edit().putString(KEY_ALBUMS, next.joinToString("\n")).apply()
    }

    private fun load(): List<String> =
        prefs.getString(KEY_ALBUMS, null)?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
}
