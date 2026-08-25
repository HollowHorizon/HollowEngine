package ru.hollowhorizon.hollowengine.common.scripting.compiling

import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptCache
import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptFingerprint
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptId
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry
import java.io.File
import java.util.jar.JarFile
import kotlin.reflect.KClass
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript

/**
 * The classes of `@file:SharedScript` scripts, defined once for the whole game.
 *
 * An imported script is compiled into the module of every script that imports it, so each importer's
 * jar carries its own copy. The cache keeps one artifact per shared script instead, and this registry
 * defines its classes once: without it two importers would end up with two unrelated classes for one
 * file, and `@file:SharedScript` could only share an instance inside a single import graph.
 */
object SharedScriptClasses {
    private class Entry(val fingerprint: String, val loader: ClassLoader)

    private val loaders = HashMap<String, Entry>()
    private val instances = HashMap<KClass<*>, EvaluationResult>()

    /**
     * Where the classes of shared scripts among [imports] have to be looked up, by class name.
     *
     * Imports come before their importers, so a shared script that imports another one is built after
     * the loader it needs already exists.
     */
    @Synchronized
    fun loadersFor(
        imports: List<CompiledScript>,
        entries: Map<String, ByteArray>,
        parent: ClassLoader?,
    ): Map<String, ClassLoader> {
        val result = LinkedHashMap<String, ClassLoader>()
        imports.forEach { collect(it, entries, parent, result) }
        return result
    }

    private fun collect(
        script: CompiledScript,
        entries: Map<String, ByteArray>,
        parent: ClassLoader?,
        result: LinkedHashMap<String, ClassLoader>,
    ) {
        script.otherScripts.forEach { collect(it, entries, parent, result) }

        if (script.compilationConfiguration[ScriptCompilationConfiguration.isSharedScript] != true) return
        val name = (script as? KJvmCompiledScript)?.scriptClassFQName ?: return
        if (name in result) return

        // No stable identity means no safe sharing: better a private copy than a wrong one.
        val id = identityOf(script) ?: return
        val fingerprint = runCatching { ScriptFingerprint.compute(id) }.getOrNull() ?: return

        val existing = loaders[name]
        val entry = if (existing != null && existing.fingerprint == fingerprint) {
            existing
        } else {
            val classes = sharedArtifactEntries(id, fingerprint) ?: entries

            val delegates = result.toMap()
            Entry(fingerprint, MemoryClassLoader(classes, parent).apply { shareClassesOf(delegates) })
                .also { loaders[name] = it }
        }
        result[name] = entry.loader
    }

    private fun identityOf(script: KJvmCompiledScript): ScriptId? {
        val source = script.sourceLocationId?.let(::File)?.takeIf(File::isFile) ?: return null
        return ScriptRegistry.idOf(source)
    }

    private fun sharedArtifactEntries(id: ScriptId, fingerprint: String): Map<String, ByteArray>? {
        val artifact = ScriptCache.sharedArtifact(id)
        if (!ScriptCache.isValid(artifact, fingerprint)) return null
        return runCatching {
            JarFile(artifact).use { jar ->
                jar.entries().asSequence().associate { it.name to jar.getInputStream(it).readBytes() }
            }
        }.getOrNull()
    }

    /** The instance of a shared script, or `null` when it has not been evaluated yet. */
    @Synchronized
    fun instanceOf(scriptClass: KClass<*>): EvaluationResult? = instances[scriptClass]

    @Synchronized
    fun rememberInstance(scriptClass: KClass<*>, result: EvaluationResult) {
        instances[scriptClass] = result
    }

    @Synchronized
    fun clearInstances() {
        instances.clear()
    }
}