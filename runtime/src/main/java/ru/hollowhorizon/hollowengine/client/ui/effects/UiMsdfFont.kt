package ru.hollowhorizon.hollowengine.client.ui.effects

import com.mojang.blaze3d.platform.NativeImage
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

        val textureId = GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP)
        nativeImage.upload(0, 0, 0, false)
        nativeImage.close()

        fonts[fontPath] = UiMsdfFontData(
            meta = fontInfo,
            glyphMap = glyphMap,
            textureId = textureId,
        )
    }

    fun getFontData(fontPath: String): UiMsdfFontData? = fonts[fontPath]

    fun bindTexture(fontPath: String): Boolean {
        val entry = fonts[fontPath] ?: return false
        RenderSystem.bindTexture(entry.textureId)
        return true
    }

    fun unloadAll() {
        fonts.values.forEach { entry ->
            GL11.glDeleteTextures(entry.textureId)
        }
        fonts.clear()
    }
}
