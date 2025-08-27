package ru.hollowhorizon.hc.client.models.gltf

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class GltfSkin(
    val inverseBindMatrices: Int = -1,
    val skeleton: Int = -1,
    val joints: List<Int>,
    val name: String? = null
) {
    @Transient
    var inverseBindMatrixAccessorRef: GltfAccessor? = null
    @Transient
    lateinit var jointRefs: List<GltfNode>
}