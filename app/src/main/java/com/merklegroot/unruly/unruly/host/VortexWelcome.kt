/*
 * Copyright (c) 2026 Unruly Project
 * VortexWelcome.kt is part of Unruly.
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
 
package com.merklegroot.unruly.unruly.host

import android.app.Activity
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.merklegroot.unruly.R
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

internal val VortexRed = Color(0xFFF32B16)
internal val VortexIvory = Color(0xFFF4EFDF)
internal val VortexInk = Color(0xFF131310)
internal val WelcomeBody =
    FontFamily(Font(R.font.inter_regular), Font(R.font.inter_semibold, FontWeight.SemiBold))

/** Every setup page is printed on the same red stock, so the system bars use light icons. */
@Suppress("DEPRECATION")
internal fun applyRedlineSystemBars(context: Context) {
    val window = (context as? Activity)?.window ?: return
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = false
    }
    // Before Android 15 the system bars do not automatically inherit the content behind them.
    window.statusBarColor = VortexRed.toArgb()
    window.navigationBarColor = VortexRed.toArgb()
}

/**
 * A native, continuously advancing type tunnel. Only drawing observes the frame clock; text,
 * semantics and the CTA stay still. The CTA enters folder setup, rather than bypassing its grant.
 */
@Composable
internal fun VortexWelcome(stepCount: Int, onContinue: () -> Unit) {
    val context = LocalContext.current
    val appName = stringResource(R.string.info_app_brand)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val motionScale by rememberSystemMotionScale()
    val phase = remember { mutableFloatStateOf(0.35f) }
    // Drives the wobble. It is separate from phase so the ring wrap stays seamless; every wobble
    // frequency is a multiple of 0.1 rad/s, so wrapping at 20π seconds is seamless as well.
    val clock = remember { mutableFloatStateOf(0f) }
    val dive = remember { Animatable(0f) }
    var entering by remember { mutableStateOf(false) }
    val continueNow by rememberUpdatedState(onContinue)
    val tunnel = remember(context) { TypeTunnel(context) }

    LaunchedEffect(lifecycle, motionScale) {
        // Capture this effect's scale. The preference can become zero one frame before the old
        // effect is cancelled; reading the live state in that frame would poison phase with NaN.
        val durationScale = motionScale
        if (durationScale <= 0f || !durationScale.isFinite()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previousFrame = 0L
            while (true) {
                withFrameNanos { now ->
                    if (previousFrame != 0L) {
                        val seconds = ((now - previousFrame) / 1_000_000_000f).coerceAtMost(0.05f)
                        val speed = 1f + dive.value * dive.value * 22f
                        phase.floatValue =
                            (phase.floatValue + seconds * speed / (2.8f * durationScale)) % 1f
                        clock.floatValue =
                            (clock.floatValue + seconds / durationScale) % (20 * PI.toFloat())
                    }
                    previousFrame = now
                }
            }
        }
    }

    LaunchedEffect(entering) {
        if (entering) {
            dive.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
            continueNow()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(VortexRed)) {
        // Landscape phones, tablets and unfolded foldables put the tunnel beside the copy; a
        // stacked layout there would leave the tunnel a thin strip under the banner.
        val sideBySide = maxWidth > maxHeight
        val screenHeight = maxHeight
        val controlsWidth = min(400.dp, maxWidth * 0.42f)
        val tunnelPane: @Composable (Modifier) -> Unit = { modifier ->
            VortexTunnelPane(modifier, appName) { canvas, width, height ->
                tunnel.draw(canvas, width, height, phase.floatValue, clock.floatValue, dive.value)
            }
        }
        val controls: @Composable (Modifier) -> Unit = { modifier ->
            WelcomeControls(modifier, enabled = !entering) {
                if (motionScale <= 0f) continueNow() else entering = true
            }
        }

        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp)
        ) {
            StepProgress(step = 0, count = stepCount)

            if (sideBySide) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    tunnelPane(Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.width(20.dp))
                    // Bottom-aligned when it fits, scrollable when large fonts make it taller.
                    Box(
                        Modifier.width(controlsWidth).fillMaxHeight(),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        controls(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()))
                    }
                }
            } else {
                tunnelPane(Modifier.fillMaxWidth().weight(1f))
                // Cap the copy so very large font scales scroll instead of collapsing the tunnel.
                controls(
                    Modifier.fillMaxWidth()
                        .heightIn(max = screenHeight * 0.5f)
                        .verticalScroll(rememberScrollState())
                )
            }
        }

        // A cached transparent grain tile stays fixed in screen space, including during the dive.
        Spacer(Modifier.matchParentSize().printGrain())
        Canvas(Modifier.matchParentSize()) {
            if (dive.value > 0f) drawRect(VortexInk.copy(alpha = dive.value.pow(4)))
        }
    }
}

