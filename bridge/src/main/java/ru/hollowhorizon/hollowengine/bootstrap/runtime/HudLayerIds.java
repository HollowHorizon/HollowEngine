package ru.hollowhorizon.hollowengine.bootstrap.runtime;

/**
 * Canonical HUD layer ids shared by both bootstraps and the runtime. They are the vanilla layer
 * resource-location strings, so on NeoForge {@code RenderGuiLayerEvent.getName().toString()} equals
 * one of these directly (and any layer we do not list still passes through as its own id), while the
 * Fabric mixins pass these constants explicitly.
 */
public final class HudLayerIds {
    private HudLayerIds() {
    }

    // Vanilla named layers (match net.neoforged.neoforge.client.gui.VanillaGuiLayers on NeoForge).
    public static final String CAMERA_OVERLAYS = "minecraft:camera_overlays";
    public static final String CROSSHAIR = "minecraft:crosshair";
    public static final String HOTBAR = "minecraft:hotbar";
    public static final String JUMP_METER = "minecraft:jump_meter";
    public static final String EXPERIENCE_BAR = "minecraft:experience_bar";
    public static final String EXPERIENCE_LEVEL = "minecraft:experience_level";
    public static final String PLAYER_HEALTH = "minecraft:player_health";
    public static final String ARMOR_LEVEL = "minecraft:armor_level";
    public static final String FOOD_LEVEL = "minecraft:food_level";
    public static final String AIR_LEVEL = "minecraft:air_level";
    public static final String VEHICLE_HEALTH = "minecraft:vehicle_health";
    public static final String SELECTED_ITEM_NAME = "minecraft:selected_item_name";
    public static final String SPECTATOR_TOOLTIP = "minecraft:spectator_tooltip";
    public static final String EFFECTS = "minecraft:effects";
    public static final String BOSS_OVERLAY = "minecraft:boss_overlay";
    public static final String SLEEP_OVERLAY = "minecraft:sleep_overlay";
    public static final String DEMO_OVERLAY = "minecraft:demo_overlay";
    public static final String DEBUG_OVERLAY = "minecraft:debug_overlay";
    public static final String SCOREBOARD_SIDEBAR = "minecraft:scoreboard_sidebar";
    public static final String OVERLAY_MESSAGE = "minecraft:overlay_message";
    public static final String TITLE = "minecraft:title";
    public static final String SUBTITLE_OVERLAY = "minecraft:subtitle_overlay";
    public static final String CHAT = "minecraft:chat";
    public static final String TAB_LIST = "minecraft:tab_list";
    public static final String SAVING_INDICATOR = "minecraft:saving_indicator";

    // Camera overlays vanilla draws as distinct methods rather than named layers. NeoForge does not
    // expose these through RenderGuiLayerEvent, so they only fire from the Fabric Gui mixin.
    public static final String VIGNETTE = "minecraft:vignette";
    public static final String SPYGLASS = "minecraft:spyglass";
    public static final String HELMET = "minecraft:helmet";
    public static final String FROSTBITE = "minecraft:frostbite";
    public static final String PORTAL = "minecraft:portal";
}
