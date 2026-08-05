package org.oxycblt.auxio.shellac.engine.lyrics

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject
import org.oxycblt.auxio.BuildConfig
import org.shellac.skin.SkinLyrics
import org.shellac.skin.SkinLyricsState

data class LrcLibTrack(
    val artist: String,
    val title: String,
    val album: String,
    val durationMs: Long,
)

class LrcLibClient @javax.inject.Inject constructor() {
    fun lookup(track: LrcLibTrack): SkinLyricsState {
        val exact = get(
            path = "/api/get",
            parameters =
                listOf(
                    "artist_name" to track.artist,
                    "track_name" to track.title,
                    "album_name" to track.album,
                    // The engine and SkinSong use milliseconds; LRCLIB expects seconds.
                    "duration" to (track.durationMs.coerceAtLeast(0L) / 1_000L).toString(),
                ),
        )

        val entry =
            when (exact.status) {
                HttpURLConnection.HTTP_OK -> parseEntry(JSONObject(exact.body))
                HttpURLConnection.HTTP_NOT_FOUND -> search(track)
                else -> throw IOException("LRCLIB exact lookup returned HTTP ${exact.status}")
            }
                ?: return SkinLyricsState.NotFound

        if (entry.instrumental) return SkinLyricsState.NotFound

        val synced = entry.syncedLyrics?.let(::parseLrc).orEmpty()
        val plain = entry.plainLyrics?.lines() ?: synced.map { it.text }
        if (synced.isEmpty() && plain.all { it.isEmpty() }) return SkinLyricsState.NotFound

        return SkinLyricsState.Found(SkinLyrics(plain = plain, synced = synced))
    }

    private fun search(track: LrcLibTrack): Entry? {
        val response =
            get(
                path = "/api/search",
                parameters =
                    listOf(
                        "track_name" to track.title,
                        "artist_name" to track.artist,
                    ),
            )
        if (response.status == HttpURLConnection.HTTP_NOT_FOUND) return null
        if (response.status != HttpURLConnection.HTTP_OK) {
            throw IOException("LRCLIB search returned HTTP ${response.status}")
        }

        val durationSeconds = track.durationMs.coerceAtLeast(0L) / 1_000L
        val results = JSONArray(response.body)
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            val candidateDuration = item.optLong("duration", Long.MIN_VALUE)
            if (candidateDuration == Long.MIN_VALUE || abs(candidateDuration - durationSeconds) > 2L) {
                continue
            }
            return parseEntry(item)
        }
        return null
    }

    private fun parseEntry(json: JSONObject): Entry =
        Entry(
            plainLyrics = json.optNullableString("plainLyrics"),
            syncedLyrics = json.optNullableString("syncedLyrics"),
            instrumental = json.optBoolean("instrumental", false),
        )

    private fun get(path: String, parameters: List<Pair<String, String>>): Response {
        val query =
            parameters.joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }
        val connection = (URL("$BASE_URL$path?$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty(
                "User-Agent",
                "Shellac/${BuildConfig.VERSION_NAME} (https://github.com/oxycblt/auxio)",
            )
        }
        return try {
            val status = connection.responseCode
            val body =
                if (status in 200..299) {
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    ""
                }
            Response(status, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private data class Response(val status: Int, val body: String)

    private data class Entry(
        val plainLyrics: String?,
        val syncedLyrics: String?,
        val instrumental: Boolean,
    )

    private companion object {
        const val BASE_URL = "https://lrclib.net"
        const val TIMEOUT_MS = 5_000
    }
}

private fun JSONObject.optNullableString(name: String): String? {
    val value = opt(name)
    return value as? String
}
