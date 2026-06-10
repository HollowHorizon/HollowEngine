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
    val meta: MsdfMeta,
    val glyphMap: Map<Char, MsdfGlyph>,
    val textureId: Int,
)

object UiMsdfFont {

    private val fonts = ConcurrentHashMap<String, UiMsdfFontData>()

    fun isLoaded(fontPath: String): Boolean = fonts.containsKey(fontPath)

    fun loadFont(fontPath: String) {
        if (fonts.containsKey(fontPath)) return

        val metaStream = "$fontPath.json".rl.stream
        val fontInfo: MsdfMeta = JsonFormat.decodeFromStream(metaStream)
        val glyphs = fontInfo.glyphs.ifEmpty {
            fontInfo.compactGlyphs.map { it.toMsdfGlyph() }
        }
        val glyphMap = glyphs.associateBy { it.unicode.toChar() }

        val imageStream = "$fontPath.png".rl.stream
        val nativeImage = NativeImage.read(imageStream)
        nativeImage.flipY()

        val textureId = GL11.glGenTextures()
        RenderSystem.bindTexture(textureId)
        TextureUtil.prepareImage(textureId, nativeImage.width, nativeImage.height)
        nativeImage.upload(0, 0, 0, 0, 0, nativeImage.width, nativeImage.height, true, true, false, false)
        nativeImage.close()

        fonts[fontPath] = UiMsdfFontData(
            meta = fontInfo,
            glyphMap = glyphMap,
            textureId = textureId,
        )
    }

    fun getFontData(fontPath: String): UiMsdfFontData? = fonts[fontPath]

    fun unloadAll() {
        fonts.values.forEach { entry ->
            GL11.glDeleteTextures(entry.textureId)
        }
        fonts.clear()
    }
}
