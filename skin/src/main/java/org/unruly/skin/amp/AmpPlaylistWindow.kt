/*
 * Copyright (c) 2026 Unruly Project
 * AmpPlaylistWindow.kt is part of Unruly.
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

/** One line of the list: text at the left, an optional right-aligned figure (a duration). */
internal data class PlRow(val text: String, val right: String = "")

/** The list window's rows are 13 pixels, as the original's at its default font size. */
internal const val ROW_H = 13

internal const val PL_TOP = 20
internal const val PL_BOTTOM = 38
internal const val PL_LEFT = 12
internal const val PL_RIGHT = 20

/** The list window's minimum height: its frame plus four rows. */
internal const val PL_MIN_H = PL_TOP + PL_BOTTOM + ROW_H * 4

internal class PlaylistModel(
    val title: String,
    val rows: List<PlRow>,
    val scrollPx: Int,
    val selected: Int,
    val current: Int,
    val runningTime: String,
    val miniTime: String,
    val pressed: PlHit?,
    /** Lines drawn white rather than green: the playing track, or the sung lyric. */
    val highlightWhite: Boolean = true,
)

internal enum class PlHit {
    CLOSE,
    SEL,
    LYRICS,
    SETTINGS,
    MINI_PREVIOUS,
    MINI_PLAY,
    MINI_PAUSE,
    MINI_STOP,
    MINI_NEXT,
    MINI_EJECT,
    SCROLLBAR,
    LIST_AREA,
}

internal fun listHeight(h: Int) = h - PL_TOP - PL_BOTTOM

internal fun plHitAt(x: Float, y: Float, w: Int, h: Int): PlHit? {
    fun inside(l: Int, t: Int, ww: Int, hh: Int) = x >= l && x < l + ww && y >= t && y < t + hh
    val b = h - PL_BOTTOM
    return when {
        inside(w - 13, 0, 13, PL_TOP) -> PlHit.CLOSE
        inside(PL_LEFT, PL_TOP, w - PL_LEFT - PL_RIGHT, listHeight(h)) -> PlHit.LIST_AREA
        inside(w - PL_RIGHT, PL_TOP, PL_RIGHT, listHeight(h)) -> PlHit.SCROLLBAR
        inside(12, b + 6, 27, 24) -> PlHit.SEL
        inside(41, b + 6, 38, 24) -> PlHit.LYRICS
        inside(w - 46, b + 6, 27, 24) -> PlHit.SETTINGS
        y >= b + 18 && x >= w - 148 && x < w - 88 -> {
            when (((x - (w - 148)) / 10).toInt()) {
                0 -> PlHit.MINI_PREVIOUS
                1 -> PlHit.MINI_PLAY
                2 -> PlHit.MINI_PAUSE
                3 -> PlHit.MINI_STOP
                4 -> PlHit.MINI_NEXT
                else -> PlHit.MINI_EJECT
            }
        }
        else -> null
    }
}

internal fun PixelPainter.drawPlaylistWindow(m: PlaylistModel) {
    clear(Amp.PlFrame)
    drawTop(m.title)
    drawSides()
    drawList(m)
    drawScrollbar(m)
    drawBottom(m)
}

