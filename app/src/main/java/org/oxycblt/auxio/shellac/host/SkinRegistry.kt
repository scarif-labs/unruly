package org.oxycblt.auxio.shellac.host

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber as L
import org.shellac.skin.AppSkin
import org.shellac.skin.debug.DebugSkinA
import org.shellac.skin.debug.DebugSkinB

/**
 * Owns which skins exist, which one is active, and the recovery path when the active one is
 * unusable.
 *
 * Buildspec 3.8 asks for "safe fallback to a known-good skin if the stored ID is missing or
 * its skin throws during composition". Both halves matter and they fail differently:
 *
 *  - **Missing id** is cheap to detect - resolve returns null, fall back, done.
 *  - **Throwing skin** is the dangerous one. Falling back in-memory is not enough: the
 *    stored preference still names the broken skin, so the next launch crashes again. That
 *    is the "a bad skin means reinstalling" failure the buildspec warns about. So
 *    [reportSkinFailure] **rewrites the persisted preference** to the fallback and
 *    quarantines the offender for the rest of the process.
 */
@Singleton
class SkinRegistry @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * The skin used when nothing else is usable. MUST be trivially simple and must never be
     * quarantined - it is the floor of the recovery path.
     */
    private val fallback: AppSkin = DebugSkinA()

    private val skins: Map<String, AppSkin> =
        listOf(fallback, DebugSkinB()).associateBy { it.id }

    /** Skins that threw during composition this process. Not persisted; a restart re-tries. */
    private val quarantined = mutableSetOf<String>()

    private val _activeSkin = MutableStateFlow(resolveActive())
    val activeSkin: StateFlow<AppSkin> = _activeSkin.asStateFlow()

    /** Every registered skin, for the settings picker. */
    fun available(): List<AppSkin> = skins.values.sortedBy { it.displayName }

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
     * Persists an "in flight" marker. Compose has no error boundary (see [SkinHost]), so a
     * throwing skin kills the process and no catch block ever runs. The marker is what
     * survives that: if it is still set at next launch, the skin it names crashed.
     *
     * Uses `commit()`, not `apply()` - an async write would very likely lose the race
     * against the crash it exists to record.
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

        val storedId = prefs.getString(KEY_ACTIVE_SKIN, null) ?: return fallback
        val stored = skins[storedId]
        if (stored == null) {
            // Stored id names a skin that no longer exists - e.g. it was removed in an
            // update, or the preference was edited by hand. Do NOT crash.
            L.w("Stored skin id '$storedId' is unknown; falling back to '${fallback.id}'")
            return fallback
        }
        if (storedId in quarantined) return fallback
        return stored
    }

    private companion object {
        const val PREFS_NAME = "shellac_skins"
        const val KEY_ACTIVE_SKIN = "active_skin_id"
        const val KEY_RENDER_IN_FLIGHT = "render_in_flight_skin_id"
    }
}
