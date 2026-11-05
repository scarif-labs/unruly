/*
 * Copyright (c) 2026 Unruly Project
 * SkinRegistry.kt is part of Unruly.
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

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.merklegroot.unruly.unruly.web.UserSkinManifest
import com.merklegroot.unruly.unruly.web.UserSkinStore
import com.merklegroot.unruly.unruly.web.WebSkin
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.unruly.skin.AppSkin
import org.unruly.skin.wheel.TouchWheelSkin
import org.unruly.skin.amp.AmpSkin
import org.unruly.skin.crossbar.CrossbarSkin
import org.unruly.skin.glossy.GlossySkin
import org.unruly.skin.zune.ZuneSkin
import timber.log.Timber as L

/**
 * Owns which skins exist, which one is active, and the recovery path when the active one is
 * unusable.
 *
 * Buildspec 3.8 asks for "safe fallback to a known-good skin if the stored ID is missing or its
 * skin throws during composition". Both halves matter and they fail differently:
 * - **Missing id** is cheap to detect - resolve returns null, fall back, done.
 * - **Throwing skin** is the dangerous one. Falling back in-memory is not enough: the stored
 *   preference still names the broken skin, so the next launch crashes again. That is the "a bad
 *   skin means reinstalling" failure the buildspec warns about. So [reportSkinFailure] **rewrites
 *   the persisted preference** to the fallback and quarantines the offender for the rest of the
 *   process.
 */
