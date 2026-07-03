package ru.hollowhorizon.hollowengine.client.ui.style

import ru.hollowhorizon.hollowengine.client.ui.UiBorder
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiInsets
import ru.hollowhorizon.hollowengine.client.ui.UiLength
import ru.hollowhorizon.hollowengine.client.ui.UiVec3
import ru.hollowhorizon.hollowengine.client.ui.percent
import ru.hollowhorizon.hollowengine.client.ui.px

enum class StyleOrigin(val priority: Int) {
    THEME_DEFAULTS(1), STYLESHEET(2), STATE_STYLESHEET(3),
}

enum class UiTextOverflow {
    SHOW, HIDDEN, DOTS
}

enum class UiImageFit {
    STRETCH, CONTAIN, COVER, NONE, NINE_SLICE, THREE_SLICE_VERTICAL, THREE_SLICE_HORIZONTAL
}

enum class UiBackfaceVisibility {
    VISIBLE, HIDDEN
}

sealed interface UiPaint {
    data object None : UiPaint
    data class Color(val color: UiColor) : UiPaint
    data class LinearGradient(val angleDegrees: Float, val stops: List<UiGradientStop>) : UiPaint
    data class RadialGradient(val gradient: UiRadialGradient) : UiPaint
    data class Image(val source: UiBoundString) : UiPaint
    data class Shader(val name: UiBoundString) : UiPaint
}

data class UiRadialGradient(
    val centerX: UiLength = 50.percent,
    val centerY: UiLength = 50.percent,
    val radius: UiLength = 50.percent,
    val stops: List<UiGradientStop>,
)

data class UiGradientStop(
    val offset: Float,
    val color: UiColor,
)

data class UiShadow(
    val offset: UiVec3 = UiVec3(),
    val blur: Float = 0f,
    val spread: Float = 0f,
    val color: UiColor = UiColor.Transparent,
    val inset: Boolean = false,
) {
    fun interpolate(to: UiShadow, progress: Float) = UiShadow(
        offset = interpolateVec3(offset, to.offset, progress),
        blur = blur + (to.blur - blur) * progress,
        spread = spread + (to.spread - spread) * progress,
        color = color.interpolate(to.color, progress),
        inset = if (progress >= 1f) to.inset else inset,
    )
}

data class UiFilterChain(
    val effects: List<UiFilterEffect> = emptyList(),
) {
    val requiresLayer: Boolean get() = effects.any { it.requiresLayer }

    fun grayscaleAmount(): Float =
        effects.filterIsInstance<UiFilterEffect.Grayscale>().sumOf { it.amount.toDouble() }.toFloat().coerceIn(0f, 1f)

    fun blurRadius(): Float =
        effects.filterIsInstance<UiFilterEffect.Blur>().sumOf { it.radius.toDouble() }.toFloat().coerceAtLeast(0f)

    fun withoutBlur(): UiFilterChain = UiFilterChain(effects.filterNot { it is UiFilterEffect.Blur })

    fun interpolate(to: UiFilterChain, progress: Float): UiFilterChain {
        if (effects.size != to.effects.size) return if (progress >= 1f) to else this
        return UiFilterChain(effects.zip(to.effects) { from, target -> from.interpolate(target, progress) })
    }

    companion object {
        val Empty = UiFilterChain()
    }
}

sealed interface UiFilterEffect {
    val requiresLayer: Boolean

    fun interpolate(to: UiFilterEffect, progress: Float): UiFilterEffect

    data class Grayscale(val amount: Float) : UiFilterEffect {
        override val requiresLayer: Boolean get() = amount != 0f

        override fun interpolate(to: UiFilterEffect, progress: Float): UiFilterEffect {
            if (to !is Grayscale) return if (progress >= 1f) to else this
            return Grayscale(amount + (to.amount - amount) * progress)
        }
    }

    data class Blur(val radius: Float) : UiFilterEffect {
        override val requiresLayer: Boolean get() = radius > 0f

        override fun interpolate(to: UiFilterEffect, progress: Float): UiFilterEffect {
            if (to !is Blur) return if (progress >= 1f) to else this
            return Blur(radius + (to.radius - radius) * progress)
        }
    }

    data class Shader(val name: UiBoundString, val arguments: Map<String, Float> = emptyMap()) : UiFilterEffect {
        override val requiresLayer: Boolean get() = true

        override fun interpolate(to: UiFilterEffect, progress: Float): UiFilterEffect = if (progress >= 1f) to else this
    }
}

data class UiInputStyle(
    val hoverable: Boolean = false,
    val clickable: Boolean = false,
    val focusable: Boolean = false,
    val draggable: Boolean = false,
    val scrollable: Boolean = false,
) {
    fun merge(other: UiInputStyle) = UiInputStyle(
        hoverable = hoverable || other.hoverable,
        clickable = clickable || other.clickable,
        focusable = focusable || other.focusable,
        draggable = draggable || other.draggable,
        scrollable = scrollable || other.scrollable,
    )
}

data class UiBoundString(val template: String) {
    fun resolve(): String = template
}

data class UiScrollbarStyle(
    val thickness: UiLength? = null,
    val margin: UiLength? = null,
    val minThumbSize: UiLength? = null,
    val track: UiScrollbarPartStyle = UiScrollbarPartStyle(),
    val thumb: UiScrollbarPartStyle = UiScrollbarPartStyle(),
) {
    fun merge(other: UiScrollbarStyle): UiScrollbarStyle = UiScrollbarStyle(
        thickness = other.thickness ?: thickness,
        margin = other.margin ?: margin,
        minThumbSize = other.minThumbSize ?: minThumbSize,
        track = track.merge(other.track),
        thumb = thumb.merge(other.thumb),
    )

    fun resolved(reference: Float): ResolvedUiScrollbarStyle {
        val resolvedThickness = (thickness ?: DefaultThickness).resolve(reference).coerceAtLeast(0f)
        val resolvedMargin = (margin ?: DefaultMargin).resolve(reference).coerceAtLeast(0f)
        return ResolvedUiScrollbarStyle(
            thickness = resolvedThickness,
            margin = resolvedMargin,
            minThumbSize = (minThumbSize ?: DefaultMinThumbSize).resolve(reference).coerceAtLeast(1f),
            track = track,
            thumb = thumb,
        )
    }

    companion object {
        val DefaultThickness: UiLength = 3.5.px
        val DefaultMargin: UiLength = 3.px
        val DefaultMinThumbSize: UiLength = 18.px
    }
}

data class UiScrollbarPartStyle(
    val paint: UiPaint? = null,
    val border: UiBorder? = null,
    val radius: Float? = null,
    val fit: UiImageFit? = null,
    val slice: UiInsets? = null,
) {
    fun merge(other: UiScrollbarPartStyle): UiScrollbarPartStyle = UiScrollbarPartStyle(
        paint = other.paint ?: paint,
        border = other.border ?: border,
        radius = other.radius ?: radius,
        fit = other.fit ?: fit,
        slice = other.slice ?: slice,
    )
}

data class ResolvedUiScrollbarStyle(
    val thickness: Float,
    val margin: Float,
    val minThumbSize: Float,
    val track: UiScrollbarPartStyle,
    val thumb: UiScrollbarPartStyle,
) {
    val gutter: Float get() = thickness + margin * 2f
}

internal fun interpolatePaint(from: UiPaint, to: UiPaint, progress: Float): UiPaint {
    if (from is UiPaint.Color && to is UiPaint.Color) {
        return UiPaint.Color(from.color.interpolate(to.color, progress))
    }
    if (from is UiPaint.LinearGradient && to is UiPaint.LinearGradient && from.stops.size == to.stops.size) {
        return UiPaint.LinearGradient(
            angleDegrees = from.angleDegrees + (to.angleDegrees - from.angleDegrees) * progress,
            stops = interpolateStops(from.stops, to.stops, progress),
        )
    }
    if (from is UiPaint.RadialGradient && to is UiPaint.RadialGradient &&
        from.gradient.stops.size == to.gradient.stops.size
    ) {
        return UiPaint.RadialGradient(
            gradient = from.gradient.copy(stops = interpolateStops(from.gradient.stops, to.gradient.stops, progress)),
        )
    }
    return if (progress >= 1f) to else from
}

private fun interpolateStops(from: List<UiGradientStop>, to: List<UiGradientStop>, progress: Float) =
    from.zip(to) { start, target ->
        UiGradientStop(
            offset = start.offset + (target.offset - start.offset) * progress,
            color = start.color.interpolate(target.color, progress),
        )
    }

internal fun interpolateShadows(from: List<UiShadow>, to: List<UiShadow>, progress: Float): List<UiShadow> {
    if (from.size != to.size) return if (progress >= 1f) to else from
    return from.zip(to) { start, target -> start.interpolate(target, progress) }
}

internal fun lerp(from: Float, to: Float, progress: Float): Float = from + (to - from) * progress

internal fun interpolateVec3(from: UiVec3, to: UiVec3, progress: Float) = UiVec3(
    x = from.x + (to.x - from.x) * progress,
    y = from.y + (to.y - from.y) * progress,
    z = from.z + (to.z - from.z) * progress,
)
