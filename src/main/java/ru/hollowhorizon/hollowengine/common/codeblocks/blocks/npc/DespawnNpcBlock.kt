package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.despawn

@Serializable
@SerialName("hollowengine:npc/despawn")
class DespawnNpcBlock : StatementBlock() {
    val npc by input<NpcEntity>("npc")

    override suspend fun BlockContext.execute() {
        npc().despawn()
    }

    override fun InputSlotScope.composeContent() {
        Text("Удалить НИПа") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
    }
}