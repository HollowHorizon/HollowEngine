package ru.hollowhorizon.hollowengine.common.codeblocks

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.LivingEntityContainer
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.SerializableVariableContainer
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.VariableContainer
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf as kTypeOf

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

fun createContainer(type: ExpressionType): VariableContainer<*> {
    return if (typeOf<LivingEntity>().accepts(type)) {
        LivingEntityContainer<LivingEntity>()
    } else {
        // TODO: Зачем вообще теперь нужен AnyType, можно просто использовать KTypeExpressionType
        val serializer = NBTFormat.Default.serializersModule.serializer((type as KTypeExpressionType).kType) as KSerializer<Any>
        SerializableVariableContainer(serializer)
    }
}

inline fun <reified T> typeOf() = KTypeExpressionType(kTypeOf<T>())

class KTypeExpressionType(val kType: KType) : ExpressionType {
    override fun accepts(other: ExpressionType): Boolean {
        if (other === AnyType) return true

        return (other as? KTypeExpressionType)?.kType?.isSubtypeOf(this.kType) == true
    }

    override fun toString() = kType.toString()
}

val BlockModel.expressionTypeOrNull: ExpressionType?
    get() = (this as? ExpressionBlock)?.expressionType

object AnyType : ExpressionType {
    override fun accepts(type: ExpressionType) = true
}