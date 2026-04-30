package ru.hollowhorizon.hollowengine.neoforge;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.EventBridge;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.RuntimeBridge;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class NeoForgeClientEvents {
    private static final RuntimeBridge bridge = BootstrapRuntimeManager.bridge();
    private static final EventBridge events = bridge.events();
    private static final Map<ResourceLocation, RuntimeBridge.OverlayKind> LAYERS = new HashMap<>();

    static {
        //registerLayer(VanillaGuiLayers.CAMERA_OVERLAYS, RuntimeBridge.OverlayKind.CROSSHAIR);
        registerLayer(VanillaGuiLayers.CROSSHAIR, RuntimeBridge.OverlayKind.CROSSHAIR);
        registerLayer(VanillaGuiLayers.HOTBAR, RuntimeBridge.OverlayKind.HOTBAR);
        registerLayer(VanillaGuiLayers.JUMP_METER, RuntimeBridge.OverlayKind.JUMP_BAR);
        registerLayer(VanillaGuiLayers.EXPERIENCE_BAR, RuntimeBridge.OverlayKind.EXPERIENCE_BAR);
        registerLayer(VanillaGuiLayers.PLAYER_HEALTH, RuntimeBridge.OverlayKind.PLAYER_HEALTH);
        registerLayer(VanillaGuiLayers.ARMOR_LEVEL, RuntimeBridge.OverlayKind.HELMET);
        //registerLayer(VanillaGuiLayers.FOOD_LEVEL, RuntimeBridge.OverlayKind.PLAYER_HEALTH);
        registerLayer(VanillaGuiLayers.VEHICLE_HEALTH, RuntimeBridge.OverlayKind.MOUNT_HEALTH);
        //registerLayer(VanillaGuiLayers.AIR_LEVEL, RuntimeBridge.OverlayKind.PLAYER_HEALTH);
        registerLayer(VanillaGuiLayers.SELECTED_ITEM_NAME, RuntimeBridge.OverlayKind.ITEM_NAME);
        //registerLayer(VanillaGuiLayers.SPECTATOR_TOOLTIP, RuntimeBridge.OverlayKind.PLAYER_HEALTH);
        //registerLayer(VanillaGuiLayers.EXPERIENCE_LEVEL, RuntimeBridge.OverlayKind.EXPERIENCE_BAR);
        registerLayer(VanillaGuiLayers.EFFECTS, RuntimeBridge.OverlayKind.POTION_ICONS);
        registerLayer(VanillaGuiLayers.BOSS_OVERLAY, RuntimeBridge.OverlayKind.BOSS_EVENT_PROGRESS);
        //registerLayer(VanillaGuiLayers.SLEEP_OVERLAY, RuntimeBridge.OverlayKind.VIGNETTE);
        //registerLayer(VanillaGuiLayers.DEMO_OVERLAY, RuntimeBridge.OverlayKind.VIGNETTE);
        registerLayer(VanillaGuiLayers.DEBUG_OVERLAY, RuntimeBridge.OverlayKind.DEBUG_TEXT);
        //registerLayer(VanillaGuiLayers.SCOREBOARD_SIDEBAR, RuntimeBridge.OverlayKind.DEBUG_TEXT);
        registerLayer(VanillaGuiLayers.OVERLAY_MESSAGE, RuntimeBridge.OverlayKind.VIGNETTE);
        //registerLayer(VanillaGuiLayers.TITLE, RuntimeBridge.OverlayKind.CROSSHAIR);
        registerLayer(VanillaGuiLayers.CHAT, RuntimeBridge.OverlayKind.CHAT_PANEL);
        //registerLayer(VanillaGuiLayers.TAB_LIST, RuntimeBridge.OverlayKind.VIGNETTE);
        //registerLayer(VanillaGuiLayers.SUBTITLE_OVERLAY, RuntimeBridge.OverlayKind.CROSSHAIR);
        //registerLayer(VanillaGuiLayers.SAVING_INDICATOR, RuntimeBridge.OverlayKind.CROSSHAIR);
    }

    private static void registerLayer(ResourceLocation location, RuntimeBridge.OverlayKind kind) {
        LAYERS.put(location, kind);
    }

    public static void init(IEventBus modBus) {
        var forgeBus = NeoForge.EVENT_BUS;
        forgeBus.addListener(NeoForgeClientEvents::onRenderTooltips);
        forgeBus.addListener(NeoForgeClientEvents::onClientTick);
        forgeBus.addListener(NeoForgeClientEvents::registerClientCommands);
        forgeBus.addListener(NeoForgeClientEvents::onRenderOverlayPre);
        forgeBus.addListener(NeoForgeClientEvents::onRenderOverlayPost);
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
        var kind = LAYERS.get(event.getName());
        if (kind == null) return;

        if (bridge.onRenderOverlayPre(Minecraft.getInstance().getWindow(), event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false), kind)) {
            event.setCanceled(true);
        }
    }

    private static void onRenderOverlayPost(RenderGuiLayerEvent.Post event) {
        var kind = LAYERS.get(event.getName());
        if (kind == null) return;

        bridge.onRenderOverlayPost(Minecraft.getInstance().getWindow(), event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false), kind);
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
            try (var shader = new ShaderInstance(event.getResourceProvider(), id, vertexFormat)) {
                event.registerShader(shader, loadCallback);
            }
        }
    }
}
