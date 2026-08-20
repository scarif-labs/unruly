/*
 * Copyright (c) 2026 Shellac Project
 * LyricsRepository.kt is part of Shellac.
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
 
package com.merklegroot.shellac.shellac.engine.lyrics

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import org.shellac.skin.SkinLyricsState
import timber.log.Timber as L

/** Process-lifetime cache and request coalescer for lyrics lookups. */
@Singleton
class LyricsRepository @Inject constructor(private val client: LrcLibClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val cached = mutableMapOf<String, SkinLyricsState>()
    private val inFlight = mutableMapOf<String, Deferred<SkinLyricsState>>()

    suspend fun lookup(songId: String, track: LrcLibTrack): SkinLyricsState {
        var cachedState: SkinLyricsState? = null
        var request: Deferred<SkinLyricsState>? = null

        synchronized(lock) {
            cachedState = cached[songId]
            if (cachedState == null) {
                request =
                    inFlight[songId]
                        ?: scope
                            .async {
                                val result =
                                    try {
                                        client.lookup(track)
                                    } catch (exception: Exception) {
                                        // The user-facing message is deliberately generic, so log
                                        // the real cause - otherwise a lookup that fails for a
                                        // fixable reason (a 4xx from a malformed query, say) is
                                        // indistinguishable from being offline.
                                        L.w(exception, "Lyrics lookup failed")
                                        SkinLyricsState.Failed(failureMessage(exception))
                                    }
                                if (
                                    result is SkinLyricsState.Found ||
                                        result is SkinLyricsState.NotFound
                                ) {
                                    synchronized(lock) { cached[songId] = result }
                                }
                                result
                            }
                            .also { deferred ->
                                inFlight[songId] = deferred
                                deferred.invokeOnCompletion {
                                    synchronized(lock) {
                                        if (inFlight[songId] === deferred) inFlight.remove(songId)
                                    }
                                }
                            }
            }
        }

        if (cachedState != null) return cachedState!!

        val result = request!!.await()
        // Failed results are intentionally not cached. The completion callback normally removes
        // this entry, but doing it here as well closes the tiny race where a new collector arrives
        // just after a failed request completed and before that callback ran.
        if (result is SkinLyricsState.Failed) {
            synchronized(lock) { if (inFlight[songId] === request) inFlight.remove(songId) }
        }
        return result
    }

    private fun failureMessage(exception: Exception): String =
        when (exception) {
            is java.net.SocketTimeoutException -> "Lyrics request timed out"
            is java.io.IOException -> "Lyrics are unavailable right now"
            else -> "Could not load lyrics"
        }
}
