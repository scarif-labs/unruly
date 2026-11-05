/*
 * Copyright (c) 2026 Unruly Project
 * SkinBackStack.kt is part of Unruly.
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
 
package org.unruly.skin.support

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * A minimal, optional back-stack primitive. A flat skin with no hierarchy is not forced to use
 * it - [rememberSkinBackStack] is just a convenience for the skins that want one.
 *
 * Backed by a [SnapshotStateList] so Compose observes every change: [current], [canPop] and [size]
 * are read through the snapshot and trigger recomposition when the stack mutates. Generic over [T];
 * destinations are the skin's own type.
 *
 * @param initial The root destination. [pop] will never remove it - the stack never empties.
 */
class SkinBackStack<T>(initial: T) {
    private val stack: SnapshotStateList<T> = mutableStateListOf(initial)

    /** The top of the stack - the destination currently shown. */
    val current: T
        get() = stack[stack.size - 1]

    /** True when there is a destination beneath the current one to pop back to. */
    val canPop: Boolean
        get() = stack.size > 1

    /** Number of destinations currently on the stack (at least 1). */
    val size: Int
        get() = stack.size

    /** Pushes [destination] onto the stack, making it the current destination. */
    fun push(destination: T) {
        stack.add(destination)
    }

    /**
     * Pops back to the previous destination.
     *
     * @return true if the stack was popped; false when already at the root (in which case the stack
     *   is left untouched and never emptied).
     */
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.size - 1)
        return true
    }

    /** Discards the current history and makes [destination] the only entry (the root). */
    fun resetTo(destination: T) {
        stack.clear()
        stack.add(destination)
    }
}

/**
 * Creates a [SkinBackStack] remembered for this composition.
 *
 * Note that [current] / the navigation state a skin's system back handling must act on has to be
 * reachable from `AppSkin.onBackPressed()`, which is **not** composable and therefore cannot read a
 * `rememberSaveable`. So while this helper is convenient inside composition, a hierarchical skin
 * typically also holds its [SkinBackStack] in a field on the skin object (per `AppSkin` rule 2)
 * rather than only in composition - the helper and the field can share the same instance when the
 * field is created once and this returns it.
 */
@Composable
fun <T : Any> rememberSkinBackStack(initial: T): SkinBackStack<T> = remember {
    SkinBackStack(initial)
}
