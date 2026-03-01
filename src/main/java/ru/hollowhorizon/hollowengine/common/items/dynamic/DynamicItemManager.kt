package ru.hollowhorizon.hollowengine.common.items.dynamic

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity
import org.apache.logging.log4j.LogManager
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.api.ReloadListener
import ru.hollowhorizon.hollowengine.client.utils.HollowPack
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.item.BuildTabContentsEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.registry.AutoModelType
import ru.hollowhorizon.hollowengine.common.registry.extend.HollowDynamicRegistry
import ru.hollowhorizon.hollowengine.common.utils.Side
import ru.hollowhorizon.hollowengine.common.utils.isPhysicalClient
import ru.hollowhorizon.hollowengine.common.utils.json.JsonFormat
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.common.utils.yaml.YamlFormat
import java.io.File

@ReloadListener(Side.SERVER)
object DynamicItemManager : ResourceManagerReloadListener {
    private val logger = LogManager.getLogger()
    private var syncedEntries: Map<String, String>? = null
    private val tabItems: MutableMap<ResourceLocation, MutableList<Item>> = HashMap()

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        val sync = syncedEntries
        if (isPhysicalClient && sync != null) {
            loadEntries(sync)
            return
        }

        val prefabsRoot = DirectoryManager.HOLLOW_ENGINE.resolve("prefabs").toFile()
        if (!prefabsRoot.exists()) return
        loadFromDisk(prefabsRoot)
    }

    fun applySync(entries: Map<String, String>) {
        syncedEntries = entries
        if (isPhysicalClient) {
            loadEntries(entries)
        }
    }

    private fun loadFromDisk(prefabsRoot: File) {
        val registry = BuiltInRegistries.ITEM as? HollowDynamicRegistry
        if (registry == null) {
            logger.warn("Dynamic item registry is not available. Items will not be loaded.")
            return
        }

        registry.clearDynamic()
        tabItems.clear()

        val itemsRoot = prefabsRoot.resolve("items")
        val roots = listOf(itemsRoot.takeIf { it.exists() }, prefabsRoot).filterNotNull()

        roots.forEach { root ->
            root.walk()
                .filter { it.isFile }
                .filter { it.name.endsWith(".item.prefab") || it.name.endsWith(".item.json") || it.name.endsWith(".item.yml") || it.name.endsWith(".item.yaml") }
                .forEach { file ->
                    runCatching {
                        val relative = prefabsRoot.toPath().relativize(file.toPath()).toString().replace("\\", "/")
                        loadItem(relative, file.readText())
                    }.onFailure { e ->
                        logger.error("Failed to load item prefab: ${file.path}", e)
                    }
                }
        }
    }

    private fun loadEntries(entries: Map<String, String>) {
        val registry = BuiltInRegistries.ITEM as? HollowDynamicRegistry
        if (registry == null) {
            logger.warn("Dynamic item registry is not available. Items will not be loaded.")
            return
        }

        registry.clearDynamic()
        tabItems.clear()

        entries.forEach { (path, content) ->
            runCatching { loadItem(path, content) }
                .onFailure { e -> logger.error("Failed to load item prefab: $path", e) }
        }
    }

    private fun loadItem(path: String, content: String) {
        val prefab = readPrefab(path, content)
        val id = resolveId(prefab, path)

        if (BuiltInRegistries.ITEM.containsKey(id)) {
            logger.warn("Item id already exists, skipping: $id")
            return
        }

        val properties = Item.Properties()

        when {
            prefab.maxDamage != null -> properties.durability(prefab.maxDamage)
            prefab.maxStack != null -> properties.stacksTo(prefab.maxStack)
        }

        prefab.rarity?.let { properties.rarity(parseRarity(it)) }
        if (prefab.fireResistant) properties.fireResistant()

        val creativeTab = prefab.tab?.let { BuiltInRegistries.CREATIVE_MODE_TAB.get(it.rl) }
        if (prefab.tab != null && creativeTab == null) {
            logger.warn("Unknown creative tab '${prefab.tab}' for item $id")
        }

        val item = DynamicItem(properties)

        Registry.register(BuiltInRegistries.ITEM, id, item)
        logger.info("Dynamic item registered: $id (tab=${prefab.tab ?: "none"})")

        if (prefab.tab != null && creativeTab != null) {
            tabItems.getOrPut(prefab.tab.rl) { ArrayList() }.add(item)
        }

        if (isPhysicalClient) {
            registerItemModelLocation(item, id)
            registerModel(id, prefab)
        }
    }

    private fun readPrefab(path: String, content: String): ItemPrefab {
        return when {
            path.endsWith(".json") -> JsonFormat.decodeFromString(content)
            else -> YamlFormat.decodeFromString(ItemPrefab.serializer(), content)
        }
    }

    private fun resolveId(prefab: ItemPrefab, path: String): ResourceLocation {
        val explicit = prefab.id?.trim()?.takeIf { it.isNotEmpty() }
        if (explicit != null) {
            return if (explicit.contains(':')) explicit.rl else "${HollowEngine.MODID}:$explicit".rl
        }

        val normalized = path.replace("\\", "/")
        val relative = when {
            normalized.startsWith("prefabs/items/") -> normalized.removePrefix("prefabs/items/")
            normalized.startsWith("prefabs/") -> normalized.removePrefix("prefabs/")
            normalized.startsWith("items/") -> normalized.removePrefix("items/")
            else -> normalized
        }
        val baseName = relative
            .removeSuffix(".item.prefab")
            .removeSuffix(".item.json")
            .removeSuffix(".item.yml")
            .removeSuffix(".item.yaml")

        return "${HollowEngine.MODID}:$baseName".rl
    }

    private fun parseRarity(value: String): Rarity {
        return when (value.lowercase()) {
            "common" -> Rarity.COMMON
            "uncommon" -> Rarity.UNCOMMON
            "rare" -> Rarity.RARE
            "epic" -> Rarity.EPIC
            else -> {
                logger.warn("Unknown rarity '$value', using COMMON")
                Rarity.COMMON
            }
        }
    }

    private fun registerModel(id: ResourceLocation, prefab: ItemPrefab) {
        val json = prefab.modelJson?.trim()?.takeIf { it.isNotEmpty() }
        if (json != null) {
            logger.info("Dynamic item model: $id -> custom json")
            HollowPack.addCustomItemModel(id, json)
            return
        }

        val parent = prefab.modelParent?.trim()?.takeIf { it.isNotEmpty() }
        val model = prefab.model?.trim()?.takeIf { it.isNotEmpty() }
        val texture = prefab.modelTexture?.trim()?.takeIf { it.isNotEmpty() }
            ?: "${id.namespace}:item/${id.path}"

        if (model == null && parent != null) {
            logger.info("Dynamic item model: $id -> parent=$parent texture=$texture")
            HollowPack.addCustomItemModel(
                id,
                "{\"parent\":\"$parent\",\"textures\":{\"layer0\":\"$texture\"}}"
            )
            return
        }

        when (model?.lowercase()) {
            null, "default", "generated" -> {
                if (prefab.modelTexture != null) {
                    logger.info("Dynamic item model: $id -> generated texture=$texture")
                    HollowPack.addCustomItemModel(
                        id,
                        "{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"$texture\"}}"
                    )
                } else {
                    logger.info("Dynamic item model: $id -> auto generated")
                    HollowPack.addItemModel(id, AutoModelType.DEFAULT)
                }
            }
            "handheld" -> {
                if (prefab.modelTexture != null) {
                    logger.info("Dynamic item model: $id -> handheld texture=$texture")
                    HollowPack.addCustomItemModel(
                        id,
                        "{\"parent\":\"item/handheld\",\"textures\":{\"layer0\":\"$texture\"}}"
                    )
                } else {
                    logger.info("Dynamic item model: $id -> auto handheld")
                    HollowPack.addItemModel(id, AutoModelType.HANDHELD)
                }
            }
            else -> {
                val parentId = parent ?: model
                logger.info("Dynamic item model: $id -> parent=$parentId texture=$texture")
                HollowPack.addCustomItemModel(
                    id,
                    "{\"parent\":\"$parentId\",\"textures\":{\"layer0\":\"$texture\"}}"
                )
            }
        }
    }

    private fun registerItemModelLocation(item: Item, id: ResourceLocation) {
        try {
            val shaper = Minecraft.getInstance().itemRenderer.itemModelShaper
            val modelLocation = ModelResourceLocation(id, "inventory")
            shaper.register(item, modelLocation)
            logger.info("Dynamic item shaper: $id -> $modelLocation")
        } catch (e: Exception) {
            logger.warn("Failed to register item model shaper for $id", e)
        }
    }

    @SubscribeEvent
    fun onBuildTabContents(event: BuildTabContentsEvent) {
        val items = tabItems[event.tabKey.location()] ?: return
        items.forEach { event.accept(it) }
    }
}
