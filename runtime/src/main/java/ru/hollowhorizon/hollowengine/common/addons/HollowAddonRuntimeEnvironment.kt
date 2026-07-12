package ru.hollowhorizon.hollowengine.common.addons

import net.fabricmc.loader.api.FabricLoader
import ru.hollowhorizon.hollowengine.common.utils.isPhysicalClient

internal object HollowAddonRuntimeEnvironment {
    val isClient: Boolean
        get() = isPhysicalClient

    fun mappingNamespace(): HollowAddonMappingNamespace {
        val runtimeNamespace = try {
            FabricLoader.getInstance().mappingResolver.currentRuntimeNamespace
        } catch (_: LinkageError) {
            return HollowAddonMappingNamespace.OFFICIAL
        }
        return resolveMappingNamespace(runtimeNamespace)
    }

    internal fun resolveMappingNamespace(runtimeNamespace: String): HollowAddonMappingNamespace =
        HollowAddonMappingNamespace.entries.firstOrNull { namespace -> namespace.id == runtimeNamespace }
            ?: error("Unsupported runtime mapping namespace '$runtimeNamespace'")
}
