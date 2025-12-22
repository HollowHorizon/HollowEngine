package ru.hollowhorizon.hollowengine.common.codeblocks.model

import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType

@Serializable
abstract class ExpressionBlock: BlockModel() {
    abstract val expressionType: ExpressionType
}