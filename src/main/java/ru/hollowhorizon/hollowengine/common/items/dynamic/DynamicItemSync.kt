package ru.hollowhorizon.hollowengine.common.items.dynamic

import kotlinx.serialization.Serializable
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterReloadListenersEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.currentServer

object DynamicItemSync {
    private var cachedEntries: Map<String, String> = emptyMap()

    @SubscribeEvent
    fun onRegisterReload(event: RegisterReloadListenersEvent.Server) {
        event.register(ServerItemPrefabSyncReloader)
    }

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerEvent.Join) {
        val player = event.player as? ServerPlayer ?: return
        if (cachedEntries.isEmpty()) {
            cachedEntries = readEntriesFromDisk()
        }
        SyncItemPrefabsPacket.from(cachedEntries).send(player)
    }

    private object ServerItemPrefabSyncReloader : ResourceManagerReloadListener {
        override fun onResourceManagerReload(resourceManager: ResourceManager) {
            cachedEntries = readEntriesFromDisk()
            val server = try {
                currentServer
            } catch (e: UninitializedPropertyAccessException) {
                return
            }
            if (server.playerList.players.isEmpty()) return
            SyncItemPrefabsPacket.from(cachedEntries).send(server.playerList.players)
        }
    }

    private fun readEntriesFromDisk(): Map<String, String> {
        val prefabsRoot = DirectoryManager.HOLLOW_ENGINE.resolve("prefabs").toFile()
        if (!prefabsRoot.exists()) return emptyMap()

        val itemsRoot = prefabsRoot.resolve("items")
        val roots = listOf(itemsRoot.takeIf { it.exists() }, prefabsRoot).filterNotNull()

        return roots.flatMap { root ->
            root.walk()
                .filter { it.isFile }
                .filter { it.name.endsWith(".item.prefab") || it.name.endsWith(".item.json") || it.name.endsWith(".item.yml") || it.name.endsWith(".item.yaml") }
                .map { file ->
                    val relative = prefabsRoot.toPath().relativize(file.toPath()).toString().replace("\\", "/")
                    relative to file.readText()
                }
        }.toMap()
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class SyncItemPrefabsPacket(val entries: List<ItemPrefabEntry>) : HollowPacket {
    override fun handle(player: net.minecraft.world.entity.player.Player) {
        DynamicItemManager.applySync(entries.associate { it.path to it.content })
        try {
            val mc = net.minecraft.client.Minecraft.getInstance()
            mc.execute {
                HollowEngine.LOGGER.info("Syncing dynamic items: ${entries.size} entries.")
                runCatching {
                    mc.itemRenderer.itemModelShaper.rebuildCache()
                    HollowEngine.LOGGER.info("Rebuilt item model cache after dynamic item sync.")
                }.onFailure { e ->
                    HollowEngine.LOGGER.warn("Failed to rebuild item model cache after dynamic item sync.", e)
                }
                refreshCreativeTabsClient()
            }
        } catch (_: Exception) {
            // Ignore if running on a non-client environment.
        }
    }

    companion object {
        fun from(entries: Map<String, String>): SyncItemPrefabsPacket {
            return SyncItemPrefabsPacket(entries.map { ItemPrefabEntry(it.key, it.value) })
        }
    }
}

@Serializable
data class ItemPrefabEntry(val path: String, val content: String)

private fun refreshCreativeTabsClient() {
    val logger = HollowEngine.LOGGER
    val mc = runCatching { net.minecraft.client.Minecraft.getInstance() }.getOrNull() ?: return
    val level = mc.level ?: return
    val player = mc.player

    val flags = runCatching {
        level.javaClass.getMethod("enabledFeatures").invoke(level)
    }.getOrNull()
    val registryAccess = runCatching {
        level.javaClass.getMethod("registryAccess").invoke(level)
    }.getOrNull()
    val hasPermissions = player?.hasPermissions(2) ?: false

    val clazz = runCatching { Class.forName("net.minecraft.world.item.CreativeModeTabs") }.getOrNull()
    if (clazz == null) {
        logger.warn("CreativeModeTabs class not found; cannot refresh creative tab contents.")
        return
    }

    val method = clazz.methods.firstOrNull { it.name == "tryRebuildTabContents" || it.name == "rebuildTabContents" }
    if (method == null) {
        logger.warn("CreativeModeTabs rebuild method not found; creative tabs may require rejoin to update.")
        return
    }

    val params = method.parameterTypes
    val args = arrayOfNulls<Any>(params.size)
    for (i in params.indices) {
        val type = params[i]
        args[i] = when {
            type == java.lang.Boolean.TYPE || type == java.lang.Boolean::class.java -> hasPermissions
            type.name.endsWith("RegistryAccess") -> registryAccess
            type.name.endsWith("FeatureFlagSet") -> flags
            else -> null
        }
    }

    if (args.any { it == null }) {
        logger.warn("Unable to build args for creative tab rebuild (params=${params.joinToString { it.name }}).")
        return
    }

    runCatching {
        method.invoke(null, *args)
        logger.info("Creative tabs rebuilt after dynamic item sync.")
    }.onFailure { e ->
        logger.warn("Failed to rebuild creative tabs after dynamic item sync.", e)
    }
}