@Singleton
class SkinRegistry @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * The skin used when nothing else is usable. MUST be trivially simple and must never be
     * quarantined - it is the floor of the recovery path.
     *
     * This is deliberately NOT [default] and deliberately not a real skin at all. The fallback's
     * job is to render *something* at the moment a real skin has just crashed the process, so it
     * must be the least likely thing in the app to fail. Every shipping skin is elaborate enough to
     * have its own ways of failing; [RecoverySkin] has none.
     */
    private val fallback: AppSkin = RecoverySkin()

    /** The skin a fresh install starts on, before the user has ever chosen one. */
    private val default: AppSkin = TouchWheelSkin()

    /**
     * Built-in skins the user can choose. [fallback] is not among them - offering "Recovery" in the
     * picker would be offering a deliberately featureless app.
     */
    private val builtIn: List<AppSkin> = listOf(default, ZuneSkin(), AmpSkin(), GlossySkin(), CrossbarSkin())

    /** User-loaded web skins (`.unruly` packages). See [WebSkin]. */
    private val store = UserSkinStore(context)
    private var userSkins: List<WebSkin> = loadUserSkins()

    private val skins: Map<String, AppSkin>
        get() = (builtIn + userSkins + fallback).associateBy { it.id }

    /** Skins that threw during composition this process. Not persisted; a restart re-tries. */
    private val quarantined = mutableSetOf<String>()

    private val _activeSkin = MutableStateFlow(resolveActive())
    val activeSkin: StateFlow<AppSkin> = _activeSkin.asStateFlow()

    /** Every built-in user-selectable skin, for the settings picker. */
    fun available(): List<AppSkin> = builtIn.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })

    /** Installed user skins, for the settings picker. */
    fun userSkins(): List<WebSkin> = userSkins

    /**
     * Installs the `.unruly` package at [uri] and returns it. Throws
     * [com.merklegroot.unruly.unruly.web.SkinPackageException] with a user-facing message when
     * the file is refused. Installing an id that is already installed updates it.
     */
    fun installUserSkin(uri: Uri): UserSkinManifest {
        val manifest = store.install(uri)
        userSkins = loadUserSkins()
        // An update replaces the skin object, so re-point the active one at the new files.
        val active = _activeSkin.value
        if (active is WebSkin && active.manifest.id == manifest.id) {
            _activeSkin.value = skins[active.id] ?: default
        }
        quarantined.remove(WebSkin.USER_PREFIX + manifest.id)
        rollbacks.remove(WebSkin.USER_PREFIX + manifest.id)
        return manifest
    }

    /** The manifest of a package, without installing it. See [UserSkinStore.peek]. */
    fun peekUserSkin(uri: Uri): UserSkinManifest = store.peek(uri)

    fun removeUserSkin(id: String) {
        val skin = userSkins.firstOrNull { it.id == id } ?: return
        if (_activeSkin.value.id == id) setActiveSkin(default.id)
        store.remove(skin.manifest.id)
        userSkins = loadUserSkins()
    }

    /**
     * A skin failed without taking the process down - a web skin's renderer crashed or its page
     * failed to load. Same treatment as a crash caught by the in-flight marker: quarantine it and
     * fall back, persisting the fallback so the next launch does not walk into it again.
     */
    fun reportSkinFailure(id: String, reason: String) {
        L.w("Skin '$id' failed: $reason")
        // A user skin that was just updated goes back to the version before, rather than to the
        // recovery screen: a bad update shouldn't cost the user a skin that was working.
        val failed = userSkins.firstOrNull { it.id == id }
        val restored = failed?.let { store.rollback(it.manifest.id) }
        if (restored != null) {
            L.w("Rolled '$id' back from ${failed.manifest.version} to ${restored.version}")
            rollbacks[id] = "UPDATE ${failed.manifest.version} FAILED · BACK ON ${restored.version}"
            userSkins = loadUserSkins()
            if (_activeSkin.value.id == id) _activeSkin.value = skins[id] ?: fallback
            return
        }
        quarantined.add(id)
        if (_activeSkin.value.id == id) {
            prefs.edit { putString(KEY_ACTIVE_SKIN, fallback.id) }
            _activeSkin.value = fallback
        }
    }

    /** Per user skin, why it is on an older version than the one last installed. */
    private val rollbacks = mutableMapOf<String, String>()

    fun rollbackNote(id: String): String? = rollbacks[id]

    private fun loadUserSkins(): List<WebSkin> =
        store.installed().map { (manifest, dir) ->
            WebSkin(
                manifest,
                dir,
                onLoaded = { skin -> store.dropPrevious(skin.manifest.id) },
                onFailure = { skin, reason -> reportSkinFailure(skin.id, reason) },
            )
        }

    /**
     * True when the recovery UI is what is currently on screen - i.e. no real skin is running.
     * Settings uses this to explain why the picker shows nothing selected.
     */
    val isRecoveryActive: Boolean
        get() = _activeSkin.value.id == fallback.id

    fun isQuarantined(id: String): Boolean = id in quarantined

    fun setActiveSkin(id: String) {
        val skin = skins[id]
        if (skin == null) {
            L.w("Refusing to activate unknown skin id '$id'")
            return
        }
        // Choosing a skin explicitly is a statement of intent - clear any quarantine so the
        // user can retry a skin they have just fixed or rebuilt.
        quarantined.remove(id)
        prefs.edit { putString(KEY_ACTIVE_SKIN, id) }
        _activeSkin.value = skin
        L.d("Active skin set to '$id'")
    }

    /**
     * Called by the host immediately BEFORE a skin composes.
     *
     * Persists an "in flight" marker. Compose has no error boundary (see [SkinHost]), so a throwing
     * skin kills the process and no catch block ever runs. The marker is what survives that: if it
     * is still set at next launch, the skin it names crashed.
     *
     * Uses `commit()`, not `apply()` - an async write would very likely lose the race against the
     * crash it exists to record.
     */
    @Suppress("ApplySharedPref")
    fun beginRender(id: String) {
        prefs.edit().putString(KEY_RENDER_IN_FLIGHT, id).commit()
    }

    /** Called once a skin has composed a frame successfully. Clears the crash marker. */
    fun markRenderSucceeded(id: String) {
        if (prefs.getString(KEY_RENDER_IN_FLIGHT, null) == id) {
            prefs.edit { remove(KEY_RENDER_IN_FLIGHT) }
        }
    }

    private fun resolveActive(): AppSkin {
        // A marker left over from the previous process means that skin crashed while
        // composing. Quarantine it before anything tries to render it again.
        val crashed = prefs.getString(KEY_RENDER_IN_FLIGHT, null)
        if (crashed != null) {
            prefs.edit { remove(KEY_RENDER_IN_FLIGHT) }
            if (crashed == fallback.id) {
                // The fallback itself crashed. Quarantining it would leave nothing to
                // render at all, so let it through - this is a build-time bug, not a
                // user-recoverable state.
                L.w("Fallback skin '$crashed' crashed while composing; not quarantining it")
            } else {
                L.w("Skin '$crashed' crashed while composing last run; quarantining it")
                quarantined.add(crashed)
                prefs.edit { putString(KEY_ACTIVE_SKIN, fallback.id) }
                return fallback
            }
        }

        // Nothing stored means a fresh install, not a recovery - start on the default.
        val storedId = prefs.getString(KEY_ACTIVE_SKIN, null) ?: return default
        val stored = skins[storedId]
        if (stored == null) {
            // Stored id names a skin that no longer exists - e.g. it was removed in an
            // update, or the preference was edited by hand. Do NOT crash.
            //
            // This resolves to the DEFAULT, not the fallback: a retired skin id is not a
            // crash, and the recovery UI is a deliberately featureless screen that would be
            // a bizarre thing to hand someone whose only sin was upgrading. The debug skins
            // removed in this build make this the common case, not a hypothetical.
            L.w("Stored skin id '$storedId' is unknown; using default '${default.id}'")
            prefs.edit { putString(KEY_ACTIVE_SKIN, default.id) }
            return default
        }
        if (storedId in quarantined) return fallback
        return stored
    }

    private companion object {
        const val PREFS_NAME = "unruly_skins"
        const val KEY_ACTIVE_SKIN = "active_skin_id"
        const val KEY_RENDER_IN_FLIGHT = "render_in_flight_skin_id"
    }
}
