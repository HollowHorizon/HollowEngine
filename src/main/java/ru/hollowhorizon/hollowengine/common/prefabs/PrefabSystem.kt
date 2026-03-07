package ru.hollowhorizon.hollowengine.common.prefabs

import com.mineinabyss.geary.prefabs.PrefabKey
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.api.ReloadListener
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization
import ru.hollowhorizon.hollowengine.common.geary.snapshot.PrefabDefinition
import java.io.File
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

@ReloadListener
object PrefabSystem : ResourceManagerReloadListener {
    val prefabs: File = DirectoryManager.HOLLOW_ENGINE.resolve("prefabs").toFile()

    private val definitions = ConcurrentHashMap<PrefabKey, PrefabDefinition>()
    private val pathToKey = ConcurrentHashMap<String, PrefabKey>()

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        definitions.clear()
        pathToKey.clear()
        if (!prefabs.exists()) return

        prefabs.walk()
            .filter { it.isFile && it.name.endsWith(".entity.prefab") }
            .forEach { file ->
                val key = keyForFile(file)
                val readablePath = toReadablePath(file)
                val definition = EntitySerialization.tryDeserializeFromYaml(
                    file.readText(),
                    "prefab $readablePath"
                ) ?: return@forEach
                definitions[key] = definition
                pathToKey[readablePath] = key
            }
    }

    fun definition(path: String): PrefabDefinition = definition(keyForPath(path))

    fun definition(key: PrefabKey): PrefabDefinition =
        definitions[key] ?: error("Unknown prefab: $key")

    fun definitionOrNull(path: String): PrefabDefinition? = definitionOrNull(keyForPath(path))

    fun definitionOrNull(key: PrefabKey): PrefabDefinition? = definitions[key]

    fun resolve(path: String): PrefabDefinition = resolve(keyForPath(path))

    fun resolve(key: PrefabKey): PrefabDefinition {
        val stack = ArrayDeque<PrefabKey>()
        return resolveInternal(key, stack)
    }

    fun resolveOrNull(path: String): PrefabDefinition? = resolveOrNull(keyForPath(path))

    fun resolveOrNull(key: PrefabKey): PrefabDefinition? = runCatching { resolve(key) }.onFailure { error ->
        HollowEngine.LOGGER.error("Failed to resolve prefab $key", error)
    }.getOrNull()

    fun keyForPath(path: String): PrefabKey =
        pathToKey[path.replace('\\', '/')] ?: keyForReadablePath(path)

    private fun resolveInternal(key: PrefabKey, stack: ArrayDeque<PrefabKey>): PrefabDefinition {
        check(!stack.contains(key)) { "Circular prefab dependency: ${(stack + key).joinToString(" -> ")}" }
        val local = definition(key)
        if (local.prefabRefs.isEmpty()) return local

        stack.addLast(key)
        val inherited = linkedMapOf<net.minecraft.resources.ResourceLocation, com.mineinabyss.geary.datatypes.Component>()
        val inheritedRefs = linkedSetOf<PrefabKey>()
        local.prefabRefs.forEach { ref ->
            val resolved = runCatching { resolveInternal(ref, stack) }.onFailure { error ->
                HollowEngine.LOGGER.error("Failed to resolve inherited prefab $ref for $key", error)
            }.getOrNull() ?: return@forEach
            inheritedRefs += resolved.prefabRefs
            inheritedRefs += ref
            resolved.componentById().forEach { (id, component) -> inherited[id] = component }
        }
        stack.removeLast()

        val merged = local.withResolvedPrefabs(inherited.values.toList())
        return merged.copy(prefabRefs = inheritedRefs + local.prefabRefs)
    }

    private fun keyForFile(file: File): PrefabKey {
        val relative = prefabs.toPath().relativize(file.toPath()).normalize().invariantSeparatorsPathString()
        val normalized = relative.removeSuffix(".entity.prefab")
        return PrefabKey.of(HollowEngine.MODID, normalized)
    }

    private fun keyForReadablePath(path: String): PrefabKey {
        val normalized = path.replace('\\', '/')
            .removePrefix("prefabs/")
            .removeSuffix(".entity.prefab")
        return PrefabKey.of(HollowEngine.MODID, normalized)
    }

    private fun toReadablePath(file: File): String =
        prefabs.toPath().relativize(file.toPath()).normalize().invariantSeparatorsPathString().let { "prefabs/$it" }
}

private fun Path.invariantSeparatorsPathString(): String = toString().replace('\\', '/')
