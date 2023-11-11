package ru.hollowhorizon.hollowengine.client.style

import com.google.gson.JsonObject
import net.minecraft.Util
import net.minecraft.util.Mth
import org.apache.commons.lang3.StringUtils

interface Effect {
    companion object {
        fun create(split: Array<out String>): Effect? {
            val params: JsonObject
            if (split.size > 1) {
                params = JsonObject()
                for (i in 1 ..< split.size) {
                    val kv = StringUtils.split(split[i], "=", 2)
                    if (kv.size == 1) params.addProperty(kv[0], true)
                    else if ("true" == kv[1] || "false" == kv[1]) params.addProperty(kv[0], kv[1].toBoolean())
                    else {
                        try {
                            params.addProperty(kv[0], kv[1].toFloat())
                        } catch (e: NumberFormatException) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            return when (split[0]) {
                "wave" -> WaveEffect()
                else -> null
            }
        }
    }

    fun apply(effectSettings: EffectSettings)

    val name: String

    val serialize: String
        get() = name

    private class WaveEffect: Effect {
        override fun apply(effectSettings: EffectSettings) {
            effectSettings.y += Mth.sin(Util.getMillis() * 0.01F + effectSettings.index) * 2
        }

        override val name: String
            get() = "wave"

    }

    private class RainbowEffect: Effect {
        override fun apply(effectSettings: EffectSettings) {
            if (effectSettings.isShadow) return
            val color = Mth.hsvToRgb(((Util.getMillis() * 0.02F + effectSettings.index) % 30) / 30, 0.8F, 0.8F)
            effectSettings.r = (color shr 16 and 255) / 255f
            effectSettings.g = (color shr 8 and 255) / 255f
            effectSettings.b = (color and 255) / 255f
        }

        override val name: String
            get() = "rainbow"
    }

    private class SnakeEffect: Effect {
        override fun apply(effectSettings: EffectSettings) {
        }

        override val name: String
            get() = "snake"

    }
}

class EffectSettings(val codePoint: Int, val index: Int, val isShadow: Boolean) {
    var x: Float = 0F
    var y: Float = 0F
    var r: Float = 0F
    var g: Float = 0F
    var b: Float = 0F
    var a: Float = 0F
}