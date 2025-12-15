package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.say

@Serializable
@SerialName("hollowengine:npc/say")
class NpcSayBlock : StatementBlock() {
    val npc by input<NpcEntity>("npc")
    val text by input<String>("text")

    override suspend fun BlockContext.execute() {
        npc().say(text())
    }

    override fun InputSlotScope.composeContent() {
        Text("НИП") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
        Box(Grow.Std) {  }
        Text("Говорит:") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).margin(start = 5.dp).bold() }
        InputSlot(text)
    }
}