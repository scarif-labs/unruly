/*
 * Copyright (c) 2026 Unruly Project
 * UserSkinStore.kt is part of Unruly.
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

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber as L

/**
 * A user skin's `skin.json`. The format is documented for skin authors in
 * `skills/create-unruly-skin/SKILL.md`; [UserSkinStore.parseManifest] and the kit's `pack.mjs`
 * apply the same rules, so a skin that packs will install.
 */
data class UserSkinManifest(
    val id: String,
    val name: String,
    val author: String?,
    val version: String,
    val unrulyApi: Int,
    val entry: String,
    val edgeToEdge: Boolean,
    /** One of [GEAR_CORNERS]. There is deliberately no "off" - see [WebSkin]. */
    val settingsGear: String,
    /** Shows the skin's JS errors on screen ([SkinErrorPanel]). For authors; off when shared. */
    val developer: Boolean = false,
)

val GEAR_CORNERS = listOf("top-start", "top-end", "bottom-start", "bottom-end")

/** The bridge version this build speaks. Skins asking for a newer one are refused. */
const val UNRULY_API = 1

/** A user-facing reason a package was refused. The message is shown as-is in settings. */
class SkinPackageException(message: String) : Exception(message)

/**
 * Installed user skins, unpacked under `filesDir/skins/<id>/`.
 *
 * An update keeps the version it replaces in `skins/.previous/<id>/` until the new one has loaded
 * once ([dropPrevious]); if it fails first, [rollback] puts the old one back.
 *
 * Packages are untrusted files from anywhere, so nothing is written into place until the whole zip
 * has been read and checked: size and file-count caps, no absolute or `..` paths (zip-slip), a valid
 * manifest, and an entry page that exists. It is extracted into a staging dir and only then swapped
 * in, so a bad update never leaves a half-written skin behind.
 */
class UserSkinStore(context: Context) {
    private val context = context.applicationContext
    val root = File(this.context.filesDir, "skins")
    private val previousRoot = File(root, ".previous")

    /** Every installed skin with a readable manifest. A broken one is skipped, not fatal. */
    fun installed(): List<Pair<UserSkinManifest, File>> =
        root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            .orEmpty()
            .mapNotNull { dir ->
                try {
                    parseManifest(File(dir, MANIFEST).readText()) to dir
                } catch (e: Exception) {
                    L.w(e, "Skipping unreadable user skin in ${dir.name}")
                    null
                }
            }
            .sortedBy { it.first.name.lowercase() }

    /** Installs (or updates, for a known id) the package at [uri]. */
    fun install(uri: Uri): UserSkinManifest {
        val input =
            context.contentResolver.openInputStream(uri)
                ?: throw SkinPackageException("Couldn't open that file")
        return input.use { install(it) }
    }

