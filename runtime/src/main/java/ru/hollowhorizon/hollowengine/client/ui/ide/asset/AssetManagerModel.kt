package ru.hollowhorizon.hollowengine.client.ui.ide.asset

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import ru.hollowhorizon.hollowengine.common.utils.listPackResources

internal enum class AssetResourceScope(
    val packType: PackType,
    val directory: String,
    val labelKey: String,
) {
    CLIENT(PackType.CLIENT_RESOURCES, "assets", AssetManagerLang.CLIENT_RESOURCES),
    SERVER(PackType.SERVER_DATA, "data", AssetManagerLang.SERVER_RESOURCES),
}

internal data class AssetFile(
    val location: ResourceLocation,
    val sourcePackId: String,
) {
    val name: String get() = location.path.substringAfterLast('/')
    val directoryPath: String get() = location.path.substringBeforeLast('/', "")
}

internal data class AssetDirectory(
    val namespace: String,
    val path: String,
) {
    val key: String get() = "$namespace:$path"
    val name: String get() = path.substringAfterLast('/').ifEmpty { namespace }
    val depth: Int get() = if (path.isEmpty()) 0 else path.count { it == '/' } + 1
}

internal sealed interface AssetGridEntry {
    val name: String

    data class Directory(val directory: AssetDirectory) : AssetGridEntry {
        override val name: String get() = directory.name
    }

    data class File(val file: AssetFile) : AssetGridEntry {
        override val name: String get() = file.name
    }
}

internal class AssetIndex(
    files: Collection<AssetFile>,
    explicitDirectories: Collection<AssetDirectory> = emptyList(),
) {
    private val directoriesByKey: Map<String, AssetDirectory>
    private val childDirectories: Map<String, List<AssetDirectory>>
    private val filesByDirectory: Map<String, List<AssetFile>>

    val files: List<AssetFile> = files.sortedWith(
        compareBy<AssetFile>({ it.location.namespace }, { it.location.path }),
    )
    val directoryKeys: Set<String>
    val namespaceCount: Int
    val rootDirectories: List<AssetDirectory>

    init {
        val directories = linkedSetOf<AssetDirectory>()
        explicitDirectories.forEach { directory ->
            directories += AssetDirectory(directory.namespace, "")
            directory.ancestorKeys().mapNotNull { key ->
                key.substringAfter(':').let { path -> AssetDirectory(directory.namespace, path) }
            }.forEach(directories::add)
        }
        this.files.forEach { file ->
            directories += AssetDirectory(file.location.namespace, "")
            var current = ""
            file.directoryPath.split('/').filter(String::isNotEmpty).forEach { segment ->
                current = if (current.isEmpty()) segment else "$current/$segment"
                directories += AssetDirectory(file.location.namespace, current)
            }
        }

        directoriesByKey = directories.associateBy(AssetDirectory::key)
        directoryKeys = directoriesByKey.keys
        rootDirectories = directories.filter { it.path.isEmpty() }.sortedBy { it.namespace }
        namespaceCount = rootDirectories.size
        childDirectories = directories.filter { it.path.isNotEmpty() }
            .groupBy { directory -> AssetDirectory(directory.namespace, directory.path.substringBeforeLast('/', "")).key }
            .mapValues { (_, children) -> children.sortedBy { it.name.lowercase() } }
        filesByDirectory = this.files.groupBy { file -> AssetDirectory(file.location.namespace, file.directoryPath).key }
            .mapValues { (_, children) -> children.sortedBy { it.name.lowercase() } }
    }

    fun directory(key: String?): AssetDirectory? = key?.let(directoriesByKey::get)

    fun hasChildDirectories(directory: AssetDirectory): Boolean = childDirectories[directory.key].orEmpty().isNotEmpty()

    fun visibleDirectories(expandedKeys: Set<String>, filter: String = ""): List<AssetDirectory> = buildList {
        val cleanFilter = filter.trim().lowercase()
        val visibleKeys = if (cleanFilter.isEmpty()) {
            directoryKeys
        } else {
            directoriesByKey.values.asSequence()
                .filter { directory ->
                    directory.name.lowercase().contains(cleanFilter) ||
                            directory.key.lowercase().contains(cleanFilter)
                }
                .flatMap(AssetDirectory::ancestorKeys)
                .toSet()
        }

        fun append(directory: AssetDirectory) {
            if (directory.key !in visibleKeys) return
            add(directory)
            if (cleanFilter.isEmpty() && directory.key !in expandedKeys) return
            childDirectories[directory.key].orEmpty().forEach(::append)
        }
        rootDirectories.forEach(::append)
    }

    fun children(directory: AssetDirectory): List<AssetGridEntry> = buildList {
        childDirectories[directory.key].orEmpty().forEach { add(AssetGridEntry.Directory(it)) }
        filesByDirectory[directory.key].orEmpty().forEach { add(AssetGridEntry.File(it)) }
    }

    companion object {
        val Empty = AssetIndex(emptyList())

        fun load(resourceManager: ResourceManager, packType: PackType): AssetIndex = AssetIndex(
            resourceManager.listPackResources(packType).map { resource ->
                AssetFile(resource.location, resource.sourcePackId)
            },
        )
    }
}

internal fun AssetDirectory.ancestorKeys(): Sequence<String> = sequence {
    var current = path
    while (true) {
        yield(AssetDirectory(namespace, current).key)
        if (current.isEmpty()) break
        current = current.substringBeforeLast('/', "")
    }
}
