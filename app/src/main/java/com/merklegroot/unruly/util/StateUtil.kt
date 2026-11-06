/*
 * Copyright (c) 2023 Unruly Project
 * StateUtil.kt is part of Unruly.
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
 
package com.merklegroot.unruly.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A wrapper around [StateFlow] exposing a one-time consumable event.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
interface Event<T> {
    /** The inner [StateFlow] contained by the [Event]. */
    val flow: StateFlow<T?>

    /**
     * Consume whatever value is currently contained by this instance.
     *
     * @return A value placed into this instance prior, or null if there isn't any.
     */
    fun consume(): T?
}

/**
 * A wrapper around [StateFlow] exposing a one-time consumable event that can be modified by it's
 * owner.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
class MutableEvent<T> : Event<T> {
    override val flow = MutableStateFlow<T?>(null)

    override fun consume() = flow.value?.also { flow.value = null }

    /**
     * Place a new value into this instance, replacing any prior value.
     *
     * @param v The value to update with.
     */
    fun put(v: T) {
        flow.value = v
    }
}
