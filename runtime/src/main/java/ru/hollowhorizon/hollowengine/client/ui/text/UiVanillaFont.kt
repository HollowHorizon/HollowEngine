package ru.hollowhorizon.hollowengine.client.ui.text

import com.mojang.blaze3d.platform.NativeImage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.utils.json.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Minecraft's own font *assets*, `assets/<namespace>/font/<name>.json` plus the bitmap sheets it points at.
 */
object UiVanillaFont {
    const val FamilyPrefix = "vanilla"

    /** Pixels per em. Vanilla's nominal glyph box, from which its 9px line height derives. */
    const val EmPixels = 8f

    /** Baseline offset from the line's top edge: the `7.0f` bearing `SheetGlyphInfo` measures against. */
    const val BaselinePixels = 7f

    private const val LineHeightPixels = 9f
    private const val DefaultBitmapHeight = 8
    private const val FallbackGlyph = '�'
    private const val LegacyFallbackGlyph = '?'
    private const val FallbackAdvancePixels = 6f

    private val faces = ConcurrentHashMap<ResourceLocation, UiVanillaFontFace>()
    private val glyphFonts = ConcurrentHashMap<ResourceLocation, UiVanillaGlyphFont>()
    private val missing = ConcurrentHashMap.newKeySet<ResourceLocation>()

    fun isVanillaFamily(fontFamily: String): Boolean =
        fontFamily == FamilyPrefix || fontFamily.startsWith("$FamilyPrefix:")

    fun locationOf(fontFamily: String): ResourceLocation? {
        if (!isVanillaFamily(fontFamily)) return null
        val name = fontFamily.removePrefix(FamilyPrefix).removePrefix(":").ifBlank { "default" }
        return if (':' in name) ResourceLocation.tryParse(name) else ResourceLocation.withDefaultNamespace(name)
    }

    fun face(fontFamily: String): UiVanillaFontFace? {
        val location = locationOf(fontFamily) ?: return null
        if (location in missing) return null
        faces[location]?.let { return it }
        return runCatching { loadFace(location) }
            .onFailure {
                missing += location
                HollowEngine.LOGGER.warn("Could not read vanilla font {}: {}", location, it.message)
            }
            .getOrNull()
            ?.also { faces[location] = it }
    }

    fun glyphFont(fontFamily: String): UiGlyphFont? {
        val location = locationOf(fontFamily) ?: return null
        glyphFonts[location]?.let { return it }
        val face = face(fontFamily) ?: return null
        return UiVanillaGlyphFont(face).also { glyphFonts[location] = it }
    }

    fun unloadAll() {
        faces.clear()
        glyphFonts.clear()
        missing.clear()
    }

    private fun loadFace(location: ResourceLocation): UiVanillaFontFace {
        val glyphs = HashMap<Char, UiVanillaGlyph>()
        readFont(location, glyphs, HashSet())
        check(glyphs.isNotEmpty()) { "no usable providers in font/${location.path}.json" }
        return UiVanillaFontFace(location, glyphs)
    }

    private fun readFont(
        location: ResourceLocation,
        glyphs: MutableMap<Char, UiVanillaGlyph>,
        visited: MutableSet<ResourceLocation>,
    ) {
        if (!visited.add(location)) return
        val definition = ResourceLocation.fromNamespaceAndPath(location.namespace, "font/${location.path}.json")
        val resources = runCatching {
            Minecraft.getInstance().resourceManager.getResourceStack(definition)
        }.getOrNull().orEmpty()
        check(resources.isNotEmpty()) { "no font definition at $definition" }
        for (resource in resources.asReversed()) {
            val providers = resource.open().use { stream ->
                json.parseToJsonElement(stream.readBytes().decodeToString())
                    .jsonObject["providers"]?.jsonArray.orEmpty()
                    .map { it.jsonObject }
            }
            for (provider in providers) readProvider(provider, glyphs, visited)
        }
    }

    private fun readProvider(
        provider: JsonObject,
        glyphs: MutableMap<Char, UiVanillaGlyph>,
        visited: MutableSet<ResourceLocation>,
    ) {
        when (provider["type"]?.jsonPrimitive?.contentOrNull) {
            "bitmap" -> readBitmapProvider(provider, glyphs)
            "space" -> readSpaceProvider(provider, glyphs)
            "reference" -> readReferenceProvider(provider, glyphs, visited)
            else -> Unit
        }
    }

    private fun readReferenceProvider(
        provider: JsonObject,
        glyphs: MutableMap<Char, UiVanillaGlyph>,
        visited: MutableSet<ResourceLocation>,
    ) {
        val id = provider["id"]?.jsonPrimitive?.contentOrNull?.let(ResourceLocation::tryParse) ?: return
        runCatching { readFont(id, glyphs, visited) }.onFailure {
            HollowEngine.LOGGER.warn("Vanilla font reference {} is unreadable: {}", id, it.message)
        }
    }

    private fun readSpaceProvider(provider: JsonObject, glyphs: MutableMap<Char, UiVanillaGlyph>) {
        val advances = provider["advances"]?.jsonObject ?: return
        for ((key, value) in advances) {
            val char = key.singleCodepointOrNull() ?: continue
            val advance = value.jsonPrimitive.floatOrNull ?: continue
            glyphs.putIfAbsent(char, UiVanillaGlyph.blank(advance / EmPixels))
        }
    }

