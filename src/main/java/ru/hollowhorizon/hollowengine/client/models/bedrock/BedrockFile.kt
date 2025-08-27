@file:UseSerializers(Vec3fSerializer::class)

package ru.hollowhorizon.hollowengine.client.models.bedrock

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.util.Color
import kotlinx.serialization.*
import kotlinx.serialization.builtins.FloatArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import net.minecraft.client.renderer.texture.TextureManager
import ru.hollowhorizon.hollowengine.client.models.internal.Material
import ru.hollowhorizon.hollowengine.common.utils.nbt.SnakeAsUpperCaseSerializer

@Serializable
class BedrockFile(
    @SerialName("format_version")
    val formatVersion: String,
    @SerialName("minecraft:geometry")
    val geometries: List<Geometry> = emptyList(),
) {
    @Serializable
    data class Geometry(
        val description: Description,
        val bones: List<Bone> = emptyList(),
    )

    @Serializable
    data class Description(
        val identifier: String,
        val texture: String = Material.MISSING_TEXTURE.toString(),
        val color: @Serializable(ColorSerializer::class) Color = Color(1f, 1f, 1f, 1f),
        @SerialName("texture_width") val textureWidth: Int,
        @SerialName("texture_height") val textureHeight: Int,
        @SerialName("texture_translucent") val textureTranslucent: Boolean = false,
        @SerialName("visible_bounds_width") val visibleBoundsWidth: Float,
        @SerialName("visible_bounds_height") val visibleBoundsHeight: Float,
        @SerialName("visible_bounds_offset") val visibleBoundsOffset: Vec3f,
    )

    @Serializable
    data class Bone(
        val name: String,
        val parent: String? = null,
        val pivot: Vec3f = Vec3f.ZERO,
        val rotation: Vec3f = Vec3f.ZERO,
        val mirror: Boolean = false,
        val side: Side? = null,
        val cubes: List<Cube> = emptyList(),
        val locators: Map<String, Vec3f> = emptyMap(),
    )

    @Serializable
    data class Cube(
        val origin: Vec3f,
        val size: Vec3f,
        val uv: Uvs,
        val mirror: Boolean? = null,
        val inflate: Float = 0f,
    )

    @Serializable(with = UvsSerializer::class)
    sealed class Uvs {
        @Serializable(with = UvBoxSerializer::class)
        class Box(val uv: FloatArray) : Uvs()

        @Serializable
        class PerFace(
            val north: UvFace? = null,
            val east: UvFace? = null,
            val south: UvFace? = null,
            val west: UvFace? = null,
            val up: UvFace? = null,
            val down: UvFace? = null,
        ) : Uvs()
    }

    @Serializable
    class UvFace(
        val uv: FloatArray,
        @SerialName("uv_size")
        val size: FloatArray,
    )
}

@Serializable
enum class Side(val displayName: String) {
    @SerialName("front")
    FRONT("Front"),

    @SerialName("left")
    LEFT("Left"),

    @SerialName("right")
    RIGHT("Right"),

    @SerialName("back")
    BACK("Back"),
    ;

    object UpperCase : SnakeAsUpperCaseSerializer<Side>(Side.serializer())

    companion object {
        @JvmStatic
        fun getDefaultSideOrNull(availableSides: Set<Side>): Side? {
            if (availableSides.isEmpty()) return null
            for (side in entries) {
                if (availableSides.contains(side)) return side
            }
            return null
        }
    }
}

private class Vec3fSerializer : KSerializer<Vec3f> {
    private val inner = FloatArraySerializer()
    override val descriptor = inner.descriptor
    override fun deserialize(decoder: Decoder): Vec3f =
        decoder.decodeSerializableValue(inner).let { (x, y, z) -> Vec3f(x, y, z) }
    override fun serialize(encoder: Encoder, value: Vec3f) =
        encoder.encodeSerializableValue(inner, floatArrayOf(value.x, value.y, value.z))
}

private class ColorSerializer: KSerializer<Color> {
    private val inner = FloatArraySerializer()
    override val descriptor = inner.descriptor
    override fun deserialize(decoder: Decoder): Color =
        decoder.decodeSerializableValue(inner).let { (x, y, z, w) -> Color(x, y, z, w) }
    override fun serialize(encoder: Encoder, value: Color) {
        encoder.encodeSerializableValue(inner, floatArrayOf(value.r, value.g, value.b, value.a))
    }
}

private class UvsSerializer : JsonContentPolymorphicSerializer<BedrockFile.Uvs>(BedrockFile.Uvs::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out BedrockFile.Uvs> =
        when (element) {
            is JsonObject -> BedrockFile.Uvs.PerFace.serializer()
            else -> BedrockFile.Uvs.Box.serializer()
        }
}

private class UvBoxSerializer : KSerializer<BedrockFile.Uvs.Box> {
    private val inner = FloatArraySerializer()
    override val descriptor = inner.descriptor
    override fun deserialize(decoder: Decoder): BedrockFile.Uvs.Box = BedrockFile.Uvs.Box(inner.deserialize(decoder))
    override fun serialize(encoder: Encoder, value: BedrockFile.Uvs.Box) = inner.serialize(encoder, value.uv)
}