package ru.hollowhorizon.hollowengine.common.codeblocks

interface BlocksScope {
    val rootBlocks: List<CodeBlock>
}

fun CodeBlock.walk(): Sequence<CodeBlock> = sequence {
    yield(this@walk)
    for (input in inputs.values) {
        yieldAll(input.walk())
    }
    next?.let {
        yieldAll(it.walk())
    }
}