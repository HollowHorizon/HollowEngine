package ru.hollowhorizon.hollowengine.common.scripting.kool

import de.fabmax.kool.scene.Scene
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hc.client.kool.KoolManager
import ru.hollowhorizon.hc.client.utils.literal

class KoolGuiScripts(val scene: Scene): Screen("".literal) {

    override fun added() {
        KoolManager.context.addScene(scene)
    }

    override fun removed() {
        KoolManager.context.removeScene(scene)
    }
}