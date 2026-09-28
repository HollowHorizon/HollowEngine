package ru.hollowhorizon.hollowengine.common.vfx.modules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.common.vfx.VfxAnimatableKind
import ru.hollowhorizon.hollowengine.common.vfx.VfxValue
import ru.hollowhorizon.hollowengine.common.vfx.constantOr
import ru.hollowhorizon.hollowengine.common.vfx.sources

/**
 * Smooth pseudo-random motion, the cheapest way to make smoke and magic look alive.
 */
@Serializable
@SerialName("hollowengine:vfx/noise")
data class VfxNoiseSpec(
    override val id: String = "noise",
    override val enabled: Boolean = true,
    /** Blocks per second squared. */
    val strength: VfxValue = VfxValue.Const(1f),
    /** Field cells per block: higher means finer, more chaotic motion. */
    val frequency: VfxValue = VfxValue.Const(0.5f),
    /** How fast the field itself moves, in field units per second. */
    val scrollSpeed: Float = 0.25f,
    /** Extra layers of finer detail. Each one doubles the cost. */
    val octaves: Int = 1,
) : VfxModuleSpec() {
    override fun withEnabled(enabled: Boolean) = copy(enabled = enabled)

    override fun expressions(): List<String> = strength.sources() + frequency.sources()

    override fun animatables() = listOf(
        VfxModuleAnimatable("strength", "hollowengine.gui.vfx.strength", VfxAnimatableKind.FLOAT) {
            floatArrayOf((it as VfxNoiseSpec).strength.constantOr(0f))
        },
        VfxModuleAnimatable("frequency", "hollowengine.gui.vfx.frequency", VfxAnimatableKind.FLOAT) {
            floatArrayOf((it as VfxNoiseSpec).frequency.constantOr(0.5f))
        },
    )
}