@Composable
private fun VortexTunnelPane(
    modifier: Modifier,
    appName: String,
    draw: (AndroidCanvas, Float, Float) -> Unit,
) {
    BoxWithConstraints(modifier.clipToBounds()) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = appName }) {
            drawIntoCanvas { draw(it.nativeCanvas, size.width, size.height) }
        }
        // Proportional to the pane, but never taller than a large share of a short one.
        val bannerHeight = (maxWidth * 0.21f).coerceIn(72.dp, 132.dp).coerceAtMost(maxHeight * 0.4f)
        VortexBanner(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bannerHeight))
    }
}

@Composable
private fun WelcomeControls(modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier.padding(top = 8.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(VortexIvory))
        Text(
            "Your music goes deeper.",
            color = VortexIvory,
            fontFamily = WelcomeBody,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        )
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(VortexIvory)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Find your way in",
                modifier = Modifier.weight(1f),
                color = VortexInk,
                fontFamily = WelcomeBody,
                fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp,
                lineHeight = 27.sp,
            )
            Spacer(Modifier.width(12.dp))
            Canvas(Modifier.size(28.dp)) {
                val stroke = 1.5.dp.toPx()
                drawLine(
                    VortexInk,
                    Offset(1f, size.height / 2),
                    Offset(size.width - 2f, size.height / 2),
                    stroke,
                )
                drawLine(
                    VortexInk,
                    Offset(size.width * 0.62f, size.height * 0.18f),
                    Offset(size.width - 2f, size.height / 2),
                    stroke,
                )
                drawLine(
                    VortexInk,
                    Offset(size.width * 0.62f, size.height * 0.82f),
                    Offset(size.width - 2f, size.height / 2),
                    stroke,
                )
            }
        }
        Colophon("NO FEED. JUST FEELING.")
    }
}

/**
 * The progress bar every setup page shares: one segment per step, filled up to [step]. The current
 * segment fills in as the page appears, so moving forward reads as progress rather than a swap. A
 * null [step] (the folder editor opened from settings) is outside the flow and shows no bar.
 */
@Composable
internal fun StepProgress(step: Int?, count: Int) {
    if (step == null) {
        Spacer(Modifier.height(16.dp))
        return
    }
    val fill = remember { Animatable(0f) }
    LaunchedEffect(step) { fill.animateTo(1f, tween(500, delayMillis = 150)) }
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp).height(4.dp).semantics {
            contentDescription = "Step ${step + 1} of $count"
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { index ->
            Box(Modifier.weight(1f).fillMaxHeight().background(VortexIvory.copy(alpha = 0.3f))) {
                val done =
                    when {
                        index < step -> 1f
                        index == step -> fill.value
                        else -> 0f
                    }
                if (done > 0f) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(done).background(VortexIvory))
                }
            }
        }
    }
}

