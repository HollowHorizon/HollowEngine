package ru.hollowhorizon.hollowengine.client.ui.ide

import androidx.compose.runtime.mutableStateOf
import de.fabmax.kool.KeyValueStore
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.ui.ide.HollowIdeScale.guiScale
import kotlin.math.ceil

object HollowIdeScale {
    const val MaxScale = 6
    private const val KEY = "hollowengine.ide.gui_scale"

    private val guiScaleState = mutableStateOf(KeyValueStore.getInt(KEY, 0).coerceIn(0, MaxScale))

    var guiScale: Int
        get() = guiScaleState.value
        set(value) {
            val clamped = value.coerceIn(0, MaxScale)
            if (guiScaleState.value == clamped) return
            guiScaleState.value = clamped
            KeyValueStore.setInt(KEY, clamped)
        }

    /** The integer pixel factor Minecraft would use for [guiScale] on the current framebuffer. */
    fun factor(): Int {
        val mc = Minecraft.getInstance()
        return mc.window.calculateScale(guiScale, mc.isEnforceUnicode).coerceAtLeast(1)
    }

    fun scaledWidth(): Float {
        val window = Minecraft.getInstance().window
        return ceil(window.width.toDouble() / factor()).toFloat().coerceAtLeast(1f)
    }

    fun scaledHeight(): Float {
        val window = Minecraft.getInstance().window
        return ceil(window.height.toDouble() / factor()).toFloat().coerceAtLeast(1f)
    }

    fun label(scale: Int): String = if (scale <= 0) "Auto" else "${scale}x"
}
