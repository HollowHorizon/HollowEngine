package ru.hollowhorizon.hollowengine.common.scripting.source

import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonMappingNamespace
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonRuntimeEnvironment
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import java.io.File

/**
 * Scripts shipped inside the engine itself. There is no directory to walk because the engine jar is loaded
 * from wherever the bootstrap put it, so the build writes an index of the scripts it packaged and
 * everything is read back through the classloader. Maybe I'll someday extend it for use scripts in any mods.
 *
 * Unlike an addon jar, one engine jar serves every platform, so it carries a compiled artifact per
 * mapping namespace and the right one is picked here.
 */
class ClasspathScriptSource(
    override val namespace: String,
    override val classLoader: ClassLoader,
    override val fingerprint: String,
    private val variant: String = currentVariant(),
) : ScriptSource {
    private val paths: List<String> = readIndex()

    override val classpath: List<File> = emptyList()

    override val dependencies: List<String> = emptyList()

    fun isEmpty(): Boolean = paths.isEmpty()

    override fun list(): List<ScriptId> = paths.map { path -> ScriptId(namespace, path) }

    override fun read(id: ScriptId): ScriptArtifacts? {
        if (id.namespace != namespace || id.path !in paths) return null
        val sourceFile = extract("$SOURCE_PREFIX${id.path}", sourceCacheFile(id))
        val compiledFile = extract("$COMPILED_PREFIX$variant/${id.path}$COMPILED_SUFFIX", bundledCacheFile(id))
        if (sourceFile == null && compiledFile == null) return null
        return ScriptArtifacts(id, sourceFile = sourceFile, precompiled = compiledFile)
    }

    private fun readIndex(): List<String> = classLoader.getResourceAsStream(INDEX_PATH)?.use { input ->
        input.bufferedReader().readLines()
            .map { it.trim().replace('\\', '/') }
            .filter { it.isNotEmpty() && it.endsWith(SCRIPT_EXTENSION) }
            .distinct()
            .sorted()
    }.orEmpty()

    private fun sourceCacheFile(id: ScriptId): File =
        DirectoryManager.SCRIPT_SOURCE_CACHE.resolve(namespace).resolve(fingerprint).resolve(id.path)

    private fun bundledCacheFile(id: ScriptId): File =
        DirectoryManager.SCRIPT_BUNDLE_CACHE.resolve(namespace).resolve(fingerprint).resolve(variant)
            .resolve(id.path + COMPILED_SUFFIX)

    private fun extract(resource: String, target: File): File? {
        val bytes = classLoader.getResourceAsStream(resource)?.use { it.readBytes() } ?: return null
        if (target.isFile && target.length() == bytes.size.toLong()) return target
        return runCatching {
            target.parentFile.mkdirs()
            target.writeBytes(bytes)
            target
        }.onFailure { HollowEngine.LOGGER.error("Failed to extract '{}'", resource, it) }.getOrNull()
    }

    companion object {
        const val INDEX_PATH = "META-INF/hollowengine/scripts.index"
        const val SOURCE_PREFIX = "scripts/"
        const val COMPILED_PREFIX = "META-INF/hollowengine/scripts/"
        const val COMPILED_SUFFIX = ".jar"
        const val SCRIPT_EXTENSION = ".kts"

        /** Bytecode is remapped per namespace, so only one of the packaged variants can be used. */
        fun currentVariant(): String {
            val namespace = runCatching { HollowAddonRuntimeEnvironment.mappingNamespace() }
                .getOrDefault(HollowAddonMappingNamespace.NAMED)
            return if (namespace == HollowAddonMappingNamespace.INTERMEDIARY) "intermediary" else "named"
        }
    }
}
