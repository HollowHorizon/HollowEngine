package ru.hollowhorizon.hc.client.models.gltf

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class GltfNode(
    val camera: Int = -1,
    val children: List<Int> = emptyList(),
    val skin: Int = -1,
    val matrix: List<Float>? = null,
    val mesh: Int = -1,
    val rotation: List<Float>? = null,
    val scale: List<Float>? = null,
    val translation: List<Float>? = null,
    val weights: List<Float>? = null,
    val name: String? = null
) {
    @Transient
    lateinit var childRefs: List<GltfNode>
    @Transient
    var meshRef: GltfMesh? = null
    @Transient
    var skinRef: GltfSkin? = null
}