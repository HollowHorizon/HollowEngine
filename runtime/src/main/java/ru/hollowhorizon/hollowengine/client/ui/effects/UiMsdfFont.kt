package ru.hollowhorizon.hollowengine.client.ui.effects

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.platform.TextureUtil
import com.mojang.blaze3d.systems.RenderSystem
import de.fabmax.kool.util.MsdfGlyph
import de.fabmax.kool.util.MsdfMeta
import org.lwjgl.opengl.GL11
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
}

data class UiMsdfFontMetrics(
    val meta: MsdfMeta,
    val glyphMap: Map<Char, MsdfGlyph>,
) {
    fun advance(char: Char, fontSize: Float): Float {
        val glyph = glyphMap[char] ?: glyphMap[FallbackGlyph] ?: glyphMap[SpaceGlyph]
        return (glyph?.advance ?: FallbackAdvance) * fontSize
    }

    fun width(text: String, fontSize: Float): Float = text.sumOf { advance(it, fontSize).toDouble() }.toFloat()

    fun lineHeight(fontSize: Float): Float = meta.metrics.lineHeight * fontSize

    companion object {
        private const val FallbackGlyph = '?'
        private const val SpaceGlyph = ' '
        private const val FallbackAdvance = 0.5f
    }
}

object UiMsdfFont {

    private val fonts = ConcurrentHashMap<String, UiMsdfFontData>()
    private val metrics = ConcurrentHashMap<String, UiMsdfFontMetrics>()
    private val missingFonts = ConcurrentHashMap.newKeySet<String>()

    fun isLoaded(fontPath: String): Boolean = fonts.containsKey(fontPath)

    fun loadFont(fontPath: String) {
        if (fontPath in missingFonts) return
        if (fonts.containsKey(fontPath)) return

        runCatching {
            val fontMetrics = loadMetrics(fontPath)
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
            missingFonts += fontPath
        }.getOrThrow()
    }

    fun getFontData(fontPath: String): UiMsdfFontData? = fonts[fontPath]

    fun getOrLoadFontData(fontPath: String): UiMsdfFontData? {
        if (fontPath in missingFonts) return null
        return runCatching {
            loadFont(fontPath)
            fonts[fontPath]
        }.getOrNull()
    }

    fun getMetrics(fontPath: String): UiMsdfFontMetrics? {
        if (fontPath in missingFonts) return null
        return runCatching { loadMetrics(fontPath) }.getOrNull()
    }

    fun unloadAll() {
        fonts.values.forEach { entry ->
            GL11.glDeleteTextures(entry.textureId)
        }
        fonts.clear()
        metrics.clear()
        missingFonts.clear()
    }

    private fun loadMetrics(fontPath: String): UiMsdfFontMetrics {
        if (fontPath in missingFonts) error("MSDF font $fontPath is not available")
        metrics[fontPath]?.let { return it }

        return runCatching {
            val metaStream = "$fontPath.json".rl.stream
            val fontInfo: MsdfMeta = JsonFormat.decodeFromStream(metaStream)
            val glyphs = fontInfo.glyphs.ifEmpty {
                fontInfo.compactGlyphs.map { it.toMsdfGlyph() }
            }
            val glyphMap = glyphs.associateBy { it.unicode.toChar() }
            UiMsdfFontMetrics(fontInfo, glyphMap).also { metrics[fontPath] = it }
        }.onFailure {
            missingFonts += fontPath
        }.getOrThrow()
    }
}
