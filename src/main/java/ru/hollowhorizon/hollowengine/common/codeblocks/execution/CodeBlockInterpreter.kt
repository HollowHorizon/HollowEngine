package ru.hollowhorizon.hollowengine.common.codeblocks.execution

import ru.hollowhorizon.hollowengine.common.codeblocks.BlockFrame
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.find
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.currentFile
import kotlin.coroutines.coroutineContext

interface BlockModelInterpreter<T : Any> {
    suspend fun execute(): T
}

class ExpressionBlockInterpreter<T : Any>(val expression: ExpressionBlock) : BlockModelInterpreter<T> {
    override suspend fun execute(): T {
        return expression.execute() as T
    }
}

class CodeBlockInterpreter<T : Any>(val root: StatementBlock) : BlockModelInterpreter<T> {
    @Suppress("UNCHECKED_CAST")
    override suspend fun execute(): T {
        val frame = coroutineContext[BlockFrame.Key] ?: error("No frame found")
        val tag = frame.tag

        var current: StatementBlock? =
            if (tag.contains("uuid")) root.find(tag.getUUID("uuid"))
            else root

        var result: Any? = null
        while (current != null) {
            tag.putUUID("uuid", current.uuid)
            currentFile().system.markDirty()
            val block: StatementBlock = current
            result = scoped { block.execute() }
            current = current.next
        }
        return result as T
    }
}
