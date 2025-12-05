package ru.hollowhorizon.hollowengine.common.codeblocks

interface ExpressionType {
    fun accepts(type: ExpressionType): Boolean = this == type
}
val CodeBlock.expressionTypeOrNull: ExpressionType?
    get() = (this as? ExpressionBlock)?.expressionType

enum class ExpressionTypes: ExpressionType {
    STRING, NUMBER, BOOLEAN, VEC3
}

object AnyType: ExpressionType {
    override fun accepts(type: ExpressionType) = true
}