package ru.hollowhorizon.hollowengine.fabric.bootstap;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.EventBridge;
import ru.hollowhorizon.hollowengine.fabric.internal.DelegatedReloadListener;

public final class FabricCommonEvents {
    private static final EventBridge EVENTS = BootstrapRuntimeManager.bridge().events();

    private FabricCommonEvents() {
    }

    public static void init() {
        registerReloadListeners();
        registerAttributes();
        registerCommands();
        registerEntityTracking();
        registerEntityLifecycle();
        registerPlayerEvents();
        registerServerEvents();
        registerTabContents();
    }

    private static void registerReloadListeners() {
        var helper = ResourceManagerHelper.get(PackType.SERVER_DATA);
        EVENTS.onRegisterServerReloadListeners(registration -> helper.registerReloadListener(new DelegatedReloadListener(registration)));
    }

    private static void registerAttributes() {
        EVENTS.onRegisterEntityAttributes(FabricDefaultAttributeRegistry::register);
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            EVENTS.onRegisterCommands(dispatcher, registryAccess, environment)
        );
    }

    private static void registerEntityTracking() {
        EntityTrackingEvents.START_TRACKING.register((entity, player) ->
            EVENTS.onEntityTrackingStart(player, entity)
        );
        EntityTrackingEvents.STOP_TRACKING.register((entity, player) ->
            EVENTS.onEntityTrackingStop(player, entity)
        );
    }

    private static void registerPlayerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            EVENTS.onPlayerJoin(handler.player)
        );
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            EVENTS.onPlayerLeave(handler.player)
        );
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(EVENTS::onPlayerChangeDimension);
    }

    private static void registerEntityLifecycle() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> EVENTS.onEntityLoad(entity));
    }

    private static void registerServerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(EVENTS::onServerTick);
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return true;
            }
            return !EVENTS.onBlockBreak(world, pos, state, serverPlayer);
        });
    }

    private static void registerTabContents() {
        ItemGroupEvents.MODIFY_ENTRIES_ALL.register((tab, entries) ->
            EVENTS.onBuildTabContents(
                tab,
                BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(tab)
                    .orElseThrow(() -> new IllegalStateException("Unregistered creative mode tab: " + tab)),
                entries.getContext(),
                entries::accept
            )
        );
    }
}
