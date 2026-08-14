package ru.hollowhorizon.hollowengine.client.utils.font

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** What resolution to bake a face at and which codepoints to include. */
internal data class MsdfBakeSpec(
    val pixelSize: Float,
    val pixelRange: Float,
    val codepoints: IntArray,
) {
    override fun equals(other: Any?): Boolean = this === other ||
            (other is MsdfBakeSpec &&
                    pixelSize == other.pixelSize &&
                    pixelRange == other.pixelRange &&
                    codepoints.contentEquals(other.codepoints))

    override fun hashCode(): Int {
        var result = pixelSize.hashCode()
        result = 31 * result + pixelRange.hashCode()
        return 31 * result + codepoints.contentHashCode()
    }
}

/**
 * A finished atlas: metadata in exactly the shape the shipped MSDF assets use, plus the RGB pixels.
 * Row 0 is the atlas's **bottom** row, matching the `yOrigin: bottom` convention the glyph bounds
 * are expressed in, so the image uploads to GL without a flip.
 */
internal class BakedMsdfAtlas(
    val meta: MsdfMeta,
    val width: Int,
    val height: Int,
    val pixels: ByteArray,
)

/**
 * Bakes [face] into a multi-channel distance field atlas.
 *
 * Every glyph gets its own cell padded by half the distance range, so the field never runs off the
 * cell before it reaches zero; cells are then shelf-packed, which is within a few percent of optimal
 * for glyph-shaped rectangles and costs nothing to implement.
 */
internal fun bakeMsdfAtlas(face: TtfFace, spec: MsdfBakeSpec): BakedMsdfAtlas {
    val scale = spec.pixelSize / face.unitsPerEm
    val rangeUnits = spec.pixelRange / scale
    val padding = rangeUnits * 0.5f

    val cells = ArrayList<GlyphCell>()
    val blankGlyphs = ArrayList<MsdfGlyph>()
    for (codepoint in spec.codepoints) {
        val outline = face.loadGlyph(codepoint, spec.pixelSize) ?: continue
        val bounds = outline.shape.bounds()
        if (bounds == null || outline.shape.isEmpty) {
            blankGlyphs += MsdfGlyph(codepoint, outline.advance)
            continue
        }
        val left = floor((bounds[0] - padding) * scale).toInt()
        val bottom = floor((bounds[1] - padding) * scale).toInt()
        val right = ceil((bounds[2] + padding) * scale).toInt()
        val top = ceil((bounds[3] + padding) * scale).toInt()
        val width = right - left
        val height = top - bottom
        if (width <= 0 || height <= 0) {
            blankGlyphs += MsdfGlyph(codepoint, outline.advance)
            continue
        }
        cells += GlyphCell(codepoint, outline, left, bottom, width, height)
    }

    val atlasWidth = atlasWidthFor(cells)
    val atlasHeight = packShelves(cells, atlasWidth)
    val pixels = ByteArray(atlasWidth * atlasHeight * 3)
    val glyphs = ArrayList<MsdfGlyph>(cells.size + blankGlyphs.size)
    glyphs += blankGlyphs

    val fieldPerThread = ThreadLocal.withInitial { FloatArray(cells.maxOfOrNull { it.width * it.height * 3 } ?: 0) }
    cells.parallelStream().forEach { cell ->
        val field = fieldPerThread.get()
        cell.renderInto(field, scale, spec)
        cell.writeInto(pixels, atlasWidth, field)
    }
    for (cell in cells) {
        glyphs += MsdfGlyph(
            unicode = cell.codepoint,
            advance = cell.outline.advance,
            planeBounds = MsdfRect(
                left = cell.pixelLeft / spec.pixelSize,
                bottom = cell.pixelBottom / spec.pixelSize,
                right = (cell.pixelLeft + cell.width) / spec.pixelSize,
                top = (cell.pixelBottom + cell.height) / spec.pixelSize,
            ),
            atlasBounds = MsdfRect(
                left = cell.atlasX.toFloat(),
                bottom = cell.atlasY.toFloat(),
                right = (cell.atlasX + cell.width).toFloat(),
                top = (cell.atlasY + cell.height).toFloat(),
            ),
        )
    }

    val meta = MsdfMeta(
        atlas = MsdfAtlasInfo(
            type = "msdf",
            distanceRange = spec.pixelRange,
            size = spec.pixelSize,
            width = atlasWidth,
            height = atlasHeight,
            yOrigin = "bottom",
        ),
        name = face.familyName,
        metrics = MsdfMetrics(
            emSize = 1f,
            lineHeight = face.lineHeight,
            ascender = face.ascender,
            descender = face.descender,
            underlineY = face.underlineY,
            underlineThickness = face.underlineThickness,
        ),
        glyphs = glyphs.sortedBy { it.unicode },
    )
    return BakedMsdfAtlas(meta, atlasWidth, atlasHeight, pixels)
}

/**
 * One glyph's finished field, outside any atlas.
 */
internal class MsdfGlyphField(
    val shape: MsdfShape,
    val values: FloatArray,
    val width: Int,
    val height: Int,
    val scale: Float,
    val translateX: Float,
    val translateY: Float,
)

/** Generates and corrects one glyph's field. Null when the face has no outline for [codepoint]. */
internal fun bakeGlyphField(face: TtfFace, codepoint: Int, spec: MsdfBakeSpec): MsdfGlyphField? =
    face.loadGlyph(codepoint, spec.pixelSize)?.let { bakeGlyphField(it, face.unitsPerEm, spec) }

