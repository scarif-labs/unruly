/*
 * Copyright (c) 2022 Shellac Project
 * SelectionFragment.kt is part of Shellac.
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
 
package com.merklegroot.shellac.list

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.viewbinding.ViewBinding
import com.merklegroot.shellac.R
import com.merklegroot.shellac.music.MusicViewModel
import com.merklegroot.shellac.playback.PlaybackViewModel
import com.merklegroot.shellac.ui.ShellacToolbar
import com.merklegroot.shellac.ui.ViewBindingFragment
import com.merklegroot.shellac.util.showToast

/**
 * A subset of ListFragment that implements aspects of the selection UI.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
abstract class SelectionFragment<VB : ViewBinding> :
    ViewBindingFragment<VB>(), Toolbar.OnMenuItemClickListener {
    protected abstract val listModel: ListViewModel
    protected abstract val musicModel: MusicViewModel
    protected abstract val playbackModel: PlaybackViewModel

    open fun getSelectionToolbar(binding: VB): ShellacToolbar? = null

    override fun onBindingCreated(binding: VB, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)
        getSelectionToolbar(binding)?.apply {
            // Add cancel and menu item listeners to manage what occurs with the selection.
            setNavigationOnClickListener { listModel.dropSelection() }
            setOnMenuItemClickListener(this@SelectionFragment)
            setOnOverflowMenuClick {
                listModel.openMenu(R.menu.selection, listModel.peekSelection())
            }
        }
    }

    override fun onDestroyBinding(binding: VB) {
        super.onDestroyBinding(binding)
        getSelectionToolbar(binding)?.setOnMenuItemClickListener(null)
    }

    override fun onMenuItemClick(item: MenuItem) =
        when (item.itemId) {
            R.id.action_selection_play_next -> {
                playbackModel.playNext(listModel.takeSelection())
                requireContext().showToast(R.string.lng_play_next)
                true
            }
            R.id.action_selection_playlist_add -> {
                musicModel.addToPlaylist(listModel.takeSelection())
                true
            }
            else -> false
        }

    // TODO: Re-add the automatic selection handling
}
