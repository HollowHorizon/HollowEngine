package ru.hollowhorizon.hollowengine.client.models.gltf

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hollowengine.common.utils.Uint8Buffer

@Serializable
data class GltfBuffer(
    val uri: String? = null,
    val byteLength: Int,
    val name: String? = null,
) {
    @Transient
    lateinit var data: Uint8Buffer
}