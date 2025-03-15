package ru.hollowhorizon.hollowengine.common.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.Util
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType

import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.metadata.MetadataSectionSerializer
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.IoSupplier
import ru.hollowhorizon.hc.common.utils.rl
import ru.hollowhorizon.hc.common.utils.literal
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
        addCustomJSON("${resourcePath.namespace}:tags/worldgen/biome/has_structure/${resourcePath.path}.json".rl, biomes.noReplaceTagBuild)
    }

    fun addCatsSpawnAsBlackTag(vararg biomes: String) {
        addCustomJSON("tags/worldgen/structure/cats_spawn_as_black.json".rl, biomes.noReplaceTagBuild)
    }

    fun addCatsSpawnInTag(vararg biomes: String) {
        addCustomJSON("tags/worldgen/structure/cats_spawn_in.json".rl, biomes.noReplaceTagBuild)
    }

    fun addDolphinLocatedTag(vararg biomes: String) {
        addCustomJSON("tags/worldgen/structure/dolphin_located.json".rl, biomes.noReplaceTagBuild)
    }

    fun addEyeOfEnderLocatedTag(vararg biomes: String) {
        addCustomJSON("tags/worldgen/structure/eye_of_ender_located.json".rl, biomes.noReplaceTagBuild)
    }

    fun addMineshaftTag(vararg biomes: String) {
        addCustomJSON("tags/worldgen/structure/mineshaft.json".rl, biomes.noReplaceTagBuild)
    }

    fun addOceanRuinTag(vararg biomes: String) {
        addCustomJSON("tags/worldgen/structure/ocean_ruin.json".rl, biomes.noReplaceTagBuild)
    }

    fun addOnOceanExplorerMapsTag(vararg biomes: String) {
        addCustomJSON("tags/worldgen/structure/on_ocean_explorer_maps.json".rl, biomes.noReplaceTagBuild)
    }

    fun addOnTreasureMapsTag(vararg biomes: String) {
        addCustomJSON("tags/worldgen/structure/on_treasure_maps.json".rl, biomes.noReplaceTagBuild)
    }

    fun addOnWoodlandExplorerMapsTag(vararg biomes: String) {
        addCustomJSON("tags/worldgen/structure/on_woodland_explorer_maps.json".rl, biomes.noReplaceTagBuild)
    }

    fun addRuinedPortalTag(vararg biomes: String) {
        addCustomJSON("tags/worldgen/structure/ruined_portal.json".rl, biomes.noReplaceTagBuild)
    }

    fun addShipwreckTag(vararg biomes: String) {
        addCustomJSON("tags/worldgen/structure/shipwreck.json".rl, biomes.noReplaceTagBuild)
    }

    fun addVillageTag(vararg biomes: String) {
        addCustomJSON("tags/worldgen/structure/village.json".rl, biomes.noReplaceTagBuild)
    }

    fun addProcessorList(resourcePath: ResourceLocation, vararg processors: String) {
        val processorBuilder = buildString {
            append("{").append('"').append("processors").append('"').append(":[")
            processors.forEachIndexed { i, it -> append('"').append(it).append('"').also { if (i != processors.size - 1) append(",") } }
            append("]}")
        }

        addCustomJSON("${resourcePath.namespace}:worldgen/processor_list/${resourcePath.path}.json".rl, processorBuilder)
    }

    fun addStructure(resourcePath: ResourceLocation, structure: String) {
        addCustomJSON("${resourcePath.namespace}:worldgen/structure/${resourcePath.path}.json".rl, structure)
    }

    fun addStructureSet(resourcePath: ResourceLocation, sets: String) {
        addCustomJSON("${resourcePath.namespace}:worldgen/structure_set/${resourcePath.path}.json".rl, sets)
    }

    fun addTemplatePool(resourcePath: ResourceLocation, pool: String) {
        addCustomJSON("${resourcePath.namespace}:worldgen/template_pool/${resourcePath.path}.json".rl, pool)
    }

    private val Array<out String>.noReplaceTagBuild: String
        get() = buildString {
            append("{").append('"').append("replace").append('"').append(":false,")
            append('"').append("values").append('"').append(":[")
            forEachIndexed { i, it -> append('"').append(it).append('"').also { if (i != size - 1) append(",") } }
            append("]}")
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

@SubscribeEvent
fun addPackListeners(event: RegisterResourcePacksEvent) {
    event.addPack(HollowEnginePack)
    event.addPack(HollowEngineCorePack)
}