/*
 * Copyright (c) 2026 Unruly Project
 * UnrulySettingsActivity.kt is part of Unruly.
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
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merklegroot.unruly.BuildConfig
import com.merklegroot.unruly.R
import com.merklegroot.unruly.music.MusicRepository
import com.merklegroot.unruly.music.MusicSettings
import com.merklegroot.unruly.music.locations.LocationMode
import com.merklegroot.unruly.playback.state.PlaybackStateManager
import com.merklegroot.unruly.unruly.engine.LyricsPreferences
import com.merklegroot.unruly.unruly.web.SkinPackageException
import com.merklegroot.unruly.unruly.web.UserSkinManifest
import com.merklegroot.unruly.unruly.web.WebSkin
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oxycblt.musikr.fs.Location
import timber.log.Timber as L

/**
 * App-native settings. **Deliberately NOT skinnable** (buildspec 3.7).
 *
 * This Activity never composes a skin. That is the entire point: it stays reachable when the active
 * skin is broken, crashes on launch, or renders an unusable interface. Without it, a bad skin means
 * uninstalling the app to recover. So: no [org.unruly.skin.AppSkin.Render], no skin theme, nothing
 * here that a skin can influence beyond its name in the picker. It is set like the index on a
 * record sleeve instead (red grotesk on off-white), as a menu that pushes one page per option.
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
class UnrulySettingsActivity : ComponentActivity() {
    @Inject lateinit var registry: SkinRegistry
    @Inject lateinit var musicRepository: MusicRepository
    @Inject lateinit var musicSettings: MusicSettings
    @Inject lateinit var playbackManager: PlaybackStateManager

    // AudioEffect expects startActivityForResult with the panel intent; there is no contract
    // analogue, so the generic one is used and its result ignored.
    private val equalizerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    /**
     * The phone's equalizer, aimed at our audio session. Unruly has no equalizer of its own; like
     * Auxio, it hands off to whatever the system offers (Dolby, a vendor EQ, Wavelet, ...).
     */
    private fun equalizerIntent() =
        Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, playbackManager.currentAudioSessionId)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)

    /** The equalizer app's name, or null when the phone has none. */
    private fun equalizerName(): String? =
        packageManager.resolveActivity(equalizerIntent(), 0)?.loadLabel(packageManager)?.toString()

    /**
     * True when the user arrived here from the launcher icon rather than from a skin.
     *
     * The two entry points need opposite navigation, and getting them confused is visible breakage
     * either way:
     * - Launched from the launcher, this Activity is alone in its task. Picking a skin does nothing
     *   observable (no skin is hosted here), and back leaves the task entirely, which reads as "the
     *   app minimised itself".
     * - Launched from a skin ([org.unruly.skin.SkinSession.openAppSettings] does a plain
     *   `startActivity` from [UnrulyActivity]), the host is already directly below us on the stack.
     *   Starting it again would stack a second host over a live one.
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
     * [UnrulyActivity] is `launchMode="singleTask"`, so if a host task already exists this reuses
     * it instead of building a second one; CLEAR_TOP additionally drops anything stacked above it.
     * NEW_TASK is required because we are starting into a *different* task than our own standalone
     * one.
     */
    private fun goToHost() {
        startActivity(
            Intent(this, UnrulyActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        // Finish so the orphaned settings-only task does not linger in recents behind the
        // host the user just asked to see.
        finish()
    }

    /**
     * Leave the screen. Every setting here writes through immediately, so there is no staging step
     * to discard, and closing ends exactly as back does. The split mirrors [isStandaloneLaunch]:
     * standalone we hand over to the host, otherwise we just pop back to the host already
     * underneath us.
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (installDevSkin()) return
        installRequest = viewedSkin(intent)
        launchedToInstall = installRequest != null
        val lyricsPreferences = getSharedPreferences(LyricsPreferences.PREFS_NAME, MODE_PRIVATE)

        // Only intercept back in the standalone case. When a skin opened us, the default
        // behaviour already does the right thing - pop back to the host underneath. Back on a
        // settings page is handled first, inside the composition, and returns to the menu.
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

        setContent {
            val request = installRequest
            if (request != null) {
                InstallSkinScreen(
                    uri = request,
                    peek = { registry.peekUserSkin(it) },
                    installedVersion = { id ->
                        registry.userSkins().firstOrNull { it.manifest.id == id }?.manifest?.version
                    },
                    install = { registry.installUserSkin(it) },
                    onInstalled = { manifest ->
                        registry.setActiveSkin(WebSkin.USER_PREFIX + manifest.id)
                        // Always to the host, even from another app's task: the point of
                        // installing a skin is to see it.
                        goToHost()
                    },
                    onCancel = {
                        installRequest = null
                        if (launchedToInstall) finish()
                    },
                )
                return@setContent
            }
            SettingsContent(
                registry = registry,
                musicSettings = musicSettings,
                lyricsPreferences = lyricsPreferences,
                onRescan = { musicRepository.requestIndex(true) },
                onSkinChosen = { onSkinChosen(it) },
                equalizerName = equalizerName(),
                onOpenEqualizer = { equalizerLauncher.launch(equalizerIntent()) },
                onClose = { leaveSettings() },
            )
        }
    }

    /**
     * A `.unruly` file opened from another app (file manager, chat), waiting for the user to
     * confirm. Nothing is installed until they tap Install: any app can send us a VIEW intent.
     */
    private var installRequest by mutableStateOf<Uri?>(null)

    /** Opened only to install a skin, so Cancel goes back to the app that sent the file. */
    private var launchedToInstall = false

    private fun viewedSkin(intent: Intent?): Uri? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewedSkin(intent)?.let { installRequest = it }
    }

    /**
     * Debug builds only: install a skin straight from adb, for the skin kit's on-device loop
     * (`skills/create-unruly-skin/scripts/push.mjs`):
     * ```
     * adb shell am start -n <pkg>/com.merklegroot.unruly.unruly.host.UnrulySettingsActivity \
     *   -a com.merklegroot.unruly.INSTALL_SKIN -d file:///sdcard/Android/data/<pkg>/files/x.unruly
     * ```
     *
     * The file must be in our own external files dir, which needs no permission to read. Release
     * builds ignore the action; users load skins through the picker.
     */
    private fun installDevSkin(): Boolean {
        if (!BuildConfig.DEBUG || intent?.action != ACTION_INSTALL_SKIN) return false
        val uri = intent.data ?: return false
        try {
            val manifest = registry.installUserSkin(uri)
            registry.setActiveSkin(WebSkin.USER_PREFIX + manifest.id)
            L.i("Dev-installed skin ${manifest.id} ${manifest.version}")
        } catch (e: Exception) {
            L.e(e, "Dev skin install failed")
            Toast.makeText(this, "Skin install failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
        goToHost()
        return true
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
        leaveSettings()
    }
}

private const val ACTION_INSTALL_SKIN = "com.merklegroot.unruly.INSTALL_SKIN"

/** The menu and the pages it opens. One level deep, so the "stack" is just the open page. */
private enum class SettingsPage {
    Menu,
    Skin,
    Library,
    Lyrics,
}

/** Enums are not Parcelable; the name is. */
private val PageSaver: Saver<SettingsPage, String> =
    Saver(save = { it.name }, restore = { SettingsPage.valueOf(it) })

/** Off-white uncoated stock, signal red for the type, orange for state, black for rules. */
private val SettingsPaper = Color(0xFFFAF6F0)
private val SettingsRed = Color(0xFFE3191B)
private val SettingsOrange = Color(0xFFF2560F)

/** Inter, bundled under SIL OFL 1.1 (licenses/Inter-OFL.txt). */
private val SettingsSans =
    FontFamily(
        Font(R.font.inter_regular),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

/** Tight, heavy grotesk: the sleeve-index headline and menu entries. */
private val HeavyType =
    TextStyle(
        color = SettingsRed,
        fontFamily = SettingsSans,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.055).em,
    )

/** Every page's headline. Each is sized against the widest, so the headlines match in size. */
private val AllSettingsHeadlines = listOf("settings", "skin", "library", "lyrics", "install")

/** The size every headline shares on this window; menu entries scale off it. */
private val LocalHeadlineSize = compositionLocalOf { 96.sp }

/**
 * Indexing runs in the service and reports no completion signal here, so the "Rescanning" state is
 * a fixed acknowledgement rather than real progress. Without it the button gives no feedback at all
 * and reads as a dead control.
 */
private const val RESCAN_FEEDBACK_MS = 1800L

@Composable
private fun SettingsContent(
    registry: SkinRegistry,
    musicSettings: MusicSettings,
    lyricsPreferences: SharedPreferences,
    onRescan: () -> Unit,
    onSkinChosen: (String) -> Unit,
    equalizerName: String?,
    onOpenEqualizer: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val active by registry.activeSkin.collectAsStateWithLifecycle()
    var page by rememberSaveable(stateSaver = PageSaver) { mutableStateOf(SettingsPage.Menu) }
    var lyricsEnabled by remember {
        mutableStateOf(
            lyricsPreferences.getBoolean(
                LyricsPreferences.LOOKUP_ENABLED_KEY,
                LyricsPreferences.DEFAULT_LOOKUP_ENABLED,
            )
        )
    }
    var rescanning by remember { mutableStateOf(false) }
    // Folders, or everything Android has indexed. The mode write itself triggers the rescan.
    var wholePhone by remember {
        mutableStateOf(musicSettings.locationMode == LocationMode.MEDIA_STORE)
    }
    var audioDenied by remember { mutableStateOf(false) }
    val audioPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            audioDenied = !granted
            if (granted) {
                wholePhone = true
                musicSettings.locationMode = LocationMode.MEDIA_STORE
            }
        }
    val setWholePhone = { on: Boolean ->
        audioDenied = false
        if (on) {
            audioPermission.launch(AUDIO_PERMISSION)
        } else {
            wholePhone = false
            musicSettings.locationMode = LocationMode.SAF
        }
    }

    // User skins: a .unruly file picked here is checked, unpacked and switched to at once.
    var userSkins by remember { mutableStateOf(registry.userSkins()) }
    var installing by remember { mutableStateOf(false) }
    var installError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val skinPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            installing = true
            installError = null
            scope.launch {
                val result =
                    withContext(Dispatchers.IO) { runCatching { registry.installUserSkin(uri) } }
                installing = false
                result
                    .onSuccess {
                        userSkins = registry.userSkins()
                        onSkinChosen(WebSkin.USER_PREFIX + it.id)
                    }
                    .onFailure {
                        installError =
                            (it as? SkinPackageException)?.message ?: "Couldn't load that skin"
                    }
            }
        }
    // Packages arrive with all sorts of types (zip, octet-stream, none), so don't filter by type.
    val pickSkin = { skinPicker.launch(arrayOf("*/*")) }
    if (rescanning) {
        LaunchedEffect(Unit) {
            delay(RESCAN_FEEDBACK_MS)
            rescanning = false
        }
    }

    // Saveable for the same reason as in onboarding: the SAF picker hands the foreground to
    // another app, and this Activity may be recreated before the result arrives. Every edit
    // writes through, so the saved copy never disagrees with MusicSettings.
    val folders: SnapshotStateList<Uri> =
        rememberSaveable(
            saver =
                listSaver<SnapshotStateList<Uri>, Uri>(
                    save = { it.toList() },
                    restore = { it.toMutableStateList() },
                )
        ) {
            musicSettings.safQuery.source.map { it.uri }.toMutableStateList()
        }
    fun saveFolders() {
        musicSettings.saveFolders(context, folders.toList())
    }
    val addFolder = rememberFolderPicker { uri ->
        if (uri !in folders) {
            folders.add(uri)
            saveFolders()
        }
    }

    SideEffect { applySettingsSystemBars(context) }
    BackHandler(enabled = page != SettingsPage.Menu) { page = SettingsPage.Menu }

    AnimatedContent(
        page,
        Modifier.background(SettingsPaper),
        transitionSpec = {
            // Pages push in from the right and pop back out to it, like a stack.
            val direction = if (targetState == SettingsPage.Menu) -1 else 1
            (slideInHorizontally(tween(280)) { direction * it / 4 } + fadeIn(tween(280)))
                .togetherWith(
                    slideOutHorizontally(tween(280)) { -direction * it / 4 } + fadeOut(tween(200))
                )
        },
        label = "settings page",
    ) { shown ->
        val back = { page = SettingsPage.Menu }
        when (shown) {
            SettingsPage.Menu ->
                SettingsScaffold("settings", onClose = onClose) {
                    MenuEntry(
                        "skin",
                        if (registry.isRecoveryActive) "RECOVERY"
                        else active.displayName.uppercase(),
                    ) {
                        page = SettingsPage.Skin
                    }
                    MenuEntry(
                        "library",
                        if (wholePhone) "WHOLE PHONE" else folderCount(folders.size),
                    ) {
                        page = SettingsPage.Library
                    }
                    MenuEntry("lyrics", if (lyricsEnabled) "ON" else "OFF") {
                        page = SettingsPage.Lyrics
                    }
                    // Opens the phone's own equalizer rather than a page here; with none, the
                    // entry stays but says so instead of failing on tap.
                    MenuEntry(
                        "equalizer",
                        equalizerName?.uppercase() ?: "NOT AVAILABLE",
                        enabled = equalizerName != null,
                        onClick = onOpenEqualizer,
                    )
                }
            SettingsPage.Skin ->
                SettingsScaffold("skin", onBack = back) {
                    if (registry.isRecoveryActive) {
                        // The picker shows nothing selected, because the recovery screen is
                        // deliberately not one of the choices. Say why.
                        StatusLabel("YOUR SKIN CRASHED · PICK ONE")
                        Spacer(Modifier.height(8.dp))
                    }
                    registry.available().forEach { skin ->
                        SkinChoice(
                            displayName = skin.displayName,
                            selected = skin.id == active.id,
                            quarantined = registry.isQuarantined(skin.id),
                            onClick = { onSkinChosen(skin.id) },
                        )
                    }
                    userSkins.forEach { skin ->
                        SkinChoice(
                            displayName = skin.displayName,
                            selected = skin.id == active.id,
                            quarantined = registry.isQuarantined(skin.id),
                            onClick = { onSkinChosen(skin.id) },
                            detail =
                                registry.rollbackNote(skin.id)
                                    ?: skin.manifest.author?.let { "BY ${it.uppercase()}" },
                            onRemove = {
                                registry.removeUserSkin(skin.id)
                                userSkins = registry.userSkins()
                            },
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    SettingsButton(
                        if (installing) "Loading…" else "Load skin",
                        primary = false,
                        onClick = { if (!installing) pickSkin() },
                    ) {
                        drawPlus(it)
                    }
                    installError?.let {
                        Spacer(Modifier.height(10.dp))
                        StatusLabel(it.uppercase())
                    }
                    Spacer(Modifier.height(14.dp))
                }
            SettingsPage.Library ->
                SettingsScaffold(
                    "library",
                    onBack = back,
                    scrolls = false,
                    actions = {
                        if (!wholePhone) {
                            SettingsButton("Add folder", primary = false, onClick = addFolder) {
                                drawPlus(it)
                            }
                        }
                        SettingsButton(
                            if (rescanning) "Rescanning…" else "Rescan",
                            onClick = {
                                if (!rescanning) {
                                    rescanning = true
                                    onRescan()
                                }
                            },
                        ) {
                            drawRescan(it)
                        }
                    },
                ) {
                    SettingsToggle("All music on this phone", wholePhone, setWholePhone)
                    Spacer(Modifier.height(12.dp))
                    StatusLabel(
                        when {
                            audioDenied -> "NO ACCESS TO MUSIC"
                            wholePhone -> "WHOLE PHONE · READY"
                            folders.isEmpty() -> "0 FOLDERS · NOTHING TO SCAN"
                            else -> "${folderCount(folders.size)} · READY"
                        }
                    )
                    if (!wholePhone) {
                        Spacer(Modifier.height(8.dp))
                        FolderRows(
                            folders,
                            onRemove = {
                                folders.remove(it)
                                saveFolders()
                            },
                            Modifier.weight(1f, fill = false),
                        )
                    }
                }
            SettingsPage.Lyrics ->
                SettingsScaffold("lyrics", onBack = back) {
                    SettingsToggle(
                        "Fetch from lrclib.net",
                        checked = lyricsEnabled,
                        onChange = { enabled ->
                            lyricsEnabled = enabled
                            lyricsPreferences
                                .edit()
                                .putBoolean(LyricsPreferences.LOOKUP_ENABLED_KEY, enabled)
                                .apply()
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    StatusLabel("SENDS TITLE, ARTIST & ALBUM ONLY")
                }
        }
    }
}

/**
 * Confirm screen for a skin file opened from another app: what it is and who made it, then Install
 * or Cancel. Reading the manifest is all that happens before the user agrees.
 */
@Composable
private fun InstallSkinScreen(
    uri: Uri,
    peek: (Uri) -> UserSkinManifest,
    installedVersion: (String) -> String?,
    install: (Uri) -> UserSkinManifest,
    onInstalled: (UserSkinManifest) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    SideEffect { applySettingsSystemBars(context) }
    BackHandler(onBack = onCancel)
    val scope = rememberCoroutineScope()
    val peeked by
        produceState<Result<UserSkinManifest>?>(null, uri) {
            value = withContext(Dispatchers.IO) { runCatching { peek(uri) } }
        }
    var installing by remember(uri) { mutableStateOf(false) }
    var installError by remember(uri) { mutableStateOf<String?>(null) }
    fun message(e: Throwable) = (e as? SkinPackageException)?.message ?: "Couldn't read that file"

    SettingsScaffold("install", onClose = onCancel) {
        val result = peeked
        val manifest = result?.getOrNull()
        when {
            result == null -> StatusLabel("READING…")
            manifest == null -> {
                StatusLabel(message(result.exceptionOrNull()!!).uppercase())
                Spacer(Modifier.height(18.dp))
                SettingsButton("Close", onClick = onCancel) { drawCross(it) }
            }
            else -> {
                Text(
                    manifest.name,
                    style = HeavyType.copy(fontSize = 34.sp, lineHeight = 38.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusLabel(
                    listOfNotNull(manifest.author?.let { "BY $it" }, "V${manifest.version}")
                        .joinToString(" · ")
                        .uppercase()
                )
                installedVersion(manifest.id)?.let {
                    StatusLabel("REPLACES V${it.uppercase()} · KEPT IF THIS ONE FAILS")
                }
                Spacer(Modifier.height(24.dp))
                SettingsButton(
                    if (installing) "Installing…" else "Install",
                    onClick = {
                        if (installing) return@SettingsButton
                        installing = true
                        installError = null
                        scope.launch {
                            withContext(Dispatchers.IO) { runCatching { install(uri) } }
                                .onSuccess(onInstalled)
                                .onFailure {
                                    installing = false
                                    installError = message(it)
                                }
                        }
                    },
                ) {
                    drawPlus(it)
                }
                Spacer(Modifier.height(10.dp))
                SettingsButton("Cancel", primary = false, onClick = onCancel) { drawCross(it) }
                installError?.let {
                    Spacer(Modifier.height(10.dp))
                    StatusLabel(it.uppercase())
                }
            }
        }
    }
}

private fun folderCount(count: Int) = "$count " + if (count == 1) "FOLDER" else "FOLDERS"

/** Dark icons on the paper. Before Android 15 the bars do not inherit the content behind them. */
private fun applySettingsSystemBars(context: Context) {
    val window = (context as? Activity)?.window ?: return
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = true
        isAppearanceLightNavigationBars = true
    }
    window.statusBarColor = SettingsPaper.toArgb()
    window.navigationBarColor = SettingsPaper.toArgb()
}

/**
 * One settings page: a top bar ("← settings" or the close ×), the red headline, then the page's
 * content. The column is capped in width so wide windows keep the sleeve-index proportions rather
 * than stretching the headline across a tablet. [actions] are pinned under the content. When
 * [scrolls] is false the content is not scrolled as a whole, so a child given `weight(1f, fill =
 * false)` can scroll on its own while the pinned actions stay put.
 */
@Composable
private fun SettingsScaffold(
    headline: String,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    scrolls: Boolean = true,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier.fillMaxSize()
            .background(SettingsPaper)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxHeight().padding(horizontal = 18.dp)) {
            TopBar(onBack, onClose)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val size = rememberHeadlineSize(maxWidth)
                CompositionLocalProvider(LocalHeadlineSize provides size) {
                    Column(
                        Modifier.fillMaxWidth()
                            .heightIn(max = this@BoxWithConstraints.maxHeight)
                            .then(
                                if (scrolls) Modifier.verticalScroll(rememberScrollState())
                                else Modifier
                            )
                    ) {
                        Text(
                            headline,
                            Modifier.padding(bottom = 22.dp).semantics { heading() },
                            style = HeavyType.copy(fontSize = size, lineHeight = size * 1.05f),
                            maxLines = 1,
                        )
                        content()
                        actions?.let {
                            Column(
                                Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                content = it,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The headline size that fits the widest of [AllSettingsHeadlines] to the column. */
@Composable
private fun rememberHeadlineSize(width: Dp): TextUnit {
    val measurer = rememberTextMeasurer()
    val widthPx = with(LocalDensity.current) { width.toPx() }
    return remember(measurer, widthPx) {
        val probe = HeavyType.copy(fontSize = 100.sp)
        val widest =
            AllSettingsHeadlines.maxOf { measurer.measure(it, probe).size.width }.coerceAtLeast(1)
        // Leave a little air on the right, as on the sleeve; stop growing on wide panes.
        (100f * widthPx * 0.93f / widest).coerceAtMost(132f).sp
    }
}

@Composable
private fun TopBar(onBack: (() -> Unit)?, onClose: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Row(
                Modifier.fillMaxHeight()
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(end = 12.dp)
                    .semantics { contentDescription = "Back to settings" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(width = 22.dp, height = 16.dp)) {
                    drawArrow(VortexInk, pointsRight = false, stroke = 2.dp.toPx())
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "settings",
                    color = VortexInk,
                    fontFamily = SettingsSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (onClose != null) {
            Box(
                Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClose).semantics {
                    contentDescription = "Close settings"
                },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(20.dp)) {
                    val stroke = 2.dp.toPx()
                    drawLine(VortexInk, Offset.Zero, Offset(size.width, size.height), stroke)
                    drawLine(VortexInk, Offset(size.width, 0f), Offset(0f, size.height), stroke)
                }
            }
        }
    }
}

/** A hairline rule under a row, as between the tracks of a sleeve's index. */
private fun Modifier.drawBehindRule(): Modifier = drawBehind {
    val stroke = 1.5.dp.toPx()
    drawLine(
        VortexInk,
        Offset(0f, size.height - stroke / 2),
        Offset(size.width, size.height - stroke / 2),
        stroke,
    )
}

/** A menu entry: the option in heavy red, its state in orange caps beneath, an arrow, a rule. */
@Composable
private fun MenuEntry(label: String, status: String, enabled: Boolean = true, onClick: () -> Unit) {
    val size = LocalHeadlineSize.current * 0.6f
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .drawBehindRule()
            .padding(top = 14.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = HeavyType.copy(fontSize = size, lineHeight = size * 1.1f),
                maxLines = 1,
            )
            StatusLabel(status)
        }
        if (enabled) {
            Canvas(Modifier.size(width = 30.dp, height = 22.dp)) {
                drawArrow(SettingsOrange, pointsRight = true, stroke = 2.dp.toPx())
            }
        }
    }
}

/**
 * One skin, set in red like a menu entry with a check on the active one. Picking a skin leaves
 * settings to show it, so the check only ever marks the current skin.
 */
@Composable
private fun SkinChoice(
    displayName: String,
    selected: Boolean,
    quarantined: Boolean,
    onClick: () -> Unit,
    detail: String? = null,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = displayName + if (selected) ", current" else "" }
            .drawBehindRule()
            .heightIn(min = 76.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                displayName,
                style = HeavyType.copy(fontSize = 34.sp, lineHeight = 38.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (quarantined) StatusLabel("CRASHED LAST TIME · WILL RETRY")
            else if (detail != null) StatusLabel(detail)
        }
        if (onRemove != null) {
            Box(
                Modifier.size(48.dp).clickable(role = Role.Button, onClick = onRemove).semantics {
                    contentDescription = "Remove $displayName"
                },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(14.dp)) {
                    val stroke = 2.dp.toPx()
                    drawLine(VortexInk, Offset.Zero, Offset(size.width, size.height), stroke)
                    drawLine(VortexInk, Offset(size.width, 0f), Offset(0f, size.height), stroke)
                }
            }
        }
        if (selected) {
            Canvas(Modifier.size(28.dp)) {
                drawPath(
                    Path().apply {
                        moveTo(size.width * 0.08f, size.height * 0.55f)
                        lineTo(size.width * 0.38f, size.height * 0.85f)
                        lineTo(size.width * 0.94f, size.height * 0.15f)
                    },
                    SettingsOrange,
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Square, join = StrokeJoin.Miter),
                )
            }
        }
    }
}

/** The music folders, one ruled row each, with a × to remove. Scrolls once it fills the space. */
@Composable
private fun FolderRows(folders: List<Uri>, onRemove: (Uri) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        folders.forEach { uri ->
            // Re-derived per row rather than stored: `Path` is what turns an opaque tree Uri
            // into a readable folder, and it is cheap.
            val label = folderLabel(Location.Unopened.from(context, uri).path, context)
            Row(
                Modifier.fillMaxWidth().drawBehindRule().heightIn(min = 60.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    color = VortexInk,
                    fontFamily = SettingsSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.02).em,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier.size(48.dp)
                        .clickable(role = Role.Button) { onRemove(uri) }
                        .semantics { contentDescription = "Remove $label" },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(14.dp)) {
                        val stroke = 2.dp.toPx()
                        drawLine(VortexInk, Offset.Zero, Offset(size.width, size.height), stroke)
                        drawLine(VortexInk, Offset(size.width, 0f), Offset(0f, size.height), stroke)
                    }
                }
            }
        }
    }
}

/** A solid red primary or a black-outlined secondary, label left and glyph right. */
@Composable
private fun SettingsButton(
    label: String,
    primary: Boolean = true,
    onClick: () -> Unit,
    glyph: DrawScope.(Color) -> Unit,
) {
    val ink = if (primary) SettingsPaper else VortexInk
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 58.dp)
            .then(
                if (primary) Modifier.background(SettingsRed)
                else Modifier.border(1.5.dp, VortexInk)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = ink,
            fontFamily = SettingsSans,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = (-0.02).em,
        )
        Spacer(Modifier.width(12.dp))
        Canvas(Modifier.size(22.dp)) { glyph(ink) }
    }
}

