package ru.hollowhorizon.hc.client.models.bbmodel

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import ru.hollowhorizon.hc.common.utils.rl
import ru.hollowhorizon.hc.client.utils.stream

private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    allowSpecialFloatingPointValues = true
    useArrayPolymorphism = true
}

fun main() {
    val model: BlockBenchModel = json.decodeFromStream("hollowcore:models/example.bbmodel".rl.stream)

    println(model)
}

@Serializable
data class BlockBenchModel(
    val resolution: Resolution,
    val elements: List<BBElement>,
    @SerialName("outliner")
    val nodes: List<BBNode>,
    val textures: List<BBTexture>,
) {
    @Serializable
    data class Resolution(val width: Int, val height: Int)
}

@Serializable(with = BBNodeSerializer::class)
sealed class BBNode {
    @Serializable
    data class Data(
        val name: String,
        val origin: List<Float>,
        val uuid: String,
        val children: List<BBNode>,
        val value: String? = null,
    ) : BBNode()

    @Serializable
    data class UUIDData(val value: String) : BBNode()
}

object BBNodeSerializer : KSerializer<BBNode> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BBNode")

    override fun deserialize(decoder: Decoder): BBNode {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Expected JsonDecoder")
        return when (val element = input.decodeJsonElement()) {
            is JsonObject -> json.decodeFromJsonElement<BBNode.Data>(element)
            is JsonPrimitive -> BBNode.UUIDData(element.content)
            else -> throw SerializationException("Unexpected JSON element")
        }
    }

    override fun serialize(encoder: Encoder, value: BBNode) {
        val output = encoder as? JsonEncoder ?: throw SerializationException("Expected JsonEncoder")
        when (value) {
            is BBNode.Data -> output.encodeJsonElement(json.encodeToJsonElement(value))
            is BBNode.UUIDData -> output.encodeJsonElement(JsonPrimitive(value.value))
        }
    }
}

@Serializable
data class BBElement(
    val name: String,
    val from: List<Float>? = null,
    val to: List<Float>? = null,
    val origin: List<Float>,
    val uuid: String,
    @SerialName("uv_offset") val uvOffset: List<Float> = listOf(),
    val faces: BBFaces,
    val type: String,
) {


}

@Serializable
data class BBFaces(
    val north: BBFace? = null,
    val south: BBFace? = null,
    val east: BBFace? = null,
    val west: BBFace? = null,
    val up: BBFace? = null,
    val down: BBFace? = null,
) {
    @Serializable
    data class BBFace(
        val uv: List<Float>,
        val texture: Int? = -1,
    )
}

@Serializable
data class BBTexture(
    val name: String,
    val source: String,
)