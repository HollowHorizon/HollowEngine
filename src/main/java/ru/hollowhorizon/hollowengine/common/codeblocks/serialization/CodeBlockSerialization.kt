package ru.hollowhorizon.hollowengine.common.codeblocks.serialization

import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock

fun CodeBlock.flatten(): Set<CodeBlock> = buildSet {
    add(this@flatten)
    next?.flatten()?.let { addAll(it) }
    inputs.values.forEach {
        addAll(it.flatten())
    }
}
