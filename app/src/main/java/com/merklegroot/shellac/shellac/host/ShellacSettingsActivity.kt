/*
 * Copyright (c) 2026 Shellac Project
 * ShellacSettingsActivity.kt is part of Shellac.
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
 
package com.merklegroot.shellac.shellac.host

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merklegroot.shellac.music.MusicRepository
import com.merklegroot.shellac.music.MusicSettings
import com.merklegroot.shellac.shellac.engine.LyricsPreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay

/**
 * App-native settings. **Deliberately NOT skinnable** (buildspec 3.7).
 *
 * This Activity never composes a skin. That is the entire point: it stays reachable when the active
 * skin is broken, crashes on launch, or renders an unusable interface. Without it, a bad skin means
 * uninstalling the app to recover. So: no [org.shellac.skin.AppSkin.Render], no skin theme, nothing
 * here that a skin can influence beyond its name in the picker.
 *
 * It has NO launcher entry. It used to, so that it stayed reachable when a skin was unusable, but
 * it shared the host's icon and label and users kept opening it by mistake - see the comment on
 * this Activity in AndroidManifest.xml for why that trade came out the other way. Recovery is
 * instead handled by [SkinRegistry], which quarantines a skin that crashed while composing and
 * falls back to one that can reach this screen.
 *
 * It remains exported, so `adb shell am start` can still reach it directly. That standalone route
 * is rare but real, and it needs different navigation from the common opened-from-a-skin case -
 * which is what makes the navigation in this file non-trivial. See [isStandaloneLaunch].
 */
@AndroidEntryPoint
class ShellacSettingsActivity : ComponentActivity() {
    @Inject lateinit var registry: SkinRegistry
    @Inject lateinit var musicRepository: MusicRepository
    @Inject lateinit var musicSettings: MusicSettings

