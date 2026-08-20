/*
 * Copyright (c) 2021 Shellac Project
 * SearchAdapter.kt is part of Shellac.
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
 
package com.merklegroot.shellac.search

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.merklegroot.shellac.list.BasicHeader
import com.merklegroot.shellac.list.Item
import com.merklegroot.shellac.list.PlainDivider
import com.merklegroot.shellac.list.SelectableListListener
import com.merklegroot.shellac.list.adapter.SelectionIndicatorAdapter
import com.merklegroot.shellac.list.adapter.SimpleDiffCallback
import com.merklegroot.shellac.list.recycler.AlbumViewHolder
import com.merklegroot.shellac.list.recycler.ArtistViewHolder
import com.merklegroot.shellac.list.recycler.BasicHeaderViewHolder
import com.merklegroot.shellac.list.recycler.DividerViewHolder
import com.merklegroot.shellac.list.recycler.GenreViewHolder
import com.merklegroot.shellac.list.recycler.PlaylistViewHolder
import com.merklegroot.shellac.list.recycler.SongViewHolder
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song

/**
 * An adapter that displays search results.
 *
 * @param listener An [SelectableListListener] to bind interactions to.
 * @author Alexander Capehart (OxygenCobalt)
 */
class SearchAdapter(private val listener: SelectableListListener<Music>) :
    SelectionIndicatorAdapter<Item, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    override fun getItemViewType(position: Int) =
        when (getItem(position)) {
            is Song -> SongViewHolder.VIEW_TYPE
            is Album -> AlbumViewHolder.VIEW_TYPE
            is Artist -> ArtistViewHolder.VIEW_TYPE
            is Genre -> GenreViewHolder.VIEW_TYPE
            is Playlist -> PlaylistViewHolder.VIEW_TYPE
            is PlainDivider -> DividerViewHolder.VIEW_TYPE
            is BasicHeader -> BasicHeaderViewHolder.VIEW_TYPE
            else -> super.getItemViewType(position)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        when (viewType) {
            SongViewHolder.VIEW_TYPE -> SongViewHolder.from(parent)
            AlbumViewHolder.VIEW_TYPE -> AlbumViewHolder.from(parent)
            ArtistViewHolder.VIEW_TYPE -> ArtistViewHolder.from(parent)
            GenreViewHolder.VIEW_TYPE -> GenreViewHolder.from(parent)
            PlaylistViewHolder.VIEW_TYPE -> PlaylistViewHolder.from(parent)
            DividerViewHolder.VIEW_TYPE -> DividerViewHolder.from(parent)
            BasicHeaderViewHolder.VIEW_TYPE -> BasicHeaderViewHolder.from(parent)
            else -> error("Invalid item type $viewType")
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is Song -> (holder as SongViewHolder).bind(item, listener)
            is Album -> (holder as AlbumViewHolder).bind(item, listener)
            is Artist -> (holder as ArtistViewHolder).bind(item, listener)
            is Genre -> (holder as GenreViewHolder).bind(item, listener)
            is Playlist -> (holder as PlaylistViewHolder).bind(item, listener)
            is BasicHeader -> (holder as BasicHeaderViewHolder).bind(item)
        }
    }

    private companion object {
        /** A comparator that can be used with DiffUtil. */
        val DIFF_CALLBACK =
            object : SimpleDiffCallback<Item>() {
                override fun areContentsTheSame(oldItem: Item, newItem: Item) =
                    when {
                        oldItem is Song && newItem is Song ->
                            SongViewHolder.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)
                        oldItem is Album && newItem is Album ->
                            AlbumViewHolder.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)
                        oldItem is Artist && newItem is Artist ->
                            ArtistViewHolder.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)
                        oldItem is Genre && newItem is Genre ->
                            GenreViewHolder.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)
                        oldItem is Playlist && newItem is Playlist ->
                            PlaylistViewHolder.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)
                        oldItem is PlainDivider && newItem is PlainDivider ->
                            DividerViewHolder.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)
                        oldItem is BasicHeader && newItem is BasicHeader ->
                            BasicHeaderViewHolder.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)
                        else -> false
                    }
            }
    }
}
