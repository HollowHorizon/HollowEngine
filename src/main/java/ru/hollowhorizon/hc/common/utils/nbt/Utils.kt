package ru.hollowhorizon.hc.common.utils.nbt

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

typealias ListOrSingle<T> = @Serializable(with = ListOrSingleSerializer::class) List<T>

class ListOrSingleSerializer<T>(
    elementSerializer: KSerializer<T>,
) : JsonTransformingSerializer<List<T>>(ListSerializer(elementSerializer)) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonArray) element
        else JsonArray(listOf(element))

    override fun transformSerialize(element: JsonElement): JsonElement =
        (element as? JsonArray)?.singleOrNull() ?: element
}

open class SnakeAsUpperCaseSerializer<T : Any>(val inner: KSerializer<T>) : JsonTransformingSerializer<T>(inner) {
    override fun transformSerialize(element: JsonElement): JsonElement =
        if (element is JsonPrimitive && element.isString) {
            JsonPrimitive(element.content.uppercase())
        } else {
            element
        }

    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonPrimitive && element.isString) {
            JsonPrimitive(element.content.lowercase())
        } else {
            element
        }
}