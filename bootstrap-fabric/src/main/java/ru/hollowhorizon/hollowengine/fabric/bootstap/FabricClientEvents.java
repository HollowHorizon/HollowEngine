package ru.hollowhorizon.hollowengine.fabric.bootstap;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.packs.PackType;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.EventBridge;
import ru.hollowhorizon.hollowengine.fabric.internal.DelegatedReloadListener;

public class FabricClientEvents {
    private static final EventBridge events = BootstrapRuntimeManager.bridge().events();

    public static void init() {
        registerShaders();
        registerReloadListeners();
        events.onRegisterEntityRenderers(EntityRenderers::register);
        events.onRegisterBlockEntityRenderers(BlockEntityRenderers::register);
        ItemTooltipCallback.EVENT.register(events::onGetTooltip);
        events.onRegisterKeybindings(KeyBindingHelper::registerKeyBinding);
        ClientTickEvents.END_CLIENT_TICK.register(events::onClientTick);
        ClientCommandRegistrationCallback.EVENT.register(((commandDispatcher, commandBuildContext) ->
                events.onClientCommandRegistration((CommandDispatcher<SharedSuggestionProvider>) (Object) commandDispatcher, commandBuildContext)));
    }


    private static void registerReloadListeners() {
        var helper = ResourceManagerHelper.get(PackType.CLIENT_RESOURCES);
        events.onRegisterClientReloadListeners(registration -> helper.registerReloadListener(new DelegatedReloadListener(registration)));
    }

    private static void registerShaders() {
        CoreShaderRegistrationCallback.EVENT.register((CoreShaderRegistrationCallback.RegistrationContext ctx) -> {
            events.onRegisterShaders(ctx::register);
        });
    }
}
