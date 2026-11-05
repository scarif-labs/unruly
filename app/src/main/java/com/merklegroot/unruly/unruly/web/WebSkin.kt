/*
 * Copyright (c) 2026 Unruly Project
 * WebSkin.kt is part of Unruly.
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

package com.merklegroot.unruly.unruly.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.merklegroot.unruly.BuildConfig
import com.merklegroot.unruly.image.covers.SettingCovers
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.oxycblt.musikr.covers.CoverResult
import org.unruly.skin.AppSkin
import org.unruly.skin.SkinSession
import timber.log.Timber as L

/**
 * A user-loaded skin: a web page from an installed `.unruly` package, run in a WebView and
 * driven through `window.unruly` ([WebSkinBridge] + `bridge.js`).
 *
 * The page is someone else's code, so the WebView is locked down:
 * - It is served from its own origin, `https://<id>.skin.unruly/`, through
 *   [WebViewAssetLoader] from the unpacked package. Per-skin origins keep one skin's localStorage
 *   (its place to save its own settings) away from another's.
 * - **Every other request is refused** in [WebViewClient.shouldInterceptRequest], and a CSP on
 *   each page backs that up for what interception can't see (sockets, beacons). Album art is
 *   served on the same origin under `/__unruly/art/`, from our own cover provider. Skins have no
 *   network at all; lyrics come through the bridge.
 * - No file or content access, and navigation away from the skin's origin is blocked.
 *
 * Unlike built-in skins, a web skin cannot turn the host's settings gear off
 * ([settingsGearAlignment] is never null): it is the guaranteed way out of a skin we didn't
 * write, and a page can paint anything over its own "settings" button.
 *
 * A renderer crash is caught ([WebViewClient.onRenderProcessGone] returns true, so the app
 * survives it) and reported through [onFailure], which quarantines the skin like a built-in crash.
 */
