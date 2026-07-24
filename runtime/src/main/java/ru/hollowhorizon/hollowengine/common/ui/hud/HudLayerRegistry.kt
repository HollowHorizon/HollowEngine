package ru.hollowhorizon.hollowengine.common.ui.hud

import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks which HUD layers are suppressed. Both vanilla layers (hidden during a cutscene, say) and
 * scripted ones go through here, so a caller never has to know whether the layer it wants gone is
 * ours or Minecraft's.
 *
 * Hiding is keyed by an owner so independent systems can suppress the same layer without fighting:
 * a layer stays hidden until every owner has released it.
 */
object HudLayerRegistry {
    private val hidden = ConcurrentHashMap<ResourceLocation, MutableSet<String>>()

    fun hide(layer: ResourceLocation, owner: String = DefaultOwner) {
        hidden.computeIfAbsent(layer) { ConcurrentHashMap.newKeySet() } += owner
    }

    fun show(layer: ResourceLocation, owner: String = DefaultOwner) {
        val owners = hidden[layer] ?: return
        owners -= owner
        if (owners.isEmpty()) hidden.remove(layer)
    }

    fun isHidden(layer: ResourceLocation): Boolean = hidden.containsKey(layer)

    val hiddenLayers: Set<ResourceLocation> get() = hidden.keys.toSet()

    /** Replaces everything [owner] hides with [layers]; used to apply a server's hide list wholesale. */
    fun setHidden(layers: Collection<ResourceLocation>, owner: String) {
        hidden.keys.toList().forEach { layer -> if (layer !in layers) show(layer, owner) }
        layers.forEach { layer -> hide(layer, owner) }
    }

    fun releaseAll(owner: String) {
        hidden.keys.toList().forEach { layer -> show(layer, owner) }
    }

    fun clear() = hidden.clear()

    const val DefaultOwner = "hollowengine"

    /** Owner used for hide requests that arrived from the server. */
    const val ServerOwner = "server"
}
