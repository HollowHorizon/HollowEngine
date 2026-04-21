package ru.hollowhorizon.hollowengine.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.EventBridge;

public class NeoForgeEvents {
    private static final EventBridge EVENTS = BootstrapRuntimeManager.bridge().events();

    private NeoForgeEvents() {
    }

    public static void init(IEventBus modBus) {
        var forgeBus = NeoForge.EVENT_BUS;
        forgeBus.addListener(NeoForgeEvents::registerReloadListeners);
        forgeBus.addListener(NeoForgeEvents::registerCommands);
        forgeBus.addListener(NeoForgeEvents::registerEntityTrackingStart);
        forgeBus.addListener(NeoForgeEvents::registerEntityTrackingStop);
        forgeBus.addListener(NeoForgeEvents::registerEntityLifecycle);
        forgeBus.addListener(NeoForgeEvents::onPlayerJoin);
        forgeBus.addListener(NeoForgeEvents::onPlayerChangeDimension);
        forgeBus.addListener(NeoForgeEvents::onPlayerLeave);
        forgeBus.addListener(NeoForgeEvents::onServerTick);
        forgeBus.addListener(NeoForgeEvents::onBlockBreak);

        modBus.addListener(NeoForgeEvents::registerAttributes);
        modBus.addListener(NeoForgeEvents::registerTabContents);
    }

    private static void registerReloadListeners(AddReloadListenerEvent event) {
        EVENTS.onRegisterServerReloadListeners(event::addListener);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        EVENTS.onRegisterEntityAttributes(event::put);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        EVENTS.onRegisterCommands(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection());
    }

    private static void registerEntityTrackingStart(PlayerEvent.StartTracking event) {
        EVENTS.onEntityTrackingStart((ServerPlayer) event.getEntity(), event.getTarget());
    }

    public static void registerEntityTrackingStop(PlayerEvent.StopTracking event) {
        EVENTS.onEntityTrackingStop((ServerPlayer) event.getEntity(), event.getTarget());
    }

    private static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        EVENTS.onPlayerJoin((ServerPlayer) event.getEntity());
    }

    private static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        EVENTS.onPlayerLeave((ServerPlayer) event.getEntity());
    }

    private static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        var server = event.getEntity().getServer();
        if (server == null) return;
        var from = server.getLevel(event.getFrom());
        var to = server.getLevel(event.getTo());
        EVENTS.onPlayerChangeDimension((ServerPlayer) event.getEntity(), from, to);
    }

    private static void registerEntityLifecycle(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide) EVENTS.onEntityLoad(event.getEntity());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        EVENTS.onServerTick(event.getServer());
    }

    private static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        var cancel = EVENTS.onBlockBreak((Level) event.getLevel(), event.getPos(), event.getState(), (ServerPlayer) event.getPlayer());
        if (cancel) event.setCanceled(true);
    }

    private static void registerTabContents(BuildCreativeModeTabContentsEvent event) {
        EVENTS.onBuildTabContents(event.getTab(), event.getTabKey(), event.getParameters(), event);
    }
}
