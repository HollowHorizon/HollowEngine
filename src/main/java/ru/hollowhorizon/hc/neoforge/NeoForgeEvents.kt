package ru.hollowhorizon.hc.neoforge

//? if neoforge {

/*import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.AddReloadListenerEvent
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent
import net.neoforged.neoforge.event.entity.item.ItemTossEvent
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent
import net.neoforged.neoforge.event.entity.player.ArrowNockEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import ru.hollowhorizon.hc.common.events.EventBus
import ru.hollowhorizon.hc.common.events.EventBus.post
import ru.hollowhorizon.hc.common.events.entity.BabySpawnEvent
import ru.hollowhorizon.hc.common.events.entity.EntityTrackingEvent
import ru.hollowhorizon.hc.common.events.entity.ItemEntityEvent
import ru.hollowhorizon.hc.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hc.common.events.item.ArrowEvent
import ru.hollowhorizon.hc.common.events.item.BuildTabContentsEvent
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hc.common.events.registry.RegisterEntityAttributesEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterReloadListenersEvent
import ru.hollowhorizon.hc.common.events.server.ServerEvent
import ru.hollowhorizon.hc.common.utils.currentServer

class NeoForgeEvents(val modBus: IEventBus) {
    init {
        modBus.addListener(::registerAttributes)
        modBus.addListener(::onBuildCreativeTab)
        NeoForge.EVENT_BUS.addListener(::registerReloadListeners)
        NeoForge.EVENT_BUS.addListener(::onServerStart)
        NeoForge.EVENT_BUS.addListener(::onServerStop)
        NeoForge.EVENT_BUS.addListener(::registerCommands)
        NeoForge.EVENT_BUS.addListener(::onServerTick)
        NeoForge.EVENT_BUS.addListener(::onEntityTracking)
        NeoForge.EVENT_BUS.addListener(::onPlayerJoin)
        NeoForge.EVENT_BUS.addListener(::onPlayerChangeDimension)
        NeoForge.EVENT_BUS.addListener(::onBlockBreak)
        NeoForge.EVENT_BUS.addListener(::onItemEntityToss)
        NeoForge.EVENT_BUS.addListener(::onBabySpawn)
        NeoForge.EVENT_BUS.addListener(::onBlockPlaced)
        NeoForge.EVENT_BUS.addListener<ArrowNockEvent> {
            val evt = ArrowEvent.Nock(it.bow, it.level, it.entity, it.hand, it.hasAmmo())
            evt.post()
            if(evt.stack != it.bow) {
                it.action = InteractionResultHolder(InteractionResult.SUCCESS, evt.stack)
            }
        }
        NeoForge.EVENT_BUS.addListener<ArrowLooseEvent> {
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
        val breakEvent = ru.hollowhorizon.hc.common.events.blocks.BlockEvent.Break(
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
        ru.hollowhorizon.hc.common.events.registry.RegisterCommandsEvent(
            event.dispatcher, event.buildContext, event.commandSelection
        ).post()
    }

    private fun onServerTick(event: ServerTickEvent.Pre) {
        post(
            ru.hollowhorizon.hc.common.events.tick.TickEvent.Server(
                event.server
            )
        )
    }

    private fun onEntityTracking(event: net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking) {
        EntityTrackingEvent(event.entity, event.target).post()
    }

    private fun onPlayerJoin(event: net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent) {
        PlayerEvent.Join(event.entity).post()
    }

    private fun onPlayerChangeDimension(event: net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent) {
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
        val event = ru.hollowhorizon.hc.common.events.blocks.BlockEvent.Placed(player, e.placedBlock, e.pos)
        event.post()
        if(event.isCanceled) e.isCanceled = true
    }
}

*///?}