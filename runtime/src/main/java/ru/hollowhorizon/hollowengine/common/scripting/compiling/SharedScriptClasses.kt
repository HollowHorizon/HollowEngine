package ru.hollowhorizon.hollowengine.common.scripting.compiling

import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptFingerprint
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry
import java.io.File
import kotlin.reflect.KClass
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript

/**
 * The classes of `@file:SharedScript` scripts, defined once for the whole game.
 *
 * An imported script is compiled into the module of every script that imports it, so each importer's
 * jar carries its own copy of the class. Without this registry two importers would end up with two
 * unrelated classes for one file, and `@file:SharedScript` could only ever share an instance inside a
 * single import graph - never between, say, a `.reload.kts` and a `.node.kts`.
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

        val fingerprint = fingerprintOf(script) ?: return

        val existing = loaders[name]
        val entry = if (existing != null && existing.fingerprint == fingerprint) {
            existing
        } else {
            val delegates = result.toMap()
            Entry(fingerprint, MemoryClassLoader(entries, parent).apply { shareClassesOf(delegates) })
                .also { loaders[name] = it }
        }
        result[name] = entry.loader
    }

    private fun fingerprintOf(script: KJvmCompiledScript): String? {
        val source = script.sourceLocationId?.let(::File)?.takeIf(File::isFile) ?: return null
        val id = ScriptRegistry.idOf(source) ?: return null
        return runCatching { ScriptFingerprint.compute(id) }.getOrNull()
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