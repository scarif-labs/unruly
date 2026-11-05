/*
 * Copyright (c) 2026 Unruly Project
 * SkinAlbumArt.kt is part of Unruly.
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

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

/**
 * Album-art image wrapper around Coil, owned by the skin support layer.
 *
 * The whole point of this wrapper is **no reload flicker on recomposition**: the [ImageRequest] is
 * built inside a [remember] keyed on [uri], so any unrelated recomposition (a slider moving, a
 * state tick, an update to a sibling) reuses the same request object instead of restarting the
 * image load. Callers should not reach for Coil directly; loading a `content://` URI needs no Auxio
 * type, but keeping it here keeps the Coil dependency confined behind one skin-visible entry point.
 *
 * When [uri] is null, or the load fails, [fallback] is composed instead. [fallback] is a
 * caller-supplied composable so each skin styles its own placeholder - nothing is hardcoded here. A
 * missing or corrupt file must never crash the skin, so the only way to reach [fallback] from a
 * failed load is through AsyncImage's error path, never an exception escaping this composable.
 *
 * @param uri The album-art content URI, or null (renders [fallback] and never touches Coil).
 * @param contentDescription Accessibility description, passed straight to [AsyncImage].
 * @param modifier Applied to the [AsyncImage] (and, when rendering, the fallback).
 * @param contentScale How the image is fit into its slot; defaults to [ContentScale.Crop].
 * @param fallback Caller-supplied composable shown for a null uri or a load error.
 */
@Composable
fun SkinAlbumArt(
    uri: Uri?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit = {},
) {
    // Remember the built request on the uri so an unrelated recomposition does not
    // restart the load. This is the sole reason this wrapper exists.
    // LocalContext must be read in composable scope; a remember{} lambda is NOT one.
    // Reading it inside the lambda fails with
    // "@Composable invocations can only happen from the context of a @Composable function".
    val context = LocalContext.current
    val request =
        remember(uri, context) {
            if (uri == null) {
                null
            } else {
                ImageRequest.Builder(context).data(uri).build()
            }
        }

    // Track load failures so we can swap in the caller-supplied fallback composable.
    // Coil's AsyncImage `error` slot takes a Painter, not a composable, so we detect the
    // failure via the onError callback and render the composable fallback ourselves. The
    // state is keyed on the uri too, so a new uri starts clean (no stale error).
    var hasFailed by remember(uri) { mutableStateOf(false) }

    when {
        request == null || hasFailed -> fallback()
        else ->
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                onError = { hasFailed = true },
            )
    }
}
