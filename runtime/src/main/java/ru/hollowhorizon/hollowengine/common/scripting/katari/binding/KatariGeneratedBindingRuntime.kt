package ru.hollowhorizon.hollowengine.common.scripting.katari.binding

import com.sunnychung.lib.multiplatform.kotlite.model.BooleanValue
import com.sunnychung.lib.multiplatform.kotlite.model.ByteValue
import com.sunnychung.lib.multiplatform.kotlite.model.CharValue
import com.sunnychung.lib.multiplatform.kotlite.model.DoubleValue
import com.sunnychung.lib.multiplatform.kotlite.model.FloatValue
import com.sunnychung.lib.multiplatform.kotlite.model.FunctionResponse
import com.sunnychung.lib.multiplatform.kotlite.model.IntValue
import com.sunnychung.lib.multiplatform.kotlite.model.KotlinValueHolder
import com.sunnychung.lib.multiplatform.kotlite.model.ListValue
import com.sunnychung.lib.multiplatform.kotlite.model.LongValue
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeEnumValue
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeHostValue
import com.sunnychung.lib.multiplatform.kotlite.model.NullValue
import com.sunnychung.lib.multiplatform.kotlite.model.RuntimeMapEntry
import com.sunnychung.lib.multiplatform.kotlite.model.RuntimeValue
import com.sunnychung.lib.multiplatform.kotlite.model.ShortValue
import com.sunnychung.lib.multiplatform.kotlite.model.StringValue
import com.sunnychung.lib.multiplatform.kotlite.model.SymbolTable
import com.sunnychung.lib.multiplatform.kotlite.model.XmlValue
import com.sunnychung.lib.multiplatform.kotlite.stdlib.collections.MapValue
import kotlinx.coroutines.runBlocking
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariHostReferences
import kotlin.reflect.KClass

object KatariGeneratedBindingRuntime {
    private val hostTypes = linkedMapOf<String, HostTypeRegistration>()
    private val hostTypeIdsByClass = linkedMapOf<KClass<*>, String>()

    fun registerHostType(
        typeClass: KClass<*>,
        typeId: String,
        superTypeIds: List<String>,
    ) {
        hostTypes[typeId] = HostTypeRegistration(typeId, typeClass, superTypeIds.toSet())
        hostTypeIdsByClass[typeClass] = typeId
    }

    fun toRuntimeValue(value: Any?, hostTypeId: String? = null, symbolTable: SymbolTable): RuntimeValue {
        return when (value) {
            null, Unit -> NullValue
            is RuntimeValue -> value
            is Map<*, *> -> toRuntimeMapValue(value, symbolTable)
            is Iterable<*> -> toRuntimeListValue(value, symbolTable)
            is Boolean -> BooleanValue(value, symbolTable)
            is Int -> IntValue(value, symbolTable)
            is Long -> LongValue(value, symbolTable)
            is Byte -> ByteValue(value, symbolTable)
            is Short -> ShortValue(value, symbolTable)
            is Double -> DoubleValue(value, symbolTable)
            is Float -> FloatValue(value, symbolTable)
            is Char -> CharValue(value, symbolTable)
            is String -> StringValue(value, symbolTable)
            is Enum<*> -> NarrativeEnumValue(
                typeId = hostTypeId ?: value::class.simpleName
                ?: error("Missing Katari enum type id for `${value::class.qualifiedName}`"),
                entryName = value.name,
                ordinal = value.ordinal,
                symbolTable = symbolTable
            )

            else -> KatariHostReferences.capture(value, symbolTable) ?: NarrativeHostValue(
                typeId = resolveHostTypeId(value, hostTypeId),
                value = value,
                symbolTable = symbolTable,
            )
        }
    }

    private fun toRuntimeListValue(value: Iterable<*>, symbolTable: SymbolTable): RuntimeValue {
        val elements = value.map { toRuntimeValue(it, symbolTable = symbolTable) }
        val elementType = elements.firstOrNull()?.type() ?: symbolTable.AnyType
        return ListValue(elements, elementType, symbolTable)
    }

