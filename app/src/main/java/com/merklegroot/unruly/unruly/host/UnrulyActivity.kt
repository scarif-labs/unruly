/*
 * Copyright (c) 2026 Unruly Project
 * UnrulyActivity.kt is part of Unruly.
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

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merklegroot.unruly.BuildConfig
import com.merklegroot.unruly.IntegerTable
import com.merklegroot.unruly.UnrulyService
import com.merklegroot.unruly.music.MusicSettings
import com.merklegroot.unruly.playback.PlaybackViewModel
import com.merklegroot.unruly.playback.state.DeferredPlayback
import com.merklegroot.unruly.unruly.engine.EngineAdapter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber as L

/**
 * The Unruly host. Replaces Unruly's nav-graph Activity (buildspec 3.6).
 *
 * It hosts exactly one thing: the active skin's `Render`. It owns no UI of its own - no toolbar, no
 * navigation, no now-playing panel. All of that belongs to the skin.
 */
@AndroidEntryPoint
class UnrulyActivity : ComponentActivity() {
    private val playbackModel: PlaybackViewModel by viewModels()
    @Inject lateinit var registry: SkinRegistry
    @Inject lateinit var engineAdapter: EngineAdapter
    @Inject lateinit var musicSettings: MusicSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // First-run gate. Must come before ANY of the setup below, and specifically before
        // onResume starts UnrulyService: the service's first act is to index, and with no
        // folders granted it indexes nothing, leaving the skin showing an empty library that
        // looks broken rather than unconfigured. Finishing inside onCreate skips onResume
        // entirely, so the service is not started until the user actually has music.
        // Debug builds: `am start -n <pkg>/.unruly.host.UnrulyActivity --ez debugOnboarding true`
        // shows onboarding without clearing the library, for design checks on a set-up phone.
        val debugOnboarding = BuildConfig.DEBUG && intent.getBooleanExtra("debugOnboarding", false)
        if (debugOnboarding || OnboardingActivity.isNeeded(this, musicSettings)) {
            L.d("No music folders configured; starting onboarding")
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        val session =
            engineAdapter.bind(
                playbackModel = playbackModel,
                openAppSettings = {
                    startActivity(Intent(this, UnrulySettingsActivity::class.java))
                },
            )

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
                onRenderSucceeded = { id -> registry.markRenderSucceeded(id) },
            )
        }

        L.d("Unruly host created")
    }

    override fun onResume() {
        super.onResume()

        // PRESERVED FROM MainActivity.onResume (MainActivity.kt:68-81). Both halves matter:
        //
        // 1. Starting UnrulyService is what kicks off music INDEXING and makes playback
        //    available at all. Omitting it looks like a broken library rather than a
        //    missing service - the app launches fine and simply never finds any music.
        // 2. RestoreState is what brings back the saved queue and position across restarts.
        //
        // A replacement Activity that forgets either one fails quietly, so this is the
        // single most important thing to carry over from the old UI.
        startService(
            Intent(this, UnrulyService::class.java)
                .setAction(UnrulyService.ACTION_START)
                .putExtra(UnrulyService.INTENT_KEY_START_ID, IntegerTable.START_ID_ACTIVITY)
        )

        playbackModel.playDeferred(DeferredPlayback.RestoreState(false))
    }
}
