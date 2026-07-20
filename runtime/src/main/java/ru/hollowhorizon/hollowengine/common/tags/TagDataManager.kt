@file:UseSerializers(ForResourceLocation::class)
package ru.hollowhorizon.hollowengine.common.tags

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterTagsEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.network.SSyncTagDataPacket
import ru.hollowhorizon.hollowengine.common.utils.PlayerPermissions
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hollowengine.common.utils.yaml.YamlFormat
import java.io.File

object TagDataManager {
    private val blockChanges = HashMap<ResourceLocation, MutableSet<ResourceLocation>>()
    private val itemChanges = HashMap<ResourceLocation, MutableSet<ResourceLocation>>()
    private val deletedTags = HashSet<ResourceLocation>()

    private val folder = DirectoryManager.HOLLOW_ENGINE.resolve("tags").toFile().apply { if (!exists()) mkdirs() }
    private val configFile = File(folder, "tag_changes.yml")

    init {
        load()
    }

    fun addEntry(tag: ResourceLocation, entry: ResourceLocation, type: String) {
        val map = if (type == "BLOCK") blockChanges else itemChanges
        map.getOrPut(tag) { HashSet() }.add(entry)
    }

    fun removeEntry(tag: ResourceLocation, entry: ResourceLocation, type: String) {
        val map = if (type == "BLOCK") blockChanges else itemChanges
        map[tag]?.remove(entry)
    }

    fun deleteTag(tag: ResourceLocation, type: String) {
        deletedTags.add(tag)
    }

    fun restoreTag(tag: ResourceLocation, type: String) {
        deletedTags.remove(tag)
    }

    fun createTag(tag: ResourceLocation, type: String) {
        val map = if (type == "BLOCK") blockChanges else itemChanges
        map.getOrPut(tag) { HashSet() }
    }

    @Serializable
    class TagStorage(
        val blockChanges: Map<ResourceLocation, Set<ResourceLocation>>,
        val itemChanges: Map<ResourceLocation, Set<ResourceLocation>>,
        val deletedTags: Set<ResourceLocation>,
    )

    fun save() {
        configFile.writeText(YamlFormat.encodeToString(TagStorage(blockChanges, itemChanges, deletedTags)))
    }

    @Suppress("UNCHECKED_CAST")
    fun load() {
        if (!configFile.exists()) return
        try {
            val data = YamlFormat.decodeFromString<TagStorage>(configFile.readText())
            data.blockChanges.forEach { (k, v) ->
                blockChanges[k] = v.toMutableSet()
            }
            data.itemChanges.forEach { (k, v) ->
                itemChanges[k] = v.toMutableSet()
            }
            data.deletedTags.forEach { k ->
                deletedTags.add(k)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun syncAll() {
        val server = currentServer ?: return
        server.playerList.players.filter { it.hasPermissions(PlayerPermissions.GAMEMASTER) }.forEach { player ->
            SSyncTagDataPacket(blockChanges, "BLOCK").send(player)
            SSyncTagDataPacket(itemChanges, "ITEM").send(player)
        }
    }

    @SubscribeEvent
    fun onRegisterTags(event: RegisterTagsEvent) {
        val changes = when (event.registry.key()) {
            Registries.BLOCK -> blockChanges
            Registries.ITEM -> itemChanges
            else -> return
        }

        changes.forEach { (tagLoc, entries) ->
            if (deletedTags.contains(tagLoc)) return@forEach

            // Here we would ideally clear and replace, but the event only allows adding/removing
            // We apply our overrides
            entries.forEach { entryLoc ->
                val value = (event.registry as net.minecraft.core.Registry<Any>).get(entryLoc)
                if (value != null) event.addToTag(net.minecraft.tags.TagKey.create(event.registry.key() as net.minecraft.resources.ResourceKey<out net.minecraft.core.Registry<Any>>, tagLoc), value)
            }
        }
    }
}