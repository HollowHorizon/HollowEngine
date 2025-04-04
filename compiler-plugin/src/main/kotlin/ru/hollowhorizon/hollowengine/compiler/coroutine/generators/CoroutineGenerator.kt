package ru.hollowhorizon.hollowengine.compiler.coroutine.generators

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlinx.serialization.compiler.backend.ir.BaseIrGenerator
import ru.hollowhorizon.hollowengine.compiler.serializationContext

class CoroutineGenerator(
    val coroutine: IrClass,
    val serializer: IrClass,
    val descriptorProperty: IrProperty,
    val serializerDescriptor: IrFunctionReference,
    val invokeFunction: IrFunction,
    val restoreFunction: IrSimpleFunction,
) {
    internal val generator = object : BaseIrGenerator(coroutine, serializationContext) {}
    internal val serializableFields = HashSet<IrField>()
    internal val branchMap = hashMapOf<Int, HashSet<Pair<IrField, IrExpression>>>()

    lateinit var stateIndex: IrField

    fun addField(name: Name, type: IrType) = coroutine.addField {
        this.name = name
        this.type = type
    }.apply {
        if(name == Name.special("<stateIndex>")) stateIndex = this
    }

    fun addSerializableField(field: IrField) {
        serializableFields += field
    }

    fun addRestorableField(field: IrField, branch: Int, initializer: IrExpression) {
        branchMap.getOrPut(branch) { LinkedHashSet() }.add(field to initializer)
    }
}

fun groupRestorableFields(
    branchMap: HashMap<Int, HashSet<Pair<IrField, IrExpression>>>
): MutableMap<Pair<Int, Int>, MutableMap<IrField, IrExpression>> {
    val groupedMap = mutableMapOf<Pair<Int, Int>, MutableMap<IrField, IrExpression>>()
    val fieldBranches = mutableMapOf<IrField, MutableMap<Int, IrExpression>>()

    // Собираем, в каких ветках встречается каждое поле
    for ((branch, pairs) in branchMap) {
        for ((field, initializer) in pairs) {
            fieldBranches.getOrPut(field) { mutableMapOf() }[branch] = initializer
        }
    }

    // Для каждого поля строим диапазоны
    for ((field, changes) in fieldBranches) {
        val sortedChanges = changes.entries.sortedBy { it.key }
        for ((index, change) in sortedChanges.withIndex()) {
            val nextBranch = sortedChanges.getOrNull(index + 1)?.key ?: Int.MIN_VALUE
            val fieldMap = groupedMap.getOrPut(change.key to nextBranch) { mutableMapOf() }
            fieldMap[field] = change.value
        }
    }

    return groupedMap
}

val CoroutineGenerator.receiver get() = coroutine.thisReceiver ?: error("Coroutine this receiver not found")