package ru.hollowhorizon.hollowengine.common.vfx

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import ru.hollowhorizon.hollowengine.common.vfx.modules.VfxModuleSpec

/** A fresh node id; readable on purpose, because it appears in timeline tracks and in save files. */
fun newVfxNodeId(prefix: String): String = "$prefix-${Integer.toHexString((0..0xFFFFFF).random())}"

/** Where an emitter keeps the positions and velocities of its particles. */
@Serializable
enum class VfxSimulationSpace {
    /** The emitter space, that particles follows. */
    LOCAL,

    /** The world space. Particles stay where they were born. */
    WORLD,
}

/** A node placement inside the effect. */
@Serializable
data class VfxTransform(
    val position: Vec3f = Vec3f.ZERO,
    val rotation: Vec3f = Vec3f.ZERO,
    val scale: Vec3f = Vec3f.ONES,
) {
    companion object {
        val IDENTITY = VfxTransform()
    }
}

/**
 * One node of an effect.
 *
 * Node kinds live in [VfxNodeTypes], so an addon can add its own.
 */
@Serializable
abstract class VfxNodeSpec {
    abstract val id: String
    abstract val name: String
    abstract val enabled: Boolean
    abstract val transform: VfxTransform
    abstract val children: List<VfxNodeSpec>

    /** A copy with the shared fields replaced, whichever kind of node this is. */
    abstract fun withCommon(
        id: String = this.id,
        name: String = this.name,
        enabled: Boolean = this.enabled,
        transform: VfxTransform = this.transform,
        children: List<VfxNodeSpec> = this.children,
    ): VfxNodeSpec

    /** Every expression this node evaluates, so a file compiles them all in one pass. */
    open fun expressions(): List<String> = emptyList()
}

/** How many particles appear and when the emitter stops. */
@Serializable
data class VfxEmission(
    /** Particles per second. */
    val rate: VfxValue = VfxValue.Const(20f),
    /** Particles released at once when a loop starts. */
    val burst: Int = 0,
    /** Seconds the emitter stays active; zero means forever. */
    val duration: Float = 2f,
    val loop: Boolean = true,
    /** Seconds of silence between loops. */
    val loopDelay: Float = 0f,
    val maxParticles: Int = 256,
)

/** Shapes a particle can be born on. */
@Serializable
enum class VfxShapeKind {
    POINT, SPHERE, BOX, CONE, DISC, LINE,
}

/** Which way a newborn particle is sent. */
@Serializable
enum class VfxDirectionMode {
    /** Outwards from the shape. Away from a sphere center, along a cone, up from a disc. */
    SHAPE,

    /** A random direction, whatever the shape. */
    RANDOM,

    /** Always [VfxShape.direction]. */
    FIXED,
}

/**
 * Where particles are born.
 *
 * The sizes are values rather than numbers.
 * TODO: Add Mesh shape.
 */
@Serializable
data class VfxShape(
    val kind: VfxShapeKind = VfxShapeKind.POINT,
    val radius: VfxValue = VfxValue.Const(0.5f),
    /** Box half-extents, and the end point of a line. */
    val extents: VfxVec3Value = VfxVec3Value.all(0.5f),
    /** Cone half-angle in degrees. */
    val angle: VfxValue = VfxValue.Const(25f),
    /** 0 puts particles on the surface only, 1 fills the whole volume. */
    val thickness: VfxValue = VfxValue.ONE,
    val direction: Vec3f = Vec3f.Y_AXIS,
    val directionMode: VfxDirectionMode = VfxDirectionMode.SHAPE,
) {
    fun expressions(): List<String> = radius.sources() + extents.sources() + angle.sources() + thickness.sources()
}

/**
 * What a particle is given at spawn and never changes afterward.
 */