    private fun readBitmapProvider(provider: JsonObject, glyphs: MutableMap<Char, UiVanillaGlyph>) {
        val file = provider["file"]?.jsonPrimitive?.contentOrNull?.let(ResourceLocation::tryParse) ?: return
        val rows = provider["chars"]?.jsonArray?.map { it.jsonPrimitive.content } ?: return
        if (rows.isEmpty()) return
        val grid = rows.map { it.codePoints().toArray() }
        val columns = grid[0].size
        if (columns == 0) return
        val height = provider["height"]?.jsonPrimitive?.intOrNull ?: DefaultBitmapHeight
        val ascent = provider["ascent"]?.jsonPrimitive?.int ?: return
        val texture = ResourceLocation.fromNamespaceAndPath(file.namespace, "textures/${file.path}")

        val sheet = runCatching {
            Minecraft.getInstance().resourceManager.getResourceOrThrow(texture).open().use {
                NativeImage.read(NativeImage.Format.RGBA, it)
            }
        }.getOrElse {
            HollowEngine.LOGGER.warn("Vanilla font sheet {} is unreadable: {}", texture, it.message)
            return
        }

        sheet.use { image ->
            val cellWidth = image.width / columns
            val cellHeight = image.height / grid.size
            if (cellWidth <= 0 || cellHeight <= 0) return
            val scale = height.toFloat() / cellHeight.toFloat()
            val top = -ascent / EmPixels
            val bottom = (height - ascent) / EmPixels
            val right = cellWidth * scale / EmPixels
            for (row in grid.indices) {
                for (column in grid[row].indices) {
                    val codepoint = grid[row][column]
                    if (codepoint == 0 || codepoint > Char.MAX_VALUE.code) continue
                    val char = codepoint.toChar()
                    if (char in glyphs) continue
                    val trimmed = trimmedWidth(image, cellWidth, cellHeight, column, row)
                    val advance = ((0.5 + trimmed * scale).toInt() + 1) / EmPixels
                    glyphs[char] = UiVanillaGlyph(
                        advance = advance,
                        left = 0f,
                        top = top,
                        right = right,
                        bottom = bottom,
                        uMin = (column * cellWidth).toFloat() / image.width,
                        vTop = (row * cellHeight).toFloat() / image.height,
                        uMax = ((column + 1) * cellWidth).toFloat() / image.width,
                        vBottom = ((row + 1) * cellHeight).toFloat() / image.height,
                        texture = texture,
                    )
                }
            }
        }
    }

    private fun trimmedWidth(image: NativeImage, cellWidth: Int, cellHeight: Int, column: Int, row: Int): Int {
        for (x in cellWidth - 1 downTo 0) {
            val imageX = column * cellWidth + x
            for (y in 0 until cellHeight) {
                if (image.getLuminanceOrAlpha(imageX, row * cellHeight + y).toInt() != 0) return x + 1
            }
        }
        return 0
    }

    private fun String.singleCodepointOrNull(): Char? {
        val codepoints = codePoints().toArray()
        val codepoint = codepoints.singleOrNull() ?: return null
        return if (codepoint > Char.MAX_VALUE.code) null else codepoint.toChar()
    }

    internal val fallbackChars = charArrayOf(FallbackGlyph, LegacyFallbackGlyph)

    internal val fallbackAdvance: Float get() = FallbackAdvancePixels / EmPixels
    internal val lineHeightEm: Float get() = LineHeightPixels / EmPixels
    internal val ascenderEm: Float get() = BaselinePixels / EmPixels
    internal val descenderEm: Float get() = (BaselinePixels - LineHeightPixels) / EmPixels

    internal val underlineYEm: Float get() = -1.5f / EmPixels
    internal val underlineThicknessEm: Float get() = 1f / EmPixels

    internal val strikethroughYEm: Float get() = 3f / EmPixels
}

internal data class UiVanillaGlyph(
    val advance: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val uMin: Float,
    val vTop: Float,
    val uMax: Float,
    val vBottom: Float,
    val texture: ResourceLocation?,
) {
    companion object {
        fun blank(advance: Float) = UiVanillaGlyph(advance, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, null)
    }
}

class UiVanillaFontFace internal constructor(
    val location: ResourceLocation,
    internal val glyphs: Map<Char, UiVanillaGlyph>,
) {
    internal fun glyphOrFallback(char: Char): UiVanillaGlyph? {
        glyphs[char]?.let { return it }
        for (fallback in UiVanillaFont.fallbackChars) glyphs[fallback]?.let { return it }
        return null
    }

    fun advance(char: Char): Float =
        (glyphs[char] ?: glyphOrFallback(char))?.advance ?: UiVanillaFont.fallbackAdvance

    fun width(text: String, fontSize: Float): Float =
        text.sumOf { advance(it).toDouble() }.toFloat() * fontSize

    fun lineHeight(fontSize: Float): Float = UiVanillaFont.lineHeightEm * fontSize
}
