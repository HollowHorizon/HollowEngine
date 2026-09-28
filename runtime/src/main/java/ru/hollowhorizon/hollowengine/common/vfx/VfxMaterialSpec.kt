package ru.hollowhorizon.hollowengine.common.vfx

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How the color a surface wrote is mixed into what is already on screen. */
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

/** Where a surface takes its light from. */
@Serializable
enum class VfxLighting {
    /** Full bright, whatever the block light is: fire, magic, anything that emits. */
    UNLIT,

    /** The block and skylight of the point it is drawn at. */
    WORLD,
}

/**
 * The part of the texture a surface shows, in 0..1 texture coordinates.
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
 * How a surface is drawn: a plane, a mesh, a trail or a beam.
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
    val softness: Float = 0f,
    val glow: Float = 0f,
    /** `namespace:path` of `assets/namespace/shaders/core/path.json`; null for the engine's own. */
    val shader: String? = null,
    val uniforms: List<VfxUniformSpec> = emptyList(),
    val samplers: List<VfxSamplerSpec> = emptyList(),
) {
    fun expressions(): List<String> = uniforms.flatMap { it.value.sources() }

    companion object {
        const val DEFAULT_TEXTURE = "hollowengine:textures/particle/circle.png"
    }
}

/**
 * A uniform a shader declares, and what the effect writes into it every frame.
 */
@Serializable
data class VfxUniformSpec(
    val name: String = "",
    val value: VfxUniformValue = VfxUniformValue.Scalar(),
)

/** What a uniform holds. A vector also fills a `vec2`, a color a `vec4`. */
@Serializable
sealed interface VfxUniformValue {
    val kind: VfxAnimatableKind

    fun sources(): List<String>

    fun constants(): FloatArray

    @Serializable
    @SerialName("float")
    data class Scalar(val value: VfxValue = VfxValue.ZERO) : VfxUniformValue {
        override val kind get() = VfxAnimatableKind.FLOAT
        override fun sources() = value.sources()
        override fun constants() = floatArrayOf(value.constantOr(0f))
    }

    @Serializable
    @SerialName("vector")
    data class Vector(val value: VfxVec3Value = VfxVec3Value.ZERO) : VfxUniformValue {
        override val kind get() = VfxAnimatableKind.VEC3
        override fun sources() = value.sources()
        override fun constants() = value.constants()
    }

    @Serializable
    @SerialName("color")
    data class Color(val value: VfxColorValue = VfxColorValue.WHITE) : VfxUniformValue {
        override val kind get() = VfxAnimatableKind.COLOR
        override fun sources() = value.sources()
        override fun constants() = value.constants()
    }
}

/** A texture bound to a sampler the shader declares. */
@Serializable
data class VfxSamplerSpec(
    val name: String = "",
    val texture: String = "",
)

/** The timeline tracks the uniforms offer, one per uniform, under `uniforms.<name>`. */
fun List<VfxUniformSpec>.animatables(): List<VfxAnimatable> = filter { it.name.isNotBlank() }.map { uniform ->
    VfxAnimatable(
        property = VfxProperty.uniform(uniform.name),
        titleKey = uniform.name,
        kind = uniform.value.kind,
        ownerTitleKey = VfxAnimatables.key("section_uniforms"),
    ) { uniform.value.constants() }
}
