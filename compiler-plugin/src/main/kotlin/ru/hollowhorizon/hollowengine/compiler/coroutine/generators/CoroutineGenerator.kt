package ru.hollowhorizon.hollowengine.compiler.coroutine.generators

import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrExpression
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
    val updateAsyncsFunction: IrFunction,
) {
    internal val generator = object : BaseIrGenerator(coroutine, serializationContext) {}
    internal val serializableFields = HashSet<IrField>()
    internal val branchMap = hashMapOf<Int, HashSet<Pair<IrField, IrExpression>>>()
    internal val asyncs = HashSet<IrField>()

    internal var asyncId = 0

    fun addField(name: Name, type: IrType) = coroutine.addField {
        this.name = name
        this.type = type
    }

    fun addSerializableField(field: IrField) {
        serializableFields += field
    }

    fun addRestorableField(field: IrField, branch: Int, initializer: IrExpression) {
        branchMap.getOrPut(branch) { LinkedHashSet() }.add(field to initializer)
    }

    fun addAsync(field: IrField) {
        asyncs += field
    }
}

fun groupRestorableFields(
    branchMap: HashMap<Int, HashSet<Pair<IrField, IrExpression>>>,
): MutableMap<Pair<Int, Int>, LinkedHashMap<IrField, IrExpression>> {
    val groupedMap = mutableMapOf<Pair<Int, Int>, LinkedHashMap<IrField, IrExpression>>()
    val fieldBranches = mutableMapOf<IrField, LinkedHashMap<Int, IrExpression>>()

    // Собираем, в каких ветках встречается каждое поле
    for ((branch, pairs) in branchMap) {
        for ((field, initializer) in pairs.sortedBy { it.first.name.asString().startsWith("coroutine$") }) {
            fieldBranches.getOrPut(field) { LinkedHashMap() }[branch] = initializer
        }
    }

    // Для каждого поля строим диапазоны
    for ((field, changes) in fieldBranches) {
        val sortedChanges = changes.entries.sortedBy { it.key }
        for ((index, change) in sortedChanges.withIndex()) {
            val nextBranch = sortedChanges.getOrNull(index + 1)?.key ?: Int.MIN_VALUE
            val fieldMap = groupedMap.getOrPut(change.key to nextBranch) { LinkedHashMap() }
            fieldMap[field] = change.value
        }
    }

    return groupedMap
}

val CoroutineGenerator.receiver get() = coroutine.thisReceiver ?: error("Coroutine this receiver not found")