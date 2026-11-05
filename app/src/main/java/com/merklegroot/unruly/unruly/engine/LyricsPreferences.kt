/*
 * Copyright (c) 2026 Unruly Project
 * LyricsPreferences.kt is part of Unruly.
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
 
package com.merklegroot.unruly.unruly.engine

/** Shared preferences used by the Unruly engine and its app-native settings screen. */
object LyricsPreferences {
    const val PREFS_NAME = "unruly_skins"
    const val LOOKUP_ENABLED_KEY = "lyrics_lookup_enabled"
    const val DEFAULT_LOOKUP_ENABLED = true
}
