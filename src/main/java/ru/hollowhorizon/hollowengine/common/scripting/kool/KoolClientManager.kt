package ru.hollowhorizon.hollowengine.common.scripting.kool

import de.fabmax.kool.modules.ui2.UiSurface
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.client.kool.KoolManager
import ru.hollowhorizon.hollowengine.client.kool.gl.render
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.GuiOverlay
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderOverlayEvent

object KoolClientManager {
    private val ACTIVE_SCENES = HashMap<String, KoolScript>()

    fun addScene(name: String, scene: KoolScript) {
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
    }

    operator fun contains(name: String) = ACTIVE_SCENES.containsKey(name)

    @SubscribeEvent
    fun render(event: RenderOverlayEvent.Post) {
        if(event.overlay != GuiOverlay.CROSSHAIR) return
        ACTIVE_SCENES.forEach { it.value.render() }
    }
}