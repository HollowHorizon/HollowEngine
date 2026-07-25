package ru.hollowhorizon.hollowengine.client.ui.ide

import androidx.compose.runtime.mutableStateOf
import ru.hollowhorizon.hollowengine.common.config.HollowEngineConfig

/**
 * Font size of the IDE code editor, as observable state backed by the config.
 *
 * Mirrors [HollowIdeScale]: the editor reads the state so a change recomposes it, and the write goes
 * through the config property, which schedules its own deferred save.
 */
object HollowIdeFontSize {
    /** Mirrors the `@PropertyRange` on [HollowEngineConfig.ideEditorFontSize]; a write outside it throws. */
    const val MinSize = 6f
    const val MaxSize = 36f

    /** One notch of the wheel. Whole points, so the size stays on values a font renders crisply. */
    const val Step = 1f

    private val sizeState = mutableStateOf(HollowEngineConfig.ideEditorFontSize.coerceIn(MinSize, MaxSize))

    var size: Float
        get() = sizeState.value
        set(value) {
            val clamped = value.coerceIn(MinSize, MaxSize)
            if (sizeState.value == clamped) return
            sizeState.value = clamped
            HollowEngineConfig.ideEditorFontSize = clamped
        }

    /** Applies a wheel notch; [scrollY] follows the usual convention of positive meaning scroll up. */
    fun zoom(scrollY: Double) {
        if (scrollY == 0.0) return
        size += if (scrollY > 0) Step else -Step
    }
}
