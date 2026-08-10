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
    private val surfaces = ConcurrentHashMap<ResourceLocation, UiSurfaceDefinition>()

    val allScreens: Collection<UiScreenDefinition> get() = screens.values
    val allOverlays: Collection<UiOverlayDefinition> get() = overlays.values

    fun register(definition: UiScreenDefinition) {
        screens[definition.id] = definition
    }

    fun register(definition: UiOverlayDefinition) {
        overlays[definition.id] = definition
    }

    fun register(definition: UiSurfaceDefinition) {
        surfaces[definition.id] = definition
    }

    fun screen(id: ResourceLocation): UiScreenDefinition? = screens[id]

    fun overlay(id: ResourceLocation): UiOverlayDefinition? = overlays[id]

    fun surface(id: ResourceLocation): UiSurfaceDefinition? = surfaces[id]

    fun clear() {
        screens.clear()
        overlays.clear()
        surfaces.clear()
    }
}
