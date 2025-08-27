package ru.hollowhorizon.hc.client.models.gltf

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class GltfImage(
    var uri: String? = null,
    val mimeType: String? = null,
    val bufferView: Int = -1,
    val name: String? = null
) {
    @Transient
    var bufferViewRef: GltfBufferView? = null
}