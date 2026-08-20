/*
 * Copyright (c) 2026 Shellac Project
 * OnboardingActivity.kt is part of Shellac.
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

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import com.merklegroot.shellac.music.MusicSettings
import com.merklegroot.shellac.music.locations.LocationMode
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.delay
import org.oxycblt.musikr.fs.Location
import org.shellac.skin.support.ShellacMark
import timber.log.Timber as L

/**
 * First-run setup: pick the folders Shellac scans for music.
 *
 * ## Why this exists as its own screen
 *
 * Shellac loads music through SAF, so it scans **nothing** until the user has granted a folder
 * tree. Before this screen existed, a fresh install opened straight into the Touch Wheel with an
 * empty library and no explanation - indistinguishable from a scan that had failed. The folder
 * grant is not a setting, it is a precondition, and the app has no content at all without it.
 *
 * ## Why it is not skinnable
 *
 * Same reasoning as [ShellacSettingsActivity]: it runs before any skin has been shown, and the user
 * has no way to switch skins yet, so a skin that renders this badly would be unrecoverable. It also
 * has to host a `startActivityForResult` flow (the SAF picker), which the skin contract
 * deliberately does not expose.
 *
 * ## Re-entry
 *
 * Launched a second way, from settings' "Music folders" row, with [EXTRA_MANAGE]. That skips the
 * welcome copy and returns to the caller on finish, rather than starting the host.
 */
@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {
    @Inject lateinit var musicSettings: MusicSettings

    /** True when opened from settings to edit existing folders, rather than on first run. */
    private val isManaging: Boolean
        get() = intent?.getBooleanExtra(EXTRA_MANAGE, false) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dark chrome needs light system icons to keep the controls legible.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            OnboardingContent(
                managing = isManaging,
                initialFolders = musicSettings.safQuery.source,
                onFinished = { folders -> commit(folders) },
            )
        }
    }

    /**
     * Write the chosen folders and leave.
     *
     * Setting [MusicSettings.safQuery] is what actually triggers a scan: `MusicSettingsImpl`
     * dispatches `onMusicLocationsChanged` off the shared-preference write, and the indexer picks
     * it up. There is no separate "start scanning" call to make here.
     *
     * [MusicSettings.forceLocationUpdate] covers the case where the folder list came out
     * byte-identical to what was already stored - the preference listener would not fire, and a
     * user who just walked through a setup flow expecting a rescan would get nothing.
     */
    private fun commit(folderUris: List<Uri>) {
        // Re-open each Uri to get a Location.Opened. The persistable permission was already
        // taken when the folder was picked, so `open` is a permission *check* here, not a
        // second grant - and a folder whose permission has since been revoked is dropped
        // rather than stored as a source that can never be read.
        val folders = folderUris.mapNotNull { Location.Unopened.from(this, it).open(this) }
        val previous = musicSettings.safQuery
        musicSettings.locationMode = LocationMode.SAF
        musicSettings.safQuery = previous.copy(source = folders)
        if (previous.source == folders) {
            musicSettings.forceLocationUpdate()
        }
        markComplete(this)
        L.d("Onboarding complete with ${folders.size} folder(s)")

        if (!isManaging) {
            startActivity(Intent(this, ShellacActivity::class.java))
        }
        finish()
    }

    companion object {
        /** Set to open straight on the folder list, for editing an existing setup. */
        const val EXTRA_MANAGE = "com.merklegroot.shellac.extra.MANAGE_FOLDERS"

        private const val PREFS_NAME = "shellac_onboarding"
        private const val KEY_COMPLETE = "onboarding_complete"

        /**
         * Whether first-run setup still needs to happen.
         *
         * Two conditions, and both are load-bearing:
         * - **No folders configured** is the real signal. An install that has never been granted a
         *   tree can show nothing, so it must be onboarded.
         * - **The completion flag** is what stops the flow reappearing for someone who walked
         *   through it and deliberately chose no folders, or who later removed their last folder
         *   from settings. Without it, an empty library would trap the user in a setup screen they
         *   had already dismissed.
         *
         * MediaStore mode is never onboarded: choosing it is itself a deliberate act in settings,
         * and it has no folder list to fill in.
         */
        fun isNeeded(context: Context, musicSettings: MusicSettings): Boolean {
            if (musicSettings.locationMode != LocationMode.SAF) return false
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_COMPLETE, false)) return false
            return musicSettings.safQuery.source.isEmpty()
        }

        private fun markComplete(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putBoolean(KEY_COMPLETE, true)
            }
        }
    }
}

