/*
 * Copyright (c) 2026 Unruly Project
 * PixelPainter.kt is part of Unruly.
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

package org.unruly.skin.amp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.text.Normalizer

/**
 * Draws one window at its native resolution - one unit is one pixel of the original 275-pixel-wide
 * player - into a small bitmap that the skin then scales up with nearest-neighbour filtering. That
 * is what keeps every edge square and every line a whole number of pixels, the way the original
 * looked at "double size", at whatever scale the phone needs.
 *
 * Every shape here is drawn from code: the original's skin bitmaps are not shipped, only their
 * geometry and colours were measured.
 */
internal class PixelPainter(val width: Int, val height: Int) {
    val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val paint = Paint().apply { isAntiAlias = false }
    private val textPaint =
        Paint().apply {
            isAntiAlias = false
            typeface = Typeface.SANS_SERIF
            textSize = 9f
            // Hinting snaps stems to whole pixels; unhinted, small unsmoothed text loses its
            // counters and "e" reads as "a".
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                hinting = Paint.HINTING_ON
            }
        }

    fun clear(color: Int) = bitmap.eraseColor(color)

    /** Runs [block] with drawing confined to the given rectangle. */
    inline fun clipped(x: Int, y: Int, w: Int, h: Int, block: () -> Unit) {
        val save = canvasSave(x, y, w, h)
        try {
            block()
        } finally {
            canvasRestore(save)
        }
    }

    fun canvasSave(x: Int, y: Int, w: Int, h: Int): Int {
        val save = canvas.save()
        canvas.clipRect(x, y, x + w, y + h)
        return save
    }

    fun canvasRestore(save: Int) = canvas.restoreToCount(save)

    fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
        if (w <= 0 || h <= 0) return
        paint.color = color
        canvas.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
    }

    fun dot(x: Int, y: Int, color: Int) = rect(x, y, 1, 1, color)

    fun hline(x: Int, y: Int, w: Int, color: Int) = rect(x, y, w, 1, color)

    fun vline(x: Int, y: Int, h: Int, color: Int) = rect(x, y, 1, h, color)

    /** A one-pixel bevel: [light] on the top and left edges, [dark] on the bottom and right. */
    fun bevel(x: Int, y: Int, w: Int, h: Int, light: Int, dark: Int) {
        hline(x, y, w, light)
        vline(x, y, h, light)
        hline(x, y + h - 1, w, dark)
        vline(x + w - 1, y, h, dark)
    }

    /** Fills a rectangle column by column with a colour chosen per column. */
    fun columns(x: Int, y: Int, w: Int, h: Int, colorAt: (Int) -> Int) {
        for (i in 0 until w) rect(x + i, y, 1, h, colorAt(x + i))
    }

    /**
     * Text in the small 4x5 capitals of the player's readouts, five pixels per character. [bold]
     * spaces the letters a pixel wider, for titles; thickening a four-pixel glyph instead would
     * close up its counters and turn N into M.
     */
    fun pixelText(text: String, x: Int, y: Int, color: Int, bold: Boolean = false): Int {
        var cx = x
        for (c in normalise(text)) {
            val glyph = PixelFont.glyph(c)
            for (row in glyph.indices) {
                val line = glyph[row]
                for (col in line.indices) {
                    if (line[col] == '#') dot(cx + col, y + row, color)
                }
            }
            cx += if (bold) 6 else 5
        }
        return cx - x
    }

    fun pixelTextWidth(text: String, bold: Boolean = false): Int =
        normalise(text).length * (if (bold) 6 else 5)

    /**
     * Ordinary proportional text, unsmoothed, for the playlist - the original drew its list in the
     * system's small sans with no anti-aliasing, and that jagged look is part of it.
     */
    fun sysText(text: String, x: Int, baseline: Int, color: Int, size: Float = 9f) {
        textPaint.color = color
        textPaint.textSize = size
        canvas.drawText(text, x.toFloat(), baseline.toFloat(), textPaint)
    }

    fun sysTextWidth(text: String, size: Float = 9f): Int {
        textPaint.textSize = size
        return kotlin.math.ceil(textPaint.measureText(text)).toInt()
    }

    /** Truncates [text] to fit [maxWidth] pixels of [sysText], with no ellipsis, as the original. */
    fun sysFit(text: String, maxWidth: Int, size: Float = 9f): String {
        if (sysTextWidth(text, size) <= maxWidth) return text
        var end = text.length
        while (end > 0 && sysTextWidth(text.substring(0, end), size) > maxWidth) end--
        return text.substring(0, end)
    }

    /** One seven-segment digit, nine by thirteen, as the time readout. [digit] -1 draws nothing. */
    fun segmentDigit(digit: Int, x: Int, y: Int, on: Int) {
        if (digit !in 0..9) return
        val segs = SEGMENTS[digit]
        // a top, b upper right, c lower right, d bottom, e lower left, f upper left, g middle
        if ('a' in segs) rect(x + 2, y, 5, 2, on)
        if ('b' in segs) rect(x + 7, y + 1, 2, 5, on)
        if ('c' in segs) rect(x + 7, y + 7, 2, 5, on)
        if ('d' in segs) rect(x + 2, y + 11, 5, 2, on)
        if ('e' in segs) rect(x, y + 7, 2, 5, on)
        if ('f' in segs) rect(x, y + 1, 2, 5, on)
        if ('g' in segs) rect(x + 2, y + 6, 5, 1, on)
    }

    private fun normalise(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").uppercase()

    private companion object {
        val SEGMENTS =
            arrayOf("abcdef", "bc", "abdeg", "abcdg", "bcfg", "acdfg", "acdefg", "abc", "abcdefg", "abcdfg")
    }
}

