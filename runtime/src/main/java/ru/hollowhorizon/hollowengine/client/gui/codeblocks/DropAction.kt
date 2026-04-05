package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock

sealed interface DropAction {
    val target: BlockModel

    data class InsertBefore(override val target: BlockModel) : DropAction
    data class AttachAfter(override val target: StatementBlock) : DropAction
    data class AttachToInput(
        override val target: BlockModel,
        val inputName: String,
        val isStatementSlot: Boolean,
    ) : DropAction

    data class AttachToOutput(
        override val target: BlockModel,
        val outputName: String,
    ) : DropAction
}
