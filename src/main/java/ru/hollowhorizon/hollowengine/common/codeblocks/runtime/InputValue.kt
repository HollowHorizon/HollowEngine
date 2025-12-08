package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType

interface InputValue<T> {
    val name: String
    val type: ExpressionType

    context(context: BlockContext)
    suspend operator fun invoke(): T
}