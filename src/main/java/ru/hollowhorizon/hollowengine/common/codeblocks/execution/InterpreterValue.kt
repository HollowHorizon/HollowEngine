package ru.hollowhorizon.hollowengine.common.codeblocks.execution

import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType

class InterpreterValue<T: Any>(
    override val name: String,
    override val type: ExpressionType,
    val interpreter: Lazy<BlockModelInterpreter<T>>,
) : InputValue<T> {

    override suspend fun invoke(): T {
        return scoped {
            interpreter.value.execute()
        }
    }
}

class ListValue<T: Any>(
    override val name: String,
    override val type: ExpressionType,
    val values: List<InputValue<T>>,
) : InputValue<List<T>> {

    override suspend fun invoke(): List<T> {
        return values.map { it() }
    }
}