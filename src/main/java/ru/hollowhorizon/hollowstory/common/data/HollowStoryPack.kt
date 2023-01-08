package ru.hollowhorizon.hollowstory.common.data

import com.google.gson.JsonObject
import net.minecraft.resources.IResourcePack
import net.minecraft.resources.ResourcePackType
import net.minecraft.resources.data.IMetadataSectionSerializer
import net.minecraft.util.ResourceLocation
import net.minecraft.util.ResourceLocationException
import net.minecraftforge.fml.loading.FMLPaths
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.HollowPack
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.function.Predicate

class HollowStoryPack : IResourcePack {
    val resourceMap: HashMap<ResourceLocation, HollowPack.IResourceStreamSupplier> =
        HashMap()

    override fun close() {
    }

    override fun getRootResource(filename: String): InputStream {
        throw FileNotFoundException(filename)
    }

    override fun getResource(type: ResourcePackType, location: ResourceLocation): InputStream {
        return this.resourceMap[location]?.create() ?: throw ResourceLocationException(location.toString())
    }

    override fun getResources(
        type: ResourcePackType, namespace: String, path: String, p_225637_4_: Int, predicate: Predicate<String>
    ): MutableCollection<ResourceLocation> {
        return this.resourceMap.entries
            .filter {
                it.key.namespace == namespace && it.key.path.startsWith(path) && predicate.test(it.key.path)
            }
            .map { it.key }
            .toMutableList()
    }

    override fun hasResource(type: ResourcePackType, location: ResourceLocation): Boolean {
        return resourceMap[location] != null && resourceMap[location]!!.exists()
    }

    override fun getNamespaces(type: ResourcePackType): MutableSet<String> {
        return resourceMap.keys.map { it.namespace }.toMutableSet()
    }

    override fun <T : Any?> getMetadataSection(deserializer: IMetadataSectionSerializer<T>): T? {
        if (deserializer.metadataSectionName == "pack") {
            val obj = JsonObject()
            obj.addProperty("pack_format", 6)
            obj.addProperty("description", "Generated resources for HollowCore")
            return deserializer.fromJson(obj)
        }
        return null
    }

    override fun getName(): String = "Hollow Story Resources"

    private fun init() {

        val file = FMLPaths.GAMEDIR.get().resolve("hollowstory").toFile()

        if (!file.exists()) {
            file.mkdirs()
        }

        processFile(file)
    }

    private fun processFile(file: File) {
        file.listFiles()?.forEach {
            if (it.isDirectory) {
                processFile(it)
            } else {

                val path = it.absolutePath.substring(
                    FMLPaths.GAMEDIR.get().resolve("hollowstory").toFile().absolutePath.length + 1
                )
                val resourceLocation = ResourceLocation("hsdata", path.replace("\\", "/"))
                HollowCore.LOGGER.info("Adding resource: $resourceLocation")
                resourceMap[resourceLocation] = ofFile(it)
            }
        }
    }

    private fun ofFile(file: File): HollowPack.IResourceStreamSupplier {
        return HollowPack.IResourceStreamSupplier.create({ file.isFile }) { FileInputStream(file) }
    }


    companion object {
        private var INSTANCE: HollowStoryPack? = null

        fun getPackInstance(): HollowStoryPack {
            if (INSTANCE == null) {
                INSTANCE = HollowStoryPack()
            }
            INSTANCE!!.init()
            return INSTANCE!!
        }
    }
}