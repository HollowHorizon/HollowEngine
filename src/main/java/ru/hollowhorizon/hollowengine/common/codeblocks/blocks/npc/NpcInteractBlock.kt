package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.destroyBlock
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.useBlock

@Serializable
@SerialName("hollowengine:npc/interact")
class NpcInteractBlock : StatementBlock() {
    val npc by input<NpcEntity>("npc")
    val pos by input<Vec3>("pos")

    var modeInt = 0 // 0 = Use, 1 = Destroy

    override suspend fun execute() {
        if (modeInt == 0) {
            npc().useBlock(pos())
        } else {
            npc().destroyBlock(pos())
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("НИП") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)

        ComboBox {
            modifier.width(FitContent).items(listOf("Использует блок", "Ломает блок"))
                .font(font)
            modifier.selectedIndex(modeInt)
            modifier.onItemSelected { modeInt = it }
        }

        Text("на") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(pos)
    }
}