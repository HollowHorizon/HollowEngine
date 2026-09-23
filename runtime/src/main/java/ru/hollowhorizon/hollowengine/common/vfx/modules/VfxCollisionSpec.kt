package ru.hollowhorizon.hollowengine.common.vfx.modules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("hollowengine:vfx/collision")
data class VfxCollisionSpec(
    override val id: String = "collision",
    override val enabled: Boolean = true,
    val action: VfxCollisionAction = VfxCollisionAction.BOUNCE,
    /** How much speed survives a bounce, 0 to 1. */
    val bounce: Float = 0.4f,
    /** How much of the sideways speed is kept after touching a surface, 0 to 1. */
    val friction: Float = 0.8f,
    /** The particle is treated as a sphere of this radius, in blocks. */
    val radius: Float = 0.05f,
    /** Seconds taken off the remaining lifetime on every hit. */
    val lifetimeLoss: Float = 0f,
) : VfxModuleSpec() {
    override fun withEnabled(enabled: Boolean) = copy(enabled = enabled)
}

/** What happens when a particle meets a block. */
@Serializable
enum class VfxCollisionAction {
    DIE, BOUNCE,

    /** Keeps the movement along the surface and drops the part going into it. */
    SLIDE,
}