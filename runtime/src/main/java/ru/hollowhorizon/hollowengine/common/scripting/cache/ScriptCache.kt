package ru.hollowhorizon.hollowengine.common.scripting.cache

import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.scripting.source.DEFAULT_SANDBOX_NAMESPACE
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptId
import java.io.File
import java.util.jar.JarInputStream

/**
 * Compiled script artifacts on disk: one jar per root script, stamped with the fingerprint of the
 * sources it was built from.
 *
 * A `@file:SharedScript` gets an artifact of its own instead of a copy inside every importer. One
 * dialogue system imported by a thousand scripts is one jar, not a thousand, and at runtime its
 * classes are defined once for everybody.
 */
object ScriptCache {
    const val HASH_ATTRIBUTE = "Script-Hashcode"

    /** Which shared scripts a root was built against, as `namespace:path=fingerprint` entries. */
    const val SHARED_ATTRIBUTE = "Shared-Scripts"

    fun artifact(id: ScriptId): File =
        DirectoryManager.SCRIPT_CACHE.resolve(id.namespace).resolve(id.path + ".jar")

    /** Classes of one shared script, referenced by every artifact that imports it. */
    fun sharedArtifact(id: ScriptId): File =
        DirectoryManager.SCRIPT_CACHE.resolve(id.namespace).resolve(id.path + ".shared.jar")

    /** The fingerprint a jar was built for, or `null` if it is missing, unreadable or unstamped. */
    fun hashOf(jar: File): String? {
        if (!jar.isFile) return null
        return runCatching {
            jar.inputStream().use { input ->
                JarInputStream(input).use { archive ->
                    archive.manifest?.mainAttributes?.getValue(HASH_ATTRIBUTE)
                }
            }
        }.onFailure { HollowEngine.LOGGER.warn("Unreadable compiled script '{}'", jar, it) }.getOrNull()
    }

    fun isValid(jar: File, expectedHash: String?): Boolean =
        expectedHash != null && hashOf(jar) == expectedHash

    fun invalidate(id: ScriptId) {
        val file = artifact(id)
        if (file.isFile && !file.delete()) {
            HollowEngine.LOGGER.warn("Could not remove the compiled script '{}'", file)
        }
    }

    fun sharedScriptsOf(jar: File): Map<ScriptId, String> {
        val raw = runCatching {
            jar.inputStream().use { input ->
                JarInputStream(input).use { archive ->
                    archive.manifest?.mainAttributes?.getValue(SHARED_ATTRIBUTE)
                }
            }
        }.getOrNull() ?: return emptyMap()

        return raw.split(';')
            .mapNotNull { entry ->
                val separator = entry.lastIndexOf('=')
                if (separator <= 0) return@mapNotNull null
                val id = runCatching {
                    ScriptId.parse(entry.substring(0, separator), DEFAULT_SANDBOX_NAMESPACE)
                }.getOrNull() ?: return@mapNotNull null
                id to entry.substring(separator + 1)
            }
            .toMap()
    }

    /**
     * Removes artifacts that no longer belong to any known script, so renaming or deleting a script
     * does not leave its bytecode behind forever.
     *
     * Only namespaces represented in [known] are touched: a disabled addon has no scripts to report and
     * must not lose the artifacts it will need when it is enabled again.
     */
    fun prune(known: Collection<ScriptId>) {
        val root = DirectoryManager.SCRIPT_CACHE
        if (!root.isDirectory) return
        val expected = known.flatMapTo(HashSet()) {
            listOf(artifact(it).canonicalPath, sharedArtifact(it).canonicalPath)
        }
        known.mapTo(HashSet(), ScriptId::namespace).forEach { namespace ->
            val namespaceRoot = root.resolve(namespace)
            if (!namespaceRoot.isDirectory) return@forEach
            namespaceRoot.walkBottomUp().forEach { file ->
                when {
                    file.isFile && file.extension == "jar" && file.canonicalPath !in expected -> {
                        if (file.delete()) HollowEngine.LOGGER.debug("Removed stale compiled script '{}'", file)
                    }

                    file.isDirectory && file != namespaceRoot && file.list()?.isEmpty() == true -> file.delete()
                }
            }
        }
    }
}
