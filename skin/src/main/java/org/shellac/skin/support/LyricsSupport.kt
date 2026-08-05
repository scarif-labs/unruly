package org.shellac.skin.support

import org.shellac.skin.SkinLyricLine

/**
 * Index of the line that should be highlighted at [positionMs], or `-1` when the track has
 * not reached the first line yet.
 *
 * Shared because every skin showing synced lyrics needs exactly this, and the edges are easy
 * to get wrong:
 *
 *  - **Before the first line.** Songs routinely open with an instrumental bar, so the correct
 *    answer is "no line yet" (`-1`), not "line 0". Highlighting the first line during the
 *    intro makes the whole display look one line ahead.
 *  - **Exactly on a boundary.** A line starting at exactly [positionMs] is the active one, so
 *    the comparison is `<=`, not `<`. With 10 Hz position updates a sample lands on a
 *    boundary often enough to notice.
 *  - **After the last line.** The final line stays highlighted to the end of the track rather
 *    than clearing.
 *
 * [lines] must be sorted ascending by [SkinLyricLine.startMs] - which is the order the
 * adapter produces. Binary search, so this is cheap enough to call once per frame against a
 * long lyric sheet.
 */
fun activeLyricIndexAt(lines: List<SkinLyricLine>, positionMs: Long): Int {
    if (lines.isEmpty()) return -1
    if (positionMs < lines[0].startMs) return -1

    var low = 0
    var high = lines.lastIndex
    var answer = 0
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (lines[mid].startMs <= positionMs) {
            answer = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return answer
}
