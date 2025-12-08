package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType

class InterpreterValue<T: Any>(
    override val name: String,
    override val type: ExpressionType,
    val interpreter: Lazy<CodeBlockInterpreter<T>>,
) : InputValue<T> {

    context(context: BlockContext)
    override suspend fun invoke(): T {
        return interpreter.value.execute(context)
    }
}