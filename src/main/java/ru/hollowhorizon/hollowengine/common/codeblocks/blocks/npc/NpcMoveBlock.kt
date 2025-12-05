package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionTypes
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.move

@Serializable
@SerialName("hollowengine:npc/move")
class NpcMoveBlock : CodeBlock() {
    override suspend fun execute(context: BlockContext): Any? {
        val npcEntity = inputs["npc"]?.execute(context) as? NpcEntity ?: return next?.execute(context)
        val target = inputs["target"]?.execute(context)
        val speed = inputs["speed"]?.execute(context).toString().toDoubleOrNull() ?: 1.0

        when (target) {
            is Vec3 -> npcEntity.move(target, speed = speed)
            is Entity -> npcEntity.move(target, speed = speed)
        }

        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Column {
            Row {
                Text("NPC Move") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
                InputSlot("npc", AnyType) // Сюда подключаем GetVarBlock
            }
            Row {
                Text("To") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
                InputSlot("target", AnyType) // LocationBlock или GetVarBlock(Entity)
            }
            Row {
                Text("Speed") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
                InputSlot("speed", ExpressionTypes.NUMBER)
            }
        }
    }
}