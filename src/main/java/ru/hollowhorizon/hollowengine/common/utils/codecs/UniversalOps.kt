package ru.hollowhorizon.hollowengine.common.utils.codecs

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import net.minecraft.world.item.ItemStack
import java.util.stream.Stream

sealed interface DynamicValue {
    data class Obj(val map: Map<String, DynamicValue>) : DynamicValue
    data class Arr(val list: List<DynamicValue>) : DynamicValue
    data class Prim(val value: Any?) : DynamicValue
    data object Null : DynamicValue
}

object UniversalOps : DynamicOps<DynamicValue> {
    override fun empty(): DynamicValue = DynamicValue.Null

    override fun <U> convertTo(
        outOps: DynamicOps<U>,
        input: DynamicValue,
    ): U = when (input) {
        is DynamicValue.Prim -> when (val v = input.value) {
            is String -> outOps.createString(v)
            is Boolean -> outOps.createBoolean(v)
            is Number -> outOps.createNumeric(v)
            null -> outOps.empty()
            else -> outOps.createString(v.toString())
        }

        is DynamicValue.Arr -> {
            val converted = input.list.stream().map { convertTo(outOps, it) }
            outOps.createList(converted)
        }

        is DynamicValue.Obj -> {
            val map = input.map.map {
                outOps.createString(it.key) to convertTo(outOps, it.value)
            }.toMap(mutableMapOf())
            outOps.createMap(map)
        }

        DynamicValue.Null -> outOps.empty()
    }

    override fun getNumberValue(input: DynamicValue?): DataResult<Number> {
        val number = (input as? DynamicValue.Prim)?.value as? Number
        return number?.let { DataResult.success(it) } ?: DataResult.error { "Not a number" }
    }

    override fun createNumeric(i: Number?): DynamicValue = DynamicValue.Prim(i)

    override fun getStringValue(input: DynamicValue?): DataResult<String> =
        ((input as? DynamicValue.Prim)?.value as? String)
            ?.let { DataResult.success(it) } ?: DataResult.error { "Not a string" }

    override fun createString(value: String?): DynamicValue = DynamicValue.Prim(value)

    override fun mergeToList(
        list: DynamicValue?,
        value: DynamicValue?,
    ): DataResult<DynamicValue?>? {
        TODO("Not yet implemented")
    }

    override fun mergeToMap(
        map: DynamicValue?,
        key: DynamicValue?,
        value: DynamicValue?,
    ): DataResult<DynamicValue?>? {
        TODO("Not yet implemented")
    }

    override fun getMapValues(input: DynamicValue): DataResult<Stream<Pair<DynamicValue, DynamicValue>>> =
        (input as? DynamicValue.Obj)?.map
            ?.mapKeys { DynamicValue.Prim(it.key) as DynamicValue }
            ?.let {
                val stream = it.toList().map { Pair.of(it.first, it.second) }.stream()
                DataResult.success(stream)
            }
            ?: DataResult.error { "Not an object" }

    override fun createMap(map: Stream<Pair<DynamicValue, DynamicValue>>): DynamicValue =
        DynamicValue.Obj(map.toList().mapNotNull {
            val key = (it.first as? DynamicValue.Prim)?.value as? String ?: return@mapNotNull null
            key to it.second
        }.toMap())

    override fun getStream(input: DynamicValue): DataResult<Stream<DynamicValue>> {
        TODO("Not yet implemented")
    }

    override fun createList(input: Stream<DynamicValue>): DynamicValue =
        DynamicValue.Arr(input.toList())

    override fun remove(
        input: DynamicValue?,
        key: String?,
    ): DynamicValue? {
        TODO("Not yet implemented")
    }

}

class CodecSerializer<T>(private val codec: Codec<T>) : KSerializer<T> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("CodecSerializer")

    override fun serialize(encoder: Encoder, value: T) {
        val dyn = codec.encodeStart(UniversalOps, value).result()
            .orElseThrow { IllegalStateException("Failed to encode with codec") }

        encodeDynamic(encoder, dyn)
    }

    override fun deserialize(decoder: Decoder): T {
        val dyn = decodeDynamic(decoder)
        return codec.parse(UniversalOps, dyn).result()
            .orElseThrow { IllegalStateException("Failed to decode with codec") }
    }

    private fun encodeDynamic(encoder: Encoder, value: DynamicValue) {
        when (value) {
            is DynamicValue.Prim -> when (val v = value.value) {
                is String -> encoder.encodeString(v)
                is Boolean -> encoder.encodeBoolean(v)
                is Number -> encoder.encodeDouble(v.toDouble())
                null -> encoder.encodeNull()
                else -> encoder.encodeString(v.toString())
            }
            is DynamicValue.Arr -> {
                val composite = encoder.beginCollection(descriptor, value.list.size)
                value.list.forEachIndexed { i, elem ->
                    composite.encodeSerializableElement(descriptor, i, this, elem as T)
                }
                composite.endStructure(descriptor)
            }
            is DynamicValue.Obj -> {
                val composite = encoder.beginStructure(descriptor)
                for ((k, v) in value.map)
                    composite.encodeSerializableElement(descriptor, 0, this, DynamicValue.Prim(k) as T)
                        .also { encodeDynamic(encoder, v) }
                composite.endStructure(descriptor)
            }
            DynamicValue.Null -> encoder.encodeNull()
        }
    }

    private fun decodeDynamic(decoder: Decoder): DynamicValue = when (decoder) {
        is JsonDecoder -> jsonToDynamic(decoder.decodeJsonElement())
        else -> DynamicValue.Prim(decoder.decodeString()) // fallback
    }

    private fun jsonToDynamic(json: JsonElement): DynamicValue = when (json) {
        is JsonObject -> DynamicValue.Obj(json.mapValues { jsonToDynamic(it.value) })
        is JsonArray -> DynamicValue.Arr(json.map { jsonToDynamic(it) })
        is JsonPrimitive -> {
            when {
                json.isString -> DynamicValue.Prim(json.content)
                json.booleanOrNull != null -> DynamicValue.Prim(json.boolean)
                json.longOrNull != null -> DynamicValue.Prim(json.long)
                json.doubleOrNull != null -> DynamicValue.Prim(json.double)
                else -> DynamicValue.Prim(json.content)
            }
        }
        JsonNull -> DynamicValue.Null
    }
}

