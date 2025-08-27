package ru.hollowhorizon.hc.common.utils.nbt

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

typealias PairAsList<K, V> =
        @Serializable(with = PairAsListSerializer::class)
        Pair<K, V>

class PairAsListSerializer<K, V>(
    keySerializer: KSerializer<K>,
    valueSerializer: KSerializer<V>,
) : JsonTransformingSerializer<Pair<K, V>>(PairSerializer(keySerializer, valueSerializer)) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return if (element is JsonArray) {
            JsonObject(
                mapOf(
                    "first" to element[0],
                    "second" to element[1]
                )
            )
        } else {
            element
        }
    }
}