class WebSkin(
    val manifest: UserSkinManifest,
    val dir: File,
    /** The page loaded and has run for a while - this version works. */
    private val onLoaded: (WebSkin) -> Unit,
    private val onFailure: (WebSkin, String) -> Unit,
) : AppSkin {
    override val id: String = USER_PREFIX + manifest.id
    override val displayName: String = manifest.name
    override val supportsEdgeToEdge: Boolean = manifest.edgeToEdge
    override val settingsGearAlignment: Alignment =
        when (manifest.settingsGear) {
            "top-start" -> Alignment.TopStart
            "bottom-start" -> Alignment.BottomStart
            "bottom-end" -> Alignment.BottomEnd
            else -> Alignment.TopEnd
        }

    /** Set by the page through `unruly.setCanGoBack`; read synchronously on back. */
    @Volatile internal var canGoBack = false
    private var bridge: WebSkinBridge? = null

    private val host = manifest.id.replace('.', '-').replace('_', '-') + ".skin.unruly"
    private val origin = "https://$host"

    override fun onBackPressed(): Boolean {
        val bridge = bridge ?: return false
        if (!canGoBack) return false
        bridge.pushBack()
        return true
    }

    @Composable
    override fun Render(session: SkinSession) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        // Errors for the on-screen panel; only collected when it can be shown.
        val errors = remember(id) { mutableStateListOf<String>() }
        val showErrors = BuildConfig.DEBUG || manifest.developer
        val webView =
            remember(id) {
                createWebView(context) { error ->
                    if (showErrors) {
                        errors.add(error)
                        if (errors.size > MAX_SKIN_ERRORS) errors.removeAt(0)
                    }
                }
            }
        val bridge =
            remember(webView) {
                WebSkinBridge(context, this, session, webView, scope, "$origin/__unruly/art/") {
                    // Confirmed once it has run a while: a page that crashes straight after
                    // loading should still be able to roll back. A crash disposes this
                    // composition, cancelling the wait.
                    scope.launch {
                        delay(CONFIRM_AFTER_MS)
                        onLoaded(this@WebSkin)
                    }
                }
            }

        DisposableEffect(webView) {
            this@WebSkin.bridge = bridge
            if (!connect(webView, bridge)) {
                onFailure(this@WebSkin, "This phone's WebView is too old for web skins")
            } else {
                // Only once it has a size: a page loaded into a 0x0 WebView keeps a zero-height
                // layout viewport, so everything sized to 100% collapses (seen on device).
                webView.doOnLayout { webView.loadUrl("$origin/${manifest.entry}") }
            }
            onDispose {
                if (this@WebSkin.bridge === bridge) this@WebSkin.bridge = null
                canGoBack = false
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
                setSystemBarIcons(context, null)
            }
        }

        LaunchedEffect(bridge) { session.playback.collect { bridge.pushPlayback(it) } }
        LaunchedEffect(bridge) { session.library.loadState.collect { bridge.pushLoadState(it) } }
        // The first tick is the current state, which the page already gets on hello.
        LaunchedEffect(bridge) { session.library.changes.drop(1).collect { bridge.pushLibraryChanged() } }

        // Insets as CSS px (which are dp, given a width=device-width viewport).
        val density = LocalDensity.current
        val insets = WindowInsets.safeDrawing
        val top = with(density) { insets.getTop(this).toDp().value }
        val bottom = with(density) { insets.getBottom(this).toDp().value }
        val left = with(density) { insets.getLeft(this, LayoutDirection.Ltr).toDp().value }
        val right = with(density) { insets.getRight(this, LayoutDirection.Ltr).toDp().value }
        LaunchedEffect(bridge, top, right, bottom, left) {
            // A skin that isn't edge-to-edge is already padded by the host.
            if (supportsEdgeToEdge) bridge.pushInsets(top, right, bottom, left)
            else bridge.pushInsets(0f, 0f, 0f, 0f)
        }

        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle, webView) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        webView.onResume()
                        webView.resumeTimers()
                        bridge.pushVisibility(true)
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        bridge.pushVisibility(false)
                        webView.onPause()
                        webView.pauseTimers()
                    }
                    else -> {}
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }

        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = {
                    webView.apply {
                        layoutParams =
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (showErrors) {
                SkinErrorPanel(
                    errors,
                    gearAtStart = manifest.settingsGear.endsWith("start"),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context, onError: (String) -> Unit): WebView {
        val loader =
            WebViewAssetLoader.Builder()
                .setDomain(host)
                .addPathHandler("/__unruly/art/") { path -> art(context, path) }
                .addPathHandler("/", WebViewAssetLoader.InternalStoragePathHandler(context, dir))
                .build()
        // Debug builds: inspect the skin from desktop Chrome/Brave at chrome://inspect.
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        return WebView(context).apply {
            setBackgroundColor(AndroidColor.BLACK)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                setGeolocationEnabled(false)
                mediaPlaybackRequiresUserGesture = false
                setSupportZoom(false)
                builtInZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = false
                textZoom = 100
            }
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            webViewClient =
                object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse {
                        if (request.url.host != host) return refused()
                        // Browsers ask for one unprompted; skins don't ship it.
                        if (request.url.path == "/favicon.ico") return notFound()
                        val response = loader.shouldInterceptRequest(request.url) ?: return notFound()
                        return response.withPolicy()
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = request.url.host != host

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (request.isForMainFrame) {
                            onFailure(this@WebSkin, "The skin's page didn't load")
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        if (request.isForMainFrame) {
                            onFailure(this@WebSkin, "The skin's page didn't load")
                        }
                    }

                    override fun onRenderProcessGone(
                        view: WebView,
                        detail: RenderProcessGoneDetail,
                    ): Boolean {
                        L.e("Web skin $id renderer gone (crash=${detail.didCrash()})")
                        (view.parent as? ViewGroup)?.removeView(view)
                        view.destroy()
                        onFailure(this@WebSkin, "The skin crashed")
                        // True keeps the app alive; false would kill it along with the page.
                        return true
                    }
                }
            webChromeClient =
                object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        val source = message.sourceId().substringAfterLast('/')
                        val line = "${message.message()} ($source:${message.lineNumber()})"
                        L.d("[skin ${manifest.id}] $line")
                        if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            onError(line)
                        }
                        return true
                    }
                }
        }
    }

    /** Wires bridge.js and the message channel to this skin's origin only. */
    private fun connect(webView: WebView, bridge: WebSkinBridge): Boolean {
        if (
            !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            return false
        }
        val origins = setOf(origin)
        WebViewCompat.addWebMessageListener(webView, "__unrulyNative", origins) {
            _,
            message,
            _,
            isMainFrame,
            reply ->
            if (isMainFrame) message.data?.let { bridge.onMessage(it, reply) }
        }
        val script = webView.context.assets.open("unruly-bridge.js").bufferedReader().use { it.readText() }
        WebViewCompat.addDocumentStartJavaScript(webView, script, origins)
        return true
    }

    /** `icons` is "dark" or "light"; anything else restores the default (light icons). */
    internal fun setSystemBarIcons(context: Context, icons: String?) {
        val window = (context as? Activity)?.window ?: return
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = icons == "dark"
            isAppearanceLightNavigationBars = icons == "dark"
        }
    }

    /**
     * Serves a cover by id. Looked up directly rather than through the cover provider, whose
     * one-segment URI match and `lastPathSegment` read can't take folder-cover ids: those are a whole
     * `fs:content://...` URI and span several path segments.
     */
    private fun art(context: Context, path: String): WebResourceResponse? =
        try {
            val pfd =
                runBlocking {
                    (SettingCovers.immutable(context).obtain(path) as? CoverResult.Hit)?.cover?.fd()
                }
            // AutoClose closes the descriptor with the stream, so served covers don't leak fds.
            pfd?.let {
                WebResourceResponse("image/jpeg", null, ParcelFileDescriptor.AutoCloseInputStream(it))
            }
        } catch (e: Exception) {
            L.w(e, "No cover for $path")
            null
        }

    private fun WebResourceResponse.withPolicy(): WebResourceResponse {
        responseHeaders = (responseHeaders.orEmpty() + POLICY_HEADERS)
        return this
    }

    companion object {
        const val USER_PREFIX = "user:"

        /** How long a newly installed version must run before its rollback copy is dropped. */
        private const val CONFIRM_AFTER_MS = 10_000L

        private val POLICY_HEADERS =
            mapOf(
                "Content-Security-Policy" to
                    "default-src 'self' data: blob:; " +
                        "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "connect-src 'self'; form-action 'none'; frame-src 'self'",
                "Cache-Control" to "no-cache",
            )

        private fun refused() = WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", null, null)

        private fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null)
    }
}
