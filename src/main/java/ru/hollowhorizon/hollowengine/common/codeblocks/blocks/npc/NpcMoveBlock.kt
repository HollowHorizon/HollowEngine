package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.move

@Serializable
@SerialName("hollowengine:npc/move")
class NpcMoveBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.NPCS

    val npc by input<NpcEntity>("npc")
    val target by input<Any>("target")
    val speed by input<Number>("speed")

    override suspend fun execute() {
        val target = target()
        val speed = speed().toDouble()

        when (target) {
            is Vec3 -> npc().move(target, speed = speed)
            is Entity -> npc().move(target, speed = speed)
        }
    }

    override fun InputSlotScope.composeContent() {
        Column(Grow.Std) {
            Row(Grow.Std) {
                Text("hollowengine.gui.codeblocks.label.npc".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                Box(Grow.Std) {  }
                InputSlot(npc)
            }
            Box { modifier.margin(Dimensions.PaddingNormal.scaled()) }
            Row(Grow.Std) {
                Text("hollowengine.gui.codeblocks.label.npc_moves_to".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                Box(Grow.Std) {  }
                InputSlot("target", ExpressionType.anyOf(
                    typeOf<Vec3>(),
                    typeOf<Entity>(),
                ))
            }
            Box { modifier.margin(Dimensions.PaddingNormal.scaled()) }
            Row(Grow.Std) {
                Text("hollowengine.gui.codeblocks.label.npc_speed".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                Box(Grow.Std) {  }
                InputSlot(speed)
            }
        }
    }
}