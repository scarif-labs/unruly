/*
 * Copyright (c) 2026 Unruly Project
 * AmpChrome.kt is part of Unruly.
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

/**
 * Colours measured off the classic late-90s player's default skin. Names describe the role, not the
 * source.
 */
internal object Amp {
    val Edge = rgb(0x0D0D14)
    val EdgeDark = rgb(0x14131E)
    val FrameLight = rgb(0x5C5C63)
    val InnerLight = rgb(0x6D6D7F)
    val BodyEdge = rgb(0x1C1C2C)
    val BodyCenter = rgb(0x383758)
    val PanelShadow = rgb(0x161622)
    val Black = rgb(0x000000)
    val White = rgb(0xFFFFFF)

    val ButtonFace = rgb(0xBDCED6)
    val ButtonHi = rgb(0xEFFFFF)
    val ButtonOuter = rgb(0xADB5C6)
    val ButtonShadow = rgb(0x4A5A6B)
    val ButtonShadow2 = rgb(0x7B8494)
    val ButtonGlyph = rgb(0x97A8B9)
    val ButtonPressed = rgb(0x8494A5)

    val Gold = rgb(0xECCE7A)
    val GoldDark = rgb(0x45413C)
    val GoldMid = rgb(0xA3946A)
    val GoldShade = rgb(0x1E1F25)
    val GoldShade2 = rgb(0x25262C)
    val TitleGrey = rgb(0xC0C1C6)

    val Lcd = rgb(0x00D800)
    val LcdDim = rgb(0x0A4A12)
    val LedOn = rgb(0x00F000)
    val LedOff = rgb(0x1D3A1D)
    val Label = rgb(0xDADFE6)
    val LabelGold = rgb(0xD7B44C)

    val VisDot = rgb(0x182129)
    val VisPeak = rgb(0x969696)
    val VisBase = rgb(0x006AB2)

    /** Analyser colours, top of a bar to the bottom: red through orange and yellow to green. */
    val VisRows =
        intArrayOf(
            rgb(0xEF3110), rgb(0xCE2910), rgb(0xD65A00), rgb(0xD66600), rgb(0xD67300), rgb(0xC67B08),
            rgb(0xDEA518), rgb(0xD6B521), rgb(0xBDDE29), rgb(0x94DE21), rgb(0x29CE10), rgb(0x32BE10),
            rgb(0x39B510), rgb(0x319C08), rgb(0x299400), rgb(0x188408),
        )

    /** Slider fills from low to high: green, yellow-green, yellow, orange, red. */
    val SliderStops =
        intArrayOf(rgb(0x18920B), rgb(0xA4E238), rgb(0xE0DA30), rgb(0xD97C1F), rgb(0xE00E15))

    val PlText = rgb(0x00FF00)
    val PlCurrent = rgb(0xFFFFFF)
    val PlSelected = rgb(0x0000C6)
    val PlFrame = rgb(0x292940)
    val PlFrameLight = rgb(0x6A6A7A)
    val PlFrameDark = rgb(0x1D1D2D)

    val SeekThumbEdge = rgb(0xDBCB9E)
    val SeekThumbFace = rgb(0xF4EAC7)
    val SeekThumbMid = rgb(0xC5B27E)
    val SeekThumbDark = rgb(0x8D753A)
    val GrooveDark = rgb(0x0F0F18)
    val GrooveMid = rgb(0x2E2D48)
    val GrooveLight = rgb(0x737388)
}

/** Interpolates along [Amp.SliderStops]; [t] 0 is the low end. */
internal fun sliderColor(t: Float): Int {
    val stops = Amp.SliderStops
    val scaled = t.coerceIn(0f, 1f) * (stops.size - 1)
    val i = scaled.toInt().coerceAtMost(stops.size - 2)
    return blend(stops[i], stops[i + 1], scaled - i)
}

