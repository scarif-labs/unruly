package org.oxycblt.auxio.shellac.host

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.oxycblt.auxio.AuxioService
import org.oxycblt.auxio.IntegerTable
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.playback.state.DeferredPlayback
import org.oxycblt.auxio.shellac.engine.EngineAdapter
import timber.log.Timber as L

/**
 * The Shellac host. Replaces Auxio's nav-graph Activity (buildspec 3.6).
 *
 * It hosts exactly one thing: the active skin's `Render`. It owns no UI of its own - no
 * toolbar, no navigation, no now-playing panel. All of that belongs to the skin.
 */
@AndroidEntryPoint
class ShellacActivity : ComponentActivity() {
    private val playbackModel: PlaybackViewModel by viewModels()
    @Inject lateinit var registry: SkinRegistry
    @Inject lateinit var engineAdapter: EngineAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val session =
            engineAdapter.bind(
                playbackModel = playbackModel,
                openAppSettings = {
                    startActivity(Intent(this, ShellacSettingsActivity::class.java))
                })

        // Back is delegated to the skin. Only when the skin reports it is at its root does
        // this fall through to the default behaviour and exit the app.
        val backCallback =
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val skin = registry.activeSkin.value
                    val consumed = runCatching { skin.onBackPressed() }.getOrDefault(false)
                    if (!consumed) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        onBackPressedDispatcher.addCallback(this, backCallback)

        setContent {
            val skin by registry.activeSkin.collectAsStateWithLifecycle()
            WindowCompat.setDecorFitsSystemWindows(window, !skin.supportsEdgeToEdge)
            // Marker written before composing; cleared once a frame lands. See SkinRegistry.
            registry.beginRender(skin.id)
            SkinHost(
                skin = skin,
                session = session,
                onRenderSucceeded = { id -> registry.markRenderSucceeded(id) })
        }

        L.d("Shellac host created")
    }

    override fun onResume() {
        super.onResume()

        // PRESERVED FROM MainActivity.onResume (MainActivity.kt:68-81). Both halves matter:
        //
        // 1. Starting AuxioService is what kicks off music INDEXING and makes playback
        //    available at all. Omitting it looks like a broken library rather than a
        //    missing service - the app launches fine and simply never finds any music.
        // 2. RestoreState is what brings back the saved queue and position across restarts.
        //
        // A replacement Activity that forgets either one fails quietly, so this is the
        // single most important thing to carry over from the old UI.
        startService(
            Intent(this, AuxioService::class.java)
                .setAction(AuxioService.ACTION_START)
                .putExtra(AuxioService.INTENT_KEY_START_ID, IntegerTable.START_ID_ACTIVITY))

        playbackModel.playDeferred(DeferredPlayback.RestoreState(false))
    }
}
