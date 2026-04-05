package ru.hollowhorizon.hollowengine.common.codeblocks.execution

import ru.hollowhorizon.hollowengine.common.codeblocks.BlockFrame
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.find
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.currentFile
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.currentInstance
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
        val instance = currentInstance()
        val current = if (tag.contains("uuid")) {
            val savedUuid = tag.getUUID("uuid")
            root.find(savedUuid)
                ?: error("Saved execution points to missing block '$savedUuid' in script '${currentFile().path}'.")
        } else {
            root
        }

        var cursor: StatementBlock? = current
        var result: Any? = null
        while (cursor != null) {
            val block = cursor
            tag.putUUID("uuid", block.uuid)
            instance.updateCurrentBlockId(block.uuid)
            currentFile().system.markDirty()
            result = scoped { block.execute() }
            cursor = block.next
        }
        instance.updateCurrentBlockId(null)
        return result as T
    }
}

