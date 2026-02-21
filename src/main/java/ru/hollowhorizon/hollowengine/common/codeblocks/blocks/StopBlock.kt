package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.model.EndBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.currentInstance

@Serializable
@SerialName("hollowengine:stop")
class StopBlock: StatementBlock(), EndBlock {
    override val color: Color get() = CodeBlocksColors.ENDS

    override suspend fun execute() {
        currentInstance().stop()
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("Завершить скрипт")
    }
}

@Serializable
@SerialName("hollowengine:stop-if")
class StopIfBlock: StatementBlock() {
    override val color: Color get() = CodeBlocksColors.ENDS

    val condition by input<Boolean>("condition")

    override suspend fun execute() {
        if(condition()) currentInstance().stop()
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("Завершить скрипт, если")
        InputSlot(condition)
    }
}

