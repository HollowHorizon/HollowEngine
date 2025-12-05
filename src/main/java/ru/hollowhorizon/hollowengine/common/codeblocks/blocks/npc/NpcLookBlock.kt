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
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.look

@Serializable
@SerialName("hollowengine:npc/look")
class NpcLookBlock : CodeBlock() {
    override suspend fun execute(context: BlockContext): Any? {
        val npcEntity = inputs["npc"]?.execute(context) as? NpcEntity ?: return next?.execute(context)
        val target = inputs["target"]?.execute(context)

        when (target) {
            is Vec3 -> npcEntity.look(target)
            is Entity -> npcEntity.look(target)
        }

        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("NPC Look") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        InputSlot("npc", AnyType)
        Text("At") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        InputSlot("target", AnyType)
    }
}