private fun PixelPainter.drawTop(title: String) {
    rect(0, 0, width, PL_TOP, Amp.PlFrame)
    hline(0, 0, width, Amp.PlFrameDark)
    hline(1, 1, width - 2, Amp.PlFrameLight)
    vline(0, 0, PL_TOP, Amp.PlFrameDark)
    vline(1, 1, PL_TOP, Amp.PlFrameLight)
    vline(width - 1, 0, PL_TOP, Amp.PlFrameDark)
    // Gold rules either side of the title, as on the other windows, but in a narrower bar.
    val textWidth = pixelTextWidth(title, bold = true)
    val textX = (width - textWidth) / 2
    fun stripes(x0: Int, x1: Int) {
        if (x1 <= x0) return
        val ww = x1 - x0
        hline(x0, 4, ww, Amp.GoldShade)
        hline(x0, 5, ww, Amp.Gold)
        hline(x0, 6, ww, Amp.White)
        hline(x0, 7, ww, Amp.GoldDark)
        hline(x0, 8, ww, Amp.GoldMid)
        hline(x0, 9, ww, Amp.Gold)
        hline(x0, 10, ww, Amp.GoldShade2)
    }
    stripes(6, textX - 5)
    stripes(textX + textWidth + 4, width - 26)
    pixelText(title, textX, 4, Amp.White, bold = true)
    windowButton(width - 22, 3, WindowGlyph.SHADE)
    windowButton(width - 12, 3, WindowGlyph.CLOSE)
    // The lower lip of the title bar: a dark band, a light rule, then the frame.
    hline(2, 12, width - 4, Amp.PlFrameDark)
    hline(2, 13, width - 4, Amp.PlFrameDark)
    hline(4, 14, width - 8, Amp.PlFrameLight)
}

/** The side tiles: two light rules on the left; the scrollbar's groove on the right. */
private fun PixelPainter.drawSides() {
    val top = PL_TOP
    val h = listHeight(height)
    val left = intArrayOf(Amp.PlFrameDark, Amp.PlFrameLight, Amp.PlFrame, Amp.PlFrame, Amp.PlFrameDark, Amp.PlFrameDark, Amp.PlFrameLight, Amp.PlFrame, Amp.PlFrame, Amp.PlFrame, Amp.PlFrameDark, Amp.Black)
    left.forEachIndexed { i, c -> vline(i, top, h, c) }
    val rx = width - PL_RIGHT
    val right =
        intArrayOf(
            Amp.Black, Amp.PlFrameLight, Amp.PlFrame, Amp.PlFrame, Amp.PlFrame, Amp.PlFrameDark,
            Amp.Black, Amp.Black, Amp.Black, Amp.Black, Amp.Black, Amp.Black, Amp.Black, Amp.Black,
            Amp.PlFrameDark, Amp.PlFrame, Amp.PlFrameLight, Amp.PlFrame, Amp.PlFrame, Amp.PlFrameDark,
        )
    right.forEachIndexed { i, c -> vline(rx + i, top, h, c) }
}

private fun PixelPainter.drawList(m: PlaylistModel) {
    val x0 = PL_LEFT
    val y0 = PL_TOP
    val w = width - PL_LEFT - PL_RIGHT
    val h = listHeight(height)
    rect(x0, y0, w, h, Amp.Black)
    clipped(x0, y0, w, h) {
        val first = (m.scrollPx / ROW_H).coerceAtLeast(0)
        val last = ((m.scrollPx + h) / ROW_H).coerceAtMost(m.rows.size - 1)
        for (i in first..last) {
            val top = y0 + i * ROW_H - m.scrollPx
            if (i == m.selected) rect(x0, top, w, ROW_H, Amp.PlSelected)
            val color = if (i == m.current && m.highlightWhite) Amp.PlCurrent else Amp.PlText
            val row = m.rows[i]
            val rightWidth = if (row.right.isEmpty()) 0 else sysTextWidth(row.right, 11f) + 4
            if (row.right.isNotEmpty()) sysText(row.right, x0 + w - rightWidth + 1, top + 10, color, 11f)
            sysText(sysFit(row.text, w - rightWidth - 4, 11f), x0 + 2, top + 10, color, 11f)
        }
    }
}

private fun PixelPainter.drawScrollbar(m: PlaylistModel) {
    val h = listHeight(height)
    val content = m.rows.size * ROW_H
    val travel = h - 18
    val frac = if (content <= h) 0f else m.scrollPx.toFloat() / (content - h)
    val y = PL_TOP + (frac * travel).toInt()
    val x = width - 15
    steelButton(x, y, 8, 18)
    hline(x + 2, y + 7, 4, Amp.ButtonShadow)
    hline(x + 2, y + 9, 4, Amp.ButtonShadow)
    hline(x + 2, y + 11, 4, Amp.ButtonShadow)
}

