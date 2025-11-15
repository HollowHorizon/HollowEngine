package ru.hollowhorizon.hollowengine.common.scripting.codegen.asset

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import ru.hollowhorizon.hollowengine.common.scripting.codegen.generateFieldName

open class AssetFile(val location: ResourceLocation) {
    open fun getBytes(manager: ResourceManager): ByteArray? {
        return manager.getResource(location).orElse(null)?.open()?.readAllBytes()
    }

    open fun getStream(manager: ResourceManager) = manager.getResource(location).orElse(null)?.open()
}

class FileAssetGenerator : AssetGenerator<AssetFile> {
    override val fileExtensions = listOf("*")

    override fun generate(manager: ResourceManager, location: ResourceLocation): AssetFile {
        return AssetFile(location)
    }

    override fun generateCode(location: ResourceLocation, generated: AssetFile): String {
        val name = generateFieldName(location)
        return "val $name = AssetFile(\"$location\")"
    }
}