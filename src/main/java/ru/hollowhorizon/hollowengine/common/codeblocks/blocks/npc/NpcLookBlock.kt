package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.look

@Serializable
@SerialName("hollowengine:npc/look")
class NpcLookBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.NPCS

    val npc by input<NpcEntity>("npc")
    val target by input<Any>("target")

    override suspend fun execute() {
        val npcEntity = npc()
        val target = target()

        when (target) {
            is Vec3 -> npcEntity.look(target)
            is Entity -> npcEntity.look(target)
        }
    }

    override fun InputSlotScope.composeContent() {
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