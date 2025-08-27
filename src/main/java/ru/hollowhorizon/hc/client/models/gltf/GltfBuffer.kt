package ru.hollowhorizon.hc.client.models.gltf

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class GltfBuffer(
    val uri: String? = null,
    val byteLength: Int,
    val name: String? = null,
) {
    @Transient
    lateinit var data: Uint8Buffer
}