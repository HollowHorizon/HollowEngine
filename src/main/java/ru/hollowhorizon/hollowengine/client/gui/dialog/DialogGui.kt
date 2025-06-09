package ru.hollowhorizon.hollowengine.client.gui.dialog

import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.kool.Entity
import ru.hollowhorizon.hc.client.kool.KoolScreen
import ru.hollowhorizon.hc.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity

class DialogGui : KoolScreen() {
    override fun Scene.setup() {

        addPanelSurface(IdeTheme.colors, IdeTheme.sizes) {
            modifier.backgroundColor(Color("00000088"))
                .layout(CellLayout)
            Entity(NpcEntity(Minecraft.getInstance().level!!)) {
                modifier.size(Grow.Std, Grow.Std)
                    .mouseRotation()
            }
            Image("hollowengine:textures/gui/dialogues/dialogue_box.png") {
                modifier.size(Grow(0.75f), Grow(0.35f))
                    .align(AlignmentX.Center, AlignmentY.Bottom)
                    .border(RectBorder(colors.primary, sizes.borderWidth))

                Text("Ну типа хеллоу ворлд!") {
                    modifier.zLayer(5)
                        .margin(start = 14.dp)
                        .alignY(AlignmentY.Center)
                }
            }
        }
    }
}