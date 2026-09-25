package ru.hollowhorizon.hollowengine.common.vfx

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import ru.hollowhorizon.hollowengine.common.vfx.VfxAnimatables.scalar
import ru.hollowhorizon.hollowengine.common.vfx.VfxAnimatables.vector
import ru.hollowhorizon.hollowengine.common.vfx.modules.VfxModuleSpec

/** Where an emitter keeps the positions and velocities of its particles. */
@Serializable
enum class VfxSimulationSpace {
    /** The emitter space, that particles follows. */
    LOCAL,

    /** The world space. Particles stay where they were born. */
    WORLD,
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

    /** The surface of a model in its bind pose. */
    MODEL,
}

/** Which way a newborn particle is sent. */
@Serializable
enum class VfxDirectionMode {
    /** Outwards from the shape. Away from a sphere center, along a cone, up from a disc, off a model surface. */
    SHAPE,

    /** A random direction, whatever the shape. */
    RANDOM,

    /** Always [VfxShape.direction]. */
    FIXED,
}

/**
 * Where particles are born.
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
    /** The model whose surface a [VfxShapeKind.MODEL] shape covers. */
    val model: String = "",
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
 * Makes particles and moves them. It draws nothing itself: the renderers directly under it do, one
 * each per particle.
 */
@Serializable
@SerialName("hollowengine:vfx/emitter")
data class VfxEmitterSpec(
    override val id: String = newVfxNodeId("emitter"),
    override val name: String = "Emitter",
    override val enabled: Boolean = true,
    override val transform: VfxTransform = VfxTransform.IDENTITY,
    override val children: List<VfxNodeSpec> = emptyList(),
    val emission: VfxEmission = VfxEmission(),
    val space: VfxSimulationSpace = VfxSimulationSpace.WORLD,
    val inheritRotation: Boolean = true,
    val inheritScale: Boolean = false,
    val shape: VfxShape = VfxShape(),
    val spawn: VfxSpawn = VfxSpawn(),
    val motion: VfxMotion = VfxMotion(),
    val modules: List<VfxModuleSpec> = emptyList(),
    /** The longest step the simulation may take for this emitter, in seconds. */
    val maxStep: Float = DEFAULT_MAX_STEP,
) : VfxNodeSpec() {
    override fun withCommon(
        id: String,
        name: String,
        enabled: Boolean,
        transform: VfxTransform,
        children: List<VfxNodeSpec>,
    ) = copy(id = id, name = name, enabled = enabled, transform = transform, children = children)

    fun module(id: String): VfxModuleSpec? = modules.firstOrNull { it.id == id }

    override fun expressions(): List<String> = buildList {
        addAll(emission.rate.sources())
        addAll(shape.expressions())
        addAll(spawn.expressions())
        addAll(motion.expressions())
        modules.forEach { addAll(it.expressions()) }
    }

    override fun animatables(): List<VfxAnimatable> = buildList {
        add(scalar(VfxProperty.RATE, "rate", emission.rate))
        add(scalar(VfxProperty.SHAPE_RADIUS, "radius", shape.radius))
        add(vector(VfxProperty.SHAPE_EXTENTS, "extents", shape.extents))
        add(scalar(VfxProperty.SHAPE_ANGLE, "angle", shape.angle))
        add(scalar(VfxProperty.SHAPE_THICKNESS, "thickness", shape.thickness))
        add(scalar(VfxProperty.LIFETIME, "lifetime", spawn.lifetime))
        add(scalar(VfxProperty.SPEED, "speed", spawn.speed))
        add(vector(VfxProperty.OFFSET, "spawn_offset", spawn.offset))
        add(scalar(VfxProperty.INHERIT_VELOCITY, "inherit_velocity", spawn.inheritVelocity))
        add(scalar(VfxProperty.GRAVITY, "gravity", motion.gravity))
        add(scalar(VfxProperty.DRAG, "drag", motion.drag))

        modules.forEach { module ->
            val owner = VfxModuleTypes.of(module)?.titleKey
            module.animatables().forEach { field ->
                add(
                    VfxAnimatable(
                        property = VfxProperty.module(module.id, field.field),
                        titleKey = field.titleKey,
                        kind = field.kind,
                        ownerTitleKey = owner,
                    ) { field.read(module) }
                )
            }
        }
    }
}

internal const val DEFAULT_MAX_STEP = 1f / 60f
