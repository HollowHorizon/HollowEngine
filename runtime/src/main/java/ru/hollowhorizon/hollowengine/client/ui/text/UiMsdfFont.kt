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
) {
    val meta: MsdfMeta get() = metrics.meta
    val glyphMap: Map<Char, MsdfGlyph> get() = metrics.glyphMap

    /** The glyph for [char], or the atlas fallback glyph when the codepoint is not covered. */
    fun glyphOrFallback(char: Char): MsdfGlyph? = metrics.glyphOrFallback(char)
}

data class UiMsdfFontMetrics(
    val meta: MsdfMeta,
    val glyphMap: Map<Char, MsdfGlyph>,
) {
    fun glyphOrFallback(char: Char): MsdfGlyph? =
        glyphMap[char] ?: glyphMap[UiMsdfFont.FallbackGlyph] ?: glyphMap[LegacyFallbackGlyph]

    fun advance(char: Char, fontSize: Float): Float {
        val glyph = glyphMap[char] ?: glyphOrFallback(char) ?: glyphMap[SpaceGlyph]
        return (glyph?.advance ?: FallbackAdvance) * fontSize
    }

    fun width(text: String, fontSize: Float): Float = text.sumOf { advance(it, fontSize).toDouble() }.toFloat()

    fun lineHeight(fontSize: Float): Float = meta.metrics.lineHeight * fontSize

    companion object {
        private const val LegacyFallbackGlyph = '?'
        private const val SpaceGlyph = ' '
        private const val FallbackAdvance = 0.5f
    }
}

object UiMsdfFont {
    const val DefaultFontFamily = "hollowengine:fonts/monocraft"

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
        UiTextFonts.clearResolvedFonts()
    }

    private fun loadMetrics(fontPath: String): UiMsdfFontMetrics? {
        metrics[fontPath]?.let { return it }
        return runCatching {
            val metaStream = "$fontPath.json".rl.stream
            val fontInfo: MsdfMeta = JsonFormat.decodeFromStream(metaStream)
            val glyphs = fontInfo.glyphs.ifEmpty {
                fontInfo.compactGlyphs.map { it.toMsdfGlyph() }
            }
            val glyphMap = glyphs.associateBy { it.unicode.toChar() }
            UiMsdfFontMetrics(fontInfo, glyphMap).also { metrics[fontPath] = it }
        }.getOrNull()
    }
}
