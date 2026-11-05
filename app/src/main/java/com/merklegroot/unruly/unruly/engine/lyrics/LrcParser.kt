/*
 * Copyright (c) 2026 Unruly Project
 * LrcParser.kt is part of Unruly.
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
 
package com.merklegroot.unruly.unruly.engine.lyrics

import org.unruly.skin.SkinLyricLine

private val TIMESTAMP = Regex("\\[(\\d+):(\\d{2})(?:\\.(\\d{1,3}))?\\]")

/**
 * Parses the timestamped portion of an LRC document.
 *
 * This deliberately has no Android or network dependencies so the timestamp rules can be checked
 * independently of the LRCLIB client.
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
