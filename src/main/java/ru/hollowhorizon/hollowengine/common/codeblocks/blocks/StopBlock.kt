package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.model.EndBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.currentInstance

@Serializable
@SerialName("hollowengine:stop")
class StopBlock: StatementBlock(), EndBlock {
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
    val condition by input<Boolean>("condition")

    override suspend fun execute() {
        if(condition()) currentInstance().stop()
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("Завершить скрипт, если")
        InputSlot(condition)
    }
}

