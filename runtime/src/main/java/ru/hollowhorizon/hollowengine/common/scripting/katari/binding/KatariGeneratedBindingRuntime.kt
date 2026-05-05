package ru.hollowhorizon.hollowengine.common.scripting.katari.binding

import com.sunnychung.lib.multiplatform.kotlite.model.*

object KatariGeneratedBindingRuntime {
    fun toRuntimeValue(value: Any?, hostTypeId: String? = null, symbolTable: SymbolTable): RuntimeValue {
        return when (value) {
            null, Unit -> NullValue
            is RuntimeValue -> value
            is Boolean -> BooleanValue(value, symbolTable)
            is Int -> IntValue(value, symbolTable)
            is Double -> DoubleValue(value, symbolTable)
            is Float -> FloatValue(value, symbolTable)
            is String -> StringValue(value, symbolTable)
            is Enum<*> -> NarrativeEnumValue(
                typeId = hostTypeId ?: value::class.simpleName
                ?: error("Missing Katari enum type id for `${value::class.qualifiedName}`"),
                entryName = value.name,
                ordinal = value.ordinal,
                symbolTable = symbolTable
            )

            else -> NarrativeHostValue(
                typeId = hostTypeId ?: error("Missing Katari host type id for `${value::class.qualifiedName}`"),
                value = value,
                symbolTable = symbolTable
            )
        }
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

    inline fun <reified T : Any> asHost(value: RuntimeValue?, typeId: String, name: String): T {
        val host = value as? NarrativeHostValue ?: error("$name expects host value `$typeId`")
        if (host.typeId != typeId && host.value !is T) error("$name expects `$typeId`, got `${host.typeId}`")
        return host.value as? T ?: error("$name has unexpected host value `${host.value}`")
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
}

data class GeneratedRuntimeValueResponse(val value: RuntimeValue) : FunctionResponse

data class GeneratedKatariErrorResponse(val message: String) : FunctionResponse
