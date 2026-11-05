/*
 * Copyright (c) 2026 Unruly Project
 * WebSkinBridge.kt is part of Unruly.
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

package com.merklegroot.unruly.unruly.web

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.unruly.skin.LibraryLoadState
import org.unruly.skin.SkinAlbum
import org.unruly.skin.SkinArtist
import org.unruly.skin.SkinGenre
import org.unruly.skin.SkinLyricsState
import org.unruly.skin.SkinPlaybackState
import org.unruly.skin.SkinPlaylist
import org.unruly.skin.SkinRepeatMode
import org.unruly.skin.SkinSearchResults
import org.unruly.skin.SkinSession
import org.unruly.skin.SkinSong
import org.unruly.skin.SkinSort
import timber.log.Timber as L

/**
 * The Kotlin half of `window.unruly`. The JS half is `bridge.js` (in the skill kit, packaged into
 * assets), which turns these messages into the API skins see.
 *
 * Wire format, both directions JSON strings over the origin-scoped message channel:
 * - JS → us: `{t:"hello"}` once per page load, `{t:"call", id, m, a:[...]}` for API calls.
 * - us → JS: `{t:"res", id, ok, v | err}` answering a call, `{t:"ev", n, v}` for state pushes.
 *
 * Every method name in [call] must have a matching entry in bridge.js and in `unruly.d.ts`.
 * Arguments arrive untyped from a page we don't control, so every read is defensive and a bad call
 * rejects its promise instead of throwing here.
 */
