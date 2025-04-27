package ru.hollowhorizon.hollowengine.common.scripting.kool

import de.fabmax.kool.modules.ui2.UiSurface
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hc.client.kool.KoolManager

object KoolClientManager {
    private val ACTIVE_SCENES = HashMap<String, KoolScript>()

    fun addScene(name: String, scene: KoolScript) {
        ACTIVE_SCENES[name]?.let { KoolManager.context.removeScene(it) }
        ACTIVE_SCENES[name] = scene
        KoolManager.context.addScene(scene)
    }

    fun updateScene(name: String, tag: CompoundTag) {
        ACTIVE_SCENES[name]?.let {
            it.tag = tag
            it.children.filterIsInstance<UiSurface>().forEach { it.triggerUpdate() }
        }
    }

    fun removeScene(name: String) {
        ACTIVE_SCENES.remove(name)
            ?.let { KoolManager.context.removeScene(it) }
    }

    operator fun contains(name: String) = ACTIVE_SCENES.containsKey(name)
}