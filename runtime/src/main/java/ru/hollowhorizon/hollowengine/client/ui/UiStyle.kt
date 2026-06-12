package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.effects.UiTextEffect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

enum class StyleOrigin(val priority: Int) {
    ENGINE_DEFAULTS(0),
    THEME_DEFAULTS(1),
    STYLESHEET(2),
    STATE_STYLESHEET(3),
    INLINE(4),
    ANIMATION(5)
}

sealed class TransitionEasing {
    abstract fun transform(progress: Float): Float

    open fun inverse(progress: Float): Float {
        val target = progress.coerceIn(0f, 1f)
        var low = 0f
        var high = 1f
        repeat(16) {
            val mid = (low + high) / 2f
            if (transform(mid) < target) low = mid else high = mid
        }
        return (low + high) / 2f
    }

    data object LINEAR : TransitionEasing() {
        override fun transform(progress: Float): Float = progress.coerceIn(0f, 1f)
        override fun inverse(progress: Float): Float = progress.coerceIn(0f, 1f)
    }

    data object EASE_IN : TransitionEasing() {
        override fun transform(progress: Float): Float {
            val linear = progress.coerceIn(0f, 1f)
            return linear * linear
        }
    }

    data object EASE_OUT : TransitionEasing() {
        override fun transform(progress: Float): Float {
            val linear = progress.coerceIn(0f, 1f)
            return 1f - (1f - linear) * (1f - linear)
        }
    }

    data object EASE_IN_OUT : TransitionEasing() {
        override fun transform(progress: Float): Float {
            val linear = progress.coerceIn(0f, 1f)
            return if (linear < 0.5f) 2f * linear * linear else 1f - 2f * (1f - linear) * (1f - linear)
        }
    }

    data class Steps(
        val count: Int,
        val position: StepPosition = StepPosition.END,
    ) : TransitionEasing() {
        override fun transform(progress: Float): Float {
            val linear = progress.coerceIn(0f, 1f)
            if (linear >= 1f) return 1f
            val steps = count.coerceAtLeast(1).toFloat()
            return when (position) {
                StepPosition.START -> ceil(linear * steps) / steps
                StepPosition.END -> floor(linear * steps) / steps
            }.coerceIn(0f, 1f)
        }
    }

    data class CubicBezier(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
    ) : TransitionEasing() {
        override fun transform(progress: Float): Float {
            val targetX = progress.coerceIn(0f, 1f)
            var low = 0f
            var high = 1f
            repeat(18) {
                val mid = (low + high) / 2f
                if (sampleCurve(mid, x1, x2) < targetX) low = mid else high = mid
            }
            return sampleCurve((low + high) / 2f, y1, y2).coerceIn(0f, 1f)
        }

        private fun sampleCurve(t: Float, a1: Float, a2: Float): Float {
            val inverse = 1f - t
            return 3f * inverse * inverse * t * a1 + 3f * inverse * t * t * a2 + t * t * t
        }
    }

    enum class StepPosition {
        START,
        END
    }
}

