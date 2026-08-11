package ru.hollowhorizon.hollowengine.client.ui.text

import ru.hollowhorizon.hollowengine.client.ui.UiColor
import kotlin.math.cos

sealed interface UiTextEffect {
    val tag: String
}

/**
 * Faux-bold. [weight] is the extra stroke width in em (1 em = the run's font size): the glyph's
 * signed-distance field is grown by half of it on each side on the GPU, so the stroke thickens
 * smoothly at any scale instead of the vanilla "stamp the glyph twice, a pixel apart" trick. The
 * same value is added to every advance, so a bold run measures exactly as wide as it draws.
 */
data class Bold(val weight: Float = DefaultBoldWeight) : UiTextEffect {
    override val tag: String get() = "bold"
}

/**
 * Faux-italic: the run is sheared around its own baseline by [skewDegrees], positive leaning right.
 * Shearing leaves the advances untouched (as CSS `font-style: oblique` does), so italic text keeps
 * the upright text's layout and only the last glyph overhangs its box.
 */
data class Italic(val skewDegrees: Float = DefaultItalicSkew) : UiTextEffect {
    override val tag: String get() = "italic"
}

/**
 * A rule under the text. [thickness] and [offset] are in em and both default to 0, meaning "use the
 * font's own underline metrics"; [offset] shifts the rule down from that position. [color] defaults
 * to the color the run is drawn in.
 */
data class Underline(
    val thickness: Float = 0f,
    val offset: Float = 0f,
    val color: UiColor? = null,
) : UiTextEffect {
    override val tag: String get() = "underline"
}

/** A rule through the text, positioned around the x-height. Parameters mirror [Underline]. */
data class Strikethrough(
    val thickness: Float = 0f,
    val offset: Float = 0f,
    val color: UiColor? = null,
) : UiTextEffect {
    override val tag: String get() = "strikethrough"
}

/** Extra stroke width of [Bold], in em. Matches the 1/16-em offset the old boolean bold applied. */
const val DefaultBoldWeight = 0.0625f

/** Shear of [Italic], in degrees. */
const val DefaultItalicSkew = 12f

data object Code : UiTextEffect {
    override val tag = "code"
}

data class Link(val url: String) : UiTextEffect {
    override val tag = "link"
}

data class TextColor(val value: UiColor) : UiTextEffect {
    override val tag = "color"
}

data class TextSize(val value: Float) : UiTextEffect {
    override val tag = "fontSize"
}

data class TextFont(val name: String) : UiTextEffect {
    override val tag = "fontFamily"
}

data object Typewriter : UiTextEffect {
    override val tag = "typewriter"
}

data class Shadow(
    val offsetX: Float = 1.5f,
    val offsetY: Float = 1.5f,
    val blur: Float = 0f,
    val color: UiColor = UiColor(0f, 0f, 0f, 0.7f),
) : UiTextEffect {
    override val tag: String get() = "shadow"
}

data class Outline(
    val width: Float = 1.5f,
    val color: UiColor = UiColor(0f, 0f, 0f, 1f),
) : UiTextEffect {
    override val tag: String get() = "outline"
}

data class Glow(
    val radius: Float = 3f,
    val color: UiColor = UiColor(1f, 1f, 1f, 0.8f),
    val quality: Int = 4,
) : UiTextEffect {
    override val tag: String get() = "glow"
}

enum class GradientMode { HORIZONTAL, VERTICAL }

data class Gradient(
    val colors: List<UiColor> = listOf(
        UiColor(1f, 0.2f, 0.2f, 1f),
        UiColor(1f, 0.8f, 0.2f, 1f),
        UiColor(0.2f, 1f, 0.3f, 1f),
        UiColor(0.2f, 0.7f, 1f, 1f),
        UiColor(0.3f, 0.3f, 1f, 1f),
        UiColor(0.8f, 0.2f, 1f, 1f),
    ),
    val mode: GradientMode = GradientMode.HORIZONTAL,
    val speed: Float = 0f,
    val phaseOffset: Float = 0f,
) : UiTextEffect {
    override val tag: String get() = "gradient"
}

data class Rainbow(
    val frequency: Float = 0.5f,
    val saturation: Float = 1f,
    val brightness: Float = 1f,
    val speed: Float = 0.5f,
    val phaseOffset: Float = 0f,
) : UiTextEffect {
    override val tag: String get() = "rainbow"
}

data class Pulse(
    val frequency: Float = 1.5f,
    val amplitude: Float = 0.4f,
    val minAlpha: Float = 0.3f,
) : UiTextEffect {
    override val tag: String get() = "pulse"
}

data class Wave(
    val amplitude: Float = 3f,
    val frequency: Float = 1.5f,
    val speed: Float = 2f,
    val phaseOffset: Float = 0f,
) : UiTextEffect {
    override val tag: String get() = "wave"
}

data class Shake(
    val amplitude: Float = 2f,
    val frequency: Float = 10f,
    val seed: Float = 42f,
) : UiTextEffect {
    override val tag: String get() = "shake"
}

data class Wiggle(
    val amplitude: Float = 2f,
    val frequency: Float = 2f,
    val speed: Float = 1.5f,
    val angleDegrees: Float = 0f,
) : UiTextEffect {
    override val tag: String get() = "wiggle"
}

data class Swing(
    val amplitude: Float = 5f,
    val frequency: Float = 0.8f,
    val speed: Float = 1.2f,
) : UiTextEffect {
    override val tag: String get() = "swing"
}

data class Scroll(
    val speed: Float = 30f,
    val pauseAtEnd: Float = 1f,
    val clip: Boolean = true,
) : UiTextEffect {
    override val tag: String get() = "scroll"
}

data class Glitch(
    val frequency: Float = 3f,
    val intensity: Float = 2f,
    val chromaticAberration: Boolean = true,
) : UiTextEffect {
    override val tag: String get() = "glitch"
}

data class UiEffectContext(
    val time: Float,
    val charIndex: Int,
    val totalChars: Int,
    val charPos: Float,
    val runWidth: Float,
    val runHeight: Float,
    val random: Float,
) {
    fun seededRandom(seed: Float, index: Int = charIndex): Float {
        val value = (index * 2654435761L.toInt() + (seed * 127).toInt()) * 0x45d9f3b
        val hashed = (value xor (value ushr 16)) * 0x85ebca6b
        val mixed = hashed xor (hashed ushr 13)
        return (mixed and 0x7fffffff).toFloat() / 0x7fffffff.toFloat() * 2f - 1f
    }

    fun sineWave(amplitude: Float, frequency: Float, speed: Float, phaseOffset: Float): Float {
        val phase = charPos * frequency * (Math.PI.toFloat() * 2f) + time * speed + phaseOffset
        return cos(phase) * amplitude
    }
}

data class UiTextEffectResult(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val colorMultiplier: Float = 1f,
    val alphaMultiplier: Float = 1f,
    val colorOverride: UiColor? = null,
)

val UiTextEffect.isAnimated: Boolean
    get() = this is Rainbow || this is Pulse || this is Wave || this is Shake ||
            this is Wiggle || this is Swing || this is Scroll || this is Glitch ||
            this is Gradient

val UiTextEffect.isLayer: Boolean
    get() = this is Shadow || this is Outline || this is Glow

fun Iterable<UiTextEffect>.hasAnimatedEffects(): Boolean = any { it.isAnimated }
fun Iterable<UiTextEffect>.hasLayerEffects(): Boolean = any { it.isLayer }
