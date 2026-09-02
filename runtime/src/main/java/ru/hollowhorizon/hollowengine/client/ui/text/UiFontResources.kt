package ru.hollowhorizon.hollowengine.client.ui.text

import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener

/**
 * Ticks over whenever the resource manager reloads, so the font caches know their contents are gone.
 */
internal object UiFontResources : ResourceManagerReloadListener {
    @Volatile
    var generation: Int = 0
        private set

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        generation++
    }
}
