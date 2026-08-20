/*
 * Copyright (c) 2023 Shellac Project
 * AudioPreferenceFragment.kt is part of Shellac.
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
 
package com.merklegroot.shellac.settings.categories

import androidx.navigation.fragment.findNavController
import com.merklegroot.shellac.R
import com.merklegroot.shellac.settings.BasePreferenceFragment
import com.merklegroot.shellac.settings.ui.WrappedDialogPreference
import com.merklegroot.shellac.util.navigateSafe
import timber.log.Timber as L

/**
 * Audio settings interface.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
class AudioPreferenceFragment : BasePreferenceFragment(R.xml.preferences_audio) {

    override fun onOpenDialogPreference(preference: WrappedDialogPreference) {
        if (preference.key == getString(R.string.set_key_pre_amp)) {
            L.d("Navigating to pre-amp dialog")
            findNavController().navigateSafe(AudioPreferenceFragmentDirections.preAmpSettings())
        }
    }
}
