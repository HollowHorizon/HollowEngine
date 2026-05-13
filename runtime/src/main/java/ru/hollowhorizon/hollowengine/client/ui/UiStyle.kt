package ru.hollowhorizon.hollowengine.client.ui

import kotlin.math.max
import kotlin.math.min

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
    var padding: UiInsets? = null,
    var margin: UiInsets? = null,
    var gap: UiLength? = null,
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
    var opacity: Float? = null,
    var transform: UiTransform? = null,
    var input: UiInputStyle? = null,
    var clip: Boolean? = null,
    var layer: Int? = null,
    var imageFit: UiImageFit? = null,
    var transitions: List<UiTransition>? = null,
) {
    fun merge(other: MutableUiStyle) {
        other.layout?.let { layout = it }
        other.size?.let { size = it }
        other.minSize?.let { minSize = it }
        other.maxSize?.let { maxSize = it }
        other.padding?.let { padding = it }
        other.margin?.let { margin = it }
        other.gap?.let { gap = it }
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
        other.opacity?.let { opacity = it }
        other.transform?.let { transform = it }
        other.input?.let { input = input?.merge(it) ?: it }
        other.clip?.let { clip = it }
        other.layer?.let { layer = it }
        other.imageFit?.let { imageFit = it }
        other.transitions?.let { transitions = it }
    }

    fun toComputed(parent: ComputedStyle? = null): ComputedStyle {
        val inheritedForeground = parent?.foreground ?: UiColor.White
        return ComputedStyle(
            layout = layout ?: LayoutType.COLUMN,
            size = size ?: UiSize(),
            minSize = minSize ?: UiSize(),
            maxSize = maxSize ?: UiSize(),
            padding = padding ?: UiInsets.Zero,
            margin = margin ?: UiInsets.Zero,
            gap = gap ?: 0.px,
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
            opacity = opacity?.coerceIn(0f, 1f) ?: 1f,
            transform = transform ?: UiTransform(),
            input = input ?: UiInputStyle(),
            clip = clip ?: false,
            layer = layer ?: 0,
            imageFit = imageFit ?: UiImageFit.STRETCH,
            transitions = transitions ?: emptyList(),
        )
    }
}

data class ComputedStyle(
    val layout: LayoutType,
    val size: UiSize,
    val minSize: UiSize,
    val maxSize: UiSize,
    val padding: UiInsets,
    val margin: UiInsets,
    val gap: UiLength,
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
    val opacity: Float,
    val transform: UiTransform,
    val input: UiInputStyle,
    val clip: Boolean,
    val layer: Int,
    val imageFit: UiImageFit,
    val transitions: List<UiTransition>,
) {
    fun interpolate(to: ComputedStyle, progress: Float): ComputedStyle {
        val clamped = progress.coerceIn(0f, 1f)
        return to.copy(
            background = background.interpolate(to.background, clamped),
            foreground = foreground.interpolate(to.foreground, clamped),
            opacity = opacity + (to.opacity - opacity) * clamped,
            transform = UiTransform(
                translate = transform.translate.interpolate(to.transform.translate, clamped),
                rotate = transform.rotate.interpolate(to.transform.rotate, clamped),
                scale = transform.scale.interpolate(to.transform.scale, clamped),
                perspective = transform.perspective + (to.transform.perspective - transform.perspective) * clamped,
            ),
        )
    }

    fun interpolate(to: ComputedStyle, progress: TransitionProgress): ComputedStyle {
        return to.copy(
            background = background.interpolate(to.background, progress.background),
            foreground = foreground.interpolate(to.foreground, progress.foreground),
            opacity = opacity + (to.opacity - opacity) * progress.opacity,
            transform = UiTransform(
                translate = transform.translate.interpolate(to.transform.translate, progress.translate),
                rotate = transform.rotate.interpolate(to.transform.rotate, progress.rotate),
                scale = transform.scale.interpolate(to.transform.scale, progress.scale),
                perspective = transform.perspective + (to.transform.perspective - transform.perspective) * progress.perspective,
            ),
        )
    }
}

data class TransitionProgress(
    val background: Float = 1f,
    val foreground: Float = 1f,
    val opacity: Float = 1f,
    val translate: Float = 1f,
    val rotate: Float = 1f,
    val scale: Float = 1f,
    val perspective: Float = 1f,
) {
    fun complete(): Boolean {
        return background >= 1f &&
            foreground >= 1f &&
            opacity >= 1f &&
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
    NONE
}

private fun UiPaint.interpolate(to: UiPaint, progress: Float): UiPaint {
    if (this is UiPaint.Color && to is UiPaint.Color) return UiPaint.Color(color.interpolate(to.color, progress))
    return if (progress >= 1f) to else this
}

private fun UiVec3.interpolate(to: UiVec3, progress: Float) = UiVec3(
    x = x + (to.x - x) * progress,
    y = y + (to.y - y) * progress,
    z = z + (to.z - z) * progress,
)

sealed interface UiPaint {
    data object None : UiPaint
    data class Color(val color: UiColor) : UiPaint
    data class Image(val source: UiBoundString) : UiPaint
    data class Shader(val name: UiBoundString) : UiPaint
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
        val transitions = target.transitions.filter { it.property in TransitionProperties && startStyle.changed(it.property, target) }
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
            opacity = progress("opacity", elapsedMillis),
            translate = progress("translate", elapsedMillis),
            rotate = progress("rotate", elapsedMillis),
            scale = progress("scale", elapsedMillis),
            perspective = progress("perspective", elapsedMillis),
        )
    }

    private fun List<UiTransition>.progress(property: String, elapsedMillis: Long): Float {
        return firstOrNull { it.property == property }?.progress(elapsedMillis) ?: 1f
    }

    private fun ComputedStyle.changed(property: String, target: ComputedStyle): Boolean {
        return when (property) {
            "background" -> background != target.background
            "foreground" -> foreground != target.foreground
            "opacity" -> opacity != target.opacity
            "translate" -> transform.translate != target.transform.translate
            "rotate" -> transform.rotate != target.transform.rotate
            "scale" -> transform.scale != target.transform.scale
            "perspective" -> transform.perspective != target.transform.perspective
            else -> false
        }
    }

    companion object {
        private val TransitionProperties = setOf("background", "foreground", "opacity", "scale", "translate", "rotate", "perspective")
    }
}

fun clampToByte(value: Float): Int = min(255, max(0, (value * 255f).toInt()))
