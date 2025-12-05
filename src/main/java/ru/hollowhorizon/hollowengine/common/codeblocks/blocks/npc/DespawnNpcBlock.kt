package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.despawn

@Serializable
@SerialName("hollowengine:npc/despawn")
class DespawnNpcBlock : CodeBlock() {
    override suspend fun execute(context: BlockContext): Any? {
        val npcEntity = inputs["npc"]?.execute(context) as? NpcEntity
        npcEntity?.despawn()
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Удалить NPC") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        InputSlot("npc", AnyType)
    }
}