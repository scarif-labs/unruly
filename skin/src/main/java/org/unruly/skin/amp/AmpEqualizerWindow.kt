/*
 * Copyright (c) 2026 Unruly Project
 * AmpEqualizerWindow.kt is part of Unruly.
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

internal const val EQ_H = 116

/** Slider left edges: the preamp, then the ten bands. */
internal val EQ_SLIDER_X = intArrayOf(21, 78, 96, 114, 132, 150, 168, 186, 204, 222, 240)

private const val SLIDER_TOP = 38
private const val SLIDER_H = 63
private const val THUMB = 11

private val BAND_LABELS = listOf("60", "170", "310", "600", "1K", "3K", "6K", "12K", "14K", "16K")

/**
 * The equaliser window. The engine has no equaliser, so this one is honest about being a model: the
 * sliders move and are remembered, the graph follows them, and nothing reaches the audio.
 */
internal class EqModel(val on: Boolean, val auto: Boolean, val values: FloatArray)

internal enum class EqHit {
    CLOSE,
    ON,
    AUTO,
    RESET,
}

internal fun eqHitAt(x: Float, y: Float): EqHit? {
    fun inside(l: Int, t: Int, w: Int, h: Int) = x >= l && x < l + w && y >= t && y < t + h
    return when {
        inside(262, 0, 13, 14) -> EqHit.CLOSE
        inside(12, 15, 30, 18) -> EqHit.ON
        inside(40, 15, 34, 18) -> EqHit.AUTO
        inside(215, 15, 48, 18) -> EqHit.RESET
        else -> null
    }
}

/** Which slider (0 = preamp) a touch at [x] belongs to, if any. */
internal fun eqSliderAt(x: Float, y: Float): Int? {
    if (y < SLIDER_TOP - 4 || y > SLIDER_TOP + SLIDER_H + 4) return null
    return EQ_SLIDER_X.indices.firstOrNull { x >= EQ_SLIDER_X[it] - 3 && x < EQ_SLIDER_X[it] + 17 }
}

/** Slider value for a touch at [y]: 1 at the top (+12 dB), 0 at the bottom (-12 dB). */
internal fun eqValueAt(y: Float): Float =
    (1f - (y - SLIDER_TOP - THUMB / 2f) / (SLIDER_H - THUMB)).coerceIn(0f, 1f)

internal fun PixelPainter.drawEqualizerWindow(m: EqModel) {
    windowBody()
    titleBar("UNRULY EQUALIZER", leftButton = false)
    hline(1, 14, 271, Amp.InnerLight)
    hline(1, 112, 271, Amp.InnerLight)
    vline(6, 14, 99, Amp.InnerLight)
    vline(271, 14, 99, Amp.InnerLight)

    ledButton(14, 18, 26, 12, "ON", m.on)
    ledButton(40, 18, 32, 12, "AUTO", m.auto)
    steelButton(217, 18, 44, 12)
    // Flattens the curve: there are no presets to choose from.
    pixelText("RESET", 239 - pixelTextWidth("RESET") / 2, 21, Amp.ButtonShadow)

    drawGraph(m)

    // Level marks and labels.
    pixelText("+12DB", 44, 40, Amp.LabelGold)
    pixelText("+0DB", 47, 67, Amp.LabelGold)
    pixelText("-12DB", 44, 95, Amp.LabelGold)
    for (x in EQ_SLIDER_X) {
        for (y in intArrayOf(42, 69, 97)) hline(x - 4, y, 3, Amp.Label)
    }
    for (y in intArrayOf(42, 69, 97)) hline(EQ_SLIDER_X.last() + 17, y, 3, Amp.Label)

    EQ_SLIDER_X.forEachIndexed { i, x -> drawSlider(x, m.values[i]) }

    pixelText("PREAMP", 14, 104, Amp.Label)
    BAND_LABELS.forEachIndexed { i, label ->
        val x = EQ_SLIDER_X[i + 1] + 7 - pixelTextWidth(label) / 2
        pixelText(label, x, 104, Amp.Label)
    }
}

private fun PixelPainter.drawSlider(x: Int, value: Float) {
    val color = sliderColor(value)
    vline(x + 3, SLIDER_TOP, SLIDER_H, rgb(0x13131E))
    rect(x + 4, SLIDER_TOP + 1, 4, SLIDER_H - 2, color)
    vline(x + 4, SLIDER_TOP + 1, SLIDER_H - 2, blend(color, Amp.Black, 0.15f))
    vline(x + 8, SLIDER_TOP, SLIDER_H, rgb(0x9393A5))
    val thumbY = SLIDER_TOP + ((1f - value) * (SLIDER_H - THUMB)).toInt()
    eqThumb(x + 1, thumbY)
}

/** The response graph: a dark well with the preamp line and the bands joined into a curve. */
private fun PixelPainter.drawGraph(m: EqModel) {
    rect(86, 17, 113, 19, rgb(0x0E0E16))
    bevel(86, 17, 113, 19, Amp.PanelShadow, Amp.InnerLight)
    val preampY = 18 + ((1f - m.values[0]) * 16).toInt()
    hline(87, preampY, 111, rgb(0x6E6E2A))
    var lastY = -1
    for (px in 0 until 111) {
        val pos = px / 110f * 9f
        val i = pos.toInt().coerceAtMost(8)
        val v = m.values[1 + i] * (1 - (pos - i)) + m.values[2 + i] * (pos - i)
        val y = 18 + ((1f - v) * 16).toInt()
        val color = sliderColor(v)
        if (lastY >= 0) {
            val from = minOf(lastY, y)
            val to = maxOf(lastY, y)
            vline(87 + px, from, to - from + 1, color)
        } else {
            dot(87 + px, y, color)
        }
        lastY = y
    }
}
