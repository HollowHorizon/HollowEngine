package ru.hollowhorizon.hollowengine.client.ui.script

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.utils.mc
import ru.hollowhorizon.hollowengine.common.ui.UiData
import ru.hollowhorizon.hollowengine.common.ui.UiDefinitionRegistry
import ru.hollowhorizon.hollowengine.common.ui.UiSurfaceDefinition
import ru.hollowhorizon.hollowengine.common.ui.net.UiSurfaceKind

/**
 * Hosts the sessions whose surface decides for itself whether it is a screen or an overlay.
 *
 * The session and its document belong here, not to either host: a switch tears one host down and
 * builds the other around the *same* [UiData], which is what lets a dialogue turn into a menu
 * without the server resending anything or the content losing its state.
 */
internal object UiAdaptiveSurfaces {
    private class Mounted(
        val definition: UiSurfaceDefinition,
        val data: UiData,
        var kind: UiSurfaceKind?,
    )

    private val sessions = HashMap<Int, Mounted>()

    /**
     * Set while a host is being swapped out. The screen reports its own removal to the server, and
     * without this a switch to the overlay would read as the player closing the dialogue.
     */
    private var swapping: Int? = null

    fun open(sessionId: Int, surface: ResourceLocation, state: CompoundTag) {
        val definition = UiDefinitionRegistry.surface(surface) ?: run {
            HollowEngine.LOGGER.warn("Server opened unknown UI surface {}", surface)
            return
        }
        close(sessionId)
        val mounted = Mounted(definition, UiData(state), kind = null)
        sessions[sessionId] = mounted
        apply(sessionId, mounted)
    }

    /** Returns true when this session is an adaptive surface and the patch was handled here. */
    fun applyPatch(sessionId: Int, patch: CompoundTag, removed: Collection<String>): Boolean {
        val mounted = sessions[sessionId] ?: return false
        mounted.data.applyPatch(patch, removed)
        apply(sessionId, mounted)
        return true
    }

    /** Returns true when this session was an adaptive surface. */
    fun close(sessionId: Int): Boolean {
        val mounted = sessions.remove(sessionId) ?: return false
        unmount(sessionId, mounted, mounted.kind)
        return true
    }

    /** Whether [sessionId] belongs here; the screen asks before reporting itself closed. */
    fun owns(sessionId: Int): Boolean = sessionId in sessions

    /** Whether the screen for [sessionId] is being taken down by a switch rather than by the player. */
    fun isSwapping(sessionId: Int): Boolean = swapping == sessionId

    fun reset() {
        sessions.keys.toList().forEach(::close)
    }

    /** Mounts the host the surface currently asks for, if it is not the one already up. */
    private fun apply(sessionId: Int, mounted: Mounted) {
        val requested = runCatching { mounted.definition.mode(mounted.data) }
            .onFailure { HollowEngine.LOGGER.error("mode { } of UI surface {} failed", mounted.definition.id, it) }
            .getOrDefault(mounted.kind ?: UiSurfaceKind.OVERLAY)
        if (requested == mounted.kind) return
        unmount(sessionId, mounted, mounted.kind)
        mounted.kind = requested
        when (requested) {
            UiSurfaceKind.SCREEN ->
                mc.setScreen(UiScriptScreen(mounted.definition.screen, mounted.data, sessionId))

            else -> UiScriptHudHost.show(mounted.definition.overlay, sessionId, mounted.data)
        }
    }

    private fun unmount(sessionId: Int, mounted: Mounted, kind: UiSurfaceKind?) {
        when (kind) {
            UiSurfaceKind.SCREEN -> {
                swapping = sessionId
                try {
                    if (mc.screen is UiScriptScreen) mc.setScreen(null)
                } finally {
                    swapping = null
                }
            }

            UiSurfaceKind.OVERLAY, UiSurfaceKind.ADAPTIVE -> UiScriptHudHost.hide(mounted.definition.id)
            null -> Unit
        }
    }
}