    private fun toRuntimeMapValue(value: Map<*, *>, symbolTable: SymbolTable): RuntimeValue {
        val entries = value.map { (key, mapValue) ->
            RuntimeMapEntry(
                toRuntimeValue(key, symbolTable = symbolTable),
                toRuntimeValue(mapValue, symbolTable = symbolTable),
            )
        }
        val keyType = entries.firstOrNull()?.key?.type() ?: symbolTable.AnyType
        val valueType = entries.firstOrNull()?.value?.type() ?: symbolTable.AnyType
        return MapValue(entries.associate { it.key to it.value }, keyType, valueType, symbolTable)
    }

    fun asBoolean(value: RuntimeValue?, name: String): Boolean {
        return (value as? BooleanValue)?.value ?: error("$name expects Boolean")
    }

    fun asInt(value: RuntimeValue?, name: String): Int {
        return when (value) {
            is IntValue -> value.value
            is DoubleValue -> value.value.toInt()
            else -> error("$name expects Int")
        }
    }

    fun asDouble(value: RuntimeValue?, name: String): Double {
        return when (value) {
            is IntValue -> value.value.toDouble()
            is DoubleValue -> value.value
            else -> error("$name expects Double")
        }
    }

    fun asFloat(value: RuntimeValue?, name: String): Float {
        return asDouble(value, name).toFloat()
    }

    fun asString(value: RuntimeValue?, name: String): String {
        return when (value) {
            is StringValue -> value.value
            is IntValue -> value.value.toString()
            is DoubleValue -> value.value.toString()
            is BooleanValue -> value.value.toString()
            else -> error("$name expects String")
        }
    }

    fun asAny(value: RuntimeValue?, name: String): Any? {
        return when (value) {
            null, NullValue -> null
            is BooleanValue -> value.value
            is IntValue -> value.value
            is LongValue -> value.value
            is ByteValue -> value.value
            is ShortValue -> value.value
            is DoubleValue -> value.value
            is FloatValue -> value.value
            is CharValue -> value.value
            is StringValue -> value.value
            is NarrativeHostValue -> value.value
            is NarrativeEnumValue -> value
            is KotlinValueHolder<*> -> value.value
            else -> error("$name has unsupported generic runtime value `${value::class.qualifiedName}`")
        }
    }

    fun asXml(value: RuntimeValue?, name: String): XmlValue {
        return value as? XmlValue ?: error("$name expects XmlValue")
    }

    fun <T> asList(
        value: RuntimeValue?,
        name: String,
        convertElement: (RuntimeValue, Int) -> T,
    ): List<T> {
        val listValue = (value as? KotlinValueHolder<*>)?.value as? List<*>
            ?: error("$name expects List")
        return listValue.mapIndexed { index, item ->
            convertElement(item as? RuntimeValue ?: error("$name[$index] expects runtime value"), index)
        }
    }

    fun <K, V> asMap(
        value: RuntimeValue?,
        name: String,
        convertKey: (RuntimeValue, Int) -> K,
        convertValue: (RuntimeValue, Int) -> V,
    ): Map<K, V> {
        val mapValue = (value as? KotlinValueHolder<*>)?.value as? Map<*, *>
            ?: error("$name expects Map")
        return mapValue.entries.mapIndexed { index, entry ->
            val key = entry.key as? RuntimeValue ?: error("$name key[$index] expects runtime value")
            val itemValue = entry.value as? RuntimeValue ?: error("$name value[$index] expects runtime value")
            convertKey(key, index) to convertValue(itemValue, index)
        }.toMap()
    }

    inline fun <reified T : Any> asHost(value: RuntimeValue?, typeId: String, name: String): T {
        return runBlocking { awaitHost(value, typeId, name) }
    }

    suspend inline fun <reified T : Any> awaitHost(value: RuntimeValue?, typeId: String, name: String): T {
        val host = value as? NarrativeHostValue ?: error("$name expects host value `$typeId`")
        val resolved = KatariHostReferences.resolve(host.value)
        if (host.typeId != typeId && resolved !is T) error("$name expects `$typeId`, got `${host.typeId}`")
        return resolved as? T ?: error("$name has unexpected host value `$resolved`")
    }

