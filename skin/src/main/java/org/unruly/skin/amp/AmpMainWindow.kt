/*
 * Copyright (c) 2026 Unruly Project
 * AmpMainWindow.kt is part of Unruly.
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

import org.unruly.skin.SkinRepeatMode

/** The main window's native size. Every coordinate below is in these pixels. */
internal const val MAIN_W = 275
internal const val MAIN_H = 116

/** Number of analyser bars: 76 pixels at four per bar (three lit, one gap). */
internal const val BAR_COUNT = 19

/** Everything the main window shows, gathered so drawing is a pure function of it. */
internal class MainModel(
    val hasSong: Boolean,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val marquee: String,
    val marqueeOffset: Int,
    val bars: FloatArray,
    val peaks: FloatArray,
    val volume: Float,
    val balance: Float,
    val shuffle: Boolean,
    val repeat: SkinRepeatMode,
    val eqShown: Boolean,
    val playlistShown: Boolean,
    val showRemaining: Boolean,
    val pressed: MainHit?,
)

/** The main window's controls, as hit-test results. */
internal enum class MainHit {
    OPTIONS,
    CLUTTER_O,
    CLUTTER_I,
    TIME,
    VOLUME,
    BALANCE,
    EQ,
    PL,
    SEEK,
    PREVIOUS,
    PLAY,
    PAUSE,
    STOP,
    NEXT,
    EJECT,
    SHUFFLE,
    REPEAT,
}

/** Maps a point in native pixels to the control under it. */
internal fun mainHitAt(x: Float, y: Float): MainHit? {
    fun inside(l: Int, t: Int, w: Int, h: Int) = x >= l && x < l + w && y >= t && y < t + h
    return when {
        inside(4, 0, 14, 14) -> MainHit.OPTIONS
        inside(10, 22, 8, 11) -> MainHit.CLUTTER_O
        inside(10, 39, 8, 8) -> MainHit.CLUTTER_I
        inside(36, 24, 66, 17) -> MainHit.TIME
        inside(105, 54, 72, 18) -> MainHit.VOLUME
        inside(176, 54, 40, 18) -> MainHit.BALANCE
        inside(218, 55, 24, 16) -> MainHit.EQ
        inside(242, 55, 26, 16) -> MainHit.PL
        inside(14, 70, 252, 14) -> MainHit.SEEK
        inside(16, 86, 23, 22) -> MainHit.PREVIOUS
        inside(39, 86, 23, 22) -> MainHit.PLAY
        inside(62, 86, 23, 22) -> MainHit.PAUSE
        inside(85, 86, 23, 22) -> MainHit.STOP
        inside(108, 86, 24, 22) -> MainHit.NEXT
        inside(134, 86, 26, 22) -> MainHit.EJECT
        inside(162, 86, 48, 20) -> MainHit.SHUFFLE
        inside(210, 86, 30, 20) -> MainHit.REPEAT
        else -> null
    }
}

/** Fraction along a horizontal slider for a touch at [x], given the slider's left edge and travel. */
internal fun sliderFraction(x: Float, left: Int, width: Int, thumb: Int): Float =
    ((x - left - thumb / 2f) / (width - thumb)).coerceIn(0f, 1f)

internal fun PixelPainter.drawMainWindow(m: MainModel) {
    windowBody()
    titleBar("UNRULY")
    // The inner frame the controls sit in.
    hline(1, 14, 271, Amp.InnerLight)
    hline(1, 112, 271, Amp.InnerLight)
    vline(6, 14, 99, Amp.InnerLight)
    vline(271, 14, 99, Amp.InnerLight)

    drawDisplay(m)
    drawInfo(m)
    drawSliders(m)
    drawSeek(m)
    drawTransport(m)
}

