package org.oxycblt.auxio.shellac.engine.lyrics

import org.shellac.skin.SkinLyricLine

private val TIMESTAMP = Regex("\\[(\\d+):(\\d{2})(?:\\.(\\d{1,3}))?\\]")

/**
 * Parses the timestamped portion of an LRC document.
 *
 * This deliberately has no Android or network dependencies so the timestamp rules can be
 * checked independently of the LRCLIB client.
 */
internal fun parseLrc(raw: String): List<SkinLyricLine> {
    val lines = buildList {
        raw.lineSequence().forEach lineLoop@{ rawLine ->
            val timestamps = TIMESTAMP.findAll(rawLine).toList()
            if (timestamps.isEmpty() || timestamps.first().range.first != 0) return@lineLoop

            val textStart = timestamps.last().range.last + 1
            val text = rawLine.substring(textStart)
            timestamps.forEach timestampLoop@{ match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return@timestampLoop
                val seconds = match.groupValues[2].toLongOrNull() ?: return@timestampLoop
                if (seconds >= 60L) return@timestampLoop

                val fraction = match.groupValues[3]
                val fractionMs =
                    if (fraction.isEmpty()) {
                        0L
                    } else {
                        fraction.padEnd(3, '0').take(3).toLong()
                    }
                add(
                    SkinLyricLine(
                        startMs = minutes * 60_000L + seconds * 1_000L + fractionMs,
                        text = text,
                    )
                )
            }
        }
    }
    return lines.sortedBy { it.startMs }
}
