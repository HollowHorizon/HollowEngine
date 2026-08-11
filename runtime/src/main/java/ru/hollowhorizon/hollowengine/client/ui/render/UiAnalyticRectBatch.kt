package ru.hollowhorizon.hollowengine.client.ui.render

import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.render.UiShaderClip.Companion.None
import ru.hollowhorizon.hollowengine.client.ui.style.UiFilterChain
import ru.hollowhorizon.hollowengine.client.ui.style.UiShadow
import ru.hollowhorizon.hollowengine.client.ui.text.UiGlyphSampling
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.min

internal class UiAnalyticRectBatch {
    private val instances = UiFloatArrayBuilder()
    private val records = UiFloatArrayBuilder()
    private val paints = UiFloatArrayBuilder()
    private val stops = UiFloatArrayBuilder()
    private val paintEncoder = UiPaintBufferEncoder(paints, stops)

    val isEmpty: Boolean get() = instances.size == 0
    val instanceCount: Int get() = instances.size / InstanceStride
    val instanceFloatCount: Int get() = instances.size
    val recordFloatCount: Int get() = records.size
    val paintFloatCount: Int get() = paints.size
    val stopFloatCount: Int get() = stops.size

    /** Font-atlas texture bound for glyph instances in this batch, or 0 when it carries no glyphs. */
    var atlasTextureId: Int = 0
        private set
    var glyphDistanceRange: Float = 0f
        private set
    var glyphAtlasWidth: Float = 0f
        private set
    var glyphAtlasHeight: Float = 0f
        private set

    /** Whether [atlas] can share this batch: a batch carries glyphs from at most one atlas. */
    fun acceptsGlyphAtlas(atlas: Int): Boolean = atlasTextureId == 0 || atlasTextureId == atlas

    fun canAppend(command: DrawBoxCommand): Boolean {
        if (command.renderToFramebuffer) return false
        if (command.paint != UiResolvedPaint.None && !command.paint.isBufferPaint()) return false
        uniformBorderWidth(command) ?: return false
        return true
    }

    fun append(command: DrawBoxCommand, transform: UiMatrix4, clip: UiShaderClip) {
        val width = command.rect.width
        val height = command.rect.height
        if (width <= 0f || height <= 0f || command.opacity <= 0f) return
        val borderWidth = checkNotNull(uniformBorderWidth(command)).coerceIn(0f, min(width, height) * 0.5f)
        if (command.paint == UiResolvedPaint.None && (borderWidth <= 0f || command.border.color.alpha <= 0f)) return
        val paintIndex = if (command.paint == UiResolvedPaint.None) {
            NoPaint
        } else {
            paintEncoder.append(command.paint, command.opacity, command.filter, width, height)
        }
        val borderColor = if (borderWidth > 0f) {
            command.border.color.withOpacity(command.opacity).filtered(command.filter)
        } else {
            UiColor.Transparent
        }
        val radius = command.border.radius.coerceIn(0f, min(width, height) * 0.5f)
        records.add(width, height, radius, borderWidth)
        records.add(paintIndex.toFloat(), 0f, 0f, 0f)
        records.add(borderColor.red, borderColor.green, borderColor.blue, borderColor.alpha)
        records.add(clip.minX, clip.minY, clip.maxX, clip.maxY)
        appendInstance(transform, 0f, 0f, width, height)
    }

    fun appendShadow(
        command: DrawShadowCommand,
        shadow: UiShadow,
        transform: UiMatrix4,
        clip: UiShaderClip,
    ) {
        val width = command.rect.width
        val height = command.rect.height
        if (width <= 0f || height <= 0f || command.opacity <= 0f || shadow.color.alpha <= 0f) return
        val blurRadius = shadow.blur.coerceAtLeast(0f)
        val spreadRadius = shadow.spread
        val rasterMargin = abs(spreadRadius) + blurRadius * BlurExtentFactor + AntialiasMargin
        val paintIndex = paintEncoder.append(
            UiResolvedPaint.Color(shadow.color),
            command.opacity,
            command.filter,
            width,
            height,
        )
        val radius = command.radius.coerceIn(0f, min(width, height) * 0.5f)
        records.add(width, height, radius, 0f)
        records.add(paintIndex.toFloat(), blurRadius, spreadRadius, ShadowMode)
        records.add(0f, 0f, 0f, 0f)
        records.add(clip.minX, clip.minY, clip.maxX, clip.maxY)
        appendInstance(
            transform,
            -rasterMargin,
            -rasterMargin,
            width + rasterMargin,
            height + rasterMargin,
        )
    }