/** The steps, in order. [Welcome] is skipped when re-entered from settings. */
private enum class Step {
    Welcome,
    Folders,
    Notifications,
}

/** Enums are not Parcelable; the name is, and is stable enough for a three-value flow. */
private val StepSaver: Saver<Step, String> =
    Saver(save = { it.name }, restore = { Step.valueOf(it) })

@Composable
private fun OnboardingContent(
    managing: Boolean,
    initialFolders: List<Location.Opened>,
    onFinished: (List<Uri>) -> Unit,
) {
    val context = LocalContext.current

    // ALL of this is `rememberSaveable`, and it has to be. Launching the SAF picker hands
    // the foreground to another app, and this Activity is frequently destroyed and recreated
    // before its result arrives - observed on the first device run, where picking a folder
    // dropped the user back on the welcome screen with an empty list. `remember` alone
    // survives recomposition, not process recreation, so every piece of flow state that the
    // picker round-trip crosses must be saved.
    //
    // Folders are held as [Uri] rather than as `Location.Opened` because Uri is Parcelable
    // and Location is not. Nothing is lost: the persistable permission is already taken by
    // the time a Uri lands in this list, so re-deriving a Location from it is free.
    val folders: SnapshotStateList<Uri> =
        rememberSaveable(
            saver =
                listSaver<SnapshotStateList<Uri>, Uri>(
                    save = { it.toList() },
                    restore = { it.toMutableStateList() },
                )
        ) {
            initialFolders.map { it.uri }.toMutableStateList()
        }
    var step by
        rememberSaveable(stateSaver = StepSaver) {
            mutableStateOf(if (managing) Step.Folders else Step.Welcome)
        }

    val treePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri == null) {
                // User backed out of the system picker. Not an error, and not worth a toast.
                return@rememberLauncherForActivityResult
            }
            // `open` is what takes the *persistable* permission. Without it the grant dies
            // with this Activity and the next scan finds nothing, which looks exactly like a
            // folder that contains no music.
            val opened = Location.Unopened.from(context, uri).open(context)
            if (opened == null) {
                L.w("Could not take persistable permission for $uri")
                Toast.makeText(
                        context,
                        "Shellac could not keep access to that folder",
                        Toast.LENGTH_LONG,
                    )
                    .show()
                return@rememberLauncherForActivityResult
            }
            if (folders.none { it == opened.uri }) {
                folders.add(opened.uri)
            }
        }

    fun addFolder() {
        try {
            treePicker.launch(null)
        } catch (e: ActivityNotFoundException) {
            L.e("No document picker available: $e")
            Toast.makeText(context, "No file picker is available on this device", Toast.LENGTH_LONG)
                .show()
        }
    }

    // The notification step is pure friction on devices that cannot show the dialog or have
    // already answered it, so it is skipped rather than shown as a no-op.
    val needsNotificationStep =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED

    fun finishFolders() {
        if (needsNotificationStep && !managing) step = Step.Notifications
        else onFinished(folders.toList())
    }

    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            L.d("Notification permission granted: $granted")
            // Either way the setup is over - playback works without the notification, it is
            // just harder to control. Refusing must not dead-end the flow.
            onFinished(folders.toList())
        }

    when (step) {
        Step.Welcome -> WelcomeStep(onContinue = { step = Step.Folders })
        Step.Folders ->
            FoldersStep(
                managing = managing,
                folders = folders,
                onAdd = { addFolder() },
                onRemove = { folders.remove(it) },
                onContinue = { finishFolders() },
            )
        Step.Notifications ->
            NotificationsStep(
                onAllow = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onFinished(folders.toList())
                    }
                },
                onSkip = { onFinished(folders.toList()) },
            )
    }
}

/**
 * The player face, used as the welcome screen.
 *
 * The LCD is doing the persuading here, so it gets the top of the window and the brand sits under
 * it in the slack. The one thing the user can do - start setup - is the pinned primary action, not
 * a button competing with five bevelled panels.
 */
