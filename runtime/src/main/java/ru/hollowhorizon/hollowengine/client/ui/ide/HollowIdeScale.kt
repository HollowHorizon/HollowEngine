package ru.hollowhorizon.hollowengine.client.ui.ide

import androidx.compose.runtime.mutableStateOf
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.common.config.HollowEngineConfig
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

object HollowIdeScale {
    const val MaxScale = 6

    /** Below this the interface would be larger than the window; treat it as "follow the game". */
    private const val MinScale = 0.25f

    /** The smallest interface Minecraft itself is willing to lay out, and so are we. */
    private const val MinLogicalWidth = 320f
    private const val MinLogicalHeight = 240f

    private val guiScaleState = mutableStateOf(HollowEngineConfig.ideGuiScale.coerceIn(0f, MaxScale.toFloat()))

    var guiScale: Float
        get() = guiScaleState.value
        set(value) {
            val clamped = value.coerceIn(0f, MaxScale.toFloat())
            if (guiScaleState.value == clamped) return
            guiScaleState.value = clamped
            HollowEngineConfig.ideGuiScale = clamped
        }

    fun factor(): Float {
        val mc = Minecraft.getInstance()
        val window = mc.window
        val fits = min(window.width / MinLogicalWidth, window.height / MinLogicalHeight).coerceAtLeast(1f)
        val scale = guiScale
        if (scale >= MinScale) return scale.coerceAtMost(fits)
        return window.calculateScale(0, mc.isEnforceUnicode).coerceAtLeast(1).toFloat()
    }

    fun scaledWidth(): Float {
        val window = Minecraft.getInstance().window
        return ceil(window.width.toDouble() / factor()).toFloat().coerceAtLeast(1f)
    }

    fun scaledHeight(): Float {
        val window = Minecraft.getInstance().window
        return ceil(window.height.toDouble() / factor()).toFloat().coerceAtLeast(1f)
    }

    /** What the slider shows: whole steps, with the configured fraction spelled out as it is. */
    fun label(scale: Float): String = when {
        scale < MinScale -> "Auto"
        scale == scale.roundToInt().toFloat() -> "${scale.roundToInt()}x"
        else -> "${scale}x"
    }
}
