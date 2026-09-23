package ru.hollowhorizon.hollowengine.common.vfx

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How the color a particle wrote is mixed into what is already on screen. */
@Serializable
enum class VfxBlend {
    /** No blending; the alpha channel only cuts holes. */
    OPAQUE,
    /** Ordinary alpha blending. The only mode that has to be sorted back to front. */
    BLEND,
    /** Adds light: fire, sparks, magic. */
    ADDITIVE,
    /** Multiplies what is behind: shadows, stains, darkening smoke. */
    MULTIPLY;
}

/** Where a particle takes its light from. */
@Serializable
enum class VfxLighting {
    /** Full bright, whatever the block light is: fire, magic, anything that emits. */
    UNLIT,

    /** The block and skylight of the particle position. */
    WORLD,
}

/**
 * The part of the texture a particle shows, in 0..1 texture coordinates.
 *
 * The whole texture by default. A sprite that shares its file with others picks its own corner here;
 * a flipbook is cut out of this region by the frame-atlas module, so the two work together.
 */
@Serializable
data class VfxUvRect(
    val u0: Float = 0f,
    val v0: Float = 0f,
    val u1: Float = 1f,
    val v1: Float = 1f,
) {
    val width: Float get() = u1 - u0
    val height: Float get() = v1 - v0

    companion object {
        val FULL = VfxUvRect()
    }
}

/**
 * How particles of one emitter are drawn.
 */
@Serializable
data class VfxMaterialSpec(
    val texture: String = DEFAULT_TEXTURE,
    val blend: VfxBlend = VfxBlend.BLEND,
    val lighting: VfxLighting = VfxLighting.UNLIT,
    val cull: Boolean = false,
    val depthTest: Boolean = true,
    val depthWrite: Boolean = false,
    val uv: VfxUvRect = VfxUvRect.FULL,
    @SerialName("shader") val shader: String? = null,
) {
    companion object {
        const val DEFAULT_TEXTURE = "hollowengine:textures/particle/circle.png"
    }
}