    /**
     * A single glyph quad. [minX]..[maxY] are the glyph's local-space bounds (the shared run
     * [transform] maps them to screen), [u0]..[v1] the atlas UV rect, drawn in [color] and clipped
     * by [clip]. All glyphs in a batch must share one [atlas] (see [acceptsGlyphAtlas]).
     *
     * [sampling] picks how the atlas texel becomes coverage, and [sdBias] grows the signed-distance
     * edge for faux-bold (distance-field sampling only; ignored for bitmap atlases).
     */
    fun appendGlyph(
        transform: UiMatrix4,
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        color: UiColor,
        clip: UiShaderClip,
        atlas: Int,
        distanceRange: Float,
        atlasWidth: Float,
        atlasHeight: Float,
        sampling: UiGlyphSampling = UiGlyphSampling.MSDF,
        sdBias: Float = 0f,
    ) {
        atlasTextureId = atlas
        glyphDistanceRange = distanceRange
        glyphAtlasWidth = atlasWidth
        glyphAtlasHeight = atlasHeight
        records.add(GlyphMarker, sdBias, sampling.shaderMode.toFloat(), 0f)
        records.add(u0, v0, u1, v1)
        records.add(color.red, color.green, color.blue, color.alpha)
        records.add(clip.minX, clip.minY, clip.maxX, clip.maxY)
        appendInstance(transform, minX, minY, maxX, maxY)
    }

    /**
     * A plain untextured rectangle at the batch's current position, for text decorations (underline
     * and strikethrough rules). Sharing the glyph batch keeps a decorated run at one draw call and
     * puts the rule on top of the glyphs it follows.
     */
    fun appendSolidRect(
        transform: UiMatrix4,
        width: Float,
        height: Float,
        color: UiColor,
        clip: UiShaderClip,
    ) {
        if (width <= 0f || height <= 0f || color.alpha <= 0f) return
        val paintIndex = paintEncoder.append(UiResolvedPaint.Color(color), 1f, UiFilterChain.Empty, width, height)
        records.add(width, height, 0f, 0f)
        records.add(paintIndex.toFloat(), 0f, 0f, 0f)
        records.add(0f, 0f, 0f, 0f)
        records.add(clip.minX, clip.minY, clip.maxX, clip.maxY)
        appendInstance(transform, 0f, 0f, width, height)
    }

    fun clear() {
        instances.clear()
        records.clear()
        paints.clear()
        stops.clear()
        atlasTextureId = 0
        glyphDistanceRange = 0f
        glyphAtlasWidth = 0f
        glyphAtlasHeight = 0f
    }

    fun writeInstances(destination: FloatBuffer) = instances.writeTo(destination)
    fun writeRecords(destination: FloatBuffer) = records.writeTo(destination)
    fun writePaints(destination: FloatBuffer) = paints.writeTo(destination)
    fun writeStops(destination: FloatBuffer) = stops.writeTo(destination)

    private fun appendInstance(transform: UiMatrix4, minX: Float, minY: Float, maxX: Float, maxY: Float) {
        instances.addMatrix(transform)
        instances.add(minX, minY, maxX, maxY)
    }

    private fun uniformBorderWidth(command: DrawBoxCommand): Float? {
        val width = command.rect.width
        val height = command.rect.height
        val left = command.border.width.left.resolve(width)
        val top = command.border.width.top.resolve(height)
        val right = command.border.width.right.resolve(width)
        val bottom = command.border.width.bottom.resolve(height)
        return left.takeIf {
            abs(it - top) <= BorderEpsilon &&
                    abs(it - right) <= BorderEpsilon &&
                    abs(it - bottom) <= BorderEpsilon
        }
    }

    companion object {
        const val InstanceStride = 20
        const val RecordStride = 16
        const val NoPaint = -1

        /** Record texel-0 x sentinel marking a glyph (rects always have positive width there). */
        const val GlyphMarker = -1f
        private const val ShadowMode = 1f
        private const val BlurExtentFactor = 3f
        private const val AntialiasMargin = 1f
        private const val BorderEpsilon = 0.001f
    }
}

/**
 * An axis-aligned clip rectangle in the batch's effective (pre-projection) screen space, encoded
 * per primitive so the fragment shader can discard out-of-bounds pixels, replacing the GL scissor
 * and letting a batch span multiple clip regions in one draw. [None] disables clipping.
 */
internal data class UiShaderClip(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
) {
    /**
     * The tighter clip common to both rectangles. An empty result (min ≥ max) makes the shader
     * discard every pixel, which is the correct outcome for two non-overlapping clip regions.
     */
    fun intersect(other: UiShaderClip): UiShaderClip = UiShaderClip(
        maxOf(minX, other.minX),
        maxOf(minY, other.minY),
        minOf(maxX, other.maxX),
        minOf(maxY, other.maxY),
    )

    companion object {
        /** No clipping: bounds far outside any UI coordinate so the shader never discards. */
        val None = UiShaderClip(-1e9f, -1e9f, 1e9f, 1e9f)
    }
}
