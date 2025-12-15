package ru.hollowhorizon.hollowengine.common.codeblocks

import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf

interface ExpressionType {
    fun accepts(other: ExpressionType): Boolean = this == other

    companion object {
        fun anyOf(vararg types: ExpressionType) = object : ExpressionType {
            override fun accepts(other: ExpressionType): Boolean {
                return types.any { it.accepts(other) }
            }
        }

        fun allOf(vararg types: ExpressionType) = object : ExpressionType {
            override fun accepts(other: ExpressionType): Boolean {
                return types.all { it.accepts(other) }
            }
        }
    }
}

inline fun <reified T> typeOf() = KTypeExpressionType(typeOf<T>())

class KTypeExpressionType(val kType: KType): ExpressionType {
    override fun accepts(other: ExpressionType): Boolean {
        if (other === AnyType) return true

        return (other as? KTypeExpressionType)?.kType?.isSubtypeOf(this.kType) == true
    }

    override fun toString() = kType.toString()
}

val BlockModel.expressionTypeOrNull: ExpressionType?
    get() = (this as? ExpressionBlock)?.expressionType

object AnyType: ExpressionType {
    override fun accepts(type: ExpressionType) = true
}