@Composable
internal fun Colophon(text: String, color: Color = VortexIvory) {
    Text(
        text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.sp,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun VortexBanner(modifier: Modifier) {
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = VortexIvory.toArgb()
            typeface =
                android.graphics.Typeface.create(
                    "sans-serif-condensed",
                    android.graphics.Typeface.BOLD_ITALIC,
                )
        }
    }
    val black = remember { Paint().apply { color = VortexInk.toArgb() } }
    Canvas(
        modifier.semantics {
            contentDescription = "Get into it."
            heading()
        }
    ) {
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            canvas.save()
            canvas.rotate(-4f, size.width / 2, size.height / 2)
            val top = size.height * 0.19f
            val bottom = size.height * 0.86f
            canvas.drawRect(0f, top, size.width, bottom, black)
            paint.textSize = size.height * 0.63f
            paint.textScaleX = 1f
            // Condense or widen only slightly; past that, resize, so tablets don't get smeared
            // letters and narrow phones don't get crushed ones.
            val target = size.width * 0.92f
            val natural = paint.measureText("GET INTO IT.")
            paint.textScaleX = (target / natural).coerceIn(0.72f, 1.15f)
            if (natural * paint.textScaleX > target) {
                paint.textSize *= target / (natural * paint.textScaleX)
            }
            val left = (size.width - paint.measureText("GET INTO IT.")) / 2f
            val baseline = (top + bottom - paint.ascent() - paint.descent()) / 2f
            canvas.drawText("GET INTO IT.", left, baseline, paint)
            canvas.restore()
        }
    }
}

/**
 * Observe the platform preference as well as its initial value: changing "Remove animations" while
 * this Activity is alive must stop it immediately, without a relaunch.
 */
@Composable
private fun rememberSystemMotionScale(): State<Float> {
    val resolver = LocalContext.current.contentResolver
    return produceState(
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
        resolver,
    ) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    value =
                        Settings.Global.getFloat(
                            resolver,
                            Settings.Global.ANIMATOR_DURATION_SCALE,
                            1f,
                        )
                }
            }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        awaitDispose { resolver.unregisterContentObserver(observer) }
    }
}