/** A labelled switch: the whole row toggles; the track fills red when on. */
@Composable
private fun SettingsToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .drawBehindRule()
            .heightIn(min = 72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = VortexInk,
            fontFamily = SettingsSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            letterSpacing = (-0.02).em,
        )
        Spacer(Modifier.width(12.dp))
        val track = RoundedCornerShape(50)
        Box(
            Modifier.size(width = 56.dp, height = 30.dp)
                .background(if (checked) SettingsRed else Color.Transparent, track)
                .border(1.5.dp, if (checked) SettingsRed else VortexInk, track)
                .padding(4.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier.size(22.dp)
                    .background(if (checked) SettingsPaper else VortexInk, CircleShape)
            )
        }
    }
}

/** Bold orange caps for a state, such as "2 FOLDERS · READY". */
@Composable
private fun StatusLabel(text: String) {
    Text(
        text,
        color = SettingsOrange,
        fontFamily = SettingsSans,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.02.em,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A thin straight arrow filling the canvas. */
private fun DrawScope.drawArrow(ink: Color, pointsRight: Boolean, stroke: Float) {
    val w = size.width
    val h = size.height
    val tip = if (pointsRight) w else 0f
    val back = if (pointsRight) w - h * 0.5f else h * 0.5f
    val style = Stroke(stroke, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
    drawLine(ink, Offset(w - tip, h / 2), Offset(tip, h / 2), stroke)
    drawPath(
        Path().apply {
            moveTo(back, 0f)
            lineTo(tip, h / 2)
            lineTo(back, h)
        },
        ink,
        style = style,
    )
}

private fun DrawScope.drawCross(ink: Color) {
    val stroke = 2.dp.toPx()
    val inset = size.width * 0.15f
    drawLine(ink, Offset(inset, inset), Offset(size.width - inset, size.height - inset), stroke)
    drawLine(ink, Offset(size.width - inset, inset), Offset(inset, size.height - inset), stroke)
}

private fun DrawScope.drawPlus(ink: Color) {
    val stroke = 2.dp.toPx()
    drawLine(ink, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), stroke)
    drawLine(ink, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke)
}

/** An open circle with an arrowhead at its end, for "rescan". */
private fun DrawScope.drawRescan(ink: Color) {
    val stroke = 2.dp.toPx()
    val inset = stroke
    drawArc(
        ink,
        startAngle = -60f,
        sweepAngle = 300f,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        style = Stroke(stroke),
    )
    val r = size.width / 2 - inset
    val end = Offset(size.width / 2 + r * 0.5f, size.height / 2 - r * 0.866f)
    drawPath(
        Path().apply {
            moveTo(end.x - r * 0.5f, end.y - r * 0.15f)
            lineTo(end.x, end.y)
            lineTo(end.x - r * 0.1f, end.y + r * 0.5f)
        },
        ink,
        style = Stroke(stroke, join = StrokeJoin.Miter),
    )
}
