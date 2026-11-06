/*
 * Copyright (c) 2026 Unruly Project
 * OnboardingActivity.kt is part of Unruly.
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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import com.merklegroot.unruly.R
import com.merklegroot.unruly.music.MusicSettings
import com.merklegroot.unruly.music.locations.LocationMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.oxycblt.musikr.fs.Location
import org.oxycblt.musikr.fs.Path
import org.oxycblt.musikr.fs.Volume
import timber.log.Timber as L

/**
 * First-run setup: pick the folders Unruly scans for music.
 *
 * ## Why this exists as its own screen
 *
 * Unruly loads music through SAF, so it scans **nothing** until the user has granted a folder tree.
 * Before this screen existed, a fresh install opened straight into the Touch Wheel with an empty
 * library and no explanation - indistinguishable from a scan that had failed. The folder grant is
 * not a setting, it is a precondition, and the app has no content at all without it.
 *
 * ## Why it is not skinnable
 *
 * Same reasoning as [UnrulySettingsActivity]: it runs before any skin has been shown, and the user
 * has no way to switch skins yet, so a skin that renders this badly would be unrecoverable. It also
 * has to host a `startActivityForResult` flow (the SAF picker), which the skin contract
 * deliberately does not expose.
 *
 * ## Re-entry
 *
 * Can be launched a second way, with [EXTRA_MANAGE]. That skips the welcome copy and returns to the
 * caller on finish, rather than starting the host. Settings no longer uses it: its library page
 * edits the folders in place, through [rememberFolderPicker] and [saveFolders].
 */
@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {
    @Inject lateinit var musicSettings: MusicSettings

    /** True when opened from settings to edit existing folders, rather than on first run. */
    private val isManaging: Boolean
        get() = intent?.getBooleanExtra(EXTRA_MANAGE, false) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // The red stock needs light system icons to keep them legible.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            OnboardingContent(
                managing = isManaging,
                initialFolders = musicSettings.safQuery.source,
                onFinished = { folders, wholePhone -> commit(folders, wholePhone) },
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
     *
     * [wholePhone] skips folders and loads everything Android has indexed (MediaStore). It is the
     * only way to reach music in `Download/`, which the folder picker refuses to grant.
     */
    private fun commit(folderUris: List<Uri>, wholePhone: Boolean) {
        if (wholePhone) {
            musicSettings.locationMode = LocationMode.MEDIA_STORE
        } else {
            musicSettings.locationMode = LocationMode.SAF
            val changed = musicSettings.saveFolders(this, folderUris)
            if (!changed) {
                musicSettings.forceLocationUpdate()
            }
        }
        markComplete(this)
        L.d(
            "Onboarding complete: ${if (wholePhone) "whole phone" else "${folderUris.size} folder(s)"}"
        )

        if (!isManaging) {
            startActivity(Intent(this, UnrulyActivity::class.java))
        }
        finish()
    }

    companion object {
        /** Set to open straight on the folder list, for editing an existing setup. */
        const val EXTRA_MANAGE = "com.merklegroot.unruly.extra.MANAGE_FOLDERS"

        private const val PREFS_NAME = "unruly_onboarding"
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

/** What Android calls access to the audio it has indexed. Renamed in 13. */
internal val AUDIO_PERMISSION =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

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
    onFinished: (folders: List<Uri>, wholePhone: Boolean) -> Unit,
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

    // Chosen instead of folders: load everything Android has indexed.
    var wholePhone by rememberSaveable { mutableStateOf(false) }
    var audioDenied by rememberSaveable { mutableStateOf(false) }

    SideEffect { applyRedlineSystemBars(context) }

    val addFolder = rememberFolderPicker { uri -> if (uri !in folders) folders.add(uri) }

    // The notification step is pure friction on devices that cannot show the dialog or have
    // already answered it, so it is skipped rather than shown as a no-op.
    val needsNotificationStep =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
    // Welcome, folders and, when it applies, the notification ask.
    val stepCount = if (needsNotificationStep) 3 else 2

    fun finishFolders() {
        if (needsNotificationStep && !managing) step = Step.Notifications
        else onFinished(folders.toList(), wholePhone)
    }

    val audioPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            L.d("Audio permission granted: $granted")
            audioDenied = !granted
            if (granted) {
                wholePhone = true
                finishFolders()
            }
        }

    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            L.d("Notification permission granted: $granted")
            // Either way the setup is over - playback works without the notification, it is
            // just harder to control. Refusing must not dead-end the flow.
            onFinished(folders.toList(), wholePhone)
        }

    if (step == Step.Welcome) {
        VortexWelcome(stepCount = stepCount, onContinue = { step = Step.Folders })
        return
    }

    // The welcome's dive ends on black ink; the later steps simply cross-fade on the shared red.
    Crossfade(step, animationSpec = tween(260), label = "onboarding step") { shown ->
        when (shown) {
            Step.Welcome -> Unit
            Step.Folders ->
                FoldersStep(
                    managing = managing,
                    stepCount = stepCount,
                    folders = folders,
                    audioDenied = audioDenied,
                    onAdd = { addFolder() },
                    onRemove = { folders.remove(it) },
                    onContinue = { finishFolders() },
                    onWholePhone = { audioPermission.launch(AUDIO_PERMISSION) },
                )
            Step.Notifications ->
                NotificationsStep(
                    onAllow = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onFinished(folders.toList(), wholePhone)
                        }
                    },
                    onSkip = { onFinished(folders.toList(), wholePhone) },
                )
        }
    }
}

