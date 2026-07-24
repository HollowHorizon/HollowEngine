package ru.hollowhorizon.hollowengine.common.ui

import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * Everything `.ui.kts` scripts have declared. Scripts are recompiled on reload, so the registry is
 * rebuilt from scratch each time rather than merged, a renamed or deleted screen must not linger.
 */
object UiDefinitionRegistry {
    private val screens = ConcurrentHashMap<ResourceLocation, UiScreenDefinition>()
    private val overlays = ConcurrentHashMap<ResourceLocation, UiOverlayDefinition>()

    val allScreens: Collection<UiScreenDefinition> get() = screens.values
    val allOverlays: Collection<UiOverlayDefinition> get() = overlays.values

    fun register(definition: UiScreenDefinition) {
        screens[definition.id] = definition
    }

    fun register(definition: UiOverlayDefinition) {
        overlays[definition.id] = definition
    }

    fun screen(id: ResourceLocation): UiScreenDefinition? = screens[id]

    fun overlay(id: ResourceLocation): UiOverlayDefinition? = overlays[id]

    fun clear() {
        screens.clear()
        overlays.clear()
    }
}
