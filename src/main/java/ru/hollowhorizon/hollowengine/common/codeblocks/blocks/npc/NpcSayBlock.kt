package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionTypes
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.say

@Serializable
@SerialName("hollowengine:npc/say")
class NpcSayBlock : CodeBlock() {
    override suspend fun execute(context: BlockContext): Any? {
        val npcEntity = inputs["npc"]?.execute(context) as? NpcEntity
        npcEntity?.say(inputs["text"]?.execute(context).toString())
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("НИП") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        InputSlot("npc", AnyType)

        Text("Говорит:") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).margin(start = 5.dp) }
        InputSlot("text", ExpressionTypes.STRING)
    }
}