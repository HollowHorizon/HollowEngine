package ru.hollowhorizon.hollowengine.client.models.gltf

import de.fabmax.kool.util.Uint8Buffer
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