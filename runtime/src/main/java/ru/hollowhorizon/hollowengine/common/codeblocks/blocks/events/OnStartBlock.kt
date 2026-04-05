package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.events

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.BlocksSystemReloadedEvent
import ru.hollowhorizon.hollowengine.common.events.await

@Serializable
@SerialName("hollowengine:start")
class OnStartBlock : StartBlock() {
    override val color: Color get() = CodeBlocksColors.EVENTS

    override suspend fun trigger() {
        await<BlocksSystemReloadedEvent>()
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.block.on_start".lang) {
            modifier.width(FitContent).align(AlignmentX.Center, AlignmentY.Center)
                .bold()
        }
    }
}