private val FoldersHeadline = listOf("music", "folders")
private val NotificationsHeadline = listOf("lock", "screen")

/** Every page sizes its headline against all of these, so the headlines match in size. */
private val AllHeadlines = FoldersHeadline + NotificationsHeadline

/**
 * The folder picker. With nothing chosen, an empty cassette case waits; once folders are added they
 * list as torn strips laid over the Walkman, which keeps its size as the list grows.
 *
 * The art takes the slack, so the screen is full whether there are zero folders or ten; the list
 * scrolls past a few rows. The pinned action is whatever the user must do next: with an empty list
 * "scan" is impossible, so the primary slot carries the picker until there is something to scan.
 */
@Composable
private fun FoldersStep(
    managing: Boolean,
    stepCount: Int,
    folders: List<Uri>,
    audioDenied: Boolean,
    onAdd: () -> Unit,
    onRemove: (Uri) -> Unit,
    onContinue: () -> Unit,
    onWholePhone: () -> Unit,
) {
    val empty = folders.isEmpty()
    RedlineScreen(
        step = if (managing) null else 1,
        stepCount = stepCount,
        headline = FoldersHeadline,
        fitTo = AllHeadlines,
        footer =
            when {
                !empty -> null
                audioDenied -> "NO ACCESS. PICK A FOLDER."
                else -> "FEED ME FOLDERS."
            },
        art = {
            if (empty) {
                PaperCutout(
                    R.drawable.onboarding_cassette_case,
                    Modifier.fillMaxSize(),
                    alignment = Alignment.TopStart,
                )
                VellumScrap(Modifier.align(Alignment.BottomEnd), seed = 7, tilt = -6f) {
                    Column(
                        Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "NOTHING TO PLAY",
                            color = VortexRed,
                            fontFamily = MarkerFont,
                            fontSize = 24.sp,
                            lineHeight = 30.sp,
                        )
                        Colophon("0 FOLDERS · WAITING", color = VortexInk)
                    }
                }
            } else {
                PaperCutout(R.drawable.onboarding_walkman, Modifier.fillMaxSize())
            }
        },
        below = {
            if (!empty) {
                FolderStrips(folders, onRemove, Modifier.weight(1f, fill = false))
                Spacer(Modifier.height(10.dp))
                Colophon(
                    "${folders.size} " +
                        (if (folders.size == 1) "FOLDER" else "FOLDERS") +
                        " · READY"
                )
            }
        },
        actions = {
            if (empty) {
                RedlineButton("Choose a folder", Glyph.Plus, onClick = onAdd)
                // Folder picking can't reach Download/, so this is the way in for music there.
                // Hidden in settings' re-entry, which has its own switch.
                if (!managing) {
                    RedlineButton(
                        "Use all music on this phone",
                        glyph = null,
                        primary = false,
                        onClick = onWholePhone,
                    )
                }
            } else {
                RedlineButton("Add another", Glyph.Plus, primary = false, onClick = onAdd)
                RedlineButton(
                    if (managing) "Save & rescan" else "Scan & play",
                    Glyph.Play,
                    onClick = onContinue,
                )
            }
        },
    )
}