/** ARGB from #RRGGBB. */
internal fun rgb(hex: Long): Int = (0xFF000000 or hex).toInt()

/** Linear blend of two colours, [t] in 0..1. */
internal fun blend(a: Int, b: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - k) + ((b shr shift) and 0xFF) * k).toInt()
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/**
 * The readout capitals: four pixels wide, five tall, drawn for this skin. Unknown characters are
 * blank, as the original's were.
 */
internal object PixelFont {
    private val glyphs: Map<Char, Array<String>> =
        mapOf(
            'A' to arrayOf(".##.", "#..#", "####", "#..#", "#..#"),
            'B' to arrayOf("###.", "#..#", "###.", "#..#", "###."),
            'C' to arrayOf(".###", "#...", "#...", "#...", ".###"),
            'D' to arrayOf("###.", "#..#", "#..#", "#..#", "###."),
            'E' to arrayOf("####", "#...", "###.", "#...", "####"),
            'F' to arrayOf("####", "#...", "###.", "#...", "#..."),
            'G' to arrayOf(".###", "#...", "#.##", "#..#", ".###"),
            'H' to arrayOf("#..#", "#..#", "####", "#..#", "#..#"),
            'I' to arrayOf("###.", ".#..", ".#..", ".#..", "###."),
            'J' to arrayOf("..##", "...#", "...#", "#..#", ".##."),
            'K' to arrayOf("#..#", "#.#.", "##..", "#.#.", "#..#"),
            'L' to arrayOf("#...", "#...", "#...", "#...", "####"),
            'M' to arrayOf("#..#", "####", "####", "#..#", "#..#"),
            'N' to arrayOf("#..#", "##.#", "#.##", "#..#", "#..#"),
            'O' to arrayOf(".##.", "#..#", "#..#", "#..#", ".##."),
            'P' to arrayOf("###.", "#..#", "###.", "#...", "#..."),
            'Q' to arrayOf(".##.", "#..#", "#..#", "#.##", ".###"),
            'R' to arrayOf("###.", "#..#", "###.", "#.#.", "#..#"),
            'S' to arrayOf(".###", "#...", ".##.", "...#", "###."),
            'T' to arrayOf("###.", ".#..", ".#..", ".#..", ".#.."),
            'U' to arrayOf("#..#", "#..#", "#..#", "#..#", ".##."),
            'V' to arrayOf("#..#", "#..#", "#..#", ".##.", ".##."),
            'W' to arrayOf("#..#", "#..#", "####", "####", "#..#"),
            'X' to arrayOf("#..#", "#..#", ".##.", "#..#", "#..#"),
            'Y' to arrayOf("#.#.", "#.#.", ".#..", ".#..", ".#.."),
            'Z' to arrayOf("####", "..#.", ".#..", "#...", "####"),
            '0' to arrayOf(".##.", "#.##", "##.#", "#..#", ".##."),
            '1' to arrayOf(".#..", "##..", ".#..", ".#..", "###."),
            '2' to arrayOf("###.", "...#", ".##.", "#...", "####"),
            '3' to arrayOf("###.", "...#", ".##.", "...#", "###."),
            '4' to arrayOf("#..#", "#..#", "####", "...#", "...#"),
            '5' to arrayOf("####", "#...", "###.", "...#", "###."),
            '6' to arrayOf(".##.", "#...", "###.", "#..#", ".##."),
            '7' to arrayOf("####", "...#", "..#.", ".#..", ".#.."),
            '8' to arrayOf(".##.", "#..#", ".##.", "#..#", ".##."),
            '9' to arrayOf(".##.", "#..#", ".###", "...#", ".##."),
            '-' to arrayOf("....", "....", "###.", "....", "...."),
            '.' to arrayOf("....", "....", "....", "....", ".#.."),
            ',' to arrayOf("....", "....", "....", ".#..", "#..."),
            ':' to arrayOf("....", ".#..", "....", ".#..", "...."),
            ';' to arrayOf("....", ".#..", "....", ".#..", "#..."),
            '\'' to arrayOf(".#..", ".#..", "....", "....", "...."),
            '"' to arrayOf("#.#.", "#.#.", "....", "....", "...."),
            '(' to arrayOf("..#.", ".#..", ".#..", ".#..", "..#."),
            ')' to arrayOf(".#..", "..#.", "..#.", "..#.", ".#.."),
            '[' to arrayOf(".##.", ".#..", ".#..", ".#..", ".##."),
            ']' to arrayOf(".##.", "..#.", "..#.", "..#.", ".##."),
            '/' to arrayOf("...#", "..#.", ".#..", "#...", "...."),
            '&' to arrayOf(".#..", "#.#.", ".#.#", "#.#.", ".#.#"),
            '!' to arrayOf(".#..", ".#..", ".#..", "....", ".#.."),
            '?' to arrayOf("###.", "...#", ".##.", "....", ".#.."),
            '*' to arrayOf("....", "#.#.", ".#..", "#.#.", "...."),
            '+' to arrayOf("....", ".#..", "###.", ".#..", "...."),
            '=' to arrayOf("....", "###.", "....", "###.", "...."),
            '_' to arrayOf("....", "....", "....", "....", "####"),
            '#' to arrayOf(".#.#", "####", ".#.#", "####", ".#.#"),
            '%' to arrayOf("#..#", "..#.", ".#..", "#...", "#..#"),
            '<' to arrayOf("..#.", ".#..", "#...", ".#..", "..#."),
            '>' to arrayOf(".#..", "..#.", "...#", "..#.", ".#.."),
            '@' to arrayOf(".##.", "#.##", "#.##", "#...", ".##."),
        )

    private val blank = arrayOf("....", "....", "....", "....", "....")

    fun glyph(c: Char): Array<String> = glyphs[c] ?: blank
}