internal class WebSkinBridge(
    private val context: Context,
    private val skin: WebSkin,
    private val session: SkinSession,
    private val webView: WebView,
    private val scope: CoroutineScope,
    private val artBase: String,
    /** Called on each page load's hello: the skin's own code is running. */
    private val onHello: () -> Unit,
) {
    private var reply: JavaScriptReplyProxy? = null
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Song lookup for the calls that take ids; rebuilt when the library changes. */
    private var songsById: Map<String, SkinSong>? = null

    /** The last state pushed, so a fresh page gets it all on hello. */
    private var lastPlayback: SkinPlaybackState? = null
    private var lastQueueIds: List<String>? = null
    private var insets = JSONObject().put("top", 0).put("right", 0).put("bottom", 0).put("left", 0)
    private var visible = true

    fun onMessage(data: String, proxy: JavaScriptReplyProxy) {
        val msg =
            try {
                JSONObject(data)
            } catch (e: Exception) {
                return
            }
        when (msg.optString("t")) {
            "hello" -> {
                reply = proxy
                skin.canGoBack = false
                lastQueueIds = null
                emitAll()
                onHello()
            }
            "call" -> {
                val id = msg.optInt("id")
                val method = msg.optString("m")
                val args = msg.optJSONArray("a") ?: JSONArray()
                scope.launch {
                    val result = runCatching { call(method, args) }
                    val out = JSONObject().put("t", "res").put("id", id)
                    result
                        .onSuccess { out.put("ok", true).put("v", it ?: JSONObject.NULL) }
                        .onFailure {
                            L.w(it, "Web skin call $method failed")
                            out.put("ok", false).put("err", it.message ?: it.javaClass.simpleName)
                        }
                    send(out)
                }
            }
        }
    }

    private fun send(message: JSONObject) {
        // The proxy must be used on the main thread; callers may be anywhere.
        scope.launch(Dispatchers.Main.immediate) {
            runCatching { reply?.postMessage(message.toString()) }
        }
    }

    private fun emit(name: String, value: Any?) {
        if (reply == null) return
        send(JSONObject().put("t", "ev").put("n", name).put("v", value ?: JSONObject.NULL))
    }

    private fun emitAll() {
        lastPlayback?.let { pushPlayback(it, force = true) }
        emit("insets", insets)
        emit("loadState", session.library.loadState.value.toJson())
        emit("volume", volumeJson())
        emit("visibility", visible)
    }

    // ---- state pushes, driven by WebSkinView ----

    private var lastPushMs = 0L

    fun pushPlayback(state: SkinPlaybackState, force: Boolean = false) {
        val previous = lastPlayback
        lastPlayback = state
        val queueIds = state.queue.map { it.id }
        // The queue can be thousands of songs, so it only goes out when it (or the index) moves.
        if (force || queueIds != lastQueueIds || previous?.queueIndex != state.queueIndex) {
            lastQueueIds = queueIds
            emit(
                "queue",
                JSONObject().put("items", songsJson(state.queue)).put("index", state.queueIndex),
            )
        }
        // Position ticks at 10 Hz; skins interpolate, so 4 Hz is plenty unless something else
        // changed, which always goes out at once.
        val now = System.currentTimeMillis()
        val onlyPosition =
            previous != null &&
                previous.copy(positionMs = state.positionMs, queue = state.queue) == state
        if (!force && onlyPosition && now - lastPushMs < 250) return
        lastPushMs = now
        emit("playback", playbackJson(state))
    }

    fun pushLoadState(state: LibraryLoadState) = emit("loadState", state.toJson())

    fun pushLibraryChanged() {
        songsById = null
        emit("library", null)
    }

    fun pushInsets(top: Float, right: Float, bottom: Float, left: Float) {
        insets =
            JSONObject().put("top", top).put("right", right).put("bottom", bottom).put("left", left)
        emit("insets", insets)
    }

    fun pushVisibility(visible: Boolean) {
        this.visible = visible
        emit("visibility", visible)
        if (visible) emit("volume", volumeJson())
    }

    fun pushBack() = emit("back", null)

    // ---- calls ----

    private suspend fun call(method: String, a: JSONArray): Any? {
        val commands = session.commands
        val library = session.library
        return when (method) {
            // Playback
            "playPause" -> commands.playPause().let { null }
            "next" -> commands.next().let { null }
            "previous" -> commands.previous().let { null }
            "seekTo" -> commands.seekTo(a.getLong(0)).let { null }
            "toggleShuffle" -> commands.toggleShuffle().let { null }
            "cycleRepeatMode" -> commands.cycleRepeatMode().let { null }
            "setRepeatMode" ->
                commands.setRepeatMode(SkinRepeatMode.valueOf(a.getString(0).uppercase())).let {
                    null
                }
            "playAlbum" -> commands.playAlbum(a.getString(0), a.optInt(1, 0)).let { null }
            "playArtist" -> commands.playArtist(a.getString(0)).let { null }
            "playGenre" -> commands.playGenre(a.getString(0)).let { null }
            "playPlaylist" -> commands.playPlaylist(a.getString(0)).let { null }
            "playSongs" -> commands.playSongs(songs(a.getJSONArray(0)), a.optInt(1, 0)).let { null }
            "playSongFromAll" -> commands.playSongFromAll(a.getString(0)).let { null }
            "playSongFromAlbum" ->
                commands.playSongFromAlbum(a.getString(0), a.getString(1)).let { null }
            "playSongFromArtist" ->
                commands.playSongFromArtist(a.getString(0), a.getString(1)).let { null }
            "playSongFromGenre" ->
                commands.playSongFromGenre(a.getString(0), a.getString(1)).let { null }
            "playSongFromPlaylist" ->
                commands.playSongFromPlaylist(a.getString(0), a.getString(1)).let { null }
            "shuffleAll" -> commands.shuffleAll().let { null }
            // Queue
            "playQueueItem" -> commands.playQueueItem(a.getInt(0)).let { null }
            "addToQueue" -> commands.addToQueue(songs(a.getJSONArray(0))).let { null }
            "playNext" -> commands.playNext(songs(a.getJSONArray(0))).let { null }
            "removeFromQueue" -> commands.removeFromQueue(a.getInt(0)).let { null }
            "moveInQueue" -> commands.moveInQueue(a.getInt(0), a.getInt(1)).let { null }
            // Playlists
            "createPlaylist" ->
                commands
                    .createPlaylist(
                        a.optString(0).takeIf { !a.isNull(0) && it.isNotBlank() },
                        a.optJSONArray(1)?.let { songs(it) }.orEmpty(),
                    )
                    .let { null }
            "addToPlaylist" ->
                commands.addToPlaylist(a.getString(0), songs(a.getJSONArray(1))).let { null }
            "renamePlaylist" -> commands.renamePlaylist(a.getString(0), a.getString(1)).let { null }
            "deletePlaylist" -> commands.deletePlaylist(a.getString(0)).let { null }
            "removeFromPlaylist" ->
                commands.removeFromPlaylist(a.getString(0), a.getString(1), a.getInt(2)).let {
                    null
                }
            // Library
            "artists" -> page(a, 0) { artistsJson(library.artistList(sort(it))) }
            "albums" -> page(a, 0) { albumsJson(library.albumList(sort(it))) }
            "songs" -> page(a, 0) { songsJson(library.songList(sort(it))) }
            "genres" -> page(a, 0) { genresJson(library.genreList()) }
            "playlists" -> page(a, 0) { playlistsJson(library.playlistList()) }
            "albumsByArtist" -> page(a, 1) { albumsJson(library.albumsByArtistList(a.getString(0))) }
            "songsByGenre" -> page(a, 1) { songsJson(library.songsByGenreList(a.getString(0))) }
            "songsByAlbum" -> bg { songsJson(library.songsByAlbum(a.getString(0)).first()) }
            "songsByPlaylist" -> bg { songsJson(library.songsByPlaylist(a.getString(0)).first()) }
            "search" -> bg { library.search(a.getString(0)).first().toJson() }
            "findAlbum" -> library.findAlbum(a.getString(0))?.toJson()
            "findArtist" -> library.findArtist(a.getString(0))?.toJson()
            "lyrics" ->
                session.lyrics
                    .lyrics(a.getString(0))
                    .first { it !is SkinLyricsState.Loading }
                    .toJson()
            // Host
            "openSettings" -> withContext(Dispatchers.Main) { session.openAppSettings() }.let { null }
            "setCanGoBack" -> {
                skin.canGoBack = a.optBoolean(0)
                null
            }
            "setSystemBars" ->
                withContext(Dispatchers.Main) {
                        skin.setSystemBarIcons(context, a.optJSONObject(0)?.optString("icons"))
                    }
                    .let { null }
            "haptic" -> withContext(Dispatchers.Main) { haptic(a.optString(0)) }.let { null }
            "setVolume" -> {
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val level = (a.getDouble(0).coerceIn(0.0, 1.0) * max).toInt()
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
                volumeJson().also { emit("volume", it) }
            }
            "adjustVolume" -> {
                val direction =
                    if (a.getInt(0) >= 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
                volumeJson().also { emit("volume", it) }
            }
            else -> throw IllegalArgumentException("Unknown unruly method \"$method\"")
        }
    }

    /** Sorting and mapping a whole library is real work; keep it off the main thread. */
    private suspend fun page(a: JSONArray, at: Int, all: suspend (JSONObject?) -> JSONArray): Any {
        val options = a.optJSONObject(at)
        val items = withContext(Dispatchers.Default) { all(options) }
        val offset = options?.optInt("offset", 0)?.coerceAtLeast(0) ?: 0
        val limit = options?.optInt("limit", 200)?.coerceIn(1, 1000) ?: 200
        val slice = JSONArray()
        for (i in offset until minOf(items.length(), offset + limit)) slice.put(items.get(i))
        return JSONObject().put("items", slice).put("total", items.length()).put("offset", offset)
    }

    private suspend fun <T> bg(block: suspend () -> T): T = withContext(Dispatchers.Default) { block() }

    private fun sort(options: JSONObject?): SkinSort {
        val sort = options?.optJSONObject("sort") ?: return SkinSort.DEFAULT
        val by =
            when (sort.optString("by")) {
                "artist" -> SkinSort.By.ARTIST
                "album" -> SkinSort.By.ALBUM
                "year" -> SkinSort.By.YEAR
                "duration" -> SkinSort.By.DURATION
                "dateAdded" -> SkinSort.By.DATE_ADDED
                else -> SkinSort.By.NAME
            }
        return SkinSort(by, sort.optBoolean("ascending", true))
    }

    private suspend fun songs(ids: JSONArray): List<SkinSong> {
        val byId =
            songsById ?: bg { session.library.songList().associateBy { it.id } }.also { songsById = it }
        return (0 until ids.length()).mapNotNull { byId[ids.optString(it)] }
    }

    private fun haptic(kind: String) {
        val constant =
            when (kind) {
                "tick" -> HapticFeedbackConstants.CLOCK_TICK
                "long" -> HapticFeedbackConstants.LONG_PRESS
                else -> HapticFeedbackConstants.KEYBOARD_TAP
            }
        webView.performHapticFeedback(constant)
    }

    private fun volumeJson(): JSONObject {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        return JSONObject().put("level", level).put("max", max).put("fraction", level / max.toDouble())
    }

    // ---- JSON ----

    /**
     * The cover id is everything after `/covers/` in the art URI, verbatim: folder-cover ids are a
     * whole `fs:content://...` URI, slashes and all. It is encoded once for our URL, and WebView
     * decodes the path once before [WebSkin] sees it, handing back the stored id exactly.
     */
    private fun art(uri: Uri?): Any =
        uri?.encodedPath?.substringAfter("/covers/", "")?.takeIf { it.isNotEmpty() }?.let {
            artBase + Uri.encode(it)
        } ?: JSONObject.NULL

    private fun playbackJson(s: SkinPlaybackState) =
        JSONObject()
            .put("song", s.song?.toJson() ?: JSONObject.NULL)
            .put("isPlaying", s.isPlaying)
            .put("positionMs", s.positionMs)
            .put("durationMs", s.durationMs)
            .put("queueIndex", s.queueIndex)
            .put("repeatMode", s.repeatMode.name.lowercase())
            .put("isShuffled", s.isShuffled)

    private fun SkinSong.toJson() =
        JSONObject()
            .put("id", id)
            .put("title", title)
            .put("artist", artist)
            .put("album", album)
            .put("albumId", albumId)
            .put("artistId", artistId)
            .put("art", art(albumArtUri))
            .put("durationMs", durationMs)
            .put("trackNumber", trackNumber ?: JSONObject.NULL)
            .put("discNumber", discNumber ?: JSONObject.NULL)
            .put("year", year ?: JSONObject.NULL)

    private fun SkinAlbum.toJson() =
        JSONObject()
            .put("id", id)
            .put("title", title)
            .put("artist", artist)
            .put("artistId", artistId)
            .put("art", art(albumArtUri))
            .put("songCount", songCount)
            .put("durationMs", durationMs)
            .put("year", year ?: JSONObject.NULL)
            .put("isExplicitForArtist", isExplicitForArtist)

    private fun SkinArtist.toJson() =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("art", art(albumArtUri))
            .put("albumCount", albumCount)
            .put("songCount", songCount)

    private fun SkinGenre.toJson() =
        JSONObject().put("id", id).put("name", name).put("art", art(albumArtUri)).put(
            "songCount",
            songCount,
        )

    private fun SkinPlaylist.toJson() =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("art", art(albumArtUri))
            .put("songCount", songCount)
            .put("durationMs", durationMs)

    private fun songsJson(list: List<SkinSong>) = JSONArray().apply { list.forEach { put(it.toJson()) } }

    private fun albumsJson(list: List<SkinAlbum>) =
        JSONArray().apply { list.forEach { put(it.toJson()) } }

    private fun artistsJson(list: List<SkinArtist>) =
        JSONArray().apply { list.forEach { put(it.toJson()) } }

    private fun genresJson(list: List<SkinGenre>) =
        JSONArray().apply { list.forEach { put(it.toJson()) } }

    private fun playlistsJson(list: List<SkinPlaylist>) =
        JSONArray().apply { list.forEach { put(it.toJson()) } }

    private fun SkinSearchResults.toJson() =
        JSONObject()
            .put("songs", songsJson(songs))
            .put("albums", albumsJson(albums))
            .put("artists", artistsJson(artists))
            .put("genres", genresJson(genres))
            .put("playlists", playlistsJson(playlists))

    private fun LibraryLoadState.toJson(): JSONObject =
        when (this) {
            LibraryLoadState.Idle -> JSONObject().put("state", "idle")
            is LibraryLoadState.Scanning ->
                JSONObject()
                    .put("state", "scanning")
                    .put("loaded", loaded ?: JSONObject.NULL)
                    .put("explored", explored ?: JSONObject.NULL)
            is LibraryLoadState.Ready -> JSONObject().put("state", "ready").put("isEmpty", isEmpty)
            is LibraryLoadState.Failed ->
                JSONObject().put("state", "failed").put("message", message)
        }

    private fun SkinLyricsState.toJson(): JSONObject =
        when (this) {
            SkinLyricsState.Disabled -> JSONObject().put("state", "disabled")
            SkinLyricsState.NoTrack -> JSONObject().put("state", "noTrack")
            SkinLyricsState.Loading -> JSONObject().put("state", "loading")
            SkinLyricsState.NotFound -> JSONObject().put("state", "notFound")
            is SkinLyricsState.Failed -> JSONObject().put("state", "failed").put("message", message)
            is SkinLyricsState.Found ->
                JSONObject()
                    .put("state", "found")
                    .put("plain", JSONArray(lyrics.plain))
                    .put(
                        "synced",
                        JSONArray().apply {
                            lyrics.synced.forEach {
                                put(JSONObject().put("startMs", it.startMs).put("text", it.text))
                            }
                        },
                    )
        }
}
