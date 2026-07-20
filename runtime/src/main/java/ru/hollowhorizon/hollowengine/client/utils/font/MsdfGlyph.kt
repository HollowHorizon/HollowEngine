package ru.hollowhorizon.hollowengine.client.utils.font

import kotlinx.serialization.Serializable

@Serializable
data class MsdfGlyph(
    val unicode: Int,
    val advance: Float,
    val planeBounds: MsdfRect = MsdfRect(0f, 0f, 0f, 0f),
    val atlasBounds: MsdfRect = MsdfRect(0f, 0f, 0f, 0f),
) {
    fun isEmpty(): Boolean = planeBounds.left == planeBounds.right
}
@Serializable
data class MsdfRect(
    val left: Float,
    val bottom: Float,
    val right: Float,
    val top: Float
)