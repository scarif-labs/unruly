/*
 * Copyright (c) 2026 Unruly Project
 * SkinStates.kt is part of Unruly.
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
import org.unruly.skin.LibraryLoadState

/**
 * Gates a screen on the whole-library indexing state.
 *
 * Note: [LibraryLoadState] **outranks** paging's own load state. Paging's load state describes
 * fetching one page from an already-built library; [LibraryLoadState] describes whether there is a
 * library at all (see `SkinLibrary.loadState`'s documentation). Gate the whole screen on this and
 * use paging's state only for in-list append spinners.
 *
 * This is a pure control-flow helper, not UI: every branch is a caller-supplied composable and
 * nothing of our own is rendered.
 *
 * @param loadState The current [LibraryLoadState].
 * @param scanning Composed while indexing. [Idle] is rendered the same as scanning with
 *   indeterminate progress - a skin need not care about the distinction.
 * @param failed Composed when indexing failed, with the error [message].
 * @param empty Composed when indexing finished but the library is empty.
 * @param ready Composed when indexing finished and the library has content.
 */
@Composable
fun SkinLibraryGate(
    loadState: LibraryLoadState,
    scanning: @Composable (loaded: Int?, explored: Int?) -> Unit,
    failed: @Composable (message: String) -> Unit,
    empty: @Composable () -> Unit,
    ready: @Composable () -> Unit,
) {
    when (loadState) {
        // Idle means no scan has been attempted; render it indistinguishably from an
        // indeterminate scan so a skin does not have to special-case it.
        LibraryLoadState.Idle -> scanning(null, null)
        is LibraryLoadState.Scanning -> scanning(loadState.loaded, loadState.explored)
        is LibraryLoadState.Failed -> failed(loadState.message)
        is LibraryLoadState.Ready -> if (loadState.isEmpty) empty() else ready()
    }
}

/**
 * Standard "no results" hook for a search/screen that found nothing for [query].
 *
 * A pass-through control-flow helper: [content] is a caller-supplied composable that receives the
 * [query] so each skin can style its own empty-state message.
 */
@Composable
fun SkinNoResults(query: String, content: @Composable (String) -> Unit) {
    content(query)
}