@Serializable
data class VfxSpawn(
    /** Seconds the particle lives. */
    val lifetime: VfxValue = VfxValue.Const(1f),
    /** Blocks per second along the shape direction. */
    val speed: VfxValue = VfxValue.Const(1f),
    /** An extra offset from the point the shape picked. */
    val offset: VfxVec3Value = VfxVec3Value.ZERO,
    /** How much of the emitter own velocity the particle keeps, 0 to 1. */
    val inheritVelocity: VfxValue = VfxValue.ZERO,
) {
    fun expressions(): List<String> =
        lifetime.sources() + speed.sources() + offset.sources() + inheritVelocity.sources()
}

/**
 * How a particle looks, read again every step for as long as it lives.
 */
@Serializable
data class VfxAppearance(
    /** Quad width and height in blocks; a mesh particle uses all three. */
    val size: VfxVec3Value = VfxVec3Value.all(0.25f),
    /** Drives all three axes of [size] from the first, which is what most effects want. */
    val uniformSize: Boolean = true,
    val rotation: VfxVec3Value = VfxVec3Value.ZERO,
    val color: VfxColorValue = VfxColorValue.WHITE,
) {
    fun expressions(): List<String> = size.sources() + rotation.sources() + color.sources()
}

/**
 * The forces every emitter has, as opposed to the ones that come from modules.
 */
@Serializable
data class VfxMotion(
    /** Blocks per second squared, pulling down. */
    val gravity: VfxValue = VfxValue.ZERO,
    /** Fraction of speed lost per second, 0 to 1. */
    val drag: VfxValue = VfxValue.ZERO,
) {
    fun expressions(): List<String> = gravity.sources() + drag.sources()
}

/**
 * Attributes, that every emitter has.
 */
@Serializable
abstract class VfxEmitterSpec : VfxNodeSpec() {
    abstract val emission: VfxEmission
    abstract val space: VfxSimulationSpace
    abstract val inheritRotation: Boolean
    abstract val inheritScale: Boolean
    abstract val shape: VfxShape
    abstract val spawn: VfxSpawn
    abstract val appearance: VfxAppearance
    abstract val motion: VfxMotion
    abstract val modules: List<VfxModuleSpec>

    /**
     * The longest step the simulation may take for this emitter, in seconds.
     */
    abstract val maxStep: Float

    abstract fun withEmitter(
        emission: VfxEmission = this.emission,
        space: VfxSimulationSpace = this.space,
        inheritRotation: Boolean = this.inheritRotation,
        inheritScale: Boolean = this.inheritScale,
        shape: VfxShape = this.shape,
        spawn: VfxSpawn = this.spawn,
        appearance: VfxAppearance = this.appearance,
        motion: VfxMotion = this.motion,
        modules: List<VfxModuleSpec> = this.modules,
        maxStep: Float = this.maxStep,
    ): VfxEmitterSpec

    fun module(id: String): VfxModuleSpec? = modules.firstOrNull { it.id == id }

    override fun expressions(): List<String> = buildList {
        addAll(emission.rate.sources())
        addAll(shape.expressions())
        addAll(spawn.expressions())
        addAll(appearance.expressions())
        addAll(motion.expressions())
        modules.forEach { addAll(it.expressions()) }
    }
}

/** How a quad is turned towards the viewer. */
@Serializable
enum class VfxFacing {
    /** Keeps initial rotation, so the quad sits in the world like a decal. */
    NONE,

    /** Always flat to the camera; the authored Z rotation still spins it. */
    CAMERA,

    /** Flat to the camera but locked to [VfxQuadEmitterSpec.facingAxis]: smoke columns, beams, etc. */
    CAMERA_AXIS,
}

