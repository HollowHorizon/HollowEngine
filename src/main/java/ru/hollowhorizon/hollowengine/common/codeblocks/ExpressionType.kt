package ru.hollowhorizon.hollowengine.common.codeblocks

interface ExpressionType {
    fun accepts(type: ExpressionType): Boolean = this == type
}

enum class ExpressionTypes: ExpressionType {
    STRING, NUMBER, BOOLEAN
}

object AnyType: ExpressionType {
    override fun accepts(type: ExpressionType) = true
}