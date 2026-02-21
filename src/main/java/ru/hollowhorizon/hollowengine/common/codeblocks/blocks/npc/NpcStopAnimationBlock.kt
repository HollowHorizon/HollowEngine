package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf

@Serializable
@SerialName("hollowengine:npcs/stop_animation")
class NpcStopAnimationBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.NPCS

    val npc by input<NpcEntity>()
    val animation by input<String>()

    override suspend fun execute() {
        val npc = npc()
        val anim = animation()
        if (anim.isBlank()) return
        NpcAnimationTransitionPacket(npc.id, from = anim, to = null).sendTrackingEntityAndSelf(npc)
    }

    override fun InputSlotScope.composeContent() {
        Text("Остановить анимацию") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
        Text("=") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(animation)
    }
}
