package ru.hollowhorizon.hollowengine.common.vfx.modules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Plays a flipbook, the material region cut into a grid of frames.
 */
@Serializable
@SerialName("hollowengine:vfx/uv_animation")
data class VfxUvAnimationSpec(
    override val id: String = "uv_animation",
    override val enabled: Boolean = true,
    val columns: Int = 4,
    val rows: Int = 4,
    /** How many cells hold a frame, counted row by row; zero means every cell. */
    val frames: Int = 0,
    val mode: VfxUvMode = VfxUvMode.OVER_LIFETIME,
    val fps: Float = 12f,
    /** How many times the sheet is walked over the lifetime; only for [VfxUvMode.OVER_LIFETIME]. */
    val cycles: Float = 1f,
) : VfxModuleSpec() {
    override fun withEnabled(enabled: Boolean) = copy(enabled = enabled)

    val frameCount: Int
        get() {
            val cells = columns.coerceAtLeast(1) * rows.coerceAtLeast(1)
            return if (frames <= 0) cells else frames.coerceAtMost(cells)
        }
}

/** How the frames of a sprite sheet are chosen. */
@Serializable
enum class VfxUvMode {
    /** Walks the sheet once across the particle lifetime. */
    OVER_LIFETIME,

    /** Walks the sheet at [VfxUvAnimationSpec.fps], looping. */
    FPS,

    /** One frame, picked when the particle is born and kept. */
    RANDOM_FRAME,
}