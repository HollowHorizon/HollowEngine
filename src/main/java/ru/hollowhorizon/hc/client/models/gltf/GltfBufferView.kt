package ru.hollowhorizon.hc.client.models.gltf

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class GltfBufferView(
    val buffer: Int,
    val byteOffset: Int = 0,
    val byteLength: Int,
    val byteStride: Int = 0,
    val target: Int = 0,
    val name: String? = null,
) {
    @Transient
    lateinit var bufferRef: GltfBuffer

    fun getData(): Uint8Buffer {
        val array = Uint8Buffer(byteLength)
        for (i in 0 until byteLength) {
            array[i] = bufferRef.data[byteOffset + i]
        }
        return array
    }
}