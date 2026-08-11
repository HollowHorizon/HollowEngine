package ru.hollowhorizon.hollowengine.client.ui.text

import ru.hollowhorizon.hollowengine.client.ui.widgets.UiInlineStyle
import ru.hollowhorizon.hollowengine.client.ui.widgets.boldWeight
import java.util.concurrent.ConcurrentHashMap

internal sealed interface UiTextFont {
    val signature: Int

    fun lineHeight(fontSize: Float): Float

    fun width(text: String, fontSize: Float, style: UiInlineStyle): Float

    fun advance(char: Char, fontSize: Float, style: UiInlineStyle): Float = width(char.toString(), fontSize, style)
}

internal object UiTextFonts {
    private const val EstimatedGlyphWidth = 6f
    private val resolvedFonts = ConcurrentHashMap<String, UiTextFont>()

    fun defaultedFamily(fontFamily: String?): String {
        return fontFamily?.takeIf { it.isNotBlank() } ?: UiMsdfFont.DefaultFontFamily
    }

    fun resolve(fontFamily: String?): UiTextFont {
        val family = defaultedFamily(fontFamily)
        resolvedFonts[family]?.let { return it }
        if (UiVanillaFont.isVanillaFamily(family)) {
            val face = UiVanillaFont.face(family) ?: return EstimatedTextFont
            return VanillaTextFont(family, face).also { resolvedFonts[family] = it }
        }
        val metrics = if (UiTtfFont.isTtfFamily(family)) {
            UiTtfFont.metrics(family)
        } else {
            UiMsdfFont.getMetrics(family)
        } ?: return EstimatedTextFont
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
            val scale = fontSize / DefaultUiFontSize
            return text.length * (EstimatedGlyphWidth * scale + style.boldWeight * fontSize)
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
            val boldOffset = style.boldWeight * fontSize
            return metrics.width(text, fontSize) + text.length * boldOffset
        }

        override fun advance(char: Char, fontSize: Float, style: UiInlineStyle): Float =
            metrics.advance(char, fontSize) + style.boldWeight * fontSize
    }

    /** Measurement over Minecraft's own font assets; see [UiVanillaFont]. */
    private data class VanillaTextFont(
        val family: String,
        val face: UiVanillaFontFace,
    ) : UiTextFont {
        override val signature: Int = 31 * family.hashCode() + System.identityHashCode(face)

        override fun lineHeight(fontSize: Float): Float = face.lineHeight(fontSize)

        override fun width(text: String, fontSize: Float, style: UiInlineStyle): Float =
            face.width(text, fontSize) + text.length * style.boldWeight * fontSize

        override fun advance(char: Char, fontSize: Float, style: UiInlineStyle): Float =
            (face.advance(char) + style.boldWeight) * fontSize
    }
}

internal object UiGlyphFonts {
    fun resolve(fontFamily: String?): UiGlyphFont? {
        val family = UiTextFonts.defaultedFamily(fontFamily)
        return when {
            UiVanillaFont.isVanillaFamily(family) -> UiVanillaFont.glyphFont(family)
            UiTtfFont.isTtfFamily(family) -> UiTtfFont.glyphFont(family)
            else -> UiMsdfFont.getOrLoadFontData(family)
        }
    }
}
