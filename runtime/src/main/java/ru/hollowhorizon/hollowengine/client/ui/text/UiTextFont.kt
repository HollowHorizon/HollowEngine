package ru.hollowhorizon.hollowengine.client.ui.text

import ru.hollowhorizon.hollowengine.client.ui.widgets.UiInlineStyle
import ru.hollowhorizon.hollowengine.client.ui.widgets.bold
import java.util.concurrent.ConcurrentHashMap

internal sealed interface UiTextFont {
    val signature: Int

    fun lineHeight(fontSize: Float): Float

    fun width(text: String, fontSize: Float, style: UiInlineStyle): Float

    fun advance(char: Char, fontSize: Float, style: UiInlineStyle): Float = width(char.toString(), fontSize, style)
}

internal object UiTextFonts {
    private const val EstimatedGlyphWidth = 6f
    private const val LegacyDefaultFamily = "minecraft:default"
    private val resolvedFonts = ConcurrentHashMap<String, UiTextFont>()

    fun defaultedFamily(fontFamily: String?): String {
        return fontFamily?.takeIf { it.isNotBlank() } ?: UiMsdfFont.DefaultFontFamily
    }

    fun resolve(fontFamily: String?): UiTextFont {
        val family = defaultedFamily(fontFamily)
        resolvedFonts[family]?.let { return it }
        val metrics = UiMsdfFont.getMetrics(family) ?: return EstimatedTextFont
        return MsdfTextFont(family, metrics).also { resolvedFonts[family] = it }
    }

    fun signature(fontFamily: String?): Int = resolve(fontFamily).signature

    fun clearResolvedFonts() {
        resolvedFonts.clear()
    }

    /** Headless / not-yet-loaded fallback: a fixed monospace estimate, no Minecraft font dependency. */
    private data object EstimatedTextFont : UiTextFont {
        override val signature: Int = 1

        override fun lineHeight(fontSize: Float): Float = fontSize

        override fun width(text: String, fontSize: Float, style: UiInlineStyle): Float {
            val advance = EstimatedGlyphWidth + if (style.bold) 1f else 0f
            return text.length * advance * (fontSize / DefaultUiFontSize)
        }

        private const val DefaultUiFontSize = 10f
    }

    private data class MsdfTextFont(
        val family: String,
        val metrics: UiMsdfFontMetrics,
    ) : UiTextFont {
        override val signature: Int = 31 * family.hashCode() + System.identityHashCode(metrics)

        override fun lineHeight(fontSize: Float): Float = metrics.lineHeight(fontSize)

        override fun width(text: String, fontSize: Float, style: UiInlineStyle): Float {
            val boldOffset = if (style.bold) fontSize / 16f else 0f
            return metrics.width(text, fontSize) + text.length * boldOffset
        }

        override fun advance(char: Char, fontSize: Float, style: UiInlineStyle): Float {
            val boldOffset = if (style.bold) fontSize / 16f else 0f
            return metrics.advance(char, fontSize) + boldOffset
        }
    }
}
