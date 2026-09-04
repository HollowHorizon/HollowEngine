package ru.hollowhorizon.hollowengine.common.scripting.nodes

import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptId
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry
import java.io.File
import java.util.concurrent.ConcurrentHashMap


object NodeAttachTargets {
    private val ATTACH = Regex("""@file\s*:\s*Attach\s*\(\s*([\w.]+)\s*::\s*class\s*\)""")
    private val IMPORT = Regex("""^\s*import\s+([\w.]+)""")
    private const val HEADER_LINES = 120
    private val cache = ConcurrentHashMap<String, CachedTarget>()

    private data class CachedTarget(val stamp: Long, val target: AttachTarget)

    sealed interface AttachTarget {
        data class Host(val fqName: String, val type: Class<*>?) : AttachTarget
        data object None : AttachTarget
        data object Unknown : AttachTarget
    }

    fun of(id: ScriptId): AttachTarget {
        val file = ScriptRegistry.artifacts(id)?.sourceFile ?: return AttachTarget.Unknown
        val stamp = runCatching { file.lastModified() }.getOrDefault(0L)
        val key = file.path
        cache[key]?.takeIf { it.stamp == stamp }?.let { return it.target }
        val target = read(file)
        cache[key] = CachedTarget(stamp, target)
        return target
    }

    fun accepts(id: ScriptId, entityType: Class<*>): Boolean = when (val target = of(id)) {
        is AttachTarget.Host -> target.type?.isAssignableFrom(entityType) ?: true
        AttachTarget.None -> false
        AttachTarget.Unknown -> true
    }

    private fun read(file: File): AttachTarget = runCatching {
        val header = file.useLines { lines -> lines.take(HEADER_LINES).toList() }
        val declared =
            header.firstNotNullOfOrNull { ATTACH.find(it)?.groupValues?.get(1) } ?: return@runCatching AttachTarget.None
        AttachTarget.Host(declared, resolve(declared, header))
    }.getOrDefault(AttachTarget.Unknown)

    private fun resolve(name: String, header: List<String>): Class<*>? {
        if ('.' in name) return classOrNull(name)
        val imported = header.firstNotNullOfOrNull { line ->
            IMPORT.find(line)?.groupValues?.get(1)?.takeIf { it.substringAfterLast('.') == name }
        }
        return imported?.let(::classOrNull)
    }

    private fun classOrNull(fqName: String): Class<*>? =
        runCatching { Class.forName(fqName, false, NodeAttachTargets::class.java.classLoader) }.getOrNull()
}
