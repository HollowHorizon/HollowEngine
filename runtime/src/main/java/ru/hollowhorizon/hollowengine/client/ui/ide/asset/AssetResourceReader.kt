package ru.hollowhorizon.hollowengine.client.ui.ide.asset

import net.minecraft.server.packs.resources.ResourceManager
import ru.hollowhorizon.hollowengine.common.data.HollowEnginePack

internal fun ResourceManager.readAsset(
    scope: AssetResourceScope,
    file: AssetFile,
    original: Boolean = false,
): ByteArray {
    if (!original) {
        return getResource(file.location).orElseThrow().open().use { it.readAllBytes() }
    }

    val packs = listPacks().use { stream -> stream.toList() }
    for (pack in packs.asReversed()) {
        if (pack.packId() == HollowEnginePack.packId()) continue
        val supplier = pack.getResource(scope.packType, file.location) ?: continue
        return supplier.get().use { it.readAllBytes() }
    }
    error("No original resource exists below the HollowEngine override")
}
