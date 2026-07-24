package ru.hollowhorizon.hollowengine.client.ui.script

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.ui.HudPlacement
import ru.hollowhorizon.hollowengine.common.ui.UiData
import ru.hollowhorizon.hollowengine.common.ui.UiDefinitionRegistry
import ru.hollowhorizon.hollowengine.common.ui.UiOverlayDefinition
import ru.hollowhorizon.hollowengine.common.ui.hud.HudLayerRegistry

/**
 * Draws the scripted HUD layers that are currently shown, and routes input to the ones that opted
 * in. Each layer is anchored to a vanilla layer and drawn from that layer's Pre/Post hook, which is
 * what lets a scripted overlay sit *between* two vanilla ones without the engine owning a fixed list
 * of slots. Overlays flagged `aboveScreens` also draw over an open screen.
 */
object UiScriptHudHost {
    private val shown = LinkedHashMap<ResourceLocation, ShownOverlay>()

    private class ShownOverlay(
        val definition: UiOverlayDefinition,
        val surface: UiScriptSurface,
        val sessionId: Int?,
    )

    /**
     * Shows an overlay. [sessionId] is set when a server session owns it, in which case its data is
     * fed by server patches and its events are routed back to that session.
     */
    fun show(
        definition: UiOverlayDefinition,
        sessionId: Int? = null,
        initialState: CompoundTag = CompoundTag(),
    ) {
        hide(definition.id)
        val data = UiData(initialState)
        val surface = UiScriptSurface(
            content = definition.content,
            data = data,
            interactive = definition.isInteractive,
            rebuildEveryFrame = definition.rebuildEveryFrame,
            onSend = sessionId?.let { id -> { payload -> UiScriptClient.send(id, payload) } },
            onClose = { hide(definition.id) },
        )
        shown[definition.id] = ShownOverlay(definition, surface, sessionId)
    }

    fun show(id: ResourceLocation, sessionId: Int? = null, initialState: CompoundTag = CompoundTag()) {
        val definition = UiDefinitionRegistry.overlay(id) ?: run {
            HollowEngine.LOGGER.warn("No UI overlay declared with id {}", id)
            return
        }
        show(definition, sessionId, initialState)
    }

    fun hide(id: ResourceLocation) {
        shown.remove(id)?.surface?.dispose()
    }

    fun isShown(id: ResourceLocation): Boolean = id in shown

    fun applyPatch(sessionId: Int, patch: CompoundTag, removed: Collection<String>) {
        shown.values.firstOrNull { it.sessionId == sessionId }?.surface?.data?.applyPatch(patch, removed)
    }

    fun closeSession(sessionId: Int) {
        shown.entries.filter { it.value.sessionId == sessionId }.forEach { hide(it.key) }
    }

    /** Draws every overlay anchored at [anchor]/[placement] that is not currently suppressed. */
    fun render(anchor: ResourceLocation, placement: HudPlacement, nowNanos: Long) {
        if (shown.isEmpty()) return
        shown.values.forEach { overlay ->
            if (overlay.definition.anchor != anchor || overlay.definition.placement != placement) return@forEach
            renderOne(overlay, nowNanos)
        }
    }

    /** Draws the overlays that opted to sit on top of an open screen. */
    fun renderAboveScreens(nowNanos: Long) {
        if (shown.isEmpty()) return
        shown.values.forEach { overlay ->
            if (overlay.definition.aboveScreens) renderOne(overlay, nowNanos)
        }
    }

    /** Whether any interactive overlay currently holds keyboard focus (e.g. a focused text field). */
    fun hasFocusedInput(): Boolean = shown.values.any { it.surface.hasFocusedInput }

    fun handleMouseMove(x: Float, y: Float): Boolean = dispatch { it.surface.mouseMoved(x, y) }

    fun handleMouseButton(x: Float, y: Float, button: Int, action: Int): Boolean =
        dispatch { it.surface.mousePressed(x, y, button, action) }

    fun handleMouseScroll(x: Float, y: Float, scrollX: Double, scrollY: Double): Boolean =
        dispatch { it.surface.mouseScrolled(x, y, scrollX, scrollY) }

    fun handleKey(key: Int, scanCode: Int, action: Int, modifiers: Int): Boolean =
        dispatch { it.surface.keyPressed(key, scanCode, action, modifiers) }

    fun handleChar(codePoint: Int, modifiers: Int): Boolean =
        dispatch { it.surface.charTyped(codePoint, modifiers) }

    /**
     * Drops every shown overlay and brings back the ones scripts declare as auto-showing. Called
     * after scripts recompile, when the old composables no longer correspond to any live definition.
     */
    fun reload() {
        hideAll()
        UiDefinitionRegistry.allOverlays.filter { it.autoShow }.forEach { show(it) }
    }

    fun hideAll() {
        shown.values.forEach { it.surface.dispose() }
        shown.clear()
    }

    private fun renderOne(overlay: ShownOverlay, nowNanos: Long) {
        if (HudLayerRegistry.isHidden(overlay.definition.id)) return
        runCatching { overlay.surface.render(nowNanos) }.onFailure { error ->
            HollowEngine.LOGGER.error("Failed to render UI overlay {}", overlay.definition.id, error)
            hide(overlay.definition.id)
        }
    }

    /** Dispatches an input action to the most recently shown interactive overlay that consumes it. */
    private inline fun dispatch(action: (ShownOverlay) -> Boolean): Boolean {
        if (shown.isEmpty()) return false
        for (overlay in shown.values.reversed()) {
            if (!overlay.definition.isInteractive) continue
            if (action(overlay)) return true
        }
        return false
    }
}
