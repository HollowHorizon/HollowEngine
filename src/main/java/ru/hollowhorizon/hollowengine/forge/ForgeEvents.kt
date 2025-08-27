package ru.hollowhorizon.hollowengine.forge//? if forge {
/*package ru.hollowhorizon.hollowengine.forge

import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.AddReloadListenerEvent
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.EntityAttributeCreationEvent
import net.minecraftforge.event.entity.item.ItemTossEvent
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent
import net.minecraftforge.event.entity.player.ArrowLooseEvent
import net.minecraftforge.event.entity.player.ArrowNockEvent
import net.minecraftforge.event.level.BlockEvent
import net.minecraftforge.event.server.ServerAboutToStartEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import ru.hollowhorizon.hollowengine.common.events.EventBus
import ru.hollowhorizon.hollowengine.common.events.EventBus.post
import ru.hollowhorizon.hollowengine.common.events.entity.BabySpawnEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityTrackingEvent
import ru.hollowhorizon.hollowengine.common.events.entity.ItemEntityEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.events.item.ArrowEvent
import ru.hollowhorizon.hollowengine.common.events.item.BuildTabContentsEvent
import ru.hollowhorizon.hollowengine.common.events.post
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterEntityAttributesEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterReloadListenersEvent
import ru.hollowhorizon.hollowengine.common.events.server.ServerEvent
import ru.hollowhorizon.hollowengine.common.utils.currentServer

object ForgeEvents {
    init {
        FMLJavaModLoadingContext.get().modEventBus.addListener(ForgeEvents::registerAttributes)
        FMLJavaModLoadingContext.get().modEventBus.addListener(ForgeEvents::onBuildCreativeTab)
        MinecraftForge.EVENT_BUS.addListener(ForgeEvents::registerReloadListeners)
        MinecraftForge.EVENT_BUS.addListener(ForgeEvents::onServerStart)
        MinecraftForge.EVENT_BUS.addListener(ForgeEvents::onServerStop)
        MinecraftForge.EVENT_BUS.addListener(ForgeEvents::registerCommands)
        MinecraftForge.EVENT_BUS.addListener(ForgeEvents::onServerTick)
        MinecraftForge.EVENT_BUS.addListener(ForgeEvents::onEntityTracking)
        MinecraftForge.EVENT_BUS.addListener(ForgeEvents::onPlayerJoin)
        MinecraftForge.EVENT_BUS.addListener(ForgeEvents::onPlayerChangeDimension)
        MinecraftForge.EVENT_BUS.addListener(::onBlockBreak)
        MinecraftForge.EVENT_BUS.addListener(::onItemEntityToss)
        MinecraftForge.EVENT_BUS.addListener(::onBabySpawn)
        MinecraftForge.EVENT_BUS.addListener(::onBlockPlaced)
        MinecraftForge.EVENT_BUS.addListener<ArrowNockEvent> {
            val evt = ArrowEvent.Nock(it.bow, it.level, it.entity, it.hand, it.hasAmmo())
            evt.post()
            if(evt.stack != it.bow) {
                it.action = InteractionResultHolder(InteractionResult.SUCCESS, evt.stack)
            }
        }
        MinecraftForge.EVENT_BUS.addListener<ArrowLooseEvent> {
            val evt = ArrowEvent.Loose(it.bow, it.level, it.entity, it.charge, it.hasAmmo())
            evt.post()
            it.charge = evt.charge
            it.isCanceled = evt.isCanceled
        }
    }

    private fun onBuildCreativeTab(event: BuildCreativeModeTabContentsEvent) {
        val buildEvent = BuildTabContentsEvent(event.tab, event.tabKey, event.parameters, event::accept)
        buildEvent.post()
    }

    private fun onBlockBreak(event: BlockEvent.BreakEvent) {
        val breakEvent = ru.hollowhorizon.hollowengine.common.events.blocks.BlockEvent.Break(
            event.player.level(),
            event.pos,
            event.state,
            event.player
        )
        breakEvent.post()
        event.isCanceled = breakEvent.isCanceled
    }

    private fun registerAttributes(event: EntityAttributeCreationEvent) {
        val attributes = RegisterEntityAttributesEvent()
        post(attributes)
        attributes.getAttributes().forEach(event::put)
    }

    private fun registerReloadListeners(event: AddReloadListenerEvent) {
        val hcevent = RegisterReloadListenersEvent.Server()
        post(hcevent)
        hcevent.listeners.forEach(event::addListener)
    }


    private fun registerCommands(event: RegisterCommandsEvent) {
        ru.hollowhorizon.hollowengine.common.events.registry.RegisterCommandsEvent(
            event.dispatcher, event.buildContext, event.commandSelection
        ).post()
    }

    private fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        post(
            ru.hollowhorizon.hollowengine.common.events.tick.TickEvent.Server(
                event.server
            )
        )
    }

    private fun onEntityTracking(event: net.minecraftforge.event.entity.player.PlayerEvent.StartTracking) {
        EntityTrackingEvent(event.entity, event.target).post()
    }

    private fun onPlayerJoin(event: net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent) {
        PlayerEvent.Join(event.entity).post()
    }

    private fun onPlayerChangeDimension(event: net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent) {
        val server = event.entity.server ?: return
        val from = server.getLevel(event.from) ?: return
        val to = server.getLevel(event.to) ?: return
        PlayerEvent.ChangeDimension(event.entity, from, to).post()
    }

    private fun onServerStart(event: ServerAboutToStartEvent) {
        currentServer = event.server
        ServerEvent.Starting(currentServer).post()
    }

    private fun onServerStop(event: ServerStoppingEvent) {
        ServerEvent.Stoping(event.server).post()
    }

    private fun onItemEntityToss(e: ItemTossEvent) {
        val ev = ItemEntityEvent.Toss(e.entity, e.player)
        ev.post()

        e.isCanceled = ev.isCanceled
    }

    private fun onBabySpawn(e: BabyEntitySpawnEvent) {
        val ev = BabySpawnEvent(e.parentA, e.parentB, e.child)
        ev.post()

        e.child = ev.child
        e.isCanceled = ev.isCanceled
    }

    private fun onBlockPlaced(e: BlockEvent.EntityPlaceEvent) {
        val player = e.entity as? Player ?: return
        val event = ru.hollowhorizon.hollowengine.common.events.blocks.BlockEvent.Placed(player, e.placedBlock, e.pos)
        event.post()
        if(event.isCanceled) e.isCanceled = true
    }
}
*///?}