    /**
     * Bumped on every resume so the folder summary re-reads settings.
     *
     * The folder list is edited in [OnboardingActivity], a separate Activity, and comes back
     * through `onResume` rather than through any state this composition observes. `MusicSettings`
     * is a SharedPreferences wrapper with no Flow to collect, so without an explicit invalidation
     * the row would keep showing the count from before the edit.
     */
    private var folderRevision by mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        folderRevision++
    }

    /**
     * True when the user arrived here from the launcher icon rather than from a skin.
     *
     * The two entry points need opposite navigation, and getting them confused is visible breakage
     * either way:
     * - Launched from the launcher, this Activity is alone in its task. Picking a skin does nothing
     *   observable (no skin is hosted here), and back leaves the task entirely, which reads as "the
     *   app minimised itself".
     * - Launched from a skin ([org.shellac.skin.SkinSession.openAppSettings] does a plain
     *   `startActivity` from [ShellacActivity]), the host is already directly below us on the
     *   stack. Starting it again would stack a second host over a live one.
     *
     * [isTaskRoot] separates them exactly: it is true only when nothing of ours sits below this
     * Activity in the task, which is precisely the "no host to go back to" case. It is checked
     * per-use rather than cached because task membership can change across a configuration change
     * or a task reparent.
     */
    private val isStandaloneLaunch: Boolean
        get() = isTaskRoot

    /**
     * Bring the skin host forward.
     *
     * [ShellacActivity] is `launchMode="singleTask"`, so if a host task already exists this reuses
     * it instead of building a second one; CLEAR_TOP additionally drops anything stacked above it.
     * NEW_TASK is required because we are starting into a *different* task than our own standalone
     * one.
     */
    private fun goToHost() {
        startActivity(
            Intent(this, ShellacActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        // Finish so the orphaned settings-only task does not linger in recents behind the
        // host the user just asked to see.
        finish()
    }

    /**
     * Leave the screen. Every setting here writes through immediately, so there is no staging step
     * to discard; Cancel and OK therefore end the same way, exactly as back does. The split mirrors
     * [isStandaloneLaunch]: standalone we hand over to the host, otherwise we just pop back to the
     * host already underneath us.
     */
    private fun leaveSettings() {
        if (isStandaloneLaunch) {
            goToHost()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lyricsPreferences = getSharedPreferences(LyricsPreferences.PREFS_NAME, MODE_PRIVATE)

        // Only intercept back in the standalone case. When a skin opened us, the default
        // behaviour already does the right thing - pop back to the host underneath.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isStandaloneLaunch) {
                        goToHost()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            },
        )

        // The navy desktop behind this window needs light system-bar glyphs.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            val active by registry.activeSkin.collectAsStateWithLifecycle()
            var lyricsEnabled by remember {
                mutableStateOf(
                    lyricsPreferences.getBoolean(
                        LyricsPreferences.LOOKUP_ENABLED_KEY,
                        LyricsPreferences.DEFAULT_LOOKUP_ENABLED,
                    )
                )
            }
            var rescanning by remember { mutableStateOf(false) }
            val folderSummary =
                remember(folderRevision) {
                    val folders = musicSettings.safQuery.source
                    when (folders.size) {
                        0 -> "No folders selected \u2014 nothing to scan."
                        1 -> folders.first().path.resolve(this)
                        else -> "${folders.size} folders"
                    }
                }

            // Indexing runs in the service with no completion signal exposed here, so this
            // is a fixed acknowledgement rather than real progress. Without it the row gives
            // no feedback at all and reads as a dead control.
            if (rescanning) {
                LaunchedEffect(Unit) {
                    delay(RESCAN_FEEDBACK_MS)
                    rescanning = false
                }
            }

            WinampScreen(
                title = "PREFERENCES",
                onClose = { leaveSettings() },
                actions = {
                    // Every control on this screen writes through the moment it is touched,
                    // so there is nothing to commit and nothing to roll back. One button
                    // saying so is honest; a Cancel/OK pair would imply a staging layer that
                    // does not exist and cannot be added without rewriting the settings
                    // stores underneath.
                    WinampButton(
                        "Done",
                        Modifier.fillMaxWidth(),
                        primary = true,
                        onClick = { leaveSettings() },
                    )
                },
            ) {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                    WinampText(
                        "Not skinnable. Reachable even if a skin breaks.",
                        size = 12,
                        dim = true,
                    )
                    Spacer(Modifier.height(20.dp))

                    WinampSectionHeader("Display skin")
                    if (registry.isRecoveryActive) {
                        // The picker will show nothing selected, because the recovery UI is
                        // deliberately not one of the choices. Say why, rather than leaving
                        // a set of unselected radios.
                        LcdText(
                            "Your skin crashed, so Shellac is running its recovery screen. " +
                                "Pick one below to get back to normal.",
                            size = 12,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                    // Stacked, not side by side. Two boxes across a phone leaves each too
                    // narrow for a two-word skin name and the labels wrap per character.
                    registry.available().forEach { skin ->
                        SkinChoice(
                            displayName = skin.displayName,
                            selected = skin.id == active.id,
                            quarantined = registry.isQuarantined(skin.id),
                            onClick = { onSkinChosen(skin.id) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Spacer(Modifier.height(24.dp))
                    WinampSectionHeader("Library")
                    WinampRow(
                        label = "Music folders",
                        caption = folderSummary,
                        onClick = {
                            startActivity(
                                Intent(this@ShellacSettingsActivity, OnboardingActivity::class.java)
                                    .putExtra(OnboardingActivity.EXTRA_MANAGE, true)
                            )
                        },
                        trailing = { WinampText("\u203A", size = 20) },
                    )
                    WinampRow(
                        label = if (rescanning) "Rescanning\u2026" else "Rescan music",
                        caption = "Re-read the library from storage.",
                        trailing = {
                            WinampButton(
                                "Go",
                                enabled = !rescanning,
                                onClick = {
                                    rescanning = true
                                    musicRepository.requestIndex(true)
                                },
                            )
                        },
                    )

                    Spacer(Modifier.height(24.dp))
                    WinampSectionHeader("Lyrics")
                    WinampRow(
                        label = "Fetch from lrclib.net",
                        caption = "Sends title, artist & album only.",
                        trailing = {
                            OnOffToggle(
                                checked = lyricsEnabled,
                                onChange = { enabled ->
                                    lyricsEnabled = enabled
                                    lyricsPreferences
                                        .edit()
                                        .putBoolean(LyricsPreferences.LOOKUP_ENABLED_KEY, enabled)
                                        .apply()
                                },
                            )
                        },
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }

    /**
     * Persist the choice, then make it *visible*.
     *
     * Selecting a skin here changes state the user cannot see, because this screen hosts no skin.
     * Standalone, that looked like the tap did nothing at all. Launched from a skin, the host is
     * already below us and simply finishing reveals the new skin - starting it again would stack a
     * duplicate.
     *
     * Re-selecting the already-active skin follows the same path deliberately:
     * [SkinRegistry.setActiveSkin] is idempotent, and the navigation is then just "go look at the
     * skin you already have", which is a sensible no-op rather than an error.
     */
    private fun onSkinChosen(id: String) {
        registry.setActiveSkin(id)
        if (isStandaloneLaunch) {
            goToHost()
        } else {
            finish()
        }
    }

    private companion object {
        /**
         * Indexing runs in the service and reports no completion signal here, so the
         * "Rescanning..." state is a fixed acknowledgement rather than real progress.
         */
        const val RESCAN_FEEDBACK_MS = 1800L
    }
}

/**
 * One skin in the picker: a full-width bevelled row carrying a radio and the name.
 *
 * Selection is signalled twice - the LED and the raised bevel - because a single cue is easy to
 * miss and this screen may be the only working UI the user has left.
 */
@Composable
private fun SkinChoice(
    displayName: String,
    selected: Boolean,
    quarantined: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) Winamp.SelectRow else Winamp.WindowBot)
            .bevel(raised = selected)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WinampRadio(selected = selected)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            WinampText(displayName, size = 16, dim = !selected, maxLines = 1)
            if (quarantined) {
                LcdText("crashed last time \u2014 will retry", size = 11, dim = true)
            }
        }
    }
}
