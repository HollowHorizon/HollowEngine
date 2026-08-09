package ru.hollowhorizon.hollowengine.neoforge;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.EventBridge;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.RuntimeBridge;

import java.io.IOException;
import java.util.function.Consumer;

public class NeoForgeClientEvents {
    private static final RuntimeBridge bridge = BootstrapRuntimeManager.bridge();
    private static final EventBridge events = bridge.events();

    public static void init(IEventBus modBus) {
        var forgeBus = NeoForge.EVENT_BUS;
        forgeBus.addListener(NeoForgeClientEvents::onRenderTooltips);
        forgeBus.addListener(NeoForgeClientEvents::onClientTick);
        forgeBus.addListener(NeoForgeClientEvents::registerClientCommands);
        forgeBus.addListener(NeoForgeClientEvents::onRenderOverlayPre);
        forgeBus.addListener(NeoForgeClientEvents::onRenderOverlayPost);
        forgeBus.addListener(NeoForgeClientEvents::onRenderHudPost);
        forgeBus.addListener(NeoForgeClientEvents::onCameraSetup);

        modBus.addListener(NeoForgeClientEvents::registerShaders);
        modBus.addListener(NeoForgeClientEvents::registerRenderers);
        modBus.addListener(NeoForgeClientEvents::registerKeyMappings);
        modBus.addListener(NeoForgeClientEvents::registerReloadListeners);

    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        events.onRegisterClientReloadListeners(event::registerReloadListener);
    }

    @SuppressWarnings("unchecked")
    private static void registerClientCommands(RegisterClientCommandsEvent event) {
        events.onClientCommandRegistration((CommandDispatcher<SharedSuggestionProvider>) (Object) event.getDispatcher(), event.getBuildContext());
    }

    private static void registerShaders(RegisterShadersEvent event) {
        events.onRegisterShaders(new NeoForgeShaders(event));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        events.onRegisterEntityRenderers(event::registerEntityRenderer);
        events.onRegisterBlockEntityRenderers(event::registerBlockEntityRenderer);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        events.onRegisterKeybindings(event::register);
    }

    private static void onRenderTooltips(ItemTooltipEvent event) {
        events.onGetTooltip(event.getItemStack(), event.getContext(), event.getFlags(), event.getToolTip());
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        events.onClientTick(Minecraft.getInstance());
    }

    private static void onRenderOverlayPre(RenderGuiLayerEvent.Pre event) {
        // Forward every named layer by its id; the runtime decides which ones it cares about. This
        // covers all vanilla layers and any modded layer without a per-layer mapping here.
        if (bridge.onRenderOverlayPre(Minecraft.getInstance().getWindow(), event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false), event.getName().toString())) {
            event.setCanceled(true);
        }
    }

    private static void onRenderOverlayPost(RenderGuiLayerEvent.Post event) {
        bridge.onRenderOverlayPost(Minecraft.getInstance().getWindow(), event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false), event.getName().toString());
    }

    private static void onRenderHudPost(RenderGuiEvent.Post event) {
        bridge.onRenderHudPost(Minecraft.getInstance().getWindow(), event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    private static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        var setup = bridge.onCameraSetup(event.getRenderer(), event.getCamera(), event.getYaw(), event.getPitch(), event.getRoll(), (float) event.getPartialTick());
        event.setYaw(setup.yaw());
        event.setPitch(setup.pitch());
        event.setRoll(setup.roll());
    }

    private record NeoForgeShaders(RegisterShadersEvent event) implements EventBridge.ShaderRegistration {
        @Override
        public void register(ResourceLocation id, VertexFormat vertexFormat, Consumer<ShaderInstance> loadCallback) throws IOException {
            var shader = new ShaderInstance(event.getResourceProvider(), id, vertexFormat);
            event.registerShader(shader, loadCallback);
        }
    }
}
