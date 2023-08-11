package ru.hollowhorizon.hollowengine.common.data

import com.google.gson.JsonObject
import net.minecraft.ResourceLocationException
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.MetadataSectionSerializer
import net.minecraftforge.fml.loading.FMLPaths
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.HollowPack
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.function.Predicate

class HollowStoryPack : PackResources {
    val resourceMap: HashMap<ResourceLocation, HollowPack.IResourceStreamSupplier> =
        HashMap()

    override fun close() {
    }

    override fun getRootResource(filename: String): InputStream {
        throw FileNotFoundException(filename)
    }

    override fun getResource(type: PackType, location: ResourceLocation): InputStream {
        return this.resourceMap[location]?.create() ?: throw ResourceLocationException(location.toString())
    }

    override fun getResources(
        type: PackType, namespace: String, path: String, predicate: Predicate<ResourceLocation>
    ): MutableCollection<ResourceLocation> {
        return this.resourceMap.entries
            .filter {
                it.key.namespace == namespace && it.key.path.startsWith(path) && predicate.test(it.key)
            }
            .map { it.key }
            .toMutableList()
    }

    override fun hasResource(type: PackType, location: ResourceLocation): Boolean {
        return resourceMap[location] != null && resourceMap[location]!!.exists()
    }

    override fun getNamespaces(type: PackType): MutableSet<String> {
        return resourceMap.keys.map { it.namespace }.toMutableSet()
    }

    override fun <T : Any?> getMetadataSection(deserializer: MetadataSectionSerializer<T>): T? {
        if (deserializer.metadataSectionName == "pack") {
            val obj = JsonObject()
            obj.addProperty("pack_format", 9)
            obj.addProperty("description", "Hollow Engine Generated Resources")
            return deserializer.fromJson(obj)
        }
        return null
    }

    override fun getName(): String = "Hollow Story Resources"

    private fun init() {

        val file = FMLPaths.GAMEDIR.get().resolve("hollowengine").toFile()

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
                    FMLPaths.GAMEDIR.get().resolve("hollowengine").toFile().absolutePath.length + 1
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