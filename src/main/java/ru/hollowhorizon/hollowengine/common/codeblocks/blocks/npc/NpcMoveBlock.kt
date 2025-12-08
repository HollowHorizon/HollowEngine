package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.move

@Serializable
@SerialName("hollowengine:npc/move")
class NpcMoveBlock : CodeBlock() {
    val npc by input<NpcEntity>("npc")
    val target by input<Any>("target")
    val speed by input<Number>("speed")

    override suspend fun BlockContext.execute() {
        val target = target()
        val speed = speed().toDouble()

        when (target) {
            is Vec3 -> npc().move(target, speed = speed)
            is Entity -> npc().move(target, speed = speed)
        }
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Column(Grow.Std) {
            Row(Grow.Std) {
                Text("НИП") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                Box(Grow.Std) {  }
                InputSlot(npc) // Сюда подключаем GetVarBlock
            }
            Box { modifier.margin(sizes.smallGap * 0.5f) }
            Row(Grow.Std) {
                Text("Идёт на") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                Box(Grow.Std) {  }
                InputSlot("target", ExpressionType.anyOf(
                    typeOf<Vec3>(),
                    typeOf<Entity>(),
                ))
            }
            Box { modifier.margin(sizes.smallGap * 0.5f) }
            Row(Grow.Std) {
                Text("Со скоростью") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                Box(Grow.Std) {  }
                InputSlot(speed)
            }
        }
    }
}