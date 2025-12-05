package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionTypes
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.destroyBlock
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.useBlock

@Serializable
@SerialName("hollowengine:npc/interact")
class NpcInteractBlock : CodeBlock() {
    var modeInt = 0 // 0 = Use, 1 = Destroy

    override suspend fun execute(context: BlockContext): Any? {
        val npcEntity = inputs["npc"]?.execute(context) as? NpcEntity ?: return next?.execute(context)
        val target = inputs["pos"]?.execute(context) as? Vec3 ?: return next?.execute(context)

        if (modeInt == 0) {
            npcEntity.useBlock(target)
        } else {
            npcEntity.destroyBlock(target)
        }

        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("НИП") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        InputSlot("npc", AnyType)

        ComboBox {
            modifier.width(FitContent).items(listOf("Использует блок", "Ломает блок"))
            modifier.selectedIndex(modeInt)
            modifier.onItemSelected { modeInt = it }
        }

        Text("на") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        InputSlot("pos", ExpressionTypes.VEC3)
    }
}