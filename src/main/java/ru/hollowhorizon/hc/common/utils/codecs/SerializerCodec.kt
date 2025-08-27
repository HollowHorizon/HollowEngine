package ru.hollowhorizon.hc.common.utils.codecs

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.JsonOps
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import com.mojang.datafixers.util.Pair as MojangPair

class SerializerCodec<X>(private val serializer: KSerializer<X>) : Codec<X> {
    override fun <T : Any> encode(input: X, ops: DynamicOps<T>, prefix: T): DataResult<T> {
        return DataResult.success(
            when (ops) {
                is JsonOps, is KJsonOps -> {
                    val element = Json.encodeToJsonElement(serializer, input)
                    KJsonOps.convertTo(ops, element)
                }

                else -> throw UnsupportedOperationException()
            }
        )
    }

    override fun <T> decode(ops: DynamicOps<T>, input: T): DataResult<MojangPair<X, T>> {
        return DataResult.success(
            MojangPair.of(
                when (ops) {
                    is JsonOps, is KJsonOps -> {
                        Json.decodeFromJsonElement(serializer, ops.convertTo(KJsonOps, input))
                    }

                    else -> throw UnsupportedOperationException()
                }, input
            )
        )
    }
}