/*
 * Copyright (c) 2023 Unruly Project
 * UpdateInstructions.kt is part of Unruly.
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
 
package com.merklegroot.unruly.list.adapter

/** How a list consumer should apply an update. Produced by the playback and library layers. */
sealed interface UpdateInstructions {
    /** Use an asynchronous diff. Useful for unpredictable updates, but looks chaotic and janky. */
    data object Diff : UpdateInstructions

    /**
     * Visually replace all items from a given point. More visually coherent than [Diff].
     *
     * @param from The index at which to start replacing items (inclusive)
     */
    data class Replace(val from: Int) : UpdateInstructions

    /**
     * Add a new set of items.
     *
     * @param at The position at which to add.
     * @param size The amount of items to add.
     */
    data class Add(val at: Int, val size: Int) : UpdateInstructions

    /**
     * Move one item to another location.
     *
     * @param from The index of the item to move.
     * @param to The index to move the item to.
     */
    data class Move(val from: Int, val to: Int) : UpdateInstructions

    /**
     * Remove an item.
     *
     * @param at The location that the item should be removed from.
     * @param size The amount of items to add.
     */
    data class Remove(val at: Int, val size: Int) : UpdateInstructions
}
