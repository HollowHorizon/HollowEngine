package ru.hollowhorizon.hollowengine.client.ui.text

/** How the fragment shader turns an atlas texel into glyph coverage. */
enum class UiGlyphSampling(internal val shaderMode: Int) {
    /** Multi-channel signed distance field: coverage is the median of RGB, resolution independent. */
    MSDF(0),

    /** Straight alpha coverage (vanilla bitmap sheets), tinted by the text color. */
    ALPHA(1),

    /** Full-color sheet: the texel's RGB replaces the text color, only alpha is modulated. */
    COLOR(2),
}

/** An atlas page glyphs are stitched into. One batch draws glyphs from a single page. */
data class UiGlyphAtlasPage(
    val textureId: Int,
    val width: Float,
    val height: Float,
    val distanceRange: Float,
    val sampling: UiGlyphSampling,
)

/**
 * One glyph ready to draw. [left]/[top]/[right]/[bottom] are the quad's em-space bounds relative to
 * the pen sitting on the baseline, y growing **down** as on screen; the UVs are already normalized,
 * with [vTop] on the quad's visual top edge. MSDF atlases and vanilla bitmap sheets both reduce to
 * this, so the renderer keeps a single glyph path.
 */
data class UiPlacedGlyph(
    val advance: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val uMin: Float,
    val vTop: Float,
    val uMax: Float,
    val vBottom: Float,
    val page: UiGlyphAtlasPage,
) {
    /** Whitespace and other glyphs that advance the pen without covering any pixels. */
    val isBlank: Boolean get() = left == right || top == bottom
}

/**
 * A resolved font as the renderer sees it: vertical metrics in em plus glyph lookup. Implementations
 * hold GL state (atlas textures), so they are only reachable on the render thread; measurement goes
 * through [UiTextFont] instead, which stays headless.
 */
interface UiGlyphFont {
    /** Distance between consecutive baselines, in em. */
    val lineHeight: Float

    /** Baseline offset from the line's top edge, in em. */
    val ascender: Float

    /** Bottom extent below the baseline, in em; negative. */
    val descender: Float

    /** Center of the underline rule, in em above the baseline; normally negative. */
    val underlineY: Float

    /** Underline rule thickness, in em. */
    val underlineThickness: Float

    /** Center of the strikethrough rule, in em above the baseline. */
    val strikethroughY: Float get() = ascender * StrikethroughAscenderShare


    /** Strikethrough rule thickness, in em. */
    val strikethroughThickness: Float get() = underlineThickness

    /**
     * Atlas pixels per em. Converts a faux-bold weight expressed in em into the distance-field bias
     * the shader applies, so the same weight thickens every font by the same visual amount.
     */
    val emPixels: Float

    fun glyph(char: Char): UiPlacedGlyph?

    /** The pen advance for [char] in em, defined even for codepoints the atlas does not cover. */
    fun advance(char: Char): Float
}

/** Where the strikethrough sits when the font carries no metric of its own: around the x-height. */
private const val StrikethroughAscenderShare = 0.32f

/** Mirrors the shaders' floor on `pxRange`, the screen pixels one distance-field unit spans. */
internal const val MinGlyphPxRange = 2f

/** Mirrors the shaders' `1 + Softness`: the width, in screen pixels, of the coverage transition. */
internal const val GlyphEdgeSoftness = 1.15f

/**
 * Ceiling on the faux-bold distance bias, and the reason there has to be one: pixels further from a
 * glyph than the atlas's range saturate at distance -0.5, so a bias approaching 0.5 lifts the whole
 * padded cell to non-zero coverage, a faint box in the text color behind every glyph. Coverage
 * stays at zero only while `bias <= 0.5 - 0.5 * softness / pxRange`, and `pxRange` is at its
 * smallest when the shader's floor applies, which is where this value comes from. The shaders clamp
 * to the same rule, so the two can never disagree.
 */
internal const val MaxBoldDistanceBias = 0.5f - 0.5f * GlyphEdgeSoftness / MinGlyphPxRange

/**
 * Faux-bold as a shift of the signed-distance threshold: growing the edge by half of [weight] (in
 * em) on each side thickens the stroke by exactly that weight, at any scale. The atlas stores
 * distance in units of [UiGlyphAtlasPage.distanceRange] atlas pixels, so the conversion goes through
 * [emPixels], the font's pixels-per-em.
 */
internal fun UiGlyphAtlasPage.boldBias(emPixels: Float, weight: Float): Float {
    if (weight <= 0f || sampling != UiGlyphSampling.MSDF || distanceRange <= 0f || emPixels <= 0f) return 0f
    return (weight * 0.5f * emPixels / distanceRange).coerceAtMost(MaxBoldDistanceBias)
}

internal fun UiGlyphAtlasPage.boldBiasWeight(emPixels: Float, bias: Float): Float {
    if (bias <= 0f || emPixels <= 0f) return 0f
    return 2f * bias * distanceRange / emPixels
}

internal fun glyphCoverage(raw: Float, bias: Float, pxRange: Float = MinGlyphPxRange): Float =
    ((raw + bias) * pxRange / GlyphEdgeSoftness + 0.5f).coerceIn(0f, 1f)
