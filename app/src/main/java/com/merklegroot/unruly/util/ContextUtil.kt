/*
 * Copyright (c) 2021 Unruly Project
 * ContextUtil.kt is part of Unruly.
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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.annotation.DimenRes
import androidx.annotation.IntegerRes
import androidx.annotation.PluralsRes
import androidx.annotation.Px
import androidx.core.content.ContextCompat
import com.merklegroot.unruly.IntegerTable
import com.merklegroot.unruly.R
import com.merklegroot.unruly.unruly.host.UnrulyActivity
import kotlin.reflect.KClass

/** Whether the device is in landscape mode or not. */
val Context.isLandscape
    get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

/**
 * Concatenate values in a list together in a localized manner.
 *
 * @param context [Context] require.d
 * @param map Function to map the [T] values to a string value to be concatenated.
 */
inline fun <T> List<T>.concatLocalized(context: Context, map: (T) -> String): String {
    if (isEmpty()) {
        // Nothing to do.
        return ""
    }

    var joined = map(first())
    for (i in 1..lastIndex) {
        // Chain all previous values with the next value in the list with another delimiter.
        joined = context.getString(R.string.fmt_list, joined, map(get(i)))
    }
    return joined
}

/**
 * @param pluralRes A plural resource ID.
 * @param value Int value for the plural.
 * @return The formatted string requested.
 * @brief Get a plural resource.
 */
fun Context.getPlural(@PluralsRes pluralRes: Int, value: Int) =
    resources.getQuantityString(pluralRes, value, value)

/**
 * @param integerRes An integer resource ID.
 * @return The integer resource requested.
 * @brief Get an integer resource.
 */
fun Context.getInteger(@IntegerRes integerRes: Int) = resources.getInteger(integerRes)

/**
 * Get the pixel size of a dimension.
 *
 * @param dimenRes The dimension resource
 * @return The size of the dimension requested, in pixels
 */
@Px fun Context.getDimenPixels(@DimenRes dimenRes: Int) = resources.getDimensionPixelSize(dimenRes)

/**
 * Get an instance of the requested system service.
 *
 * @param T The system service in question.
 * @param serviceClass The service's kotlin class [Java class will be used in function call]
 * @return The system service
 * @throws IllegalArgumentException If the system service cannot be retrieved.
 */
fun <T : Any> Context.getSystemServiceCompat(serviceClass: KClass<T>) =
    requireNotNull(ContextCompat.getSystemService(this, serviceClass.java)) {
        "System service ${serviceClass.simpleName} could not be instantiated"
    }

/**
 * Create a [PendingIntent] that will launch the app activity when launched.
 *
 * Targets [UnrulyActivity], NOT Unruly's original [com.merklegroot.unruly.MainActivity].
 *
 * This is what the playback notification uses as its content intent
 * (`MediaSessionHolder.setContentIntent`), so it is the tap target on the lock screen and in the
 * notification shade. Pointing it at MainActivity meant tapping the notification dropped the user
 * into the pre-Unruly UI - a second, parallel interface with no skin and no way to reach Unruly
 * settings - while the launcher icon opened the real one. Two different UIs for the same running
 * app, depending on how you got in.
 */
fun Context.newMainPendingIntent(): PendingIntent =
    PendingIntent.getActivity(
        this,
        IntegerTable.REQUEST_CODE,
        Intent(this, UnrulyActivity::class.java).setAction(Intent.ACTION_MAIN),
        PendingIntent.FLAG_IMMUTABLE,
    )

/**
 * Create a [PendingIntent] that will broadcast the specified command when launched.
 *
 * @param action The action to broadcast when the [PendingIntent] is launched.
 */
fun Context.newBroadcastPendingIntent(action: String): PendingIntent =
    PendingIntent.getBroadcast(
        this,
        IntegerTable.REQUEST_CODE,
        Intent(action).setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY),
        PendingIntent.FLAG_IMMUTABLE,
    )