private fun PixelPainter.drawBottom(m: PlaylistModel) {
    val b = height - PL_BOTTOM
    rect(0, b, width, PL_BOTTOM, Amp.PlFrame)
    hline(0, b, width, Amp.PlFrameLight)
    hline(0, height - 1, width, Amp.PlFrameDark)
    vline(0, b, PL_BOTTOM, Amp.PlFrameDark)
    vline(1, b, PL_BOTTOM, Amp.PlFrameLight)
    vline(width - 1, b, PL_BOTTOM, Amp.PlFrameDark)

    // SEL and LYRICS at the left (LYRICS widened to fit its word), settings at the right.
    listOf(Triple(PlHit.SEL, 14, "SEL") to 22, Triple(PlHit.LYRICS, 43, "LYRICS") to 34).forEach {
        (button, w) ->
        val (hit, x, label) = button
        rect(x - 3, b + 8, 3, 18, Amp.PlFrameDark)
        steelButton(x, b + 8, w, 18, m.pressed == hit)
        pixelText(label, x + w / 2 - pixelTextWidth(label) / 2, b + 15, Amp.ButtonShadow)
    }
    val lx = width - 44
    steelButton(lx, b + 8, 22, 18, m.pressed == PlHit.SETTINGS)
    GEAR.forEachIndexed { r, line ->
        line.forEachIndexed { c, ch -> if (ch == '#') dot(lx + 5 + c, b + 11 + r, Amp.ButtonShadow) }
    }

    // Running time readout and the miniature transport beneath it.
    val rx = width - 148
    rect(rx, b + 9, 60, 9, Amp.Black)
    pixelText(m.runningTime, rx + 2, b + 11, Amp.Lcd)
    miniTransport(rx, b + 22)
    rect(width - 84, b + 21, 30, 9, Amp.Black)
    pixelText(m.miniTime, width - 82, b + 23, Amp.Lcd)

    // Resize grip.
    for (i in 0..3) {
        val d = 4 + i * 3
        for (k in 0 until d) dot(width - 4 - k, height - 4 - d + k + 1, Amp.PlFrameLight)
    }
}

/** Six tiny transport glyphs, ten pixels apart, as printed on the original's frame. */
private fun PixelPainter.miniTransport(x: Int, y: Int) {
    val c = Amp.Label
    // previous
    vline(x + 1, y, 6, c); for (r in 0..5) hline(x + 2 + kotlin.math.abs(r - 2.5f).toInt(), y + r, 1, c)
    for (r in 0..5) hline(x + 3, y + r, 3 - kotlin.math.abs(r - 2.5f).toInt(), c)
    // play
    for (r in 0..5) hline(x + 11, y + r, 3 - kotlin.math.abs(r - 2.5f).toInt() + 1, c)
    // pause
    rect(x + 21, y, 2, 6, c); rect(x + 24, y, 2, 6, c)
    // stop
    rect(x + 31, y, 6, 6, c)
    // next
    for (r in 0..5) hline(x + 41, y + r, 3 - kotlin.math.abs(r - 2.5f).toInt(), c)
    vline(x + 45, y, 6, c)
    // eject
    for (r in 0..2) hline(x + 53 - r, y + r, r * 2 + 1, c)
    hline(x + 51, y + 4, 5, c)
}

/** A toothed gear, 11 pixels square: eight teeth around a ring with an open centre. */
private val GEAR =
    listOf(
        "....###....",
        ".##.###.##.",
        ".#########.",
        "..##...##..",
        "###.....###",
        "###.....###",
        "###.....###",
        "..##...##..",
        ".#########.",
        ".##.###.##.",
        "....###....",
    )
