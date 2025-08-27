package ru.hollowhorizon.hc.client.models.gltf

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hc.common.utils.nbt.ListOrSingle

@Serializable
data class GltfMesh(
    val primitives: ListOrSingle<Primitive>,
    val weights: List<Float> = listOf(),
    val name: String? = null,
    val extras: ListOrSingle<MeshExtras> = listOf(),
) {
    @Serializable
    data class MeshExtras(
        val targetNames: ListOrSingle<String> = listOf(),
    )

    @Serializable
    data class Primitive(
        val attributes: Map<String, Int>,
        val indices: Int = -1,
        val material: Int = -1,
        val mode: Int = MODE_TRIANGLES,
        val targets: List<Map<String, Int>> = emptyList(),
    ) {
        @Transient
        var materialRef: GltfMaterial? = null

        companion object {
            const val MODE_POINTS = 0
            const val MODE_LINES = 1
            const val MODE_LINE_LOOP = 2
            const val MODE_LINE_STRIP = 3
            const val MODE_TRIANGLES = 4
            const val MODE_TRIANGLE_STRIP = 5
            const val MODE_TRIANGLE_FAN = 6
            const val MODE_QUADS = 7
            const val MODE_QUAD_STRIP = 8
            const val MODE_POLYGON = 9

            const val ATTRIBUTE_POSITION = "POSITION"
            const val ATTRIBUTE_NORMAL = "NORMAL"
            const val ATTRIBUTE_TANGENT = "TANGENT"
            const val ATTRIBUTE_TEXCOORD_0 = "TEXCOORD_0"
            const val ATTRIBUTE_TEXCOORD_1 = "TEXCOORD_1"
            const val ATTRIBUTE_COLOR_0 = "COLOR_0"
            const val ATTRIBUTE_JOINTS_0 = "JOINTS_0"
            const val ATTRIBUTE_WEIGHTS_0 = "WEIGHTS_0"
        }
    }
}