/** The black display: clutter bar, play state, time and the analyser. */
private fun PixelPainter.drawDisplay(m: MainModel) {
    sunkenPanel(12, 23, 91, 41)

    // Clutter bar: five dim letters standing in a column at the display's left edge.
    rect(10, 22, 8, 43, rgb(0x14141F))
    "OAIDV".forEachIndexed { i, c ->
        pixelText(c.toString(), 12, 24 + i * 8, if (i == 2) Amp.FrameLight else rgb(0x3B3B4E))
    }

    // Play state.
    when {
        m.isPlaying -> {
            for (r in 0..8) hline(28, 28 + r, minOf(r, 8 - r) * 6 / 4 + 1, Amp.LedOn)
            vline(24, 28, 9, Amp.LedOn)
        }
        m.hasSong -> {
            rect(27, 29, 3, 7, Amp.Lcd)
            rect(31, 29, 3, 7, Amp.Lcd)
        }
        else -> rect(28, 30, 6, 6, Amp.Lcd)
    }

    // Time: minutes and seconds in seven segments, or blank when nothing is loaded.
    if (m.hasSong) {
        val shownMs = if (m.showRemaining) (m.durationMs - m.positionMs).coerceAtLeast(0) else m.positionMs
        val total = (shownMs / 1000).toInt()
        val minutes = (total / 60).coerceAtMost(99)
        val seconds = total % 60
        if (m.showRemaining) hline(41, 32, 5, Amp.Lcd)
        segmentDigit(minutes / 10, 48, 26, Amp.Lcd)
        segmentDigit(minutes % 10, 60, 26, Amp.Lcd)
        rect(71, 29, 2, 2, Amp.Lcd)
        rect(71, 34, 2, 2, Amp.Lcd)
        segmentDigit(seconds / 10, 78, 26, Amp.Lcd)
        segmentDigit(seconds % 10, 90, 26, Amp.Lcd)
    }

    // Analyser: a dot grid, then bars coloured by the row each pixel is on, then peak caps.
    for (y in 0 until 16 step 2) for (x in 1 until 76 step 2) dot(24 + x, 43 + y, Amp.VisDot)
    for (i in 0 until BAR_COUNT) {
        val h = (m.bars[i] * 16).toInt().coerceIn(0, 16)
        val x = 24 + i * 4
        for (row in 16 - h until 16) hline(x, 43 + row, 3, Amp.VisRows[row])
        val peak = (m.peaks[i] * 16).toInt().coerceIn(0, 16)
        if (peak > 0) hline(x, 43 + 16 - peak, 3, Amp.VisPeak)
    }
    if (!m.isPlaying) {
        vline(24, 47, 12, Amp.VisBase)
        hline(24, 58, 76, Amp.VisBase)
    }
}

/** The scrolling title, the bitrate and frequency boxes, and mono/stereo. */
private fun PixelPainter.drawInfo(m: MainModel) {
    sunkenPanel(109, 24, 157, 12)
    if (m.marquee.isNotEmpty()) {
        // Draw into a strip and wrap: the title runs off the left and comes back on the right.
        val text = m.marquee
        val textWidth = pixelTextWidth(text)
        val clipLeft = 111
        val clipRight = 264
        var x = clipLeft - (m.marqueeOffset % textWidth)
        while (x < clipRight) {
            drawClipped(text, x, 27, clipLeft, clipRight)
            x += textWidth
        }
    }
    sunkenPanel(109, 41, 18, 10)
    pixelText("KBPS", 130, 43, Amp.Label)
    sunkenPanel(154, 41, 13, 10)
    pixelText("KHZ", 170, 43, Amp.Label)
    pixelText("MONO", 212, 43, rgb(0x5A5A68))
    pixelText("STEREO", 236, 43, if (m.isPlaying) Amp.LedOn else rgb(0x5A5A68))
}

/** Pixel text clipped to the marquee box, character by character. */
private fun PixelPainter.drawClipped(text: String, x: Int, y: Int, left: Int, right: Int) {
    var cx = x
    for (c in text) {
        if (cx + 4 > left && cx < right) {
            val glyph = PixelFont.glyph(c.uppercaseChar())
            for (r in glyph.indices) for (col in glyph[r].indices) {
                val px = cx + col
                if (glyph[r][col] == '#' && px in left until right) dot(px, y + r, Amp.Lcd)
            }
        }
        cx += 5
    }
}

private fun PixelPainter.drawSliders(m: MainModel) {
    // Volume: a rounded bar whose colour climbs from green to red with the level.
    horizontalTrack(107, 57, 66, sliderColor(m.volume))
    sliderThumb(107 + (m.volume * (68 - 14)).toInt(), 58, m.pressed == MainHit.VOLUME)

    // Balance: green at the centre, warming towards either side.
    horizontalTrack(177, 57, 37, sliderColor(kotlin.math.abs(m.balance - 0.5f) * 2f))
    sliderThumb(177 + (m.balance * (38 - 14)).toInt(), 58, m.pressed == MainHit.BALANCE)

    ledButton(219, 58, 23, 12, "EQ", m.eqShown)
    ledButton(242, 58, 23, 12, "PL", m.playlistShown)
}

private fun PixelPainter.horizontalTrack(x: Int, y: Int, w: Int, color: Int) {
    val dark = blend(color, Amp.Black, 0.55f)
    hline(x + 2, y + 4, w - 4, dark)
    rect(x + 1, y + 5, w - 2, 3, color)
    hline(x + 2, y + 8, w - 4, dark)
    hline(x + 3, y + 6, w - 6, blend(color, Amp.White, 0.15f))
}

private fun PixelPainter.drawSeek(m: MainModel) {
    // The groove.
    hline(16, 72, 248, Amp.GrooveDark)
    hline(16, 73, 248, Amp.GrooveDark)
    rect(16, 74, 248, 7, Amp.GrooveMid)
    hline(16, 81, 248, Amp.GrooveLight)
    vline(16, 72, 10, Amp.GrooveDark)
    if (!m.hasSong) return
    val fraction = if (m.durationMs > 0) (m.positionMs.toFloat() / m.durationMs).coerceIn(0f, 1f) else 0f
    val x = 16 + (fraction * (248 - 29)).toInt()
    // The gold thumb: a bright bar with a recessed inner rule.
    rect(x, 72, 29, 10, Amp.SeekThumbEdge)
    rect(x + 1, 73, 27, 8, Amp.SeekThumbFace)
    hline(x + 1, 81, 28, Amp.SeekThumbDark)
    vline(x + 28, 72, 10, Amp.SeekThumbDark)
    rect(x + 3, 74, 23, 5, Amp.SeekThumbMid)
    hline(x + 3, 74, 23, Amp.SeekThumbDark)
    vline(x + 3, 74, 5, Amp.SeekThumbDark)
    hline(x + 4, 78, 22, Amp.SeekThumbFace)
}

private fun PixelPainter.drawTransport(m: MainModel) {
    val buttons =
        listOf(
            Triple(MainHit.PREVIOUS, 16, 23),
            Triple(MainHit.PLAY, 39, 23),
            Triple(MainHit.PAUSE, 62, 23),
            Triple(MainHit.STOP, 85, 23),
            Triple(MainHit.NEXT, 108, 22),
        )
    buttons.forEach { (hit, x, w) ->
        steelButton(x, 88, w, 18, m.pressed == hit)
        glyph(transportMask(hit), x + w / 2, 97)
    }
    steelButton(136, 89, 22, 16, m.pressed == MainHit.EJECT)
    glyph(transportMask(MainHit.EJECT), 147, 97)

    ledButton(164, 89, 47, 15, "SHUFFLE", m.shuffle)
    ledButton(210, 89, 28, 15, "", m.repeat != SkinRepeatMode.NONE)
    // The repeat loop: a small rounded rectangle with an arrowhead.
    outlinedGlyph(
        listOf(".#######.", "#.......#", "#..#....#", ".####..#."),
        219,
        94,
    )
    if (m.repeat == SkinRepeatMode.TRACK) pixelText("1", 230, 94, Amp.ButtonShadow)

    // The badge in the corner, in place of the original's logo: three small blocks.
    rect(254, 93, 4, 5, rgb(0xE8431C))
    rect(259, 93, 4, 5, rgb(0x1B9AD9))
    rect(254, 99, 9, 5, rgb(0xF5B310))
}

/** Draws a filled mask centred at ([cx], [cy]): pale fill, shadow-blue outline. */
private fun PixelPainter.glyph(mask: List<String>, cx: Int, cy: Int) {
    val h = mask.size
    val w = mask.maxOf { it.length }
    val x0 = cx - w / 2
    val y0 = cy - h / 2
    fun on(r: Int, c: Int) = r in mask.indices && c in mask[r].indices && mask[r][c] == '#'
    for (r in 0 until h) for (c in 0 until w) {
        if (!on(r, c)) continue
        val edge = !on(r - 1, c) || !on(r + 1, c) || !on(r, c - 1) || !on(r, c + 1)
        dot(x0 + c, y0 + r, if (edge) Amp.ButtonShadow else Amp.ButtonGlyph)
    }
}

private fun transportMask(hit: MainHit): List<String> =
    when (hit) {
        MainHit.PLAY -> triangle(11)
        MainHit.PAUSE -> List(11) { "####..####" }
        MainHit.STOP -> List(10) { "##########" }
        MainHit.PREVIOUS -> triangle(11).map { row -> "###." + row.reversed().padStart(8, '.') }
        MainHit.NEXT -> triangle(11).map { row -> row.padEnd(8, '.') + ".###" }
        MainHit.EJECT -> {
            val tri = (0 until 6).map { r -> ".".repeat(5 - r) + "#".repeat(r * 2 + 1) + ".".repeat(5 - r) }
            tri + listOf("...........") + List(3) { "###########" }
        }
        else -> emptyList()
    }

/** A right-pointing triangle [h] rows tall, as strings. */
private fun triangle(h: Int): List<String> =
    (0 until h).map { r ->
        val len = minOf(r, h - 1 - r) * 7 / ((h - 1) / 2) + 1
        "#".repeat(len).padEnd(8, '.')
    }