    fun install(input: InputStream): UserSkinManifest {
        root.mkdirs()
        val staging = File(root, ".staging-${System.nanoTime()}")
        try {
            extract(input, staging)
            // A zip of a folder (rather than of its contents) has everything one level down.
            val base =
                if (File(staging, MANIFEST).isFile) staging
                else
                    staging.listFiles().orEmpty().singleOrNull { File(it, MANIFEST).isFile }
                        ?: throw SkinPackageException("Not an Unruly skin (no skin.json)")
            val manifest = parseManifest(File(base, MANIFEST).readText())
            val entry = File(base, manifest.entry)
            if (!entry.isFile) throw SkinPackageException("${manifest.entry} is missing")

            val target = File(root, manifest.id)
            val previous = File(previousRoot, manifest.id)
            if (target.exists()) {
                // Keep what is installed now as the rollback copy, replacing any older one.
                previous.deleteRecursively()
                previousRoot.mkdirs()
                if (!target.renameTo(previous)) throw IOException("Can't replace $target")
            }
            if (!base.renameTo(target)) {
                previous.renameTo(target)
                throw IOException("Can't install into $target")
            }
            return manifest
        } catch (e: SkinPackageException) {
            throw e
        } catch (e: IOException) {
            L.e(e, "Installing a user skin failed")
            throw SkinPackageException("Couldn't read that file")
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * Reads just the manifest of the package at [uri], without installing anything - for the
     * confirm screen when a file is opened from another app. [install] still checks everything.
     */
    fun peek(uri: Uri): UserSkinManifest {
        val input =
            context.contentResolver.openInputStream(uri)
                ?: throw SkinPackageException("Couldn't open that file")
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    // At the root, or one folder down for a zip of a folder.
                    if (name == MANIFEST || (name.count { it == '/' } == 1 && name.endsWith("/$MANIFEST") && !name.startsWith("__MACOSX/"))) {
                        // Capped: this runs before any other check, on a file from anywhere.
                        val text = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            text.write(buffer, 0, read)
                            if (text.size() > 64 * 1024) {
                                throw SkinPackageException("skin.json is too big")
                            }
                        }
                        return parseManifest(text.toString(Charsets.UTF_8.name()))
                    }
                }
            }
        } catch (e: SkinPackageException) {
            throw e
        } catch (e: IOException) {
            throw SkinPackageException("Not an Unruly skin (not a zip)")
        }
        throw SkinPackageException("Not an Unruly skin (no skin.json)")
    }

    fun remove(id: String) {
        val dir = File(root, id)
        // The id came from our own listing, but never delete outside the store.
        if (dir.parentFile == root) dir.deleteRecursively()
        dropPrevious(id)
    }

    /** The version an update replaced, if it is still kept. */
    fun previous(id: String): UserSkinManifest? =
        try {
            parseManifest(File(File(previousRoot, id), MANIFEST).readText())
        } catch (e: Exception) {
            null
        }

    /** Swaps the kept previous version back in. Returns it, or null if there is none. */
    fun rollback(id: String): UserSkinManifest? {
        val manifest = previous(id) ?: return null
        val current = File(root, id)
        if (current.parentFile != root) return null
        current.deleteRecursively()
        if (!File(previousRoot, id).renameTo(current)) return null
        return manifest
    }

    /** The current version has loaded fine; the rollback copy is no longer needed. */
    fun dropPrevious(id: String) {
        val dir = File(previousRoot, id)
        if (dir.parentFile == previousRoot) dir.deleteRecursively()
    }

    private fun extract(input: InputStream, into: File) {
        val canonicalRoot = into.canonicalFile
        var files = 0
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                // macOS Finder zips carry resource-fork junk; it is harmless, so just skip it.
                if (name.startsWith("__MACOSX/") || name.endsWith("/.DS_Store")) continue
                if (name.startsWith("/") || name.split('/').any { it == ".." }) {
                    throw SkinPackageException("The skin contains an unsafe path")
                }
                val out = File(into, name).canonicalFile
                if (!out.path.startsWith(canonicalRoot.path + File.separator)) {
                    throw SkinPackageException("The skin contains an unsafe path")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                    continue
                }
                if (++files > MAX_FILES) throw SkinPackageException("The skin has too many files")
                out.parentFile?.mkdirs()
                out.outputStream().use { sink ->
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) {
                            throw SkinPackageException("The skin is too big (over 50 MB)")
                        }
                        sink.write(buffer, 0, read)
                    }
                }
            }
        }
        if (files == 0) throw SkinPackageException("Not an Unruly skin (not a zip, or empty)")
    }

    companion object {
        const val MANIFEST = "skin.json"
        private const val MAX_FILES = 2000
        private const val MAX_BYTES = 50L * 1024 * 1024
        private val ID = Regex("^[a-z0-9]+([._-][a-z0-9]+)*$")

        fun parseManifest(text: String): UserSkinManifest {
            val json =
                try {
                    JSONObject(text)
                } catch (e: JSONException) {
                    throw SkinPackageException("skin.json isn't valid JSON")
                }
            fun string(key: String): String =
                json.optString(key).trim().takeIf { it.isNotEmpty() }
                    ?: throw SkinPackageException("skin.json is missing \"$key\"")

            val id = string("id")
            if (id.length > 100 || !ID.matches(id)) {
                throw SkinPackageException(
                    "skin.json \"id\" must be lowercase letters, digits, '.', '-' or '_'"
                )
            }
            val api = json.optInt("unrulyApi", -1)
            if (api < 1) throw SkinPackageException("skin.json is missing \"unrulyApi\"")
            if (api > UNRULY_API) throw SkinPackageException("This skin needs a newer Unruly")
            val entry = json.optString("entry").trim().ifEmpty { "index.html" }
            if (entry.startsWith("/") || entry.split('/').any { it == ".." }) {
                throw SkinPackageException("skin.json \"entry\" must be a path inside the skin")
            }
            val gear = json.optString("settingsGear").trim().ifEmpty { "top-end" }
            if (gear !in GEAR_CORNERS) {
                throw SkinPackageException(
                    "skin.json \"settingsGear\" must be one of ${GEAR_CORNERS.joinToString()}"
                )
            }
            return UserSkinManifest(
                id = id,
                name = string("name").take(40),
                author = json.optString("author").trim().takeIf { it.isNotEmpty() }?.take(60),
                version = json.optString("version").trim().ifEmpty { "0" },
                unrulyApi = api,
                entry = entry,
                edgeToEdge = json.optBoolean("edgeToEdge", true),
                settingsGear = gear,
                developer = json.optBoolean("developer", false),
            )
        }
    }
}