internal fun Modifier.printGrain(): Modifier = drawWithCache {
    val random = Random(2077)
    val pixels =
        IntArray(192 * 192) {
            when (random.nextInt(100)) {
                in 0..6 -> 0x2B131310
                in 7..13 -> 0x32F4EFDF
                else -> 0
            }
        }
    val bitmap = Bitmap.createBitmap(pixels, 192, 192, Bitmap.Config.ARGB_8888)
    val paint =
        Paint().apply {
            shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    onDrawBehind {
        drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
    }
}

/**
 * Map a cached text strip onto nested rings that recede to an off-centre aperture. Each ring's
 * centre slides away from the aperture in proportion to its radius, as the mouth of a tilted tube
 * would, so the rings crowd together on the aperture's far side. The offset saturates, which bends
 * the tube's axis back toward the viewer instead of letting the large rings drift offscreen.
 *
 * Geometry depends only on absolute radius, so phase=1 is exactly phase=0 with the ring indices
 * shifted by one. New rings emerge from under the black aperture and old ones leave the viewport:
 * no crossfade or reset. The atlas, trig table, vertex buffer and paints are allocated once.
 *
 * The word is set in Inter Black, the heavy grotesk of the Unruly logo (direction L2-D), tracked
 * tight like the logo. Inter is bundled under SIL OFL 1.1 (licenses/Inter-OFL.txt); it needs no
 * network or font provider.
 */
private class TypeTunnel(context: Context) {
    private val words = 6
    private val columns = words * 16
    private val rows = 6
    private val vertices = FloatArray((columns + 1) * (rows + 1) * 2)
    private val angles = FloatArray(columns + 1) { (-PI / 2 + it * 2 * PI / columns).toFloat() }
    private val cosines = FloatArray(columns + 1) { cos(angles[it]) }
    private val sines = FloatArray(columns + 1) { sin(angles[it]) }
    private val doubleCosines = FloatArray(columns + 1) { cos(2f * angles[it]) }
    private val doubleSines = FloatArray(columns + 1) { sin(2f * angles[it]) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = VortexInk.toArgb() }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shadeKey = Float.NaN
    private val atlas =
        Bitmap.createBitmap(4096, 256, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = AndroidCanvas(bitmap)
            val type =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = VortexIvory.toArgb()
                    typeface = ResourcesCompat.getFont(context, R.font.inter_black)
                    textSize = 256f
                    letterSpacing = -0.04f
                }
            val word = context.getString(R.string.info_app_brand).lowercase(Locale.ROOT)
            val pitch = bitmap.width.toFloat() / words
            // Fit the actual ink, not line metrics: the font's leading would leave uneven red gaps.
            // The remaining 18% of the strip is the red line between rings.
            val bounds = Rect()
            type.getTextBounds(word, 0, word.length, bounds)
            type.textSize *= bitmap.height * 0.82f / bounds.height()
            type.getTextBounds(word, 0, word.length, bounds)
            type.textScaleX = pitch * 0.9f / bounds.width()
            val baseline = (bitmap.height - bounds.height()) / 2f - bounds.top
            for (cell in 0 until words) {
                val centerX = (cell + 0.5f) * pitch
                canvas.save()
                // Words 2 and 3 cover the lower arc (30° to 150°). Turning them over keeps them
                // upright and left-to-right instead of reading upside down across the bottom.
                if (cell in 2..3) canvas.rotate(180f, centerX, bitmap.height / 2f)
                canvas.drawText(
                    word,
                    centerX - (bounds.exactCenterX()) * type.textScaleX,
                    baseline,
                    type,
                )
                canvas.restore()
            }
        }

    fun draw(
        canvas: AndroidCanvas,
        width: Float,
        height: Float,
        phase: Float,
        time: Float,
        dive: Float,
    ) {
        if (width <= 0f || height <= 0f) return
        // Size by the short side so the aperture and ring rhythm hold in landscape and on tablets.
        val unit = min(width, height)
        val aperture = unit * 0.085f
        val coreX = width * 0.70f
        val coreY = height * 0.56f
        val saturation = unit * 0.65f
        val holeShift = aperture / (1f + aperture / saturation)
        val holeX = coreX - TILT_X * holeShift
        val holeY = coreY - TILT_Y * holeShift
        val zoom = 1f + dive * dive * 3f
        canvas.save()
        canvas.scale(zoom, zoom, holeX, holeY)

        var ring = 0
        while (ring < MAX_RINGS) {
            val outer = aperture * RATIO.pow(ring + phase)
            val inner = outer / RATIO
            for (row in 0..rows) {
                val r = outer + (inner - outer) * row / rows
                val depth = ln(r / aperture)
                // The wobble fades in away from the aperture, which stays a clean, steady hole.
                val loose = (depth / 1.5f).coerceIn(0f, 1f)
                val shift = r / (1f + r / saturation)
                // The tube's axis snakes slowly.
                val centerX =
                    coreX - TILT_X * shift + unit * 0.021f * loose * sin(depth * 0.8f - time * 0.5f)
                val centerY =
                    coreY - TILT_Y * shift + unit * 0.015f * loose * cos(depth * 0.6f - time * 0.4f)
                // A twist with depth gives the spiral; a rotation wave rolls down the tunnel on
                // top of it, so neighbouring rings counter-rotate. Both vary across each band,
                // which shears the letters. All terms depend on radius, not ring index, so bands
                // still meet exactly and the phase wrap stays seamless.
                val spin = 0.14f * (depth - 2f) + 0.18f * loose * sin(depth * 1.9f - time * 0.9f)
                val pinch = 0.1f * loose
                val wobble = 0.045f * loose
                // ~10k vertices a frame: per-vertex trig blew the frame budget, so each term is
                // expanded with angle-addition identities over per-row and per-column tables.
                val spinCos = cos(spin)
                val spinSin = sin(spin)
                val pinchCos = cos(depth * 1.1f - time * 0.7f)
                val pinchSin = sin(depth * 1.1f - time * 0.7f)
                val lobe2Cos = cos(depth * 1.3f + time * 0.7f)
                val lobe2Sin = sin(depth * 1.3f + time * 0.7f)
                val lobe3Cos = cos(-depth * 0.9f - time * 0.5f)
                val lobe3Sin = sin(-depth * 0.9f - time * 0.5f)
                for (col in 0..columns) {
                    // Letters stretch and squeeze along the ring like rubber: offset each angle
                    // by up to 0.1 rad. Small enough for a series expansion, and to stay
                    // monotonic, so the type never folds over itself.
                    val nudge =
                        pinch * (doubleSines[col] * pinchCos + doubleCosines[col] * pinchSin)
                    val nudgeSquared = nudge * nudge
                    val nudgeCos = 1f - nudgeSquared * 0.5f + nudgeSquared * nudgeSquared / 24f
                    val nudgeSin = nudge * (1f - nudgeSquared / 6f)
                    val spunX = cosines[col] * spinCos - sines[col] * spinSin
                    val spunY = sines[col] * spinCos + cosines[col] * spinSin
                    val x = spunX * nudgeCos - spunY * nudgeSin
                    val y = spunY * nudgeCos + spunX * nudgeSin
                    // The radius bulges into a slowly turning, lopsided blob (2 and 3 lobes).
                    val sin2 = 2f * x * y
                    val cos2 = x * x - y * y
                    val sin3 = y * (3f - 4f * y * y)
                    val cos3 = x * (4f * x * x - 3f)
                    val bulge =
                        0.6f * (sin2 * lobe2Cos + cos2 * lobe2Sin) +
                            0.4f * (sin3 * lobe3Cos + cos3 * lobe3Sin)
                    val radius = r * (1f + wobble * bulge)
                    val vertex = (row * (columns + 1) + col) * 2
                    vertices[vertex] = centerX + x * radius
                    vertices[vertex + 1] = centerY + y * radius * SQUASH
                }
            }
            val depth = ((outer / aperture - 1f) / 3f).coerceIn(0f, 1f)
            paint.alpha = (255 * (0.45f + 0.55f * depth)).toInt()
            canvas.drawBitmapMesh(atlas, columns, rows, vertices, 0, null, 0, paint)
            // Stop once the band's inner edge encloses the whole pane: everything further out
            // is offscreen.
            val shift = inner / (1f + inner / saturation)
            if (encloses(coreX - TILT_X * shift, coreY - TILT_Y * shift, inner, width, height)) {
                break
            }
            ring++
        }

        // Shade the rings toward the aperture, then cut the aperture itself.
        val reach = aperture * 3.4f
        if (shadeKey != holeX + holeY * 7f + reach * 13f) {
            shadeKey = holeX + holeY * 7f + reach * 13f
            shade.shader =
                RadialGradient(
                    holeX,
                    holeY,
                    reach,
                    intArrayOf(
                        VortexInk.toArgb(),
                        VortexInk.copy(alpha = 0.62f).toArgb(),
                        VortexInk.copy(alpha = 0.22f).toArgb(),
                        0,
                    ),
                    floatArrayOf(0f, 0.3f, 0.55f, 1f),
                    Shader.TileMode.CLAMP,
                )
        }
        canvas.drawCircle(holeX, holeY, reach, shade)
        canvas.drawOval(
            holeX - aperture,
            holeY - aperture * SQUASH,
            holeX + aperture,
            holeY + aperture * SQUASH,
            ink,
        )
        canvas.restore()
    }

    private fun encloses(x: Float, y: Float, r: Float, width: Float, height: Float): Boolean {
        for (corner in 0..3) {
            val dx = ((if (corner % 2 == 0) 0f else width) - x) / r
            val dy = ((if (corner < 2) 0f else height) - y) / (r * SQUASH)
            if (dx * dx + dy * dy > 1f) return false
        }
        return true
    }

    private companion object {
        /** Radius ratio between neighbouring rings. */
        const val RATIO = 1.36f
        /** Rings are slightly taller than wide, like the reference's aperture. */
        const val SQUASH = 1.06f
        /** How far each ring's centre moves per unit radius; must stay below 1 so rings nest. */
        const val TILT_X = 0.72f
        const val TILT_Y = 0.36f
        /** A backstop for extreme aspect ratios; a phone needs about 14. */
        const val MAX_RINGS = 40
    }
}
