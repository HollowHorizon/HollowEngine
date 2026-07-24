package ru.hollowhorizon.hollowengine.common.ui.hud

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.bootstrap.runtime.HudLayerIds
import ru.hollowhorizon.hollowengine.common.ui.hud.VanillaHudLayers.AIR_LEVEL
import ru.hollowhorizon.hollowengine.common.ui.hud.VanillaHudLayers.ARMOR_LEVEL
import ru.hollowhorizon.hollowengine.common.ui.hud.VanillaHudLayers.FOOD_LEVEL

/**
 * Identifiers for the vanilla HUD layers the engine can anchor to or hide. These are the real
 * vanilla layer resource locations (see [HudLayerIds]) rather than an enum, so on NeoForge a layer's
 * id passes straight through `RenderGuiLayerEvent.getName()`.
 *
 * Coverage differs by platform: NeoForge forwards every named layer, so all of these fire there.
 * Fabric fires the subset its mixins hook (the camera overlays plus the common status bars);
 * TODO: a few * NeoForge-only layers (e.g. [FOOD_LEVEL], [AIR_LEVEL], [ARMOR_LEVEL]) are drawn together inside
 *  `renderPlayerHealth` on Fabric and so are not separable there.
 */
object VanillaHudLayers {
    val CAMERA_OVERLAYS = ResourceLocation.parse(HudLayerIds.CAMERA_OVERLAYS)
    val CROSSHAIR = ResourceLocation.parse(HudLayerIds.CROSSHAIR)
    val HOTBAR = ResourceLocation.parse(HudLayerIds.HOTBAR)
    val JUMP_METER = ResourceLocation.parse(HudLayerIds.JUMP_METER)
    val EXPERIENCE_BAR = ResourceLocation.parse(HudLayerIds.EXPERIENCE_BAR)
    val EXPERIENCE_LEVEL = ResourceLocation.parse(HudLayerIds.EXPERIENCE_LEVEL)
    val PLAYER_HEALTH = ResourceLocation.parse(HudLayerIds.PLAYER_HEALTH)
    val ARMOR_LEVEL = ResourceLocation.parse(HudLayerIds.ARMOR_LEVEL)
    val FOOD_LEVEL = ResourceLocation.parse(HudLayerIds.FOOD_LEVEL)
    val AIR_LEVEL = ResourceLocation.parse(HudLayerIds.AIR_LEVEL)
    val VEHICLE_HEALTH = ResourceLocation.parse(HudLayerIds.VEHICLE_HEALTH)
    val SELECTED_ITEM_NAME = ResourceLocation.parse(HudLayerIds.SELECTED_ITEM_NAME)
    val SPECTATOR_TOOLTIP = ResourceLocation.parse(HudLayerIds.SPECTATOR_TOOLTIP)
    val EFFECTS = ResourceLocation.parse(HudLayerIds.EFFECTS)
    val BOSS_OVERLAY = ResourceLocation.parse(HudLayerIds.BOSS_OVERLAY)
    val SLEEP_OVERLAY = ResourceLocation.parse(HudLayerIds.SLEEP_OVERLAY)
    val DEMO_OVERLAY = ResourceLocation.parse(HudLayerIds.DEMO_OVERLAY)
    val DEBUG_OVERLAY = ResourceLocation.parse(HudLayerIds.DEBUG_OVERLAY)
    val SCOREBOARD_SIDEBAR = ResourceLocation.parse(HudLayerIds.SCOREBOARD_SIDEBAR)
    val OVERLAY_MESSAGE = ResourceLocation.parse(HudLayerIds.OVERLAY_MESSAGE)
    val TITLE = ResourceLocation.parse(HudLayerIds.TITLE)
    val SUBTITLE_OVERLAY = ResourceLocation.parse(HudLayerIds.SUBTITLE_OVERLAY)
    val CHAT = ResourceLocation.parse(HudLayerIds.CHAT)
    val TAB_LIST = ResourceLocation.parse(HudLayerIds.TAB_LIST)
    val SAVING_INDICATOR = ResourceLocation.parse(HudLayerIds.SAVING_INDICATOR)

    val VIGNETTE = ResourceLocation.parse(HudLayerIds.VIGNETTE)
    val SPYGLASS = ResourceLocation.parse(HudLayerIds.SPYGLASS)
    val HELMET = ResourceLocation.parse(HudLayerIds.HELMET)
    val FROSTBITE = ResourceLocation.parse(HudLayerIds.FROSTBITE)
    val PORTAL = ResourceLocation.parse(HudLayerIds.PORTAL)

    val all: Set<ResourceLocation> = setOf(
        CAMERA_OVERLAYS, CROSSHAIR, HOTBAR, JUMP_METER, EXPERIENCE_BAR, EXPERIENCE_LEVEL, PLAYER_HEALTH,
        ARMOR_LEVEL, FOOD_LEVEL, AIR_LEVEL, VEHICLE_HEALTH, SELECTED_ITEM_NAME, SPECTATOR_TOOLTIP, EFFECTS,
        BOSS_OVERLAY, SLEEP_OVERLAY, DEMO_OVERLAY, DEBUG_OVERLAY, SCOREBOARD_SIDEBAR, OVERLAY_MESSAGE, TITLE,
        SUBTITLE_OVERLAY, CHAT, TAB_LIST, SAVING_INDICATOR, VIGNETTE, SPYGLASS, HELMET, FROSTBITE, PORTAL,
    )

    /** Parses a layer id, accepting bare vanilla names (`crosshair`) as well as full ids. */
    fun parse(name: String): ResourceLocation =
        if (':' in name) ResourceLocation.parse(name) else ResourceLocation.withDefaultNamespace(name)
}
