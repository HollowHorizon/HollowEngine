package ru.hollowhorizon.hollowengine.common.scripting.katari.binding

import com.sunnychung.lib.multiplatform.kotlite.katari.KatariValue
import com.sunnychung.lib.multiplatform.kotlite.katari.FunctionResponse

object KatariGeneratedBindingRuntime {
    fun toKatariValue(value: Any?, hostTypeId: String? = null): KatariValue {
        return when (value) {
            null, Unit -> KatariValue.Null
            is KatariValue -> value
            is Boolean -> KatariValue.Bool(value)
            is Int -> KatariValue.Int32(value)
            is Double -> KatariValue.Float64(value)
            is Float -> KatariValue.Float64(value.toDouble())
            is String -> KatariValue.Text(value)
            else -> KatariValue.HostObject(
                typeId = hostTypeId ?: error("Missing Katari host type id for `${value::class.qualifiedName}`"),
                value = value,
            )
        }
    }

    fun asBoolean(value: KatariValue?, name: String): Boolean {
        return (value as? KatariValue.Bool)?.value ?: error("$name expects Boolean")
    }

    fun asInt(value: KatariValue?, name: String): Int {
        return when (value) {
            is KatariValue.Int32 -> value.value
            is KatariValue.Float64 -> value.value.toInt()
            else -> error("$name expects Int")
        }
    }

    fun asDouble(value: KatariValue?, name: String): Double {
        return when (value) {
            is KatariValue.Int32 -> value.value.toDouble()
            is KatariValue.Float64 -> value.value
            else -> error("$name expects Double")
        }
    }

    fun asFloat(value: KatariValue?, name: String): Float {
        return asDouble(value, name).toFloat()
    }

    fun asString(value: KatariValue?, name: String): String {
        return when (value) {
            is KatariValue.Text -> value.value
            is KatariValue.Int32 -> value.value.toString()
            is KatariValue.Float64 -> value.value.toString()
            is KatariValue.Bool -> value.value.toString()
            else -> error("$name expects String")
        }
    }

    inline fun <reified T : Any> asHost(value: KatariValue?, typeId: String, name: String): T {
        val host = value as? KatariValue.HostObject ?: error("$name expects host value `$typeId`")
        if (host.typeId != typeId && host.value !is T) error("$name expects `$typeId`, got `${host.typeId}`")
        return host.value as? T ?: error("$name has unexpected host value `${host.value}`")
    }

    inline fun <reified T : Any> nullable(
        value: KatariValue?,
        convert: (KatariValue) -> T,
    ): T? {
        if (value == null || value == KatariValue.Null) return null
        return convert(value)
    }
}

data class GeneratedKatariValueResponse(val value: KatariValue) : FunctionResponse

data class GeneratedKatariErrorResponse(val message: String) : FunctionResponse