@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    WinampScreen(
        title = "SHELLAC",
        menuItems = listOf("File", "Play", "Options", "View"),
        actions = {
            WinampButton(
                "Power on",
                Modifier.fillMaxWidth(),
                primary = true,
                glyph = "▶",
                onClick = onContinue,
            )
        },
    ) {
        LcdPanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Bottom) {
                LcdText(clockReadout(), size = 40, letterSpacing = 1f, maxLines = 1)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.padding(bottom = 6.dp)) {
                    Row {
                        LcdText("KBPS ", size = 10, dim = true)
                        LcdText("33", size = 10)
                        LcdText("  KHZ ", size = 10, dim = true)
                        LcdText("22", size = 10)
                    }
                    Box(
                        Modifier.padding(top = 4.dp)
                            .background(Winamp.Lcd)
                            .bevel(raised = false)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        LcdText("MONO", size = 9, letterSpacing = 1f)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Visualiser(Modifier.fillMaxWidth().height(46.dp))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LcdText("PLAY", size = 12, letterSpacing = 1f)
                Spacer(Modifier.width(14.dp))
                LcdText("★ NOW READING YOUR LIBRARY", size = 12, maxLines = 1)
            }
        }

        Spacer(Modifier.height(14.dp))
        SeekTrough()

        // The brand block floats in the space between the LCD and the action bar. Weights on
        // both sides centre it in whatever height is left over, at any screen size.
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ShellacMark(height = 24.dp)
            Spacer(Modifier.height(14.dp))
            LcdText("shellac", size = 34, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            WinampText(
                "YOUR FILES · YOUR STEREO",
                size = 12,
                dim = true,
                letterSpacing = 2.5f,
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

/** A recessed trough with a raised thumb. Decorative - there is nothing to seek yet. */
@Composable
private fun SeekTrough() {
    Box(Modifier.fillMaxWidth().height(20.dp).background(Winamp.Lcd).bevel(raised = false)) {
        Box(
            Modifier.fillMaxWidth(0.34f)
                .height(20.dp)
                .background(Winamp.WindowTop)
                .bevel(raised = true)
        )
    }
}

/**
 * The device clock, as an LCD readout, refreshed on the minute.
 *
 * Held as state rather than read once during composition: this screen can sit on a table for a
 * while, and a player face frozen at the minute the app happened to launch is worse than no clock
 * at all. The coroutine sleeps until the next minute boundary instead of polling every second, so
 * an idle welcome screen wakes ~once a minute.
 */
@Composable
private fun clockReadout(): String {
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val time by
        produceState(initialValue = LocalTime.now().format(formatter)) {
            while (true) {
                val now = LocalTime.now()
                value = now.format(formatter)
                delay(((60 - now.second) * 1000L - now.nano / 1_000_000L).coerceAtLeast(250L))
            }
        }
    return time
}

/**
 * Animated level meter.
 *
 * Random, because a level meter reading a real signal is: the earlier version summed two sines,
 * which is smooth and periodic and reads as an ornament rather than as audio.
 *
 * The model is the one hardware meters use - **fast attack, slow decay**. Each bar drifts steadily
 * downward every tick and occasionally gets kicked to a new random peak, so the display has the
 * uneven, falling-back quality of a real meter instead of independent bars flickering at random
 * heights.
 *
 * One coroutine mutates a shared list in place. A per-bar `animateFloat` would run twenty
 * concurrent animations to draw a decoration.
 */
@Composable
private fun Visualiser(modifier: Modifier = Modifier) {
    val levels = remember { mutableStateListOf(*Array(BAR_COUNT) { Random.nextFloat() }) }
    LaunchedEffect(Unit) {
        while (true) {
            for (i in 0 until BAR_COUNT) {
                val decayed = levels[i] - DECAY_PER_TICK
                // Only some bars get hit on any given tick, which is what keeps neighbours
                // from moving together and looking like a wave.
                val kicked = if (Random.nextFloat() < KICK_CHANCE) Random.nextFloat() else 0f
                levels[i] = maxOf(FLOOR, maxOf(decayed, kicked))
            }
            delay(TICK_MS)
        }
    }

    Canvas(modifier) {
        val gap = size.width * 0.014f
        val barWidth = (size.width - gap * (BAR_COUNT - 1)) / BAR_COUNT
        repeat(BAR_COUNT) { i ->
            val level = levels[i]
            val h = size.height * (0.10f + level * 0.90f)
            drawRect(
                // Bright only near the top of the range, so peaks read as peaks.
                color = if (level > 0.7f) Winamp.Phosphor else Winamp.PhosphorDim,
                topLeft = Offset(i * (barWidth + gap), size.height - h),
                size = Size(barWidth, h),
            )
        }
    }
}

private const val BAR_COUNT = 20
private const val TICK_MS = 70L
private const val DECAY_PER_TICK = 0.055f
private const val KICK_CHANCE = 0.22f
private const val FLOOR = 0.04f

/**
 * The folder picker, as a playlist window.
 *
 * The list takes `weight(1f)` so the window is full whether there are zero folders or ten, and
 * "scan" is the pinned primary action. Its disabled state is the existing gate: with no folders
 * granted the app can show nothing, so the flow must not let the user past.
 */
@Composable
private fun FoldersStep(
    managing: Boolean,
    folders: List<Uri>,
    onAdd: () -> Unit,
    onRemove: (Uri) -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    WinampScreen(
        title = if (managing) "MUSIC FOLDERS" else "PLAYLIST EDITOR",
        menuItems = listOf("File", "Sources", "Sort", "Help"),
        actions = {
            // The pinned action is whatever the user must do NEXT, which is not the same
            // button throughout. With an empty list, "scan" is impossible, and offering it
            // greyed out makes the one screen with a single mandatory step look like a dead
            // end - the real action, choosing a folder, was a small secondary button in the
            // toolbar. So the primary slot carries the picker until there is something to
            // scan, then becomes scan.
            if (folders.isEmpty()) {
                WinampButton(
                    "Choose a folder",
                    Modifier.fillMaxWidth(),
                    primary = true,
                    glyph = "+",
                    onClick = onAdd,
                )
            } else {
                WinampButton(
                    if (managing) "Save & rescan" else "Scan & play",
                    Modifier.fillMaxWidth(),
                    primary = true,
                    glyph = "▶",
                    onClick = onContinue,
                )
            }
        },
    ) {
        WinampText(
            "Everything under a folder is scanned. One top-level music folder is usually enough.",
            size = 13,
            dim = true,
        )
        Spacer(Modifier.height(12.dp))

        Column(
            Modifier.fillMaxWidth()
                .weight(1f)
                .background(Winamp.Lcd)
                .bevel(raised = false)
                .verticalScroll(rememberScrollState())
        ) {
            if (folders.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LcdText("no folders yet", size = 14, dim = true)
                }
            } else {
                folders.forEachIndexed { index, uri ->
                    // Re-derived per row rather than stored: `Path` is what turns an opaque
                    // tree Uri into "Internal shared storage/Music", and it is cheap.
                    val location = Location.Unopened.from(context, uri)
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (index == 0) Winamp.SelectRow else Winamp.Lcd)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LcdText("${index + 1}.", size = 13, dim = index != 0)
                        Spacer(Modifier.width(10.dp))
                        LcdText(
                            location.path.resolve(context),
                            size = 13,
                            dim = index != 0,
                            maxLines = 2,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        WinampButton("×", onClick = { onRemove(uri) })
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Only once the primary action has moved on to "scan". While the list is empty
            // the pinned button already is "choose a folder", and showing the same action
            // twice makes neither one look like the one to press.
            if (folders.isNotEmpty()) {
                WinampButton("Add", glyph = "+", onClick = onAdd)
                Spacer(Modifier.width(8.dp))
            }
            Box(
                Modifier.weight(1f).background(Winamp.Lcd).bevel(raised = false).padding(12.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                LcdText(
                    "${folders.size} " + if (folders.size == 1) "folder" else "folders",
                    size = 12,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun NotificationsStep(onAllow: () -> Unit, onSkip: () -> Unit) {
    WinampScreen(
        title = "NOTIFICATIONS",
        menuItems = listOf("File", "Options"),
        actions = {
            WinampButton("Allow", Modifier.fillMaxWidth(), primary = true, onClick = onAllow)
            Spacer(Modifier.height(10.dp))
            WinampButton("Not now", Modifier.fillMaxWidth(), onClick = onSkip)
        },
    ) {
        LcdPanel(Modifier.fillMaxWidth()) {
            LcdText("LOCK SCREEN CONTROLS", size = 12, letterSpacing = 2f, maxLines = 1)
            Spacer(Modifier.height(12.dp))
            LcdText(
                "Shellac posts a notification while music plays. That is what puts the " +
                    "transport on your lock screen.",
                size = 14,
            )
            Spacer(Modifier.height(12.dp))
            LcdText("Playback works without it — you just lose the buttons.", size = 13, dim = true)
        }
        Spacer(Modifier.weight(1f))
    }
}