internal fun bakeGlyphField(outline: TtfGlyphOutline, unitsPerEm: Float, spec: MsdfBakeSpec): MsdfGlyphField? {
    val scale = spec.pixelSize / unitsPerEm
    val cell = cellFor(outline, scale, spec.pixelRange / scale) ?: return null
    val values = FloatArray(cell.width * cell.height * 3)
    cell.renderInto(values, scale, spec)
    return MsdfGlyphField(
        shape = outline.shape,
        values = values,
        width = cell.width,
        height = cell.height,
        scale = scale,
        translateX = -cell.pixelLeft / scale,
        translateY = -cell.pixelBottom / scale,
    )
}

/** The padded pixel box a glyph's field occupies, or null when it has no ink to render. */
private fun cellFor(outline: TtfGlyphOutline, scale: Float, rangeUnits: Float): GlyphCell? {
    val bounds = outline.shape.bounds() ?: return null
    if (outline.shape.isEmpty) return null
    val padding = rangeUnits * 0.5f
    val left = floor((bounds[0] - padding) * scale).toInt()
    val bottom = floor((bounds[1] - padding) * scale).toInt()
    val width = ceil((bounds[2] + padding) * scale).toInt() - left
    val height = ceil((bounds[3] + padding) * scale).toInt() - bottom
    if (width <= 0 || height <= 0) return null
    return GlyphCell(0, outline, left, bottom, width, height)
}

private class GlyphCell(
    val codepoint: Int,
    val outline: TtfGlyphOutline,
    /** Cell origin in atlas pixels relative to the glyph's own pen-on-baseline origin. */
    val pixelLeft: Int,
    val pixelBottom: Int,
    val width: Int,
    val height: Int,
) {
    var atlasX = 0
    var atlasY = 0

    /** Generates this cell's field into [field] and runs the corrections over it. */
    fun renderInto(field: FloatArray, scale: Float, spec: MsdfBakeSpec) {
        val translateX = -pixelLeft / scale
        val translateY = -pixelBottom / scale
        generateMsdf(
            shape = outline.shape,
            width = width,
            height = height,
            scale = scale,
            translateX = translateX,
            translateY = translateY,
            range = spec.pixelRange / scale,
            output = field,
        )
        correctMsdfField(
            field = field,
            width = width,
            height = height,
            shape = outline.shape,
            scale = scale,
            translateX = translateX,
            translateY = translateY,
            pixelRange = spec.pixelRange,
        )
    }

    /**
     * Copies one cell's field into the atlas. Distances outside `[0, 1]` are clamped rather than
     * wrapped: they are further away than the range can express, so the exact value is meaningless.
     */
    fun writeInto(pixels: ByteArray, atlasWidth: Int, field: FloatArray) {
        for (row in 0 until height) {
            var source = row * width * 3
            var destination = ((atlasY + row) * atlasWidth + atlasX) * 3
            repeat(width) {
                pixels[destination] = field[source].toDistanceByte()
                pixels[destination + 1] = field[source + 1].toDistanceByte()
                pixels[destination + 2] = field[source + 2].toDistanceByte()
                source += 3
                destination += 3
            }
        }
    }
}

private fun Float.toDistanceByte(): Byte = ((this * 255f) + 0.5f).toInt().coerceIn(0, 255).toByte()

internal fun MsdfGlyphField.toDistanceBytes(): ByteArray {
    val bytes = ByteArray(width * height * 3)
    for (index in bytes.indices) bytes[index] = values[index].toDistanceByte()
    return bytes
}

internal val MsdfGlyphField.pixelLeft: Float get() = -translateX * scale
internal val MsdfGlyphField.pixelBottom: Float get() = -translateY * scale

/** Wide enough for the widest glyph plus its gutters, and a power of two for driver friendliness. */
private fun atlasWidthFor(cells: List<GlyphCell>): Int {
    val widest = (cells.maxOfOrNull { it.width } ?: 1) + 2 * CellGutter
    var width = MinAtlasWidth
    while (width < widest && width < MaxAtlasSize) width *= 2
    return width
}

/** Places the cells tallest-first into rows; returns the power-of-two height they fit in. */
private fun packShelves(cells: MutableList<GlyphCell>, atlasWidth: Int): Int {
    cells.sortWith(compareByDescending<GlyphCell> { it.height }.thenByDescending { it.width })
    var shelfY = CellGutter
    var shelfHeight = 0
    var cursorX = CellGutter
    for (cell in cells) {
        if (cursorX + cell.width + CellGutter > atlasWidth) {
            shelfY += shelfHeight + CellGutter
            shelfHeight = 0
            cursorX = CellGutter
        }
        cell.atlasX = cursorX
        cell.atlasY = shelfY
        cursorX += cell.width + CellGutter
        shelfHeight = max(shelfHeight, cell.height)
    }
    val used = shelfY + shelfHeight + CellGutter
    var height = 1
    while (height < used) height *= 2
    check(height <= MaxAtlasSize) {
        "baked atlas would be ${atlasWidth}x$height; lower the font size or narrow the charset"
    }
    return height.coerceAtLeast(1)
}

private const val MinAtlasWidth = 512
private const val MaxAtlasSize = 4096
private const val CellGutter = 2
