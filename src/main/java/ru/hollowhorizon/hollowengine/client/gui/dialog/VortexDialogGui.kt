package ru.hollowhorizon.hollowengine.client.gui.dialog

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.client.kool.Entity
import ru.hollowhorizon.hc.client.kool.KoolScreen
import ru.hollowhorizon.hc.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity

class VortexDialogGui : KoolScreen() {
    override fun Scene.setup() {

        addPanelSurface {
            modifier.backgroundColor(Color("00000088"))
            Entity(NpcEntity(Minecraft.getInstance().level!!)) {
                modifier.size(Grow.Std, Grow.Std)
                    .margin(75.dp)
            }
            Image("hollowengine:textures/gui/dialogues/vortex_dialog.png") {
                modifier.size(Grow.Std, Grow.Std)
                    .align(AlignmentX.Center, AlignmentY.Bottom)
            }
        }
    }
}