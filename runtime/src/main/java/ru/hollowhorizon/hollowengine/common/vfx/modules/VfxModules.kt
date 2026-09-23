package ru.hollowhorizon.hollowengine.common.vfx.modules

import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.common.vfx.VfxAnimatableKind

/**
 * One behavior attached to emitter.
 */
@Serializable
abstract class VfxModuleSpec {
    abstract val id: String
    abstract val enabled: Boolean

    abstract fun withEnabled(enabled: Boolean): VfxModuleSpec

    open fun expressions(): List<String> = emptyList()

    /**
     * The numbers of this module the timeline can drive, by field name.
     */
    open fun animatables(): List<VfxModuleAnimatable> = emptyList()
}

/** One number of a module that a timeline track can drive. */
class VfxModuleAnimatable(
    val field: String,
    val titleKey: String,
    val kind: VfxAnimatableKind,
    val read: (VfxModuleSpec) -> FloatArray,
)