data class UiTransition(
    val property: String,
    val durationMillis: Long,
    val easing: TransitionEasing = TransitionEasing.LINEAR,
) {
    fun progress(elapsedMillis: Long): Float {
        if (durationMillis <= 0L) return 1f
        val linear = (elapsedMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
        return easing.transform(linear)
    }
}

data class UiKeyframes(
    val name: String,
    val frames: List<UiKeyframe>,
) {
    private val sortedFrames = frames.sortedWith(compareBy<UiKeyframe> { it.offset }.thenBy { frames.indexOf(it) })

    fun sample(base: ComputedStyle, progress: Float, easing: TransitionEasing): ComputedStyle {
        if (sortedFrames.isEmpty()) return base
        val offset = progress.coerceIn(0f, 1f)
        val previous = sortedFrames.lastOrNull { it.offset <= offset } ?: sortedFrames.first()
        val next = sortedFrames.firstOrNull { it.offset >= offset } ?: sortedFrames.last()
        if (previous == next || previous.offset == next.offset) return base.withKeyframePatch(next)
        val local = ((offset - previous.offset) / (next.offset - previous.offset)).coerceIn(0f, 1f)
        val eased = easing.transform(local)
        return base.withKeyframePatch(previous).interpolate(base.withKeyframePatch(next), TransitionProgress.all(eased))
    }
}

data class UiKeyframe(
    val offset: Float,
    val style: MutableUiStyle,
    val properties: Set<String> = emptySet(),
)

data class UiAnimation(
    val name: String,
    val durationMillis: Long = 0L,
    val easing: TransitionEasing = TransitionEasing.LINEAR,
    val delayMillis: Long = 0L,
    val iterationCount: Float = 1f,
    val direction: UiAnimationDirection = UiAnimationDirection.NORMAL,
    val fillMode: UiAnimationFillMode = UiAnimationFillMode.NONE,
    val playState: UiAnimationPlayState = UiAnimationPlayState.RUNNING,
)

enum class UiAnimationDirection {
    NORMAL,
    REVERSE,
    ALTERNATE,
    ALTERNATE_REVERSE
}

enum class UiAnimationFillMode {
    NONE,
    FORWARDS,
    BACKWARDS,
    BOTH
}

enum class UiAnimationPlayState {
    RUNNING,
    PAUSED
}

data class MutableUiStyle(
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
    var tint: UiColor? = null,
    var transform: UiTransform? = null,
    var filter: UiFilterChain? = null,
    var backdropFilter: UiFilterChain? = null,
    var backfaceVisibility: UiBackfaceVisibility? = null,
    var input: UiInputStyle? = null,
    var cursor: UiCursorShape? = null,
    var clip: Boolean? = null,
    var layer: Int? = null,
    var imageFit: UiImageFit? = null,
    var imageSlice: UiInsets? = null,
    var scrollbar: UiScrollbarStyle? = null,
    var slider: UiSliderStyle? = null,
    var checkbox: UiCheckboxStyle? = null,
    var textField: UiTextFieldStyle? = null,
    var textWrap: Boolean? = null,
    var textAlign: UiTextAlign? = null,
    var lineSpacing: Float? = null,
    var spaceWidth: Float? = null,
    var fontSize: Float? = null,
    var fontFamily: String? = null,
    var textEffects: List<UiTextEffect>? = null,
    var typing: UiTyping? = null,
    var transitions: List<UiTransition>? = null,
    var animations: List<UiAnimation>? = null,
    var explicitProperties: Set<UiStyleProperty>? = null,
) {
    fun merge(other: MutableUiStyle) {
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
        other.tint?.let { tint = it }
        other.transform?.let { transform = it }
        other.filter?.let { filter = it }
        other.backdropFilter?.let { backdropFilter = it }
        other.backfaceVisibility?.let { backfaceVisibility = it }
        other.input?.let { input = input?.merge(it) ?: it }
        other.cursor?.let { cursor = it }
        other.clip?.let { clip = it }
        other.layer?.let { layer = it }
        other.imageFit?.let { imageFit = it }
        other.imageSlice?.let { imageSlice = it }
        other.scrollbar?.let { scrollbar = scrollbar?.merge(it) ?: it }
        other.slider?.let { slider = slider?.merge(it) ?: it }
        other.checkbox?.let { checkbox = checkbox?.merge(it) ?: it }
        other.textField?.let { textField = textField?.merge(it) ?: it }
        other.textWrap?.let { textWrap = it }
        other.textAlign?.let { textAlign = it }
        other.lineSpacing?.let { lineSpacing = it }
        other.spaceWidth?.let { spaceWidth = it }
        other.fontSize?.let { fontSize = it }
        other.fontFamily?.let { fontFamily = it }
        other.textEffects?.let { textEffects = textEffects.orEmpty() + it }
        other.typing?.let { typing = it }
        other.transitions?.let { transitions = transitions.mergeUiTransitions(it) }
        other.animations?.let { animations = it }
        other.explicitProperties?.let { explicitProperties = explicitProperties.orEmpty() + it }
    }

    fun toComputed(parent: ComputedStyle? = null): ComputedStyle {
        val inheritedForeground = parent?.foreground ?: UiColor.White
        val inheritedTextAlign = parent?.textAlign ?: UiTextAlign.LEFT
        val inheritedLineSpacing = parent?.lineSpacing ?: 0f
        val inheritedFontSize = parent?.fontSize ?: DefaultUiFontSize
        return ComputedStyle(
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
            tint = tint ?: UiColor.White,
            transform = transform ?: UiTransform(),
            filter = filter ?: UiFilterChain.Empty,
            backdropFilter = backdropFilter ?: UiFilterChain.Empty,
            backfaceVisibility = backfaceVisibility ?: UiBackfaceVisibility.VISIBLE,
            input = input ?: UiInputStyle(),
            cursor = cursor ?: parent?.cursor ?: UiCursorShape.DEFAULT,
            clip = clip ?: false,
            layer = layer ?: 0,
            imageFit = imageFit ?: UiImageFit.STRETCH,
            imageSlice = imageSlice ?: UiInsets.all(4.px),
            scrollbar = scrollbar ?: UiScrollbarStyle(),
            slider = slider ?: UiSliderStyle(),
            checkbox = checkbox ?: UiCheckboxStyle(),
            textField = textField ?: UiTextFieldStyle(),
            textWrap = textWrap ?: true,
            textAlign = textAlign ?: inheritedTextAlign,
            lineSpacing = lineSpacing ?: inheritedLineSpacing,
            spaceWidth = spaceWidth ?: parent?.spaceWidth,
            fontSize = fontSize ?: inheritedFontSize,
            fontFamily = fontFamily,
            textEffects = textEffects ?: emptyList(),
            typing = typing,
            transitions = transitions ?: emptyList(),
            animations = animations ?: emptyList(),
            explicitProperties = explicitProperties ?: emptySet(),
        )
    }
}

data class ComputedStyle(
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
    val tint: UiColor,
    val transform: UiTransform,
    val filter: UiFilterChain,
    val backdropFilter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
    val input: UiInputStyle,
    val cursor: UiCursorShape,
    val clip: Boolean,
    val layer: Int,
    val imageFit: UiImageFit,
    val imageSlice: UiInsets,
    val scrollbar: UiScrollbarStyle,
    val slider: UiSliderStyle,
    val checkbox: UiCheckboxStyle,
    val textField: UiTextFieldStyle,
    val textWrap: Boolean,
    val textAlign: UiTextAlign,
    val lineSpacing: Float,
    val spaceWidth: Float?,
    val fontSize: Float,
    val fontFamily: String?,
    val textEffects: List<UiTextEffect>,
    val typing: UiTyping?,
    val transitions: List<UiTransition>,
    val animations: List<UiAnimation>,
    val explicitProperties: Set<UiStyleProperty>,
) {

    fun interpolate(to: ComputedStyle, progress: TransitionProgress): ComputedStyle {
        return to.copy(
            background = background.interpolate(to.background, progress.background),
            foreground = foreground.interpolate(to.foreground, progress.foreground),
            shadows = shadows.interpolate(to.shadows, progress.shadow),
            opacity = opacity + (to.opacity - opacity) * progress.opacity,
            tint = tint.interpolate(to.tint, progress.tint),
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

fun ComputedStyle.motionDurationMillis(previous: ComputedStyle?): Long {
    val transitionDuration = if (previous == null) {
        0L
    } else {
        transitions
            .filter { transition -> previous.changedForTransition(transition.property, this) }
            .maxOfOrNull { it.durationMillis }
            ?: 0L
    }
    val animationDuration = if (previous == null || previous.animations != animations) {
        animations.maxOfOrNull { it.totalDurationMillis() ?: 0L } ?: 0L
    } else {
        0L
    }
    return max(transitionDuration, animationDuration)
}

fun UiAnimation.totalDurationMillis(): Long? {
    if (playState != UiAnimationPlayState.RUNNING || name.isBlank()) return 0L
    if (iterationCount.isInfinite()) return null
    val iterations = iterationCount.coerceAtLeast(0f)
    if (iterations <= 0f) return 0L
    val activeDuration = ceil(durationMillis.toFloat() * iterations).toLong().coerceAtLeast(0L)
    return delayMillis.coerceAtLeast(0L) + activeDuration
}

internal fun List<UiTransition>?.mergeUiTransitions(other: List<UiTransition>): List<UiTransition> {
    if (other.isEmpty()) return emptyList()
    val merged = linkedMapOf<String, UiTransition>()
    orEmpty().forEach { merged[it.property] = it }
    other.forEach { merged[it.property] = it }
    return merged.values.toList()
}

private fun ComputedStyle.changedForTransition(property: String, target: ComputedStyle): Boolean {
    return when (property) {
        "all" -> TransitionProperties.any { it != "all" && changedForTransition(it, target) }
        "transform" -> transform != target.transform
        "background" -> background != target.background
        "foreground" -> foreground != target.foreground
        "shadow", "box-shadow" -> shadows != target.shadows
        "opacity" -> opacity != target.opacity
        "tint" -> tint != target.tint
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

private val TransitionProperties = setOf(
    "background",
    "all",
    "transform",
    "foreground",
    "shadow",
    "box-shadow",
    "opacity",
    "tint",
    "filter",
    "backdrop-filter",
    "scale",
    "translate",
    "rotate",
    "pivot",
    "transform-origin",
    "perspective",
)

private fun ComputedStyle.toMutable(): MutableUiStyle = MutableUiStyle(
    size = size,
    minSize = minSize,
    maxSize = maxSize,
    aspectRatio = aspectRatio,
    padding = padding,
    margin = margin,
    gap = gap,
    alignHorizontal = alignHorizontal,
    alignVertical = alignVertical,
    alignItemsHorizontal = alignItemsHorizontal,
    alignItemsVertical = alignItemsVertical,
    alignItems = alignItems,
    alignSelf = alignSelf,
    justifySelf = justifySelf,
    justifyContent = justifyContent,
    grow = grow,
    position = position,
    background = background,
    foreground = foreground,
    image = image,
    shader = shader,
    border = border,
    shadows = shadows,
    opacity = opacity,
    tint = tint,
    transform = transform,
    filter = filter,
    backdropFilter = backdropFilter,
    backfaceVisibility = backfaceVisibility,
    input = input,
    cursor = cursor,
    clip = clip,
    layer = layer,
    imageFit = imageFit,
    imageSlice = imageSlice,
    scrollbar = scrollbar,
    slider = slider,
    checkbox = checkbox,
    textField = textField,
    textWrap = textWrap,
    textAlign = textAlign,
    lineSpacing = lineSpacing,
    spaceWidth = spaceWidth,
    fontSize = fontSize,
    fontFamily = fontFamily,
    textEffects = textEffects,
    typing = typing,
    transitions = transitions,
    animations = animations,
    explicitProperties = explicitProperties,
)

private fun ComputedStyle.withPatch(patch: MutableUiStyle): ComputedStyle {
    return toMutable().apply { merge(patch) }.toComputed(null)
}

private fun ComputedStyle.withKeyframePatch(frame: UiKeyframe): ComputedStyle {
    val patched = withPatch(frame.style)
    val transform = frame.style.transform ?: return patched
    val properties = frame.properties
    return patched.copy(
        transform = patched.transform.copy(
            translate = UiVec3(
                x = if ("transform.translate.x" in properties) transform.translate.x else this.transform.translate.x,
                y = if ("transform.translate.y" in properties) transform.translate.y else this.transform.translate.y,
                z = if ("transform.translate.z" in properties) transform.translate.z else this.transform.translate.z,
            ),
            rotate = UiVec3(
                x = if ("transform.rotate.x" in properties) transform.rotate.x else this.transform.rotate.x,
                y = if ("transform.rotate.y" in properties) transform.rotate.y else this.transform.rotate.y,
                z = if ("transform.rotate.z" in properties) transform.rotate.z else this.transform.rotate.z,
            ),
            scale = UiVec3(
                x = if ("transform.scale.x" in properties) transform.scale.x else this.transform.scale.x,
                y = if ("transform.scale.y" in properties) transform.scale.y else this.transform.scale.y,
                z = if ("transform.scale.z" in properties) transform.scale.z else this.transform.scale.z,
            ),
            pivot = if ("transform.pivot" in properties) transform.pivot else this.transform.pivot,
            perspective = if ("transform.perspective" in properties) transform.perspective else this.transform.perspective,
        ),
    )
}

const val DefaultUiFontSize = 10f

data class TransitionProgress(
    val background: Float = 1f,
    val foreground: Float = 1f,
    val shadow: Float = 1f,
    val opacity: Float = 1f,
    val tint: Float = 1f,
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
                tint >= 1f &&
                filter >= 1f &&
                backdropFilter >= 1f &&
                translate >= 1f &&
                rotate >= 1f &&
                scale >= 1f &&
                perspective >= 1f
    }

    companion object {
        fun all(progress: Float) = TransitionProgress(
            background = progress,
            foreground = progress,
            shadow = progress,
            opacity = progress,
            tint = progress,
            filter = progress,
            backdropFilter = progress,
            translate = progress,
            rotate = progress,
            scale = progress,
            perspective = progress,
        )
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
    private val activeDurations = mutableMapOf<String, Long>()
    private val startedDurations = mutableMapOf<String, Long>()

    fun apply(node: UiNode, target: ComputedStyle, nowMillis: Long): ComputedStyle {
        val key = UiNodeKeys.key(node)
        startedDurations[key] = 0L
        val current = rendered[key]
        if (current == null) {
            rendered[key] = target
            targets[key] = target
            activeDurations[key] = 0L
            return target
        }
        val oldTarget = targets[key]
        var targetChanged = false
        if (oldTarget != target) {
            starts[key] = current
            targets[key] = target
            startedAt[key] = nowMillis
            targetChanged = true
        } else if (!startedAt.containsKey(key)) {
            activeDurations[key] = 0L
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
            activeDurations[key] = 0L
            startedDurations[key] = 0L
        }
        val duration = transitions.maxOfOrNull { it.durationMillis } ?: 0L
        activeDurations[key] = duration
        if (targetChanged) startedDurations[key] = duration
        val start = startedAt[key] ?: nowMillis
        val progress = transitions.progress(max(0L, nowMillis - start))
        val result = startStyle.interpolate(target, progress)
        rendered[key] = result
        if (progress.complete()) {
            rendered[key] = target
            targets[key] = target
            starts.remove(key)
            startedAt.remove(key)
            activeDurations[key] = 0L
            startedDurations[key] = 0L
        }
        return result
    }

    fun activeDurationMillis(node: UiNode): Long = activeDurations[UiNodeKeys.key(node)] ?: 0L

    fun startedDurationMillis(node: UiNode): Long = startedDurations[UiNodeKeys.key(node)] ?: 0L

    private fun List<UiTransition>.progress(elapsedMillis: Long): TransitionProgress {
        return TransitionProgress(
            background = progress("background", elapsedMillis),
            foreground = progress("foreground", elapsedMillis),
            shadow = progress("shadow", elapsedMillis),
            opacity = progress("opacity", elapsedMillis),
            tint = progress("tint", elapsedMillis),
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
            "tint" -> tint != target.tint
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
            "tint",
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

class UiAnimationState {
    private val starts = mutableMapOf<String, AnimationStart>()

    fun apply(
        node: UiNode,
        base: ComputedStyle,
        keyframes: Map<String, UiKeyframes>,
        nowMillis: Long,
    ): ComputedStyle {
        val animations = base.animations.filter { it.playState == UiAnimationPlayState.RUNNING && it.name.isNotBlank() }
        if (animations.isEmpty()) {
            starts.remove(UiNodeKeys.key(node))
            return base
        }
        val key = UiNodeKeys.key(node)
        val signature = animations
        val start = starts[key]
        val startedAt = if (start == null || start.signature != signature) {
            starts[key] = AnimationStart(signature, nowMillis)
            nowMillis
        } else {
            start.startedAtMillis
        }
        return animations.fold(base) { style, animation ->
            val frames = keyframes[animation.name] ?: return@fold style
            val progress = animationProgress(animation, nowMillis - startedAt) ?: return@fold style
            frames.sample(style, progress, animation.easing)
        }
    }

    private fun animationProgress(animation: UiAnimation, elapsedMillis: Long): Float? {
        val activeElapsed = elapsedMillis - animation.delayMillis
        if (activeElapsed < 0L) {
            return if (animation.fillMode == UiAnimationFillMode.BACKWARDS || animation.fillMode == UiAnimationFillMode.BOTH) {
                directedProgress(animation, 0, 0f)
            } else {
                null
            }
        }
        if (animation.durationMillis <= 0L) return directedProgress(animation, 0, 1f)
        val duration = animation.durationMillis.toFloat()
        val iterations = animation.iterationCount
        val totalDuration = if (iterations.isInfinite()) Float.POSITIVE_INFINITY else duration * iterations.coerceAtLeast(0f)
        if (totalDuration <= 0f) return null
        if (activeElapsed.toFloat() >= totalDuration) {
            return if (animation.fillMode == UiAnimationFillMode.FORWARDS || animation.fillMode == UiAnimationFillMode.BOTH) {
                val finalIteration = floor(iterations.coerceAtLeast(1f) - 0.0001f).toInt().coerceAtLeast(0)
                directedProgress(animation, finalIteration, 1f)
            } else {
                null
            }
        }
        val iteration = floor(activeElapsed / duration).toInt()
        val local = ((activeElapsed % animation.durationMillis).toFloat() / duration).coerceIn(0f, 1f)
        return directedProgress(animation, iteration, local)
    }

    private fun directedProgress(animation: UiAnimation, iteration: Int, local: Float): Float {
        val reverse = when (animation.direction) {
            UiAnimationDirection.NORMAL -> false
            UiAnimationDirection.REVERSE -> true
            UiAnimationDirection.ALTERNATE -> iteration % 2 == 1
            UiAnimationDirection.ALTERNATE_REVERSE -> iteration % 2 == 0
        }
        return if (reverse) 1f - local else local
    }

    private data class AnimationStart(
        val signature: List<UiAnimation>,
        val startedAtMillis: Long,
    )
}