/**
 * The music folders as torn strips with the name in marker and a × to remove. Shared by onboarding
 * and settings. It scrolls, so give it a bounded height (`weight(1f, fill = false)` in a column
 * laid over the art) and it covers the art rather than shrinking it.
 */
@Composable
internal fun FolderStrips(folders: List<Uri>, onRemove: (Uri) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        folders.forEachIndexed { index, uri ->
            // Re-derived per row rather than stored: `Path` is what turns an opaque tree Uri
            // into a readable folder, and it is cheap.
            val label = folderLabel(Location.Unopened.from(context, uri).path, context)
            VellumScrap(
                Modifier.fillMaxWidth(),
                seed = uri.hashCode(),
                tilt = if (index % 2 == 0) -0.5f else 0.4f,
            ) {
                Row(
                    Modifier.padding(start = 18.dp).heightIn(min = 58.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        color = VortexInk,
                        fontFamily = MarkerFont,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        Modifier.size(58.dp)
                            .clickable(role = Role.Button) { onRemove(uri) }
                            .semantics { contentDescription = "Remove $label" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.size(16.dp)) {
                            val stroke = 2.5.dp.toPx()
                            drawLine(
                                VortexInk,
                                Offset.Zero,
                                Offset(size.width, size.height),
                                stroke,
                            )
                            drawLine(
                                VortexInk,
                                Offset(size.width, 0f),
                                Offset(0f, size.height),
                                stroke,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Launches the system folder picker and hands back each folder whose access could be kept. Shared
 * by onboarding and the library page in settings.
 */
@Composable
internal fun rememberFolderPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
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
                        "Unruly could not keep access to that folder",
                        Toast.LENGTH_LONG,
                    )
                    .show()
                return@rememberLauncherForActivityResult
            }
            onPicked(opened.uri)
        }
    return {
        try {
            treePicker.launch(null)
        } catch (e: ActivityNotFoundException) {
            L.e("No document picker available: $e")
            Toast.makeText(context, "No file picker is available on this device", Toast.LENGTH_LONG)
                .show()
        }
    }
}

/**
 * Store [folderUris] as the music folders. The write is what starts a scan: `MusicSettingsImpl`
 * dispatches `onMusicLocationsChanged` off the shared-preference change.
 *
 * Each Uri is re-opened to get a `Location.Opened`. The persistable permission was taken when the
 * folder was picked, so `open` is a permission *check* here, and a folder whose permission has
 * since been revoked is dropped rather than stored as a source that can never be read.
 *
 * @return Whether the stored list changed. When it did not, the preference listener will not fire.
 */
internal fun MusicSettings.saveFolders(context: Context, folderUris: List<Uri>): Boolean {
    val folders = folderUris.mapNotNull { Location.Unopened.from(context, it).open(context) }
    val previous = safQuery
    locationMode = LocationMode.SAF
    safQuery = previous.copy(source = folders)
    return previous.source != folders
}

/**
 * "Music" for a folder on internal storage, where the volume goes without saying. Folders on an SD
 * card or other storage keep the volume, "SD card/Albums", so two same-named folders stay apart.
 */
internal fun folderLabel(path: Path, context: Context): String {
    val name = path.name ?: return path.volume.resolveName(context)
    return if (path.volume is Volume.Internal) name else "${path.volume.resolveName(context)}/$name"
}

/** The notification ask, with the transport it is asking for pasted over a boombox. */
@Composable
private fun NotificationsStep(onAllow: () -> Unit, onSkip: () -> Unit) {
    RedlineScreen(
        step = 2,
        stepCount = 3,
        headline = NotificationsHeadline,
        fitTo = AllHeadlines,
        footer = "BUTTONS WHERE YOUR THUMB IS.",
        art = {
            PaperCutout(R.drawable.onboarding_boombox, Modifier.fillMaxSize())
            NowPlayingCard(
                Modifier.align(BiasAlignment(0f, 0.4f)).fillMaxWidth(0.86f).semantics {
                    contentDescription = "Preview of the lock screen player"
                }
            )
        },
        actions = {
            RedlineButton("Allow", Glyph.Arrow, onClick = onAllow)
            RedlineButton("Not now", glyph = null, primary = false, onClick = onSkip)
        },
    )
}
