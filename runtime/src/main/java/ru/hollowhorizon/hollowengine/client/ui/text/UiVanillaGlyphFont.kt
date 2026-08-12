package ru.hollowhorizon.hollowengine.client.ui.text

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

internal class UiVanillaGlyphFont(private val face: UiVanillaFontFace) : UiGlyphFont {
    private val pages = HashMap<ResourceLocation, UiGlyphAtlasPage>()
    private val placed = HashMap<Char, UiPlacedGlyph>()

    override val lineHeight: Float get() = UiVanillaFont.lineHeightEm
    override val ascender: Float get() = UiVanillaFont.ascenderEm
    override val descender: Float get() = UiVanillaFont.descenderEm
    override val underlineY: Float get() = UiVanillaFont.underlineYEm
    override val underlineThickness: Float get() = UiVanillaFont.underlineThicknessEm
    override val strikethroughY: Float get() = UiVanillaFont.strikethroughYEm
    override val strikethroughThickness: Float get() = UiVanillaFont.underlineThicknessEm
    override val emPixels: Float get() = UiVanillaFont.EmPixels

    override fun advance(char: Char): Float = face.advance(char)

    override fun glyph(char: Char): UiPlacedGlyph? {
        placed[char]?.let { return it }
        val glyph = face.glyphOrFallback(char) ?: return null
        val texture = glyph.texture ?: return null
        val page = pages.getOrPut(texture) { pageFor(texture) }
        return UiPlacedGlyph(
            advance = glyph.advance,
            left = glyph.left,
            top = glyph.top,
            right = glyph.right,
            bottom = glyph.bottom,
            uMin = glyph.uMin,
            vTop = glyph.vTop,
            uMax = glyph.uMax,
            vBottom = glyph.vBottom,
            page = page,
        ).also { placed[char] = it }
    }

    private fun pageFor(texture: ResourceLocation) = UiGlyphAtlasPage(
        textureId = Minecraft.getInstance().textureManager.getTexture(texture).id,
        width = 1f,
        height = 1f,
        distanceRange = 0f,
        sampling = if (texture in face.coloredSheets) UiGlyphSampling.COLOR else UiGlyphSampling.ALPHA,
    )
}
