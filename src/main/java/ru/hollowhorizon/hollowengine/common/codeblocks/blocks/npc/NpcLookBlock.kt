package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.look

@Serializable
@SerialName("hollowengine:npc/look")
class NpcLookBlock : StatementBlock() {
    val npc by input<NpcEntity>("npc")
    val target by input<Any>("target")

    override suspend fun BlockContext.execute() {
        val npcEntity = npc()
        val target = target()

        when (target) {
            is Vec3 -> npcEntity.look(target)
            is Entity -> npcEntity.look(target)
        }
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("НИП") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
        Box(Grow.Std) {  }
        Text("смотрит на") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot("target", ExpressionType.anyOf(
            typeOf<Entity>(),
            typeOf<Vec3>()
        ))
    }
}