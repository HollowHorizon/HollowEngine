package ru.hollowhorizon.hollowengine.client.models.gltf

import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.models.internal.Material
import ru.hollowhorizon.hollowengine.common.utils.Color

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

    fun toMaterial(file: GltfFile, location: ResourceLocation, index: Int): Material {
        val material = Material(name = name?.takeIf(String::isNotBlank) ?: "material_$index")
        val colorList = pbrMetallicRoughness.baseColorFactor
        material.color = Color(colorList[0], colorList[1], colorList[2], colorList[3])

        val baseColorTextureDeferred = pbrMetallicRoughness.baseColorTexture?.let {
            it.getTexture(file, location)
        }
        val normalTextureDeferred = this@GltfMaterial.normalTexture?.let {
            it.getTexture(file, location)
        }
        val specularTextureDeferred = pbrMetallicRoughness.metallicRoughnessTexture?.let {
            it.getTexture(file, location)
        }

        if(baseColorTextureDeferred != null)
            material.texture = baseColorTextureDeferred
        if(normalTextureDeferred != null)
            material.normalTexture = normalTextureDeferred
        if(specularTextureDeferred != null)
            material.specularTexture = specularTextureDeferred

        material.blend = if(this@GltfMaterial.alphaMode == "OPAQUE") Material.Blend.OPAQUE else Material.Blend.BLEND
        material.doubleSided = this@GltfMaterial.doubleSided

        return material
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