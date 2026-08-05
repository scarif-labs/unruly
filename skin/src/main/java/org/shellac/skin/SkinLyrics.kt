package org.shellac.skin

import kotlinx.coroutines.flow.Flow

/**
 * Lyrics for the playing track.
 *
 * ## Why this lives in the contract rather than in each skin
 *
 * Fetching lyrics means a network request. A skin has no business making one: it would need
 * its own HTTP stack, its own caching, and the app would end up with as many lyric fetchers
 * as it has skins, each with different failure behaviour. The engine side owns the lookup and
 * the cache; a skin only ever observes a state.
 *
 * This is also the first thing in Shellac that touches the network at all. The engine it
 * forked is an entirely offline player, so a lookup necessarily sends the playing track's
 * title, artist and album to a third-party service. That is a real change in what the app
 * does, so it is **user-toggleable** and the contract has an explicit [SkinLyricsState.Disabled]
 * state rather than pretending a switched-off lookup is simply "not found".
 */

/** One timed line of synced lyrics. */
data class SkinLyricLine(
    /** Offset from the start of the track. */
    val startMs: Long,
    val text: String,
)

/**
 * A resolved set of lyrics.
 *
 * [plain] is always populated when lyrics were found. [synced] is populated only when the
 * source supplied timings - many entries are plain text only - so a skin must handle the
 * unsynced case rather than assuming it can highlight a current line.
 */
data class SkinLyrics(
    val plain: List<String>,
    val synced: List<SkinLyricLine>,
) {
    /** True when timings are available and a current line can be highlighted. */
    val isSynced: Boolean get() = synced.isNotEmpty()
}

/**
 * What the lyrics lookup currently knows.
 *
 * Every one of these is a state a skin must be able to draw. In particular [NotFound] is
 * common and entirely normal - most libraries contain tracks no lyrics service has - so it
 * should read as an ordinary outcome, not an error.
 */
sealed interface SkinLyricsState {
    /** Lookup is switched off by the user. Skins should say so, and point at settings. */
    data object Disabled : SkinLyricsState

    /** Nothing is playing, so there is nothing to look up. */
    data object NoTrack : SkinLyricsState

    data object Loading : SkinLyricsState

    data class Found(val lyrics: SkinLyrics) : SkinLyricsState

    /** The lookup succeeded and the service has nothing for this track. */
    data object NotFound : SkinLyricsState

    /** The lookup failed - offline, timeout, service error. [message] is user-presentable. */
    data class Failed(val message: String) : SkinLyricsState
}

/**
 * Lyrics lookup for a track.
 *
 * Implementations must be safe to call from composition: the returned [Flow] is cold, and
 * the adapter is responsible for caching so that re-collecting for the same track does not
 * re-issue a network request.
 *
 * As everywhere else in the contract, tracks are named by id and all resolution happens in
 * the adapter (DECISION D4).
 */
interface SkinLyricsSource {
    fun lyrics(songId: String): Flow<SkinLyricsState>
}