/**
 * The window body: dark at the edges, lighter towards a soft highlight right of centre, as the
 * original's brushed panel was lit.
 */
internal fun PixelPainter.windowBody(top: Int = 0, bottom: Int = height) {
    columns(0, top, width, bottom - top) { x ->
        val t = 1f - kotlin.math.abs(x - 160f) / 165f
        blend(Amp.BodyEdge, Amp.BodyCenter, t * t)
    }
    // Outer frame: a dark rim, a light inner line on the top and left.
    hline(0, 0, width, Amp.Edge)
    vline(0, 0, height, Amp.Edge)
    hline(1, 1, width - 2, Amp.FrameLight)
    vline(1, 1, height - 2, Amp.FrameLight)
    hline(0, height - 1, width, Amp.EdgeDark)
    vline(width - 1, 0, height, Amp.EdgeDark)
}

/**
 * A title bar: the window's name in white capitals, flanked by the gold double rules, with the
 * window buttons at the right. [leftButton] draws the options glyph at the far left.
 */
internal fun PixelPainter.titleBar(title: String, active: Boolean = true, leftButton: Boolean = true) {
    val textWidth = pixelTextWidth(title, bold = true)
    val textX = (width - textWidth) / 2
    val stripeLeft = if (leftButton) 20 else 10
    val stripeRight = width - 36
    fun stripes(x0: Int, x1: Int) {
        if (x1 <= x0) return
        val w = x1 - x0
        hline(x0, 4, w, Amp.GoldShade)
        hline(x0, 5, w, if (active) Amp.Gold else Amp.GoldMid)
        hline(x0, 6, w, if (active) Amp.White else Amp.TitleGrey)
        hline(x0, 7, w, Amp.GoldDark)
        hline(x0, 8, w, Amp.GoldMid)
        hline(x0, 9, w, if (active) Amp.Gold else Amp.GoldMid)
        hline(x0, 10, w, Amp.GoldShade2)
        dot(x0, 5, Amp.GoldMid)
        dot(x0, 9, Amp.GoldMid)
    }
    stripes(stripeLeft, textX - 5)
    stripes(textX + textWidth + 4, stripeRight)
    pixelText(title, textX, 4, if (active) Amp.White else Amp.TitleGrey, bold = true)
    if (leftButton) optionsGlyph(6, 3)
    windowButton(width - 31, 3, WindowGlyph.MINIMIZE)
    windowButton(width - 21, 3, WindowGlyph.SHADE)
    windowButton(width - 11, 3, WindowGlyph.CLOSE)
}

internal enum class WindowGlyph {
    MINIMIZE,
    SHADE,
    CLOSE,
}

/** The 9x9 title-bar buttons: a dark well with a pale glyph. */
internal fun PixelPainter.windowButton(x: Int, y: Int, glyph: WindowGlyph) {
    rect(x, y, 9, 9, Amp.GoldShade)
    bevel(x, y, 9, 9, Amp.GoldMid, Amp.Edge)
    when (glyph) {
        WindowGlyph.MINIMIZE -> hline(x + 2, y + 6, 5, Amp.Gold)
        WindowGlyph.SHADE -> {
            hline(x + 2, y + 2, 5, Amp.Gold)
            hline(x + 2, y + 3, 5, Amp.GoldMid)
        }
        WindowGlyph.CLOSE -> {
            for (i in 0..4) {
                dot(x + 2 + i, y + 2 + i, Amp.Gold)
                dot(x + 6 - i, y + 2 + i, Amp.Gold)
            }
        }
    }
}

/** The squiggle at the far left of the main title bar that opens the options menu. */
internal fun PixelPainter.optionsGlyph(x: Int, y: Int) {
    val pts = listOf(0 to 6, 1 to 5, 2 to 4, 3 to 3, 3 to 2, 4 to 1, 5 to 2, 5 to 3, 6 to 4, 7 to 5, 8 to 4)
    pts.forEach { (dx, dy) -> dot(x + dx, y + dy, Amp.Gold) }
}

