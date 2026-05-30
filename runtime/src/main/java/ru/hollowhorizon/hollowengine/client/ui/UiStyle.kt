package ru.hollowhorizon.hollowengine.client.ui

import kotlin.math.max

enum class StyleOrigin(val priority: Int) {
    ENGINE_DEFAULTS(0),
    THEME_DEFAULTS(1),
    STYLESHEET(2),
    STATE_STYLESHEET(3),
    INLINE(4),
    ANIMATION(5)
}

enum class TransitionEasing {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT
}

data class UiTransition(
    val property: String,
    val durationMillis: Long,
    val easing: TransitionEasing = TransitionEasing.LINEAR,
) {
    fun progress(elapsedMillis: Long): Float {
        if (durationMillis <= 0L) return 1f
        val linear = (elapsedMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
        return when (easing) {
            TransitionEasing.LINEAR -> linear
            TransitionEasing.EASE_IN -> linear * linear
            TransitionEasing.EASE_OUT -> 1f - (1f - linear) * (1f - linear)
            TransitionEasing.EASE_IN_OUT -> {
                if (linear < 0.5f) 2f * linear * linear else 1f - 2f * (1f - linear) * (1f - linear)
            }
        }
    }
}

data class MutableUiStyle(
    var layout: LayoutType? = null,
    var size: UiSize? = null,
    var minSize: UiSize? = null,
    var maxSize: UiSize? = null,
    var aspectRatio: Float? = null,
    var padding: UiInsets? = null,
    var margin: UiInsets? = null,
    var gap: UiLength? = null,
    var alignHorizontal: UiAlign? = null,
    var alignVertical: UiAlign? = null,
    var alignItemsHorizontal: UiAlign? = null,
    var alignItemsVertical: UiAlign? = null,
    var alignItems: UiAlign? = null,
    var alignSelf: UiAlign? = null,
    var justifySelf: UiAlign? = null,
    var justifyContent: UiAlign? = null,
    var grow: Float? = null,
    var position: UiPosition? = null,
    var background: UiPaint? = null,
    var foreground: UiColor? = null,
    var image: UiBoundString? = null,
    var shader: UiBoundString? = null,
    var border: UiBorder? = null,
    var shadows: List<UiShadow>? = null,
    var opacity: Float? = null,
    var transform: UiTransform? = null,
    var filter: UiFilterChain? = null,
    var backdropFilter: UiFilterChain? = null,
    var backfaceVisibility: UiBackfaceVisibility? = null,
    var input: UiInputStyle? = null,
    var clip: Boolean? = null,
    var layer: Int? = null,
    var imageFit: UiImageFit? = null,
    var imageSlice: UiInsets? = null,
    var scrollbar: UiScrollbarStyle? = null,
    var textWrap: Boolean? = null,
    var textAlign: UiTextAlign? = null,
    var fontSize: Float? = null,
    var typing: UiTyping? = null,
    var transitions: List<UiTransition>? = null,
    var explicitProperties: Set<UiStyleProperty>? = null,
) {
    fun merge(other: MutableUiStyle) {
        other.layout?.let { layout = it }
        other.size?.let { size = it }
        other.minSize?.let { minSize = it }
        other.maxSize?.let { maxSize = it }
        other.aspectRatio?.let { aspectRatio = it }
        other.padding?.let { padding = it }
        other.margin?.let { margin = it }
        other.gap?.let { gap = it }
        other.alignHorizontal?.let { alignHorizontal = it }
        other.alignVertical?.let { alignVertical = it }
        other.alignItemsHorizontal?.let { alignItemsHorizontal = it }
        other.alignItemsVertical?.let { alignItemsVertical = it }
        other.alignItems?.let { alignItems = it }
        other.alignSelf?.let { alignSelf = it }
        other.justifySelf?.let { justifySelf = it }
        other.justifyContent?.let { justifyContent = it }
        other.grow?.let { grow = it }
        other.position?.let { position = it }
        other.background?.let { background = it }
        other.foreground?.let { foreground = it }
        other.image?.let { image = it }
        other.shader?.let { shader = it }
        other.border?.let { border = it }
        other.shadows?.let { shadows = it }
        other.opacity?.let { opacity = it }
        other.transform?.let { transform = it }
        other.filter?.let { filter = it }
        other.backdropFilter?.let { backdropFilter = it }
        other.backfaceVisibility?.let { backfaceVisibility = it }
        other.input?.let { input = input?.merge(it) ?: it }
        other.clip?.let { clip = it }
        other.layer?.let { layer = it }
        other.imageFit?.let { imageFit = it }
        other.imageSlice?.let { imageSlice = it }
        other.scrollbar?.let { scrollbar = scrollbar?.merge(it) ?: it }
        other.textWrap?.let { textWrap = it }
        other.textAlign?.let { textAlign = it }
        other.fontSize?.let { fontSize = it }
        other.typing?.let { typing = it }
        other.transitions?.let { transitions = it }
        other.explicitProperties?.let { explicitProperties = explicitProperties.orEmpty() + it }
    }

    fun toComputed(parent: ComputedStyle? = null): ComputedStyle {
        val inheritedForeground = parent?.foreground ?: UiColor.White
        val inheritedTextAlign = parent?.textAlign ?: UiTextAlign.LEFT
        val inheritedFontSize = parent?.fontSize ?: DefaultUiFontSize
        return ComputedStyle(
            layout = layout ?: LayoutType.COLUMN,
            size = size ?: UiSize(),
            minSize = minSize ?: UiSize(),
            maxSize = maxSize ?: UiSize(),
            aspectRatio = aspectRatio,
            padding = padding ?: UiInsets.Zero,
            margin = margin ?: UiInsets.Zero,
            gap = gap ?: 0.px,
            alignHorizontal = alignHorizontal ?: UiAlign.AUTO,
            alignVertical = alignVertical ?: UiAlign.AUTO,
            alignItemsHorizontal = alignItemsHorizontal ?: UiAlign.AUTO,
            alignItemsVertical = alignItemsVertical ?: UiAlign.AUTO,
            alignItems = alignItems ?: UiAlign.AUTO,
            alignSelf = alignSelf ?: UiAlign.AUTO,
            justifySelf = justifySelf ?: UiAlign.AUTO,
            justifyContent = justifyContent ?: UiAlign.AUTO,
            grow = grow ?: 0f,
            position = position ?: UiPosition(),
            background = background ?: UiPaint.None,
            foreground = foreground ?: inheritedForeground,
            image = image,
            shader = shader,
            border = border ?: UiBorder(),
            shadows = shadows ?: emptyList(),
            opacity = opacity?.coerceIn(0f, 1f) ?: 1f,
            transform = transform ?: UiTransform(),
            filter = filter ?: UiFilterChain.Empty,
            backdropFilter = backdropFilter ?: UiFilterChain.Empty,
            backfaceVisibility = backfaceVisibility ?: UiBackfaceVisibility.VISIBLE,
            input = input ?: UiInputStyle(),
            clip = clip ?: false,
            layer = layer ?: 0,
            imageFit = imageFit ?: UiImageFit.STRETCH,
            imageSlice = imageSlice ?: UiInsets.all(4.px),
            scrollbar = scrollbar ?: UiScrollbarStyle(),
            textWrap = textWrap ?: true,
            textAlign = textAlign ?: inheritedTextAlign,
            fontSize = fontSize ?: inheritedFontSize,
            typing = typing,
            transitions = transitions ?: emptyList(),
            explicitProperties = explicitProperties ?: emptySet(),
        )
    }
}

data class ComputedStyle(
    val layout: LayoutType,
    val size: UiSize,
    val minSize: UiSize,
    val maxSize: UiSize,
    val aspectRatio: Float?,
    val padding: UiInsets,
    val margin: UiInsets,
    val gap: UiLength,
    val alignHorizontal: UiAlign,
    val alignVertical: UiAlign,
    val alignItemsHorizontal: UiAlign,
    val alignItemsVertical: UiAlign,
    val alignItems: UiAlign,
    val alignSelf: UiAlign,
    val justifySelf: UiAlign,
    val justifyContent: UiAlign,
    val grow: Float,
    val position: UiPosition,
    val background: UiPaint,
    val foreground: UiColor,
    val image: UiBoundString?,
    val shader: UiBoundString?,
    val border: UiBorder,
    val shadows: List<UiShadow>,
    val opacity: Float,
    val transform: UiTransform,
    val filter: UiFilterChain,
    val backdropFilter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
    val input: UiInputStyle,
    val clip: Boolean,
    val layer: Int,
    val imageFit: UiImageFit,
    val imageSlice: UiInsets,
    val scrollbar: UiScrollbarStyle,
    val textWrap: Boolean,
    val textAlign: UiTextAlign,
    val fontSize: Float,
    val typing: UiTyping?,
    val transitions: List<UiTransition>,
    val explicitProperties: Set<UiStyleProperty>,
) {

    fun interpolate(to: ComputedStyle, progress: TransitionProgress): ComputedStyle {
        return to.copy(
            background = background.interpolate(to.background, progress.background),
            foreground = foreground.interpolate(to.foreground, progress.foreground),
            shadows = shadows.interpolate(to.shadows, progress.shadow),
            opacity = opacity + (to.opacity - opacity) * progress.opacity,
            filter = filter.interpolate(to.filter, progress.filter),
            backdropFilter = backdropFilter.interpolate(to.backdropFilter, progress.backdropFilter),
            transform = UiTransform(
                translate = transform.translate.interpolate(to.transform.translate, progress.translate),
                rotate = transform.rotate.interpolate(to.transform.rotate, progress.rotate),
                scale = transform.scale.interpolate(to.transform.scale, progress.scale),
                pivot = to.transform.pivot,
                perspective = transform.perspective + (to.transform.perspective - transform.perspective) * progress.perspective,
            ),
        )
    }
}

const val DefaultUiFontSize = 10f

data class TransitionProgress(
    val background: Float = 1f,
    val foreground: Float = 1f,
    val shadow: Float = 1f,
    val opacity: Float = 1f,
    val filter: Float = 1f,
    val backdropFilter: Float = 1f,
    val translate: Float = 1f,
    val rotate: Float = 1f,
    val scale: Float = 1f,
    val perspective: Float = 1f,
) {
    fun complete(): Boolean {
        return background >= 1f &&
                foreground >= 1f &&
                shadow >= 1f &&
                opacity >= 1f &&
                filter >= 1f &&
                backdropFilter >= 1f &&
                translate >= 1f &&
                rotate >= 1f &&
                scale >= 1f &&
                perspective >= 1f
    }
}

enum class UiImageFit {
    STRETCH,
    CONTAIN,
    COVER,
    NONE,
    NINE_SLICE,
    THREE_SLICE_VERTICAL,
    THREE_SLICE_HORIZONTAL
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
        val DefaultThickness: UiLength = 7.px
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

private fun UiPaint.interpolate(to: UiPaint, progress: Float): UiPaint {
    if (this is UiPaint.Color && to is UiPaint.Color) return UiPaint.Color(color.interpolate(to.color, progress))
    if (this is UiPaint.LinearGradient && to is UiPaint.LinearGradient && stops.size == to.stops.size) {
        return UiPaint.LinearGradient(
            angleDegrees = angleDegrees + (to.angleDegrees - angleDegrees) * progress,
            stops = stops.zip(to.stops) { from, target ->
                UiGradientStop(
                    offset = from.offset + (target.offset - from.offset) * progress,
                    color = from.color.interpolate(target.color, progress),
                )
            },
        )
    }
    return if (progress >= 1f) to else this
}

private fun List<UiShadow>.interpolate(to: List<UiShadow>, progress: Float): List<UiShadow> {
    if (size != to.size) return if (progress >= 1f) to else this
    return zip(to) { from, target -> from.interpolate(target, progress) }
}

private fun UiVec3.interpolate(to: UiVec3, progress: Float) = UiVec3(
    x = x + (to.x - x) * progress,
    y = y + (to.y - y) * progress,
    z = z + (to.z - z) * progress,
)

sealed interface UiPaint {
    data object None : UiPaint
    data class Color(val color: UiColor) : UiPaint
    data class LinearGradient(val angleDegrees: Float, val stops: List<UiGradientStop>) : UiPaint
    data class Image(val source: UiBoundString) : UiPaint
    data class Shader(val name: UiBoundString) : UiPaint
}

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
        offset = offset.interpolate(to.offset, progress),
        blur = blur + (to.blur - blur) * progress,
        spread = spread + (to.spread - spread) * progress,
        color = color.interpolate(to.color, progress),
        inset = if (progress >= 1f) to.inset else inset,
    )

}

enum class UiBackfaceVisibility {
    VISIBLE,
    HIDDEN
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
    fun resolve(bindings: UiBindingContext): String = bindings.resolve(template)
}

class UiTransitionState {
    private val rendered = mutableMapOf<String, ComputedStyle>()
    private val starts = mutableMapOf<String, ComputedStyle>()
    private val targets = mutableMapOf<String, ComputedStyle>()
    private val startedAt = mutableMapOf<String, Long>()

    fun apply(node: UiNode, target: ComputedStyle, nowMillis: Long): ComputedStyle {
        val key = UiNodeKeys.key(node)
        val current = rendered[key]
        if (current == null) {
            rendered[key] = target
            targets[key] = target
            return target
        }
        val oldTarget = targets[key]
        if (oldTarget != target) {
            starts[key] = current
            targets[key] = target
            startedAt[key] = nowMillis
        } else if (!startedAt.containsKey(key)) {
            return target
        }
        val startStyle = starts[key] ?: current
        val transitions =
            target.transitions.filter { transition ->
                transition.property in TransitionProperties && startStyle.changed(transition.property, target)
            }
        if (transitions.isEmpty()) return target.also {
            rendered[key] = target
            targets[key] = target
            starts.remove(key)
            startedAt.remove(key)
        }
        val start = startedAt[key] ?: nowMillis
        val progress = transitions.progress(max(0L, nowMillis - start))
        val result = startStyle.interpolate(target, progress)
        rendered[key] = result
        if (progress.complete()) {
            rendered[key] = target
            targets[key] = target
            starts.remove(key)
            startedAt.remove(key)
        }
        return result
    }

    private fun List<UiTransition>.progress(elapsedMillis: Long): TransitionProgress {
        return TransitionProgress(
            background = progress("background", elapsedMillis),
            foreground = progress("foreground", elapsedMillis),
            shadow = progress("shadow", elapsedMillis),
            opacity = progress("opacity", elapsedMillis),
            filter = progress("filter", elapsedMillis),
            backdropFilter = progress("backdrop-filter", elapsedMillis),
            translate = progress("translate", elapsedMillis),
            rotate = progress("rotate", elapsedMillis),
            scale = progress("scale", elapsedMillis),
            perspective = progress("perspective", elapsedMillis),
        )
    }

    private fun List<UiTransition>.progress(property: String, elapsedMillis: Long): Float {
        return firstOrNull { it.property == property }?.progress(elapsedMillis)
            ?: firstOrNull { it.property == "all" }?.progress(elapsedMillis)
            ?: 1f
    }

    private fun ComputedStyle.changed(property: String, target: ComputedStyle): Boolean {
        return when (property) {
            "all" -> TransitionProperties.any { it != "all" && changed(it, target) }
            "transform" -> transform != target.transform
            "background" -> background != target.background
            "foreground" -> foreground != target.foreground
            "shadow", "box-shadow" -> shadows != target.shadows
            "opacity" -> opacity != target.opacity
            "filter" -> filter != target.filter
            "backdrop-filter" -> backdropFilter != target.backdropFilter
            "translate" -> transform.translate != target.transform.translate
            "rotate" -> transform.rotate != target.transform.rotate
            "scale" -> transform.scale != target.transform.scale
            "pivot", "transform-origin" -> transform.pivot != target.transform.pivot
            "perspective" -> transform.perspective != target.transform.perspective
            else -> false
        }
    }

    companion object {
        private val TransitionProperties = setOf(
            "background",
            "all",
            "transform",
            "foreground",
            "shadow",
            "box-shadow",
            "opacity",
            "filter",
            "backdrop-filter",
            "scale",
            "translate",
            "rotate",
            "pivot",
            "transform-origin",
            "perspective",
        )
    }
}
