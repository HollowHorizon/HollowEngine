package ru.hollowhorizon.hollowengine.common.codeblocks.serialization

import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock

fun BlockModel.flatten(): Set<BlockModel> = buildSet {
    add(this@flatten)
    (this@flatten as? StatementBlock)?.next?.flatten()?.let { addAll(it) }
    inputs.values.forEach {
        addAll(it.flatten())
    }
}