/** Billboard particles. One textured quad each, drawn instanced. */
@Serializable
@SerialName("hollowengine:vfx/quad_emitter")
data class VfxQuadEmitterSpec(
    override val id: String = newVfxNodeId("quads"),
    override val name: String = "Quads",
    override val enabled: Boolean = true,
    override val transform: VfxTransform = VfxTransform.IDENTITY,
    override val children: List<VfxNodeSpec> = emptyList(),
    override val emission: VfxEmission = VfxEmission(),
    override val space: VfxSimulationSpace = VfxSimulationSpace.WORLD,
    override val inheritRotation: Boolean = true,
    override val inheritScale: Boolean = false,
    override val shape: VfxShape = VfxShape(),
    override val spawn: VfxSpawn = VfxSpawn(),
    override val appearance: VfxAppearance = VfxAppearance(),
    override val motion: VfxMotion = VfxMotion(),
    override val modules: List<VfxModuleSpec> = emptyList(),
    override val maxStep: Float = DEFAULT_MAX_STEP,
    val material: VfxMaterialSpec = VfxMaterialSpec(),
    val facing: VfxFacing = VfxFacing.CAMERA,
    val facingAxis: Vec3f = Vec3f.Y_AXIS,
) : VfxEmitterSpec() {
    override fun withCommon(
        id: String,
        name: String,
        enabled: Boolean,
        transform: VfxTransform,
        children: List<VfxNodeSpec>,
    ) = copy(id = id, name = name, enabled = enabled, transform = transform, children = children)

    override fun withEmitter(
        emission: VfxEmission,
        space: VfxSimulationSpace,
        inheritRotation: Boolean,
        inheritScale: Boolean,
        shape: VfxShape,
        spawn: VfxSpawn,
        appearance: VfxAppearance,
        motion: VfxMotion,
        modules: List<VfxModuleSpec>,
        maxStep: Float,
    ) = copy(
        emission = emission,
        space = space,
        inheritRotation = inheritRotation,
        inheritScale = inheritScale,
        shape = shape,
        spawn = spawn,
        appearance = appearance,
        motion = motion,
        modules = modules,
        maxStep = maxStep,
    )
}

/**
 * Model particles. One instance of a model each, drawn through the model renderer.
 */
@Serializable
@SerialName("hollowengine:vfx/mesh_emitter")
data class VfxMeshEmitterSpec(
    override val id: String = newVfxNodeId("meshes"),
    override val name: String = "Meshes",
    override val enabled: Boolean = true,
    override val transform: VfxTransform = VfxTransform.IDENTITY,
    override val children: List<VfxNodeSpec> = emptyList(),
    override val emission: VfxEmission = VfxEmission(rate = VfxValue.Const(4f), maxParticles = 48),
    override val space: VfxSimulationSpace = VfxSimulationSpace.WORLD,
    override val inheritRotation: Boolean = true,
    override val inheritScale: Boolean = false,
    override val shape: VfxShape = VfxShape(),
    override val spawn: VfxSpawn = VfxSpawn(),
    override val appearance: VfxAppearance = VfxAppearance(size = VfxVec3Value.all(1f)),
    override val motion: VfxMotion = VfxMotion(),
    override val modules: List<VfxModuleSpec> = emptyList(),
    override val maxStep: Float = DEFAULT_MAX_STEP,
    val model: String = "",
    /** Turns the model along the direction it travels, on top of the authored rotation. */
    val alignToVelocity: Boolean = false,
    val emissive: Boolean = false,
) : VfxEmitterSpec() {
    override fun withCommon(
        id: String,
        name: String,
        enabled: Boolean,
        transform: VfxTransform,
        children: List<VfxNodeSpec>,
    ) = copy(id = id, name = name, enabled = enabled, transform = transform, children = children)

    override fun withEmitter(
        emission: VfxEmission,
        space: VfxSimulationSpace,
        inheritRotation: Boolean,
        inheritScale: Boolean,
        shape: VfxShape,
        spawn: VfxSpawn,
        appearance: VfxAppearance,
        motion: VfxMotion,
        modules: List<VfxModuleSpec>,
        maxStep: Float,
    ) = copy(
        emission = emission,
        space = space,
        inheritRotation = inheritRotation,
        inheritScale = inheritScale,
        shape = shape,
        spawn = spawn,
        appearance = appearance,
        motion = motion,
        modules = modules,
        maxStep = maxStep,
    )
}

internal const val DEFAULT_MAX_STEP = 1f / 60f
