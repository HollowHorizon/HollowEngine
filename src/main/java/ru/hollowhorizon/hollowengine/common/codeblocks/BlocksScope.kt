package ru.hollowhorizon.hollowengine.common.codeblocks

import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock

interface BlocksScope {
    val rootBlocks: MutableList<BlockModel>
}

fun BlockModel.walk(): Sequence<BlockModel> = sequence {
    yield(this@walk)
    for (input in inputs.values) {
        yieldAll(input.walk())
    }
    (this@walk as? StatementBlock)?.next?.let {
        yieldAll(it.walk())
    }
}

val BlockModel.parentCount: Int
    get() = (((this as? StatementBlock)?.parent ?: (this as? ExpressionBlock)?.parentBlock)?.parentCount ?: 0) + 1