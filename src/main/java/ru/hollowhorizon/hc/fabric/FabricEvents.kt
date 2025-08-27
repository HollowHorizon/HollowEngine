//? if fabric {
package ru.hollowhorizon.hc.fabric

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.packs.PackType
import ru.hollowhorizon.hc.common.utils.currentServer
import ru.hollowhorizon.hc.common.events.EventBus
import ru.hollowhorizon.hc.common.events.EventBus.post
import ru.hollowhorizon.hc.common.events.blocks.BlockEvent
import ru.hollowhorizon.hc.common.events.entity.EntityTrackingEvent
import ru.hollowhorizon.hc.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hc.common.events.item.BuildTabContentsEvent
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hc.common.events.registry.RegisterCommandsEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterEntityAttributesEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterReloadListenersEvent
import ru.hollowhorizon.hc.common.events.server.ServerEvent
import ru.hollowhorizon.hc.common.events.tick.TickEvent
import ru.hollowhorizon.hc.fabric.internal.DelegatedReloadListener

object FabricEvents {
    init {
        registerReloadListeners()
        registerAttributes()
        registerCommands()
        onEntityTracking()
        onPlayerEvents()
        onServerEvents()
        onTabModify()
    }

    private fun onServerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick { s ->
            post(TickEvent.Server(s))
        })
        ServerLifecycleEvents.SERVER_STARTING.register(ServerLifecycleEvents.ServerStarting {
            currentServer = it
            ServerEvent.Starting(currentServer).post()
        })
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerLifecycleEvents.ServerStopping {
            ServerEvent.Stoping(it).post()
        })
        PlayerBlockBreakEvents.BEFORE.register(PlayerBlockBreakEvents.Before { world, player, pos, state, blockEntity ->
            val event = BlockEvent.Break(world, pos, state, player)
            event.post()
            !event.isCanceled
        })
    }

    private fun registerReloadListeners() {
        val event = RegisterReloadListenersEvent.Server()
        EventBus.post(event)
        val helper = ResourceManagerHelper.get(PackType.SERVER_DATA)

        event.listeners.forEach {
            helper.registerReloadListener(DelegatedReloadListener(it))
        }
    }

    private fun registerAttributes() {
        val attributes = RegisterEntityAttributesEvent()
        EventBus.post(attributes)
        attributes.getAttributes().forEach(FabricDefaultAttributeRegistry::register)
    }

    private fun registerCommands() {
        CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback { dispatcher, registryAccess, environment ->
            RegisterCommandsEvent(dispatcher, registryAccess, environment).post()
        })
    }

    private fun onEntityTracking() {
        EntityTrackingEvents.START_TRACKING.register { entity, player ->
            EntityTrackingEvent(player, entity).post()
        }
    }

    private fun onPlayerEvents() {
        ServerPlayConnectionEvents.JOIN.register { handler, sender, server ->
            PlayerEvent.Join(handler.player).post()
        }
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register { player, from, to ->
            PlayerEvent.ChangeDimension(player, from, to).post()
        }
    }

    private fun onTabModify() {
        ItemGroupEvents.MODIFY_ENTRIES_ALL.register { tab, entries ->
            BuildTabContentsEvent(
                tab,
                BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(tab).orElseThrow { IllegalStateException("Unregistered creative mode tab: $tab") },
                entries.context,
                entries::accept
            ).post()
        }
    }
}
//?}