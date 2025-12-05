package ru.hollowhorizon.hollowengine.common.codeblocks.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import java.util.*

object CodeBlockSerializer : KSerializer<List<CodeBlock>> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: List<CodeBlock>,
    ) {
        val array = value
            .flatMap { it.flatten() }
            .map {
                JsonObject(buildMap {
                    put("node", CodeBlockFormat.Json.encodeToJsonElement(it))
                    it.next?.let { put("next", JsonPrimitive(it.uuid.toString())) }
                    if (it.inputs.isNotEmpty()) put("inputs", JsonObject(buildMap {
                        it.inputs.forEach { (key, block) ->
                            put(key, JsonPrimitive(block.uuid.toString()))
                        }
                    }))
                    if (it.parent == null && it.parentBlock == null) {
                        put("x", JsonPrimitive(it.positionX.value))
                        put("y", JsonPrimitive(it.positionY.value))
                    }
                })
            }
            .let { JsonArray(it) }
        (encoder as JsonEncoder).encodeJsonElement(array)
    }

    override fun deserialize(decoder: Decoder): List<CodeBlock> {
        val jsonNodes = ((decoder as JsonDecoder).decodeJsonElement() as JsonArray)
        val nodes = jsonNodes.map {
            CodeBlockFormat.Json.decodeFromJsonElement<CodeBlock>(it.jsonObject["node"]!!)
        }.associateBy { it.uuid }
        jsonNodes.forEach { node ->
            val current = UUID.fromString(node.jsonObject["node"]!!.jsonObject["uuid"]!!.jsonPrimitive.content)
            val next = node.jsonObject["next"]?.jsonPrimitive?.content?.let { UUID.fromString(it) }
            if (next != null) {
                nodes[current]?.next = nodes[next]
                nodes[next]?.parent = nodes[current]
            }
            node.jsonObject["inputs"]?.jsonObject?.forEach { (key, value) ->
                val uuid = UUID.fromString(value.jsonPrimitive.content)
                nodes[current]?.let {
                    val input = nodes[uuid] ?: return@let
                    it.inputs[key] = input
                    input.parentBlock = it
                    input.parentInputName = key
                }
            }
            node.jsonObject["x"]?.jsonPrimitive?.floatOrNull?.let { nodes[current]?.positionX?.set(it) }
            node.jsonObject["y"]?.jsonPrimitive?.floatOrNull?.let { nodes[current]?.positionX?.set(it) }
        }
        return nodes.values.filter { it.parent == null && it.parentBlock == null }.toList()
    }
}