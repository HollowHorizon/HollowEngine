package ru.hollowhorizon.hollowengine.common.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.Util
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
//? if >=1.21 {

/*import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.repository.PackSource
import ru.hollowhorizon.hc.client.utils.mcText
import java.util.*

*///?}
//? if >=1.20.1 {
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.metadata.MetadataSectionSerializer
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.IoSupplier
import ru.hollowhorizon.hc.common.utils.rl
import ru.hollowhorizon.hc.common.utils.literal
//?}
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterResourcePacksEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.structure.StructureBiomes
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.HashMap

object HollowEnginePack : PathPackResources("HollowEngine Folder Resources", DirectoryManager.HOLLOW_ENGINE, true) {
    private val packMetadata: String = JsonObject().apply {
        add("pack", JsonObject().apply {
            addProperty("description", "HollowEngine Folder Resources")
            addProperty("pack_format", 9)
        })
    }.toString()

    override fun getRootResource(vararg strings: String): IoSupplier<InputStream>? {
        return when (strings[0]) {
            PACK_META -> IoSupplier { packMetadata.byteInputStream() }
            else -> super.getRootResource(*strings)
        }
    }
}

object HollowEngineCorePack: PackResources {
    private val resourceMap = HashMap<ResourceLocation, IoSupplier<InputStream>?>()

    init {
        close()
    }

    private fun ofText(text: String) = IoSupplier<InputStream> { ByteArrayInputStream(text.toByteArray()) }

    fun addCustomJSON(resourcePath: ResourceLocation, content: String) {
        resourceMap[resourcePath] = ofText(content)
    }

    fun addHasBiomeTag(resourcePath: ResourceLocation, vararg biomes: String) {
        val tag = buildString {
            append("{").append('"').append("replace").append('"').append(":false,")
            append('"').append("values").append('"').append(":[")
            biomes.forEachIndexed { i, it -> append('"').append(it).append('"').also { if (i != biomes.size - 1) append(",") } }
            append("]}")
        }

        addCustomJSON("${resourcePath.namespace}:tags/worldgen/biome/has_structure/${resourcePath.path}.json".rl, tag)
    }

    override fun getRootResource(vararg elements: String?): IoSupplier<InputStream> = throw FileNotFoundException(elements.joinToString())

    override fun getResource(packType: PackType, location: ResourceLocation): IoSupplier<InputStream>? =
        resourceMap[location]

    override fun listResources(
        packType: PackType,
        namespace: String,
        path: String,
        resourceOutput: PackResources.ResourceOutput
    ) {
        resourceMap.filter { it.key.namespace == namespace && it.key.path.startsWith(path) }.forEach(resourceOutput::accept)
    }

    override fun getNamespaces(type: PackType) = resourceMap.keys.map { it.namespace }.toSet()

    override fun <T : Any?> getMetadataSection(deserializer: MetadataSectionSerializer<T>): T? {
        if (deserializer.metadataSectionName == "pack") {
            val obj = JsonObject()
            val sf = JsonArray()
            (6..9).forEach(sf::add)
            obj.addProperty("pack_format", 9)
            obj.add("supported_formats", sf)
            obj.addProperty("description", "Generated resources for HollowEngine")
            return deserializer.fromJson(obj)
        }

        return null
    }

    override fun packId(): String = "HollowEngine Core Pack"

    override fun close() {}

    val resources = Pack.readMetaAndCreate(
        packId(), packId().literal, true, { this }, PackType.SERVER_DATA, Pack.Position.TOP, PackSource.BUILT_IN
    )
}

//?} else {
/*import net.minecraft.server.packs.FolderPackResources
import java.io.FileNotFoundException

object HollowEnginePack : FolderPackResources(DirectoryManager.HOLLOW_ENGINE.toFile()) {
    private val packMetadata = Util.make(JsonObject()) { json ->
        json.add("pack", JsonObject().apply {
            addProperty("description", "HollowEngine Folder Resources")
            addProperty("pack_format", 9)
        })
    }.toString()

    override fun getRootResource(fileName: String): InputStream {
        return when (fileName) {
            PACK_META -> packMetadata.byteInputStream()
            else -> super.getRootResource(fileName) ?: throw FileNotFoundException("$fileName not found")
        }
    }
}
*///?}

@SubscribeEvent
fun addPackListeners(event: RegisterResourcePacksEvent) {
    event.addPack(HollowEnginePack)
    event.addPack(HollowEngineCorePack)
}