    suspend inline fun <T : Any> awaitNullable(
        value: RuntimeValue?,
        convert: suspend (RuntimeValue) -> T,
    ): T? {
        if (value == null || value == NullValue) return null
        return convert(value)
    }

    inline fun <reified T : Enum<T>> asEnum(value: RuntimeValue?, typeId: String, name: String): T {
        val katariEnumValue = value as? NarrativeEnumValue ?: error("$name expects enum value `$typeId`")
        if (katariEnumValue.typeId != typeId) error("$name expects `$typeId`, got `${katariEnumValue.typeId}`")
        return enumValueOf<T>(katariEnumValue.entryName)
    }

    inline fun <reified T : Any> nullable(
        value: RuntimeValue?,
        convert: (RuntimeValue) -> T,
    ): T? {
        if (value == null || value == NullValue) return null
        return convert(value)
    }

    private fun resolveHostTypeId(value: Any, expectedTypeId: String?): String {
        val candidates = hostTypes.values
            .filter { it.typeClass.isInstance(value) }
            .filter { expectedTypeId == null || it.typeId == expectedTypeId || hasSuperType(it.typeId, expectedTypeId) }
        val best = candidates.maxWithOrNull(
            compareBy<HostTypeRegistration> { hostTypeDistance(it.typeId) }
                .thenBy { if (it.typeClass == value::class) 1 else 0 }
        )
        if (best != null) return best.typeId

        if (expectedTypeId != null) {
            val expected = hostTypes[expectedTypeId]
            if (expected == null || expected.typeClass.isInstance(value)) return expectedTypeId
        }

        return hostTypeIdsByClass[value::class]
            ?: error("Missing Katari host type id for `${value::class.qualifiedName}`")
    }

    suspend fun <T> awaitList(
        value: RuntimeValue?,
        name: String,
        convertElement: suspend (RuntimeValue, Int) -> T,
    ): List<T> {
        val listValue = (value as? KotlinValueHolder<*>)?.value as? List<*>
            ?: error("$name expects List")
        val result = ArrayList<T>(listValue.size)
        listValue.forEachIndexed { index, item ->
            result += convertElement(item as? RuntimeValue ?: error("$name[$index] expects runtime value"), index)
        }
        return result
    }

    suspend fun <K, V> awaitMap(
        value: RuntimeValue?,
        name: String,
        convertKey: suspend (RuntimeValue, Int) -> K,
        convertValue: suspend (RuntimeValue, Int) -> V,
    ): Map<K, V> {
        val mapValue = (value as? KotlinValueHolder<*>)?.value as? Map<*, *>
            ?: error("$name expects Map")
        val result = LinkedHashMap<K, V>()
        mapValue.entries.forEachIndexed { index, entry ->
            val key = entry.key as? RuntimeValue ?: error("$name key[$index] expects runtime value")
            val itemValue = entry.value as? RuntimeValue ?: error("$name value[$index] expects runtime value")
            result[convertKey(key, index)] = convertValue(itemValue, index)
        }
        return result
    }

    private fun hasSuperType(typeId: String, expectedSuperTypeId: String): Boolean {
        val visited = mutableSetOf<String>()
        fun visit(current: String): Boolean {
            if (!visited.add(current)) return false
            val type = hostTypes[current] ?: return false
            if (expectedSuperTypeId in type.superTypeIds) return true
            return type.superTypeIds.any(::visit)
        }
        return visit(typeId)
    }

    private fun hostTypeDistance(typeId: String): Int {
        val type = hostTypes[typeId] ?: return 0
        return type.superTypeIds.maxOfOrNull { hostTypeDistance(it) + 1 } ?: 0
    }
}

data class GeneratedRuntimeValueResponse(val value: RuntimeValue) : FunctionResponse

data class GeneratedKatariErrorResponse(val message: String) : FunctionResponse

private data class HostTypeRegistration(
    val typeId: String,
    val typeClass: KClass<*>,
    val superTypeIds: Set<String>,
)
