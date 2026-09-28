package ru.hollowhorizon.hollowengine.common.vfx.modules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import ru.hollowhorizon.hollowengine.common.vfx.*

/**
 * A force acting on the particles of this emitter, placed in the emitter frame.
 */
@Serializable
@SerialName("hollowengine:vfx/force")
data class VfxForceSpec(
    override val id: String = newVfxNodeId("force"),
    override val enabled: Boolean = true,
    val kind: VfxForceKind = VfxForceKind.DIRECTIONAL,
    /** Blocks per second squared, or the fraction of speed lost per second for [VfxForceKind.DRAG]. */
    val strength: VfxValue = VfxValue.Const(1f),
    val direction: Vec3f = Vec3f.Y_AXIS,
    val center: Vec3f = Vec3f.ZERO,
    /** Blocks; zero reaches everywhere. */
    val radius: VfxValue = VfxValue.ZERO,
    /** Whether the push fades to nothing at the edge of [radius]. */
    val falloff: Boolean = true,
) : VfxModuleSpec() {
    override fun withEnabled(enabled: Boolean) = copy(enabled = enabled)

    override fun expressions(): List<String> = strength.sources() + radius.sources()

    override fun animatables() = listOf(
        VfxModuleAnimatable("strength", "hollowengine.gui.vfx.strength", VfxAnimatableKind.FLOAT) {
            floatArrayOf((it as VfxForceSpec).strength.constantOr(0f))
        },
        VfxModuleAnimatable("radius", "hollowengine.gui.vfx.radius", VfxAnimatableKind.FLOAT) {
            floatArrayOf((it as VfxForceSpec).radius.constantOr(0f))
        },
    )
}

/** What a force does to the particles of its emitter. */
@Serializable
enum class VfxForceKind {
    /** Pushes along [VfxForceSpec.direction], i.e. wind. */
    DIRECTIONAL,

    /** Pushes away from [VfxForceSpec.center], or pulls in when the strength is negative. */
    POINT,

    /** Spins around [VfxForceSpec.direction] through [VfxForceSpec.center]. */
    VORTEX,

    /** Slows whatever is inside its radius. */
    DRAG,
}
