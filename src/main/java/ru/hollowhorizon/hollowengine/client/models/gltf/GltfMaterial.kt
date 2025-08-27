package ru.hollowhorizon.hollowengine.client.models.gltf

import de.fabmax.kool.util.Color
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import org.joml.Vector4f
import ru.hollowhorizon.hollowengine.client.models.internal.Material

@Serializable
data class GltfMaterial(
    val name: String? = null,
    val pbrMetallicRoughness: PbrMetallicRoughness = PbrMetallicRoughness(
        baseColorFactor = listOf(
            0.5f,
            0.5f,
            0.5f,
            1f
        )
    ),
    val normalTexture: GltfTexture.Info? = null,
    val occlusionTexture: GltfTexture.Info? = null,
    val emissiveTexture: GltfTexture.Info? = null,
    val emissiveFactor: List<Float>? = null,
    val alphaMode: String = ALPHA_MODE_OPAQUE,
    val alphaCutoff: Float = 0.5f,
    val doubleSided: Boolean = false,
) {

    suspend fun toMaterial(file: GltfFile, location: ResourceLocation): Material = coroutineScope {
        val material = Material()
        val colorList = pbrMetallicRoughness.baseColorFactor
        material.color = Color(colorList[0], colorList[1], colorList[2], colorList[3])

        val baseColorTextureDeferred = pbrMetallicRoughness.baseColorTexture?.let {
            async { it.getTexture(file, location) }
        }
        val normalTextureDeferred = this@GltfMaterial.normalTexture?.let {
            async { it.getTexture(file, location) }
        }
        val specularTextureDeferred = pbrMetallicRoughness.metallicRoughnessTexture?.let {
            async { it.getTexture(file, location) }
        }

        if(baseColorTextureDeferred != null)
            material.texture = baseColorTextureDeferred.await()
        if(normalTextureDeferred != null)
            material.normalTexture = normalTextureDeferred.await()
        if(specularTextureDeferred != null)
            material.specularTexture = specularTextureDeferred.await()

        material.blend = if(this@GltfMaterial.alphaMode == "OPAQUE") Material.Blend.OPAQUE else Material.Blend.BLEND
        material.doubleSided = this@GltfMaterial.doubleSided

        material
    }

    @Serializable
    data class PbrMetallicRoughness(
        val baseColorFactor: List<Float> = listOf(1f, 1f, 1f, 1f),
        val baseColorTexture: GltfTexture.Info? = null,
        val metallicFactor: Float = 1f,
        val roughnessFactor: Float = 1f,
        val metallicRoughnessTexture: GltfTexture.Info? = null,
    )

    companion object {
        const val ALPHA_MODE_BLEND = "BLEND"
        const val ALPHA_MODE_MASK = "MASK"
        const val ALPHA_MODE_OPAQUE = "OPAQUE"
    }
}