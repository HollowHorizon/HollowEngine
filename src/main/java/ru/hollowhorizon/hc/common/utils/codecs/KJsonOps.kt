package ru.hollowhorizon.hc.common.utils.codecs

import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.MapLike
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.stream.Stream
import com.mojang.datafixers.util.Pair as MojangPair

object KJsonOps : DynamicOps<JsonElement> {
    override fun empty(): JsonElement = JsonNull

    override fun <U : Any> convertTo(outOps: DynamicOps<U>, input: JsonElement): U {
        when (input) {
            is JsonObject -> return convertMap(outOps, input)
            is JsonArray -> return convertList(outOps, input)
            is JsonNull -> return outOps.empty()
            else -> {
                val literal = input.jsonPrimitive
                if (literal.isString) return outOps.createString(literal.content)
                literal.booleanOrNull?.let { return outOps.createBoolean(it) }
                val decimal = BigDecimal(literal.content)
                return try {
                    when (val long = decimal.longValueExact()) {
                        long.toByte().toLong() -> outOps.createByte(long.toByte())
                        long.toShort().toLong() -> outOps.createShort(long.toShort())
                        long.toInt().toLong() -> outOps.createInt(long.toInt())
                        else -> outOps.createLong(long)
                    }
                } catch (_: ArithmeticException) {
                    when (val double = decimal.toDouble()) {
                        double.toFloat().toDouble() -> outOps.createFloat(double.toFloat())
                        else -> outOps.createDouble(double)
                    }
                }
            }
        }
    }

    override fun getNumberValue(input: JsonElement): DataResult<Number> {
        val literal = input.jsonPrimitive
        try {
            val decimal = BigDecimal(literal.content)

            return try {
                when (val long = decimal.longValueExact()) {
                    long.toByte().toLong() -> DataResult.success(long.toByte())
                    long.toShort().toLong() -> DataResult.success(long.toShort())
                    long.toInt().toLong() -> DataResult.success(long.toInt())
                    else -> DataResult.success(long)
                }
            } catch (_: ArithmeticException) {
                when (val double = decimal.toDouble()) {
                    double.toFloat().toDouble() -> DataResult.success(double.toFloat())
                    else -> DataResult.success(double)
                }
            }
        } catch (_: NumberFormatException) {
            return DataResult.error { "Not a number: $input" }
        }
    }

    override fun createNumeric(i: Number): JsonElement {
        return JsonPrimitive(i)
    }

    override fun getBooleanValue(input: JsonElement): DataResult<Boolean> {
        val literal = input.jsonPrimitive
        return literal.booleanOrNull?.let { DataResult.success(it) }
            ?: DataResult.error { "Not a boolean: $input" }

    }

    override fun createBoolean(value: Boolean): JsonElement = JsonPrimitive(value)

    override fun getStringValue(input: JsonElement): DataResult<String> {
        val literal = input.jsonPrimitive
        return literal.contentOrNull?.takeIf { literal.isString }?.let { DataResult.success(it) }
            ?: DataResult.error { "Not a string: $input" }
    }

    override fun createString(value: String) = JsonPrimitive(value)

    override fun mergeToList(list: JsonElement, value: JsonElement): DataResult<JsonElement> {
        if (list !is JsonArray && list != empty())
            return DataResult.error({ "mergeToList called with not a list: $list" }, list)
        if (list != empty()) {
            return DataResult.success(JsonArray(list.jsonArray + value))
        }
        return DataResult.success(JsonArray(listOf(value)))
    }

    override fun mergeToMap(map: JsonElement, key: JsonElement, value: JsonElement): DataResult<JsonElement> {
        if (map !is JsonObject && map != empty())
            return DataResult.error({ "mergeToMap called with not a map: $map" }, map)
        if (key !is JsonPrimitive || !key.jsonPrimitive.isString)
            return DataResult.error({ "key is not a string: $key" }, map)
        if (map != empty()) {
            return DataResult.success(JsonObject(map.jsonObject + mapOf(key.content to value)))
        }
        return DataResult.success(JsonObject(mapOf(key.content to value)))
    }

    override fun getMapValues(input: JsonElement): DataResult<Stream<MojangPair<JsonElement, JsonElement>>> {
        if (input !is JsonObject) return DataResult.error { "Not a json object: $input" }
        return DataResult.success(input.entries.stream().map { (key, value) ->
            MojangPair(createString(key), value)
        })
    }

    override fun getMapEntries(input: JsonElement): DataResult<Consumer<BiConsumer<JsonElement, JsonElement>>> {
        if (input !is JsonObject) return DataResult.error { "Not a json object: $input" }
        return DataResult.success(Consumer { c ->
            input.entries.forEach { (key, value) -> c.accept(createString(key), value) }
        })
    }

    override fun getMap(input: JsonElement): DataResult<MapLike<JsonElement>> {
        if (input !is JsonObject) return DataResult.error { "Not a json object: $input" }
        return DataResult.success(object : MapLike<JsonElement> {
            override fun get(key: JsonElement): JsonElement? {
                return input[key.jsonPrimitive.content]
            }

            override fun get(key: String): JsonElement? {
                return input[key]
            }

            override fun entries(): Stream<MojangPair<JsonElement, JsonElement>> {
                return input.entries.stream()
                    .map { (key, value) -> MojangPair(createString(key), value) }
            }
        })
    }

    override fun createMap(map: Stream<MojangPair<JsonElement, JsonElement>>): JsonElement {
        return JsonObject(map.map { it.first.jsonPrimitive.content to it.second }.toList().toMap())
    }

    override fun getStream(input: JsonElement): DataResult<Stream<JsonElement>> {
        return if (input is JsonArray)
            DataResult.success(input.stream())
        else
            DataResult.error { "Not a json array: $input" }
    }

    override fun getList(input: JsonElement): DataResult<Consumer<Consumer<JsonElement>>> {
        return if (input is JsonArray)
            DataResult.success(Consumer { c ->
                input.forEach {
                    c.accept(it)
                }
            })
        else
            DataResult.error { "Not a json array: $input" }
    }

    override fun createList(input: Stream<JsonElement>): JsonElement = JsonArray(input.toList())

    override fun remove(input: JsonElement, key: String): JsonElement {
        if (input is JsonObject) {
            return JsonObject(input - key)
        }
        return input
    }
}