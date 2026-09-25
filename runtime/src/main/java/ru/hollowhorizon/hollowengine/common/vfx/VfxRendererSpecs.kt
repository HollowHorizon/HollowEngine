package ru.hollowhorizon.hollowengine.common.vfx

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import ru.hollowhorizon.hollowengine.common.vfx.VfxAnimatables.color
import ru.hollowhorizon.hollowengine.common.vfx.VfxAnimatables.scalar

/**
 * A node drawn with a material.
 */
@Serializable
abstract class VfxSurfaceSpec : VfxNodeSpec() {
    abstract val material: VfxMaterialSpec

    /**
     * Multiplied into the color the surface already has, a particle's own included. A trail and a
     * beam read it along their length, so a gradient on the lifetime fades them from head to tail.
     */
    abstract val tint: VfxColorValue

    abstract fun withSurface(
        material: VfxMaterialSpec = this.material,
        tint: VfxColorValue = this.tint,
    ): VfxSurfaceSpec

    override fun expressions(): List<String> = tint.sources() + material.expressions()

    override fun animatables(): List<VfxAnimatable> =
        listOf(color(VfxProperty.TINT, "tint", tint)) + material.uniforms.animatables()
}

/** How a plane is turned towards the viewer. */
@Serializable
enum class VfxFacing {
    /** Keeps its rotation, so the plane sits in the world like a decal. */
    NONE,

    /** Always flat to the camera; the authored Z rotation still spins it. */
    CAMERA,

    /** Flat to the camera but locked to [VfxPlaneSpec.facingAxis]: smoke columns, beams, etc. */
    CAMERA_AXIS,

    /** Flat to the camera and stretched along the way the particle travels: sparks, rain. */
    VELOCITY,
}

/** One textured quad, a unit square before its size or scale. */
@Serializable
@SerialName("hollowengine:vfx/plane")
data class VfxPlaneSpec(
    override val id: String = newVfxNodeId("plane"),
    override val name: String = "Plane",
    override val enabled: Boolean = true,
    override val transform: VfxTransform = VfxTransform.IDENTITY,
    override val children: List<VfxNodeSpec> = emptyList(),
    override val material: VfxMaterialSpec = VfxMaterialSpec(),
    override val tint: VfxColorValue = VfxColorValue.WHITE,
    val facing: VfxFacing = VfxFacing.CAMERA,
    val facingAxis: Vec3f = Vec3f.Y_AXIS,
    override val particle: VfxAppearance = VfxAppearance(),
) : VfxSurfaceSpec(), VfxParticleRendererSpec {
    override fun withCommon(
        id: String,
        name: String,
        enabled: Boolean,
        transform: VfxTransform,
        children: List<VfxNodeSpec>,
    ) = copy(id = id, name = name, enabled = enabled, transform = transform, children = children)

    override fun withParticle(particle: VfxAppearance) = copy(particle = particle)

    override fun withSurface(material: VfxMaterialSpec, tint: VfxColorValue) = copy(material = material, tint = tint)

    override fun expressions() = super.expressions() + particle.expressions()

    override fun animatables() = super.animatables() + particle.animatables()
}

/** Which built-in mesh a mesh node draws. */
@Serializable
enum class VfxPrimitiveKind { CUBE, SPHERE, CYLINDER }

/** A built-in mesh with its detail; equal primitives share one vertex buffer. */
data class VfxPrimitive(val kind: VfxPrimitiveKind, val segments: Int = 0, val caps: Boolean = true)

/**
 * A built-in mesh, one block across before its size or scale, with normals and texture coordinates.
 */
@Serializable
abstract class VfxMeshSpec : VfxSurfaceSpec(), VfxParticleRendererSpec {
    /** Turns the mesh along the way the particle travels, on top of its own rotation. */
    abstract val alignToVelocity: Boolean

    abstract val primitive: VfxPrimitive

    override fun expressions() = super.expressions() + particle.expressions()

    override fun animatables() = super.animatables() + particle.animatables()
}

@Serializable
@SerialName("hollowengine:vfx/cube")
data class VfxCubeSpec(
    override val id: String = newVfxNodeId("cube"),
    override val name: String = "Cube",
    override val enabled: Boolean = true,
    override val transform: VfxTransform = VfxTransform.IDENTITY,
    override val children: List<VfxNodeSpec> = emptyList(),
    override val material: VfxMaterialSpec = VfxMaterialSpec(texture = WHITE_TEXTURE),
    override val tint: VfxColorValue = VfxColorValue.WHITE,
    override val alignToVelocity: Boolean = false,
    override val particle: VfxAppearance = VfxAppearance(),
) : VfxMeshSpec() {
    override val primitive get() = VfxPrimitive(VfxPrimitiveKind.CUBE)

    override fun withCommon(
        id: String,
        name: String,
        enabled: Boolean,
        transform: VfxTransform,
        children: List<VfxNodeSpec>,
    ) = copy(id = id, name = name, enabled = enabled, transform = transform, children = children)

    override fun withParticle(particle: VfxAppearance) = copy(particle = particle)

    override fun withSurface(material: VfxMaterialSpec, tint: VfxColorValue) = copy(material = material, tint = tint)
}

@Serializable
@SerialName("hollowengine:vfx/sphere")
data class VfxSphereSpec(
    override val id: String = newVfxNodeId("sphere"),
    override val name: String = "Sphere",
    override val enabled: Boolean = true,
    override val transform: VfxTransform = VfxTransform.IDENTITY,
    override val children: List<VfxNodeSpec> = emptyList(),
    override val material: VfxMaterialSpec = VfxMaterialSpec(texture = WHITE_TEXTURE),
    override val tint: VfxColorValue = VfxColorValue.WHITE,
    override val alignToVelocity: Boolean = false,
    /** Slices around the vertical axis; half as many rings go from pole to pole. */
    val segments: Int = 24,
    override val particle: VfxAppearance = VfxAppearance(),
) : VfxMeshSpec() {
    override val primitive get() = VfxPrimitive(VfxPrimitiveKind.SPHERE, segments.coerceIn(4, MAX_SEGMENTS))

    override fun withCommon(
        id: String,
        name: String,
        enabled: Boolean,
        transform: VfxTransform,
        children: List<VfxNodeSpec>,
    ) = copy(id = id, name = name, enabled = enabled, transform = transform, children = children)

    override fun withParticle(particle: VfxAppearance) = copy(particle = particle)

    override fun withSurface(material: VfxMaterialSpec, tint: VfxColorValue) = copy(material = material, tint = tint)
}

@Serializable
@SerialName("hollowengine:vfx/cylinder")
data class VfxCylinderSpec(
    override val id: String = newVfxNodeId("cylinder"),
    override val name: String = "Cylinder",
    override val enabled: Boolean = true,
    override val transform: VfxTransform = VfxTransform.IDENTITY,
    override val children: List<VfxNodeSpec> = emptyList(),
    override val material: VfxMaterialSpec = VfxMaterialSpec(texture = WHITE_TEXTURE),
    override val tint: VfxColorValue = VfxColorValue.WHITE,
    override val alignToVelocity: Boolean = false,
    val segments: Int = 24,
    /** Whether the ends are closed; an open tube is what shockwaves and portals are made of. */
    val caps: Boolean = true,
    override val particle: VfxAppearance = VfxAppearance(),
) : VfxMeshSpec() {
    override val primitive get() = VfxPrimitive(VfxPrimitiveKind.CYLINDER, segments.coerceIn(3, MAX_SEGMENTS), caps)

    override fun withCommon(
        id: String,
        name: String,
        enabled: Boolean,
        transform: VfxTransform,
        children: List<VfxNodeSpec>,
    ) = copy(id = id, name = name, enabled = enabled, transform = transform, children = children)

    override fun withParticle(particle: VfxAppearance) = copy(particle = particle)

    override fun withSurface(material: VfxMaterialSpec, tint: VfxColorValue) = copy(material = material, tint = tint)
}

/**
 * A model in its bind pose, drawn through the model renderer with its own materials.
 */
@Serializable
@SerialName("hollowengine:vfx/model")
data class VfxModelSpec(
    override val id: String = newVfxNodeId("model"),
    override val name: String = "Model",
    override val enabled: Boolean = true,
    override val transform: VfxTransform = VfxTransform.IDENTITY,
    override val children: List<VfxNodeSpec> = emptyList(),
    val model: String = "",
    /** Turns the model along the way the particle travels, on top of its own rotation. */
    val alignToVelocity: Boolean = false,
    val emissive: Boolean = false,
    override val particle: VfxAppearance = VfxAppearance(),
) : VfxNodeSpec(), VfxParticleRendererSpec {
    override fun withCommon(
        id: String,
        name: String,
        enabled: Boolean,
        transform: VfxTransform,
        children: List<VfxNodeSpec>,
    ) = copy(id = id, name = name, enabled = enabled, transform = transform, children = children)

    override fun withParticle(particle: VfxAppearance) = copy(particle = particle)

    override fun expressions() = particle.expressions()

    override fun animatables() = particle.animatables()
}

/** How a ribbon lays its texture along its length. */
@Serializable
enum class VfxRibbonUv {
    /** Once over the whole length, whatever it is. */
    STRETCH,

    /** Once every [VfxTrailSpec.tileLength] blocks. */
    TILE,
}

/**
 * A ribbon through the places its head has been: the node itself, or every particle when it sits
 * under an emitter.
 */
@Serializable
@SerialName("hollowengine:vfx/trail")
data class VfxTrailSpec(
    override val id: String = newVfxNodeId("trail"),
    override val name: String = "Trail",
    override val enabled: Boolean = true,
    override val transform: VfxTransform = VfxTransform.IDENTITY,
    override val children: List<VfxNodeSpec> = emptyList(),
    override val material: VfxMaterialSpec = VfxMaterialSpec(texture = WHITE_TEXTURE, blend = VfxBlend.ADDITIVE),
    override val tint: VfxColorValue = VfxColorValue.Gradient(VfxGradient.fade()),
    /**
     * Blocks across, read from head (`p.progress` 0) to tail (1). Under an emitter the other
     * particle inputs are the particle's own, and its color multiplies the tint.
     */
    val width: VfxValue = VfxValue.OverTime(VfxCurve.ramp(0.2f, 0f)),
    /** Seconds a point of the trail stays. */
    val lifetime: Float = 0.5f,
    /** How far the head moves before the trail takes a new point. */
    val minDistance: Float = 0.05f,
    val maxPoints: Int = 32,
    val uvMode: VfxRibbonUv = VfxRibbonUv.STRETCH,
    val tileLength: Float = 1f,
    /** Only the colour is read: a trail has a width of its own and no turn. */
    override val particle: VfxAppearance = VfxAppearance(),
) : VfxSurfaceSpec(), VfxParticleRendererSpec {
    override fun withCommon(
        id: String,
        name: String,
        enabled: Boolean,
        transform: VfxTransform,
        children: List<VfxNodeSpec>,
    ) = copy(id = id, name = name, enabled = enabled, transform = transform, children = children)

    override fun withParticle(particle: VfxAppearance) = copy(particle = particle)

    override fun withSurface(material: VfxMaterialSpec, tint: VfxColorValue) = copy(material = material, tint = tint)

    override fun expressions() = super.expressions() + width.sources() + particle.color.sources()

    override fun animatables() = super.animatables() + listOf(
        scalar(VfxProperty.WIDTH, "width", width),
        color(VfxProperty.COLOR, "color", particle.color),
    )
}

/**
 * A ribbon from the node to another node, or to a point of its own, bent by noise: lasers,
 * lightning, tethers.
 */
@Serializable
@SerialName("hollowengine:vfx/beam")
data class VfxBeamSpec(
    override val id: String = newVfxNodeId("beam"),
    override val name: String = "Beam",
    override val enabled: Boolean = true,
    override val transform: VfxTransform = VfxTransform.IDENTITY,
    override val children: List<VfxNodeSpec> = emptyList(),
    override val material: VfxMaterialSpec = VfxMaterialSpec(texture = WHITE_TEXTURE, blend = VfxBlend.ADDITIVE),
    override val tint: VfxColorValue = VfxColorValue.WHITE,
    /** The node the beam ends at; empty ends it at [end]. */
    val target: String = "",
    /** The end point in the space of this node, when there is no [target]. */
    val end: Vec3f = Vec3f(0f, 2f, 0f),
    /** Blocks across, read from start (`p.progress` 0) to end (1). */
    val width: VfxValue = VfxValue.Const(0.15f),
    val segments: Int = 24,
    /** How far, in blocks, noise pushes the middle of the beam aside; the ends stay put. */
    val noise: VfxValue = VfxValue.ZERO,
    /** Noise waves per block of beam. */
    val noiseFrequency: Float = 1.5f,
    /** How fast the noise changes, in cycles per second. */
    val noiseSpeed: Float = 4f,
    /** Texture lengths per second the texture slides from start to end. */
    val uvScroll: Float = 0f,
    val uvMode: VfxRibbonUv = VfxRibbonUv.STRETCH,
    val tileLength: Float = 1f,
) : VfxSurfaceSpec() {
    override fun withCommon(
        id: String,
        name: String,
        enabled: Boolean,
        transform: VfxTransform,
        children: List<VfxNodeSpec>,
    ) = copy(id = id, name = name, enabled = enabled, transform = transform, children = children)

    override fun withSurface(material: VfxMaterialSpec, tint: VfxColorValue) = copy(material = material, tint = tint)

    override fun expressions() = super.expressions() + width.sources() + noise.sources()

    override fun animatables() = super.animatables() + listOf(
        scalar(VfxProperty.WIDTH, "width", width),
        scalar(VfxProperty.BEAM_NOISE, "beam_noise", noise),
        VfxAnimatable(VfxProperty.BEAM_END, VfxAnimatables.key("beam_end"), VfxAnimatableKind.VEC3) {
            floatArrayOf(end.x, end.y, end.z)
        },
    )
}

/** A plain white texture, for surfaces whose look comes from the tint or a shader. */
const val WHITE_TEXTURE = "hollowengine:textures/particle/white.png"

private const val MAX_SEGMENTS = 128
