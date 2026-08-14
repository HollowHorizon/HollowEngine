package ru.hollowhorizon.hollowengine.client.ui.text

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.platform.TextureUtil
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.opengl.GL11
import ru.hollowhorizon.hollowengine.client.utils.font.MsdfGlyph
import ru.hollowhorizon.hollowengine.client.utils.font.MsdfMeta
import ru.hollowhorizon.hollowengine.client.utils.stream
import ru.hollowhorizon.hollowengine.common.utils.json.JsonFormat
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.util.concurrent.ConcurrentHashMap

data class UiMsdfFontData(
    val metrics: UiMsdfFontMetrics,
    val textureId: Int,
) : UiGlyphFont {
    val meta: MsdfMeta get() = metrics.meta

    private val page = UiGlyphAtlasPage(
        textureId = textureId,
        width = meta.atlas.width.toFloat(),
        height = meta.atlas.height.toFloat(),
        distanceRange = meta.atlas.distanceRange,
        sampling = UiGlyphSampling.MSDF,
    )

    /**
     * Placement is derived once per font rather than per drawn glyph: the atlas is a few thousand
     * entries at most, and text rendering would otherwise allocate a quad description per character
     * per frame.
     */
    private val placed: Map<Int, UiPlacedGlyph> = metrics.glyphMap.mapValues { (_, glyph) -> glyph.place(page) }

    override val lineHeight: Float get() = meta.metrics.lineHeight
    override val ascender: Float get() = meta.metrics.ascender
    override val descender: Float get() = meta.metrics.descender
    override val underlineY: Float get() = meta.metrics.underlineY
    override val underlineThickness: Float get() = meta.metrics.underlineThickness
    override val emPixels: Float get() = meta.atlas.size

    override fun glyph(codepoint: Int): UiPlacedGlyph? =
        placed[codepoint] ?: placed[UiMsdfFont.FallbackGlyph.code] ?: placed[LegacyFallbackGlyph.code]

    override fun advance(codepoint: Int): Float = metrics.advance(codepoint, 1f)

    private companion object {
        const val LegacyFallbackGlyph = '?'
    }
}

/**
 * Per-codepoint advances read from the font program itself, independent of any atlas.
 */
fun interface UiGlyphAdvances {
    /** The advance in em, or null when the face has no glyph for [codepoint]. */
    fun advanceOf(codepoint: Int): Float?
}

data class UiMsdfFontMetrics(
    val meta: MsdfMeta,
    val glyphMap: Map<Int, MsdfGlyph>,
    /** Set for faces the engine can read directly; the atlas is then only a cache of pictures. */
    val advances: UiGlyphAdvances? = null,
) {
    fun glyphOrFallback(codepoint: Int): MsdfGlyph? =
        glyphMap[codepoint] ?: glyphMap[UiMsdfFont.FallbackGlyph.code] ?: glyphMap[LegacyFallbackGlyph.code]

    /** Whether the face itself has this codepoint, as opposed to an atlas not having got to it. */
    fun covers(codepoint: Int): Boolean = advances?.advanceOf(codepoint) != null

    fun advance(codepoint: Int, fontSize: Float): Float {
        advances?.let { face ->
            face.advanceOf(codepoint)?.let { return it * fontSize }
            face.advanceOf(UiMsdfFont.FallbackGlyph.code)?.let { return it * fontSize }
        }
        val glyph = glyphMap[codepoint] ?: glyphOrFallback(codepoint) ?: glyphMap[SpaceGlyph.code]
        return (glyph?.advance ?: FallbackAdvance) * fontSize
    }

    fun width(text: String, fontSize: Float): Float {
        var total = 0f
        text.forEachCodepoint { total += advance(it, fontSize) }
        return total
    }

    fun lineHeight(fontSize: Float): Float = meta.metrics.lineHeight * fontSize

    companion object {
        private const val LegacyFallbackGlyph = '?'
        private const val SpaceGlyph = ' '
        private const val FallbackAdvance = 0.5f
    }
}

object UiMsdfFont {
    /** The atlas shipped with the engine, for anything that wants a scalable font over the default. */
    const val MonocraftFontFamily = "hollowengine:fonts/monocraft"

    /** Rendered in place of any codepoint the atlas lacks, so text is never silently dropped. */
    const val FallbackGlyph = '�'

    private val fonts = ConcurrentHashMap<String, UiMsdfFontData>()
    private val metrics = ConcurrentHashMap<String, UiMsdfFontMetrics>()

    private val missingTextures = ConcurrentHashMap.newKeySet<String>()

    fun isLoaded(fontPath: String): Boolean = fonts.containsKey(fontPath)

    fun loadFont(fontPath: String) {
        if (fontPath in missingTextures) return
        if (fonts.containsKey(fontPath)) return

        runCatching {
            val fontMetrics = loadMetrics(fontPath) ?: error("MSDF metrics for $fontPath are unavailable")
            val imageStream = "$fontPath.png".rl.stream
            val nativeImage = NativeImage.read(imageStream)
            nativeImage.flipY()

            val textureId = GL11.glGenTextures()
            RenderSystem.bindTexture(textureId)
            TextureUtil.prepareImage(textureId, nativeImage.width, nativeImage.height)
            nativeImage.upload(0, 0, 0, 0, 0, nativeImage.width, nativeImage.height, true, true, false, false)
            nativeImage.close()

            fonts[fontPath] = UiMsdfFontData(
                metrics = fontMetrics,
                textureId = textureId,
            )
        }.onFailure {
            missingTextures += fontPath
        }.getOrThrow()
    }

    fun getOrLoadFontData(fontPath: String): UiMsdfFontData? {
        if (fontPath in missingTextures) return null
        return runCatching {
            loadFont(fontPath)
            fonts[fontPath]
        }.getOrNull()
    }

    /**
     * MSDF metrics for [fontPath], or null if the atlas metadata cannot be read yet. Metrics are
     * cheap JSON and NOT permanently blacklisted on failure: a call before the resource manager is
     * ready (e.g. headless tests, early init) returns null but a later call still succeeds.
     */
    fun getMetrics(fontPath: String): UiMsdfFontMetrics? = loadMetrics(fontPath)

    fun unloadAll() {
        fonts.values.forEach { entry ->
            GL11.glDeleteTextures(entry.textureId)
        }
        fonts.clear()
        metrics.clear()
        missingTextures.clear()
        UiVanillaFont.unloadAll()
        UiTtfFont.unloadAll()
        UiTextFonts.clearResolvedFonts()
        UiGlyphFonts.clearResolvedFonts()
    }

    private fun loadMetrics(fontPath: String): UiMsdfFontMetrics? {
        metrics[fontPath]?.let { return it }
        return runCatching {
            val metaStream = "$fontPath.json".rl.stream
            val fontInfo: MsdfMeta = JsonFormat.decodeFromStream(metaStream)
            val glyphs = fontInfo.glyphs.ifEmpty {
                fontInfo.compactGlyphs.map { it.toMsdfGlyph() }
            }
            val glyphMap = glyphs.associateBy { it.unicode }
            UiMsdfFontMetrics(fontInfo, glyphMap).also { metrics[fontPath] = it }
        }.getOrNull()
    }
}
