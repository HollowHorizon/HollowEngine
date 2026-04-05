package ru.hollowhorizon.hollowengine.common.codeblocks.execution

import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType

interface InputValue<T> {
    val name: String
    val type: ExpressionType

    suspend operator fun invoke(): T
}