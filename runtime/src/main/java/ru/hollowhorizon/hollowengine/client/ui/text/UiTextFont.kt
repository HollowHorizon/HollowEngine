package ru.hollowhorizon.hollowengine.client.ui.text

import ru.hollowhorizon.hollowengine.client.ui.widgets.UiInlineStyle
import ru.hollowhorizon.hollowengine.client.ui.widgets.boldWeight
import java.util.concurrent.ConcurrentHashMap

const val DefaultUiFontFamily = UiVanillaFont.FamilyPrefix

internal sealed interface UiTextFont {
    val signature: Int

    fun lineHeight(fontSize: Float): Float

    fun width(text: String, fontSize: Float, style: UiInlineStyle): Float
}

internal object UiTextFonts {
    private const val EstimatedGlyphWidth = 6f
    private val resolvedFonts = ConcurrentHashMap<String, UiTextFont>()

    fun defaultedFamily(fontFamily: String?): String {
        return fontFamily?.takeIf { it.isNotBlank() } ?: DefaultUiFontFamily
    }

    fun resolve(fontFamily: String?): UiTextFont {
        val family = defaultedFamily(fontFamily)
        resolvedFonts[family]?.let { return it }
        val font = when (UiFontSource.of(family)) {
            UiFontSource.VANILLA -> UiVanillaFont.face(family)?.let { VanillaTextFont(family, it) }
            UiFontSource.TTF -> UiTtfFont.metrics(family)?.let { MsdfTextFont(family, it) }
            UiFontSource.MSDF_ASSET -> UiMsdfFont.getMetrics(family)?.let { MsdfTextFont(family, it) }
        } ?: return EstimatedTextFont
        return font.also { resolvedFonts[family] = it }
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
            val scale = fontSize / EstimateBaselineFontSize
            return text.length * (EstimatedGlyphWidth * scale + style.boldWeight * fontSize)
        }

        private const val EstimateBaselineFontSize = 10f
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
    }
}

internal object UiGlyphFonts {
    private val resolved = ConcurrentHashMap<String, UiGlyphFont>()

    fun resolve(fontFamily: String?): UiGlyphFont? {
        val family = UiTextFonts.defaultedFamily(fontFamily)
        resolved[family]?.let { return it }
        val font = when (UiFontSource.of(family)) {
            UiFontSource.VANILLA -> UiVanillaFont.glyphFont(family)
            UiFontSource.TTF -> UiTtfFont.glyphFont(family)
            UiFontSource.MSDF_ASSET -> UiMsdfFont.getOrLoadFontData(family)
        } ?: return null
        return font.also { resolved[family] = it }
    }

    fun clearResolvedFonts() {
        resolved.clear()
    }

    /**
     * Lets fonts whose atlas fills in as it goes take on the glyphs baked since the last frame.
     * Called once per frame from the renderer, because it touches textures.
     */
    fun pumpDynamicGlyphs() {
        UiTtfFont.pumpGlyphs()
    }
}

internal enum class UiFontSource {
    VANILLA,
    TTF,
    MSDF_ASSET;

    companion object {
        fun of(family: String): UiFontSource = when {
            UiVanillaFont.isVanillaFamily(family) -> VANILLA
            UiTtfFont.isTtfFamily(family) -> TTF
            else -> MSDF_ASSET
        }
    }
}