/**
 * A raised steel button: pale outer line, white highlight top-left, grey-blue shadow bottom-right.
 * [pressed] sinks it.
 */
internal fun PixelPainter.steelButton(x: Int, y: Int, w: Int, h: Int, pressed: Boolean = false) {
    rect(x, y, w, h, if (pressed) Amp.ButtonPressed else Amp.ButtonFace)
    rect(x, y, w, 1, Amp.ButtonOuter)
    rect(x, y, 1, h, Amp.ButtonOuter)
    if (pressed) {
        hline(x + 1, y + 1, w - 2, Amp.ButtonShadow)
        vline(x + 1, y + 1, h - 2, Amp.ButtonShadow)
    } else {
        hline(x + 1, y + 1, w - 3, Amp.ButtonHi)
        vline(x + 1, y + 1, h - 3, Amp.ButtonHi)
        hline(x + 2, y + h - 3, w - 4, Amp.ButtonOuter)
        hline(x + 1, y + h - 2, w - 2, Amp.ButtonShadow2)
        vline(x + w - 2, y + 1, h - 2, Amp.ButtonShadow)
    }
    hline(x, y + h - 1, w, Amp.ButtonShadow)
    vline(x + w - 1, y, h, Amp.ButtonShadow)
}

/** A steel toggle with an LED square at its left and a dark label: EQ, PL, ON, AUTO, SHUFFLE. */
internal fun PixelPainter.ledButton(x: Int, y: Int, w: Int, h: Int, label: String, lit: Boolean) {
    steelButton(x, y, w, h)
    val ledY = y + (h - 4) / 2
    rect(x + 3, ledY, 4, 4, Amp.ButtonShadow)
    rect(x + 4, ledY + 1, 2, 2, if (lit) Amp.LedOn else Amp.LedOff)
    if (lit) dot(x + 3, ledY, Amp.LedOn)
    if (label.isNotEmpty()) pixelText(label, x + 9, y + (h - 5) / 2, Amp.ButtonShadow)
}

/** A glyph drawn outlined in shadow blue and filled pale, as on the transport buttons. */
internal fun PixelPainter.outlinedGlyph(cells: List<String>, x: Int, y: Int) {
    for (row in cells.indices) {
        for (col in cells[row].indices) {
            when (cells[row][col]) {
                '#' -> dot(x + col, y + row, Amp.ButtonShadow)
                'o' -> dot(x + col, y + row, Amp.ButtonGlyph)
                'w' -> dot(x + col, y + row, Amp.ButtonHi)
            }
        }
    }
}

/** A sunken black panel with a dark top-left and a light bottom-right edge. */
internal fun PixelPainter.sunkenPanel(x: Int, y: Int, w: Int, h: Int, fill: Int = Amp.Black) {
    rect(x, y, w, h, fill)
    hline(x - 1, y - 1, w + 2, Amp.PanelShadow)
    vline(x - 1, y - 1, h + 2, Amp.PanelShadow)
    hline(x - 1, y + h, w + 2, Amp.InnerLight)
    vline(x + w, y - 1, h + 2, Amp.InnerLight)
}

/** A horizontal slider thumb: steel, 14x11, with three grip lines. */
internal fun PixelPainter.sliderThumb(x: Int, y: Int, pressed: Boolean = false) {
    steelButton(x, y, 14, 11, pressed)
    for (i in 0..2) vline(x + 5 + i * 2, y + 3, 5, Amp.ButtonShadow)
}

/** A vertical equaliser thumb: steel, 11x11, with a double rule. */
internal fun PixelPainter.eqThumb(x: Int, y: Int) {
    steelButton(x, y, 11, 11)
    hline(x + 3, y + 4, 5, Amp.ButtonShadow)
    hline(x + 3, y + 6, 5, Amp.ButtonShadow)
}
