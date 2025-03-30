package ru.hollowhorizon.hollowengine.compiler.coroutine.generators

import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlinx.serialization.compiler.backend.ir.BaseIrGenerator
import ru.hollowhorizon.hollowengine.compiler.serializationContext

class CoroutineGenerator(
    val coroutine: IrClass,
    val serializer: IrClass,
    val descriptorProperty: IrProperty,
    val serializerDescriptor: IrFunctionExpression,
    val updateFunction: IrSimpleFunction,
) {
    internal val generator = object : BaseIrGenerator(coroutine, serializationContext) {}
    internal val serializableFields = HashSet<IrField>()

    fun addField(name: Name, type: IrType) = coroutine.addField {
        this.name = name
        this.type = type
    }

    fun addSerializableField(field: IrField) {
        serializableFields += field
    }
}

val CoroutineGenerator.receiver get() = coroutine.thisReceiver ?: error("Coroutine this receiver not found")