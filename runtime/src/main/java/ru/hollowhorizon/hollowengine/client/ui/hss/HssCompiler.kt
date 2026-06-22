package ru.hollowhorizon.hollowengine.client.ui.hss

import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.effects.*

data class CompiledHss(
    val rules: List<StyleRule>,
    val keyframes: Map<String, UiKeyframes> = emptyMap(),
)

data class StyleRule(
    val selector: HssSelector,
    val patch: StylePatch,
    val origin: StyleOrigin,
    val order: Int,
) {
    fun matches(node: UiNode) = selector.matches(node)
}

class StylePatch(private val instructions: List<StyleInstruction>) {
    fun apply(style: MutableUiStyle, bindings: UiBindingContext) {
        instructions.forEach { it.apply(style, bindings) }
    }
}

fun interface StyleInstruction {
    fun apply(style: MutableUiStyle, bindings: UiBindingContext)
}

class HssCompiler(private val origin: StyleOrigin = StyleOrigin.STYLESHEET) {
    fun compile(document: HssDocument): CompiledHss {
        val rules = document.rules.flatMap { rule ->
            val patch = StylePatch(rule.declarations.mapNotNull(::compileDeclaration))
            rule.selectors.map { selector ->
                val ruleOrigin = if (selector.states.isEmpty()) origin else StyleOrigin.STATE_STYLESHEET
                StyleRule(selector, patch, ruleOrigin, rule.order)
            }
        }
        val keyframes = document.keyframes.associate { keyframes ->
            keyframes.name to UiKeyframes(
                keyframes.name,
                keyframes.frames.flatMap { frame ->
                    frame.offsets.map { offset ->
                        UiKeyframe(offset, compileKeyframeStyle(frame.declarations), compileKeyframeProperties(frame.declarations))
                    }
                },
            )
        }
        return CompiledHss(rules, keyframes)
    }

    private fun compileKeyframeStyle(declarations: List<HssDeclaration>): MutableUiStyle {
        val style = MutableUiStyle()
        declarations.mapNotNull(::compileDeclaration).forEach { it.apply(style, UiBindingContext()) }
        return style
    }

    private fun compileKeyframeProperties(declarations: List<HssDeclaration>): Set<String> {
        return declarations.flatMap { keyframeProperties(it.property, it.value) }.toSet()
    }

    internal fun compileDeclaration(declaration: HssDeclaration): StyleInstruction? {
        val property = declaration.property.lowercase()
        val value = declaration.value.trim()
        return when (property) {
            "size" -> instruction(UiStyleProperty.WIDTH, UiStyleProperty.HEIGHT) { it.size = parseSize(value) }
            "width" -> instruction(UiStyleProperty.WIDTH) { it.size = (it.size ?: UiSize()).copy(width = parseLength(value)) }
            "height" -> instruction(UiStyleProperty.HEIGHT) { it.size = (it.size ?: UiSize()).copy(height = parseLength(value)) }
            "min-size" -> instruction { it.minSize = parseSize(value) }
            "min-width" -> instruction { it.minSize = (it.minSize ?: UiSize()).copy(width = parseLength(value)) }
            "min-height" -> instruction { it.minSize = (it.minSize ?: UiSize()).copy(height = parseLength(value)) }
            "max-size" -> instruction { it.maxSize = parseSize(value) }
            "max-width" -> instruction { it.maxSize = (it.maxSize ?: UiSize()).copy(width = parseLength(value)) }
            "max-height" -> instruction { it.maxSize = (it.maxSize ?: UiSize()).copy(height = parseLength(value)) }
            "aspect-ratio" -> instruction { it.aspectRatio = parseAspectRatio(value) }
            "padding" -> instruction { it.padding = parseInsets(value, allowAuto = false) }
            "padding-left" -> instruction { it.padding = (it.padding ?: UiInsets.Zero).copy(left = parseLength(value, allowAuto = false)) }
            "padding-top" -> instruction { it.padding = (it.padding ?: UiInsets.Zero).copy(top = parseLength(value, allowAuto = false)) }
            "padding-right" -> instruction { it.padding = (it.padding ?: UiInsets.Zero).copy(right = parseLength(value, allowAuto = false)) }
            "padding-bottom" -> instruction { it.padding = (it.padding ?: UiInsets.Zero).copy(bottom = parseLength(value, allowAuto = false)) }
            "padding-x", "padding-horizontal", "padding-inline" -> instruction {
                val length = parseLength(value, allowAuto = false)
                it.padding = (it.padding ?: UiInsets.Zero).copy(left = length, right = length)
            }
            "padding-y", "padding-vertical", "padding-block" -> instruction {
                val length = parseLength(value, allowAuto = false)
                it.padding = (it.padding ?: UiInsets.Zero).copy(top = length, bottom = length)
            }
            "margin" -> instruction { it.margin = parseInsets(value, allowAuto = true) }
            "margin-left" -> instruction { it.margin = (it.margin ?: UiInsets.Zero).copy(left = parseLength(value, allowAuto = true)) }
            "margin-top" -> instruction { it.margin = (it.margin ?: UiInsets.Zero).copy(top = parseLength(value, allowAuto = true)) }
            "margin-right" -> instruction { it.margin = (it.margin ?: UiInsets.Zero).copy(right = parseLength(value, allowAuto = true)) }
            "margin-bottom" -> instruction { it.margin = (it.margin ?: UiInsets.Zero).copy(bottom = parseLength(value, allowAuto = true)) }
            "margin-x", "margin-horizontal", "margin-inline" -> instruction {
                val length = parseLength(value, allowAuto = true)
                it.margin = (it.margin ?: UiInsets.Zero).copy(left = length, right = length)
            }
            "margin-y", "margin-vertical", "margin-block" -> instruction {
                val length = parseLength(value, allowAuto = true)
                it.margin = (it.margin ?: UiInsets.Zero).copy(top = length, bottom = length)
            }
            "gap" -> instruction { it.gap = parseLength(value) }
            "align" -> instruction { applySelfAlignment(it, value) }
            "align-items" -> instruction { applyChildAlignment(it, value) }
            "align-x", "align-horizontal" -> instruction { it.alignHorizontal = parseAlign(value) }
            "align-y", "align-vertical" -> instruction { it.alignVertical = parseAlign(value) }
            "align-self" -> instruction { it.alignSelf = parseAlign(value) }
            "justify-self" -> instruction { it.justifySelf = parseAlign(value) }
            "justify", "justify-content" -> instruction { it.justifyContent = parseAlign(value) }
            "grow" -> instruction { it.grow = value.toFloat() }
            "position" -> instruction { it.position = parsePosition(value) }
            "background" -> instruction { it.background = parsePaint(value) }
            "background-image" -> instruction { it.background = UiPaint.Image(parseImageSource(value)) }
            "shape" -> instruction { it.shape = parseShape(value) }
            "shape-fill", "fill" -> instruction { it.shapeFill = parsePaint(value) }
            "shape-stroke", "stroke" -> instruction { it.shapeStroke = parsePaint(value) }
            "shape-stroke-width", "stroke-width" -> instruction { it.shapeStrokeWidth = parseLength(value, allowAuto = false) }
            "foreground", "color" -> instruction { it.foreground = parseColor(value) }
            "image" -> instruction { it.image = parseBoundFunction(value, "image") ?: UiBoundString(unquote(value)) }
            "shader" -> instruction { it.shader = parseBoundFunction(value, "shader") ?: UiBoundString(unquote(value)) }
            "border" -> instruction { it.border = parseBorder(value, it.border ?: UiBorder()) }
            "border-radius" -> instruction { it.border = (it.border ?: UiBorder()).copy(radius = parseScalar(value)) }
            "shadow", "box-shadow" -> instruction { it.shadows = parseShadows(value) }
            "opacity" -> instruction { it.opacity = value.toFloat() }
            "tint" -> instruction { it.tint = parseColor(value) }
            "translate" -> instruction {
                it.transform = (it.transform ?: UiTransform()).copy(translate = parseVec3(value))
            }

            "rotate" -> instruction { it.transform = (it.transform ?: UiTransform()).copy(rotate = parseVec3(value)) }
            "scale" -> instruction { it.transform = (it.transform ?: UiTransform()).copy(scale = parseScale(value)) }
            "transform" -> instruction { it.transform = parseTransform(value, it.transform ?: UiTransform()) }
            "pivot", "transform-origin" -> instruction {
                it.transform = (it.transform ?: UiTransform()).copy(pivot = parsePivot(value))
            }
            "perspective" -> instruction {
                it.transform = (it.transform ?: UiTransform()).copy(perspective = parseScalar(value))
            }

            "filter" -> instruction { it.filter = parseFilterChain(value) }
            "backdrop-filter" -> instruction { it.backdropFilter = parseFilterChain(value) }
            "backface-visibility" -> instruction { it.backfaceVisibility = parseBackfaceVisibility(value) }
            "hoverable" -> inputInstruction(value) { style, enabled -> style.copy(hoverable = enabled) }
            "clickable" -> inputInstruction(value) { style, enabled -> style.copy(clickable = enabled) }
            "focusable" -> inputInstruction(value) { style, enabled -> style.copy(focusable = enabled) }
            "draggable" -> inputInstruction(value) { style, enabled -> style.copy(draggable = enabled) }
            "scrollable" -> inputInstruction(value) { style, enabled -> style.copy(scrollable = enabled) }
            "clip", "clip-path" -> instruction { applyClip(it, value) }
            "layer" -> instruction { it.layer = value.toInt() }
            "image-fit", "fit" -> instruction {
                it.imageFit = parseImageFit(value)
                parseImageFitSlice(value)?.let { slice -> it.imageSlice = slice }
            }
            "image-slice", "slice" -> instruction { it.imageSlice = parseInsets(value, allowAuto = false) }
            "scrollbar", "scrollbar-width", "scrollbar-thickness" -> instruction {
                it.scrollbar = (it.scrollbar ?: UiScrollbarStyle()).copy(thickness = parseLength(value))
            }
            "scrollbar-margin" -> instruction {
                it.scrollbar = (it.scrollbar ?: UiScrollbarStyle()).copy(margin = parseLength(value))
            }
            "scrollbar-min-thumb", "scrollbar-min-thumb-size" -> instruction {
                it.scrollbar = (it.scrollbar ?: UiScrollbarStyle()).copy(minThumbSize = parseLength(value))
            }
            "scrollbar-track" -> instruction { style ->
                style.scrollbar = (style.scrollbar ?: UiScrollbarStyle()).patchTrack { it.copy(paint = parsePaint(value)) }
            }
            "scrollbar-thumb" -> instruction { style ->
                style.scrollbar = (style.scrollbar ?: UiScrollbarStyle()).patchThumb { it.copy(paint = parsePaint(value)) }
            }
            "scrollbar-track-border" -> instruction { style ->
                style.scrollbar = (style.scrollbar ?: UiScrollbarStyle()).patchTrack {
                    it.copy(border = parseBorder(value, it.border ?: UiBorder()))
                }
            }
            "scrollbar-thumb-border" -> instruction { style ->
                style.scrollbar = (style.scrollbar ?: UiScrollbarStyle()).patchThumb {
                    it.copy(border = parseBorder(value, it.border ?: UiBorder()))
                }
            }
            "scrollbar-track-radius" -> instruction { style ->
                style.scrollbar = (style.scrollbar ?: UiScrollbarStyle()).patchTrack { it.copy(radius = parseScalar(value)) }
            }
            "scrollbar-thumb-radius" -> instruction { style ->
                style.scrollbar = (style.scrollbar ?: UiScrollbarStyle()).patchThumb { it.copy(radius = parseScalar(value)) }
            }
            "scrollbar-track-fit" -> instruction { style ->
                style.scrollbar = (style.scrollbar ?: UiScrollbarStyle()).patchTrack {
                    it.copy(fit = parseImageFit(value), slice = parseImageFitSlice(value) ?: it.slice)
                }
            }
            "scrollbar-thumb-fit" -> instruction { style ->
                style.scrollbar = (style.scrollbar ?: UiScrollbarStyle()).patchThumb {
                    it.copy(fit = parseImageFit(value), slice = parseImageFitSlice(value) ?: it.slice)
                }
            }
            "scrollbar-track-slice" -> instruction { style ->
                style.scrollbar = (style.scrollbar ?: UiScrollbarStyle()).patchTrack {
                    it.copy(slice = parseInsets(value, allowAuto = false))
                }
            }
            "scrollbar-thumb-slice" -> instruction { style ->
                style.scrollbar = (style.scrollbar ?: UiScrollbarStyle()).patchThumb {
                    it.copy(slice = parseInsets(value, allowAuto = false))
                }
            }
            "slider-track-thickness", "slider-track-width" -> instruction { style ->
                style.slider = (style.slider ?: UiSliderStyle()).copy(trackThickness = parseLength(value))
            }
            "slider-track" -> instruction { style ->
                style.slider = (style.slider ?: UiSliderStyle()).copy(trackPaint = parsePaint(value))
            }
            "slider-active-track", "slider-fill" -> instruction { style ->
                style.slider = (style.slider ?: UiSliderStyle()).copy(activeTrackPaint = parsePaint(value))
            }
            "slider-thumb" -> instruction { style ->
                style.slider = (style.slider ?: UiSliderStyle()).copy(thumbPaint = parsePaint(value))
            }
            "slider-thumb-border" -> instruction { style ->
                val current = style.slider ?: UiSliderStyle()
                style.slider = current.copy(thumbBorder = parseBorder(value, current.thumbBorder ?: UiBorder()))
            }
            "slider-thumb-size" -> instruction { style ->
                style.slider = (style.slider ?: UiSliderStyle()).copy(thumbSize = parseSize(value))
            }
            "slider-radius" -> instruction { style ->
                style.slider = (style.slider ?: UiSliderStyle()).copy(radius = parseScalar(value))
            }
            "checkbox-mark" -> instruction { style ->
                style.checkbox = (style.checkbox ?: UiCheckboxStyle()).copy(markPaint = parsePaint(value))
            }
            "checkbox-active", "checkbox-fill" -> instruction { style ->
                style.checkbox = (style.checkbox ?: UiCheckboxStyle()).copy(activePaint = parsePaint(value))
            }
            "checkbox-variant", "checkbox-style" -> instruction { style ->
                style.checkbox = (style.checkbox ?: UiCheckboxStyle()).copy(variant = UiCheckboxVariant.from(value))
            }
            "caret-color", "text-field-caret" -> instruction { style ->
                style.textField = (style.textField ?: UiTextFieldStyle()).copy(caretColor = parseColor(value))
            }
            "selection-color", "text-selection" -> instruction { style ->
                style.textField = (style.textField ?: UiTextFieldStyle()).copy(selectionColor = parseColor(value))
            }
            "line-number-color" -> instruction { style ->
                style.textField = (style.textField ?: UiTextFieldStyle()).copy(lineNumberColor = parseColor(value))
            }
            "inlay-hint-color" -> instruction { style ->
                style.textField = (style.textField ?: UiTextFieldStyle()).copy(inlayHintColor = parseColor(value))
            }
            "line-numbers" -> instruction { style ->
                style.textField = (style.textField ?: UiTextFieldStyle()).copy(lineNumbers = parseBoolean(value))
            }
            "inlay-hints" -> instruction { style ->
                style.textField = (style.textField ?: UiTextFieldStyle()).copy(inlayHints = parseBoolean(value))
            }
            "text-wrap", "wrap" -> instruction { it.textWrap = parseTextWrap(value) }
            "text-align" -> instruction { it.textAlign = parseTextAlign(value) }
            "line-spacing", "text-line-spacing", "leading" -> instruction {
                it.lineSpacing = parseScalar(value).coerceAtLeast(0f)
            }
            "space-width", "text-space-width" -> instruction {
                it.spaceWidth = parseScalar(value).coerceAtLeast(0f)
            }
            "font-size" -> instruction { it.fontSize = parseScalar(value).coerceAtLeast(0.0001f) }
            "font-family" -> instruction { it.fontFamily = value.trim().removeSurrounding("\"").removeSurrounding("'") }
            "typing" -> instruction { it.typing = parseTyping(value) }
            "typing-delay" -> instruction {
                val delay = parseDuration(value)
                it.typing = (it.typing ?: UiTyping()).copy(delayMillis = delay)
            }
            "text-effects", "text-effect" -> instruction { it.textEffects = parseTextEffects(value) }
            "transition" -> transitionInstruction(value)
            "animation" -> instruction { it.animations = parseAnimations(value) }
            "animation-name" -> instruction { style ->
                style.animations = splitTopLevel(value, ',').map { UiAnimation(unquote(it.trim())) }
            }
            "animation-duration" -> instruction { style ->
                style.animations = style.animations.patchAnimationValues(splitTopLevel(value, ',').map(::parseDuration)) { animation, duration ->
                    animation.copy(durationMillis = duration)
                }
            }
            "animation-timing-function" -> instruction { style ->
                style.animations = style.animations.patchAnimationValues(splitTopLevel(value, ',').map(::parseEasing)) { animation, easing ->
                    animation.copy(easing = easing)
                }
            }
            "animation-delay" -> instruction { style ->
                style.animations = style.animations.patchAnimationValues(splitTopLevel(value, ',').map(::parseDuration)) { animation, delay ->
                    animation.copy(delayMillis = delay)
                }
            }
            "animation-iteration-count" -> instruction { style ->
                style.animations = style.animations.patchAnimationValues(splitTopLevel(value, ',').map(::parseIterationCount)) { animation, count ->
                    animation.copy(iterationCount = count)
                }
            }
            "animation-direction" -> instruction { style ->
                style.animations = style.animations.patchAnimationValues(splitTopLevel(value, ',').map(::parseAnimationDirection)) { animation, direction ->
                    animation.copy(direction = direction)
                }
            }
            "animation-fill-mode" -> instruction { style ->
                style.animations = style.animations.patchAnimationValues(splitTopLevel(value, ',').map(::parseAnimationFillMode)) { animation, fillMode ->
                    animation.copy(fillMode = fillMode)
                }
            }
            "animation-play-state" -> instruction { style ->
                style.animations = style.animations.patchAnimationValues(splitTopLevel(value, ',').map(::parseAnimationPlayState)) { animation, playState ->
                    animation.copy(playState = playState)
                }
            }
            else -> null
        }
    }

    private fun instruction(writer: (MutableUiStyle) -> Unit) = StyleInstruction { style, _ -> writer(style) }

    private fun instruction(vararg properties: UiStyleProperty, writer: (MutableUiStyle) -> Unit) =
        StyleInstruction { style, _ ->
            writer(style)
            style.explicitProperties = style.explicitProperties.orEmpty() + properties
        }

    private fun transitionInstruction(value: String) = StyleInstruction { style, _ ->
        val parsed = parseTransitions(value)
        style.transitions = if (UiStyleProperty.TRANSITIONS in style.explicitProperties.orEmpty()) {
            style.transitions.mergeUiTransitions(parsed)
        } else {
            parsed
        }
        style.explicitProperties = style.explicitProperties.orEmpty() + UiStyleProperty.TRANSITIONS
    }

    private fun inputInstruction(value: String, patch: (UiInputStyle, Boolean) -> UiInputStyle) =
        instruction { style ->
            style.input = patch(style.input ?: UiInputStyle(), parseBoolean(value))
        }
}

fun compileHss(source: String, origin: StyleOrigin = StyleOrigin.STYLESHEET): CompiledHss =
    HssCompiler(origin).compile(parseHss(source))

fun compileStyleModifier(property: String, value: String): Modifier? {
    val instruction = HssCompiler().compileDeclaration(HssDeclaration(property, value)) ?: return null
    return StyleModifier(property.explicitStyleProperties()) { style -> instruction.apply(style, UiBindingContext()) }
}

private fun String.explicitStyleProperties(): Set<UiStyleProperty> = when (lowercase()) {
    "size" -> setOf(UiStyleProperty.WIDTH, UiStyleProperty.HEIGHT)
    "width" -> setOf(UiStyleProperty.WIDTH)
    "height" -> setOf(UiStyleProperty.HEIGHT)
    else -> emptySet()
}

private fun parseAlign(value: String): UiAlign = when (value.lowercase()) {
    "auto" -> UiAlign.AUTO
    "start", "flex-start" -> UiAlign.START
    "center" -> UiAlign.CENTER
    "end", "flex-end" -> UiAlign.END
    "stretch" -> UiAlign.STRETCH
    "space-between" -> UiAlign.SPACE_BETWEEN
    "space-around" -> UiAlign.SPACE_AROUND
    "space-evenly" -> UiAlign.SPACE_EVENLY
    else -> throw IllegalArgumentException("Unknown align '$value'")
}

private fun applySelfAlignment(style: MutableUiStyle, value: String) {
    val parts = splitWhitespace(value)
    val horizontal = parseAlign(parts.first())
    val vertical = parseAlign(parts.getOrElse(1) { parts.first() })
    style.alignHorizontal = horizontal
    style.alignVertical = vertical
}

private fun applyChildAlignment(style: MutableUiStyle, value: String) {
    val parts = splitWhitespace(value)
    val horizontal = parseAlign(parts.first())
    val vertical = parseAlign(parts.getOrElse(1) { parts.first() })
    style.alignItemsHorizontal = horizontal
    style.alignItemsVertical = vertical
}

private fun parseImageFit(value: String): UiImageFit = when (splitWhitespace(value).first().lowercase()) {
    "stretch", "strench" -> UiImageFit.STRETCH
    "contain", "fit" -> UiImageFit.CONTAIN
    "cover", "zoom" -> UiImageFit.COVER
    "none" -> UiImageFit.NONE
    "9-slice", "nine-slice" -> UiImageFit.NINE_SLICE
    "3-slice-vertical", "three-slice-vertical" -> UiImageFit.THREE_SLICE_VERTICAL
    "3-slice-horizontal", "three-slice-horizontal" -> UiImageFit.THREE_SLICE_HORIZONTAL
    else -> throw IllegalArgumentException("Unknown image fit '$value'")
}

private fun parseImageFitSlice(value: String): UiInsets? {
    val parts = splitWhitespace(value)
    return parts.drop(1).takeIf { it.isNotEmpty() }?.joinToString(" ")?.let { parseInsets(it, allowAuto = false) }
}

private fun UiScrollbarStyle.patchTrack(patch: (UiScrollbarPartStyle) -> UiScrollbarPartStyle): UiScrollbarStyle =
    copy(track = patch(track))

private fun UiScrollbarStyle.patchThumb(patch: (UiScrollbarPartStyle) -> UiScrollbarPartStyle): UiScrollbarStyle =
    copy(thumb = patch(thumb))

private fun parseTextWrap(value: String): Boolean = when (value.lowercase()) {
    "wrap", "true", "yes", "1", "enabled" -> true
    "nowrap", "no-wrap", "false", "no", "0", "disabled" -> false
    else -> throw IllegalArgumentException("Unknown text wrap '$value'")
}

private fun parseTextAlign(value: String): UiTextAlign = when (value.lowercase()) {
    "left", "start" -> UiTextAlign.LEFT
    "right", "end" -> UiTextAlign.RIGHT
    "center" -> UiTextAlign.CENTER
    "justify" -> UiTextAlign.JUSTIFY
    else -> throw IllegalArgumentException("Unknown text-align '$value'")
}

private fun parseAspectRatio(value: String): Float {
    val parts = value.split('/').map { it.trim() }.filter { it.isNotBlank() }
    return when (parts.size) {
        1 -> parts.single().toFloat()
        2 -> parts[0].toFloat() / parts[1].toFloat()
        else -> throw IllegalArgumentException("Unknown aspect ratio '$value'")
    }.coerceAtLeast(0.0001f)
}

private fun parseSize(value: String): UiSize {
    val parts = splitWhitespace(value)
    val width = parseLength(parts.first())
    val height = parseLength(parts.getOrElse(1) { parts.first() })
    return UiSize(width, height)
}

private fun parseInsets(value: String, allowAuto: Boolean): UiInsets {
    val parts = splitWhitespace(value)
    val top = parseLength(parts[0], allowAuto)
    val right = parseLength(parts.getOrElse(1) { parts[0] }, allowAuto)
    val bottom = parseLength(parts.getOrElse(2) { parts[0] }, allowAuto)
    val left = parseLength(parts.getOrElse(3) { parts.getOrElse(1) { parts[0] } }, allowAuto)
    return UiInsets(left, top, right, bottom)
}

private fun parseLength(value: String, allowAuto: Boolean = true): UiLength {
    val cleaned = value.trim().lowercase()
    if ((cleaned == "auto" || cleaned == "fit") && allowAuto) return UiLength.Auto
    if (cleaned == "fill") return UiLength.Fill
    if (cleaned.endsWith("px")) return cleaned.dropLast(2).toFloat().px
    if (cleaned.endsWith("%")) return UiLength.Percent(cleaned.dropLast(1).toFloat() / 100f)
    return cleaned.toFloat().px
}

private fun parsePaint(value: String): UiPaint {
    if (value.trim().equals("none", ignoreCase = true)) return UiPaint.None
    parseBoundFunction(value, "image")?.let { return UiPaint.Image(it) }
    parseBoundFunction(value, "url")?.let { return UiPaint.Image(it) }
    parseBoundFunction(value, "shader")?.let { return UiPaint.Shader(it) }
    parseLinearGradient(value)?.let { return it }
    parseRadialGradient(value)?.let { return it }
    if (looksLikeImageSource(value)) return UiPaint.Image(parseImageSource(value))
    return UiPaint.Color(parseColor(value))
}

private fun parseImageSource(value: String): UiBoundString {
    return parseBoundFunction(value, "image")
        ?: parseBoundFunction(value, "url")
        ?: UiBoundString(unquote(value))
}

private fun applyClip(style: MutableUiStyle, value: String) {
    val cleaned = value.trim()
    if (cleaned.startsWith("path(") || cleaned.startsWith("svg-path(") || cleaned.startsWith("svg(")) {
        style.clip = true
        style.clipShape = parseShape(cleaned)
        return
    }
    style.clip = parseBoolean(cleaned)
    if (style.clip == false) style.clipShape = null
}

private fun parseShape(value: String): Shape {
    val cleaned = value.trim()
    val functionName = when {
        cleaned.startsWith("path(") -> "path"
        cleaned.startsWith("svg-path(") -> "svg-path"
        cleaned.startsWith("svg(") -> "svg"
        else -> throw IllegalArgumentException("Expected path(...) or svg(...), got '$value'")
    }
    val args = functionArgs(cleaned, functionName)
    require(args.isNotEmpty()) { "$functionName requires SVG path data or resource location" }
    if (functionName == "svg") return svgResource(unquote(args.first()))
    return SvgPathShape(unquote(args.first()), parseShapeViewBox(args.drop(1)))
}

private fun parseShapeViewBox(args: List<String>): UiRect? {
    if (args.isEmpty()) return null
    val parts = splitWhitespace(args.joinToString(" "))
    val numbers = parts.map(::parseScalar)
    return when (numbers.size) {
        2 -> UiRect(0f, 0f, numbers[0], numbers[1])
        4 -> UiRect(numbers[0], numbers[1], numbers[2], numbers[3])
        else -> throw IllegalArgumentException("Shape viewBox expects width height or x y width height")
    }
}

private fun parseLinearGradient(value: String): UiPaint.LinearGradient? {
    val cleaned = value.trim()
    if (!cleaned.startsWith("linear-gradient(")) return null
    val args = functionArgs(cleaned, "linear-gradient")
    val first = args.firstOrNull()?.trim().orEmpty()
    val firstIsAngle = first.endsWith("deg") || first.toFloatOrNull() != null
    val angle = if (firstIsAngle) parseScalar(first) else 180f
    val colorArgs = if (firstIsAngle) args.drop(1) else args
    require(colorArgs.size >= 2) { "linear-gradient requires at least two colors" }
    val explicitStops = colorArgs.mapIndexed { index, entry ->
        val parts = splitTopLevelWhitespace(entry)
        val color = parseColor(parts.first())
        val offset = parts.getOrNull(1)?.let(::parseStopOffset)
            ?: if (colorArgs.size == 1) 0f else index.toFloat() / (colorArgs.size - 1).toFloat()
        UiGradientStop(offset.coerceIn(0f, 1f), color)
    }
    return UiPaint.LinearGradient(angle, explicitStops.sortedBy { it.offset })
}

private fun parseRadialGradient(value: String): UiPaint.RadialGradient? {
    val cleaned = value.trim()
    if (!cleaned.startsWith("radial-gradient(")) return null
    val args = functionArgs(cleaned, "radial-gradient")
    val first = args.firstOrNull()?.trim().orEmpty()
    val descriptor = first.takeIf { it.isNotEmpty() && !looksLikeColor(it) }
    val colorArgs = if (descriptor == null) args else args.drop(1)
    require(colorArgs.size >= 2) { "radial-gradient requires at least two colors" }
    val gradient = parseRadialGradientDescriptor(descriptor).copy(stops = parseGradientStops(colorArgs))
    return UiPaint.RadialGradient(gradient)
}

private fun parseRadialGradientDescriptor(value: String?): UiRadialGradient {
    if (value == null) return UiRadialGradient(stops = emptyList())
    val tokens = splitTopLevelWhitespace(value)
    val atIndex = tokens.indexOfFirst { it.equals("at", ignoreCase = true) }
    val radius = tokens.take(atIndex.takeIf { it >= 0 } ?: tokens.size)
        .firstOrNull { it.endsWith("%") || it.endsWith("px") || it.toFloatOrNull() != null }
        ?.let { parseLength(it, allowAuto = false) }
        ?: 50.percent
    val center = if (atIndex >= 0) tokens.drop(atIndex + 1) else emptyList()
    return UiRadialGradient(
        centerX = center.getOrNull(0)?.let { parseLength(it, allowAuto = false) } ?: 50.percent,
        centerY = center.getOrNull(1)?.let { parseLength(it, allowAuto = false) } ?: 50.percent,
        radius = radius,
        stops = emptyList(),
    )
}

private fun parseGradientStops(args: List<String>): List<UiGradientStop> {
    return args.mapIndexed { index, entry ->
        val parts = splitTopLevelWhitespace(entry)
        val color = parseColor(parts.first())
        val offset = parts.getOrNull(1)?.let(::parseStopOffset)
            ?: if (args.size == 1) 0f else index.toFloat() / (args.size - 1).toFloat()
        UiGradientStop(offset.coerceIn(0f, 1f), color)
    }.sortedBy { it.offset }
}

fun parseColor(value: String): UiColor {
    val cleaned = value.trim()
    if (cleaned.startsWith("rgba(")) {
        val args = functionArgs(cleaned, "rgba").map { it.trim().toFloat() }
        return UiColor(args[0] / 255f, args[1] / 255f, args[2] / 255f, args.getOrElse(3) { 1f })
    }
    if (cleaned.startsWith("rgb(")) {
        val args = functionArgs(cleaned, "rgb").map { it.trim().toFloat() }
        return UiColor(args[0] / 255f, args[1] / 255f, args[2] / 255f)
    }
    if (cleaned.startsWith("#")) {
        val hex = cleaned.removePrefix("#")
        val red = hex.substring(0, 2).toInt(16) / 255f
        val green = hex.substring(2, 4).toInt(16) / 255f
        val blue = hex.substring(4, 6).toInt(16) / 255f
        val alpha = if (hex.length >= 8) hex.substring(6, 8).toInt(16) / 255f else 1f
        return UiColor(red, green, blue, alpha)
    }
    return when (cleaned.lowercase()) {
        "transparent" -> UiColor.Transparent
        "white" -> UiColor.White
        "black" -> UiColor.Black
        else -> throw IllegalArgumentException("Unknown color '$value'")
    }
}

private fun parseBorder(value: String, previous: UiBorder): UiBorder {
    val parts = splitTopLevelWhitespace(value)
    val width = parseLength(parts.first())
    val color = parts.drop(1).joinToString(" ").takeIf { it.isNotBlank() }?.let(::parseColor) ?: previous.color
    return previous.copy(width = UiInsets.all(width), color = color)
}

private fun parseShadows(value: String): List<UiShadow> {
    if (value.equals("none", ignoreCase = true)) return emptyList()
    return splitTopLevel(value, ',').map { entry ->
        val parts = splitTopLevelWhitespace(entry)
        val inset = parts.firstOrNull()?.equals("inset", ignoreCase = true) == true
        val values = if (inset) parts.drop(1) else parts
        require(values.size >= 3) { "Expected shadow x y blur [spread] color, got '$entry'" }
        val spreadIndex = values.indexOfFirst { looksLikeColor(it) }.let { colorIndex ->
            if (colorIndex < 0) values.lastIndex else colorIndex
        }
        val color = values.drop(spreadIndex).joinToString(" ").takeIf { it.isNotBlank() }?.let(::parseColor)
            ?: throw IllegalArgumentException("Expected shadow color, got '$entry'")
        UiShadow(
            offset = UiVec3(parseScalar(values[0]), parseScalar(values[1]), 0f),
            blur = parseScalar(values.getOrElse(2) { "0px" }).coerceAtLeast(0f),
            spread = values.getOrNull(3)?.takeUnless(::looksLikeColor)?.let(::parseScalar) ?: 0f,
            color = color,
            inset = inset,
        )
    }
}

private fun parseFilterChain(value: String): UiFilterChain {
    if (value.equals("none", ignoreCase = true)) return UiFilterChain.Empty
    val effects = mutableListOf<UiFilterEffect>()
    var index = 0
    while (index < value.length) {
        while (index < value.length && value[index].isWhitespace()) index++
        val nameStart = index
        while (index < value.length && (value[index].isLetterOrDigit() || value[index] == '-')) index++
        if (nameStart == index) break
        val name = value.substring(nameStart, index).lowercase()
        require(value.getOrNull(index) == '(') { "Expected filter function after '$name'" }
        val argsStart = index + 1
        var depth = 1
        index++
        while (index < value.length && depth > 0) {
            when (value[index]) {
                '(' -> depth++
                ')' -> depth--
            }
            index++
        }
        val args = value.substring(argsStart, index - 1).trim()
        effects += when (name) {
            "grayscale" -> UiFilterEffect.Grayscale(parseFilterAmount(args))
            "blur" -> UiFilterEffect.Blur(parseScalar(args).coerceAtLeast(0f))
            "shader" -> UiFilterEffect.Shader(UiBoundString(unquote(args)))
            else -> UiFilterEffect.Shader(UiBoundString(name))
        }
    }
    return UiFilterChain(effects)
}

private fun parseBackfaceVisibility(value: String): UiBackfaceVisibility = when (value.lowercase()) {
    "visible" -> UiBackfaceVisibility.VISIBLE
    "hidden" -> UiBackfaceVisibility.HIDDEN
    else -> throw IllegalArgumentException("Unknown backface-visibility '$value'")
}

private fun parseVec3(value: String): UiVec3 {
    val parts = splitWhitespace(value).map(::parseScalar)
    return UiVec3(parts.getOrElse(0) { 0f }, parts.getOrElse(1) { 0f }, parts.getOrElse(2) { 0f })
}

private fun parsePosition(value: String): UiPosition {
    val parts = splitWhitespace(value)
    return UiPosition(
        x = parseLength(parts.getOrElse(0) { "0px" }),
        y = parseLength(parts.getOrElse(1) { "0px" }),
        z = parts.getOrNull(2)?.let(::parseScalar) ?: 0f,
    )
}

private fun parsePivot(value: String): UiTransformPivot {
    val cleaned = value.trim().lowercase()
    namedPivot(cleaned)?.let { return it }
    val parts = splitWhitespace(value)
    if (parts.size == 1) namedPivot(parts[0].lowercase())?.let { return it }
    return UiTransformPivot(
        x = parsePivotLength(parts.getOrElse(0) { "50%" }),
        y = parsePivotLength(parts.getOrElse(1) { "50%" }),
        z = parts.getOrNull(2)?.let(::parsePivotLength) ?: 0.px,
    )
}

private fun namedPivot(value: String): UiTransformPivot? = when (value) {
    "top-left" -> UiTransformPivot.TopLeft
    "top-center" -> UiTransformPivot.TopCenter
    "top-right" -> UiTransformPivot.TopRight
    "center-left" -> UiTransformPivot.CenterLeft
    "center" -> UiTransformPivot.Center
    "center-right" -> UiTransformPivot.CenterRight
    "bottom-left" -> UiTransformPivot.BottomLeft
    "bottom-center" -> UiTransformPivot.BottomCenter
    "bottom-right" -> UiTransformPivot.BottomRight
    else -> null
}

private fun parsePivotLength(value: String): UiLength {
    val cleaned = value.trim().lowercase()
    if (cleaned.endsWith("%")) return UiLength.Percent(cleaned.dropLast(1).toFloat() / 100f)
    if (cleaned.endsWith("px")) return cleaned.dropLast(2).toFloat().px
    return cleaned.toFloat().px
}

private fun parseScale(value: String): UiVec3 {
    val parts = splitWhitespace(value).map(::parseScalar)
    val x = parts.getOrElse(0) { 1f }
    return UiVec3(x, parts.getOrElse(1) { x }, parts.getOrElse(2) { 1f })
}

private fun parseTransform(value: String, base: UiTransform): UiTransform {
    if (value.equals("none", ignoreCase = true)) return UiTransform()
    var transform = base
    for ((name, args) in parseTransformFunctions(value)) {
        transform = when (name) {
            "translate" -> {
                val parts = splitTransformArgs(args).map(::parseScalar)
                transform.copy(translate = UiVec3(parts.getOrElse(0) { 0f }, parts.getOrElse(1) { 0f }, parts.getOrElse(2) { 0f }))
            }
            "translatex" -> transform.copy(translate = transform.translate.copy(x = parseScalar(args)))
            "translatey" -> transform.copy(translate = transform.translate.copy(y = parseScalar(args)))
            "translatez" -> transform.copy(translate = transform.translate.copy(z = parseScalar(args)))
            "scale" -> {
                val parts = splitTransformArgs(args).map(::parseScalar)
                val x = parts.getOrElse(0) { 1f }
                transform.copy(scale = UiVec3(x, parts.getOrElse(1) { x }, parts.getOrElse(2) { 1f }))
            }
            "scalex" -> transform.copy(scale = transform.scale.copy(x = parseScalar(args)))
            "scaley" -> transform.copy(scale = transform.scale.copy(y = parseScalar(args)))
            "scalez" -> transform.copy(scale = transform.scale.copy(z = parseScalar(args)))
            "rotate" -> transform.copy(rotate = transform.rotate.copy(z = parseScalar(args)))
            "rotatex" -> transform.copy(rotate = transform.rotate.copy(x = parseScalar(args)))
            "rotatey" -> transform.copy(rotate = transform.rotate.copy(y = parseScalar(args)))
            "rotatez" -> transform.copy(rotate = transform.rotate.copy(z = parseScalar(args)))
            "perspective" -> transform.copy(perspective = parseScalar(args))
            else -> transform
        }
    }
    return transform
}

private fun keyframeProperties(property: String, value: String): Set<String> {
    return when (property.lowercase()) {
        "translate" -> TransformTranslateProperties
        "rotate" -> TransformRotateProperties
        "scale" -> TransformScaleProperties
        "pivot", "transform-origin" -> setOf("transform.pivot")
        "perspective" -> setOf("transform.perspective")
        "transform" -> transformKeyframeProperties(value)
        else -> setOf(property.lowercase())
    }
}

private fun transformKeyframeProperties(value: String): Set<String> {
    if (value.equals("none", ignoreCase = true)) return TransformProperties
    return parseTransformFunctions(value).flatMap { (name, _) ->
        when (name) {
            "translate" -> TransformTranslateProperties
            "translatex" -> setOf("transform.translate.x")
            "translatey" -> setOf("transform.translate.y")
            "translatez" -> setOf("transform.translate.z")
            "rotate" -> setOf("transform.rotate.z")
            "rotatex" -> setOf("transform.rotate.x")
            "rotatey" -> setOf("transform.rotate.y")
            "rotatez" -> setOf("transform.rotate.z")
            "scale" -> TransformScaleProperties
            "scalex" -> setOf("transform.scale.x")
            "scaley" -> setOf("transform.scale.y")
            "scalez" -> setOf("transform.scale.z")
            "perspective" -> setOf("transform.perspective")
            else -> emptySet()
        }
    }.toSet()
}

private val TransformTranslateProperties = setOf(
    "transform.translate.x",
    "transform.translate.y",
    "transform.translate.z",
)

private val TransformRotateProperties = setOf(
    "transform.rotate.x",
    "transform.rotate.y",
    "transform.rotate.z",
)

private val TransformScaleProperties = setOf(
    "transform.scale.x",
    "transform.scale.y",
    "transform.scale.z",
)

private val TransformProperties = TransformTranslateProperties +
        TransformRotateProperties +
        TransformScaleProperties +
        setOf("transform.pivot", "transform.perspective")

private fun splitTransformArgs(args: String): List<String> {
    return if (args.contains(',')) splitTopLevel(args, ',') else splitWhitespace(args)
}

private fun parseTransformFunctions(value: String): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    var index = 0
    while (index < value.length) {
        while (index < value.length && value[index].isWhitespace()) index++
        val nameStart = index
        while (index < value.length && (value[index].isLetterOrDigit() || value[index] == '-')) index++
        if (nameStart == index) break
        val name = value.substring(nameStart, index).lowercase()
        require(value.getOrNull(index) == '(') { "Expected transform function after '$name'" }
        val argsStart = index + 1
        val close = findFunctionClose(value, index)
        result += name to value.substring(argsStart, close).trim()
        index = close + 1
    }
    return result
}

private fun findFunctionClose(value: String, open: Int): Int {
    var depth = 0
    var inString = false
    var quote = '\u0000'
    for (index in open until value.length) {
        val char = value[index]
        if (inString) {
            if (char == quote && value.getOrNull(index - 1) != '\\') inString = false
        } else {
            when (char) {
                '\'', '"' -> {
                    inString = true
                    quote = char
                }
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
    }
    throw IllegalArgumentException("Unclosed transform function in '$value'")
}

private fun parseScalar(value: String): Float = value.trim().removeSuffix("px").removeSuffix("deg").toFloat()

private fun parseStopOffset(value: String): Float {
    val cleaned = value.trim()
    if (cleaned.endsWith("%")) return cleaned.dropLast(1).toFloat() / 100f
    return parseScalar(cleaned).coerceIn(0f, 1f)
}

private fun parseFilterAmount(value: String): Float {
    val cleaned = value.trim()
    if (cleaned.endsWith("%")) return cleaned.dropLast(1).toFloat() / 100f
    return cleaned.toFloat()
}

private fun looksLikeColor(value: String): Boolean {
    val cleaned = value.trim().lowercase()
    return cleaned.startsWith("rgba(") ||
            cleaned.startsWith("rgb(") ||
            cleaned.startsWith("#") ||
            cleaned == "transparent" ||
            cleaned == "white" ||
            cleaned == "black"
}

private fun looksLikeImageSource(value: String): Boolean {
    val cleaned = unquote(value).lowercase()
    return ":" in cleaned || cleaned.endsWith(".png") || cleaned.endsWith(".jpg") || cleaned.endsWith(".jpeg")
}

private fun parseBoundFunction(value: String, name: String): UiBoundString? {
    if (!value.trim().startsWith("$name(")) return null
    return UiBoundString(unquote(functionArgs(value.trim(), name).joinToString(",").trim()))
}

private fun parseAnimations(value: String): List<UiAnimation> {
    if (value.equals("none", ignoreCase = true)) return emptyList()
    return splitTopLevel(value, ',').map(::parseAnimation)
}

private fun parseAnimation(value: String): UiAnimation {
    var name: String? = null
    var duration: Long? = null
    var delay = 0L
    var easing: TransitionEasing = TransitionEasing.LINEAR
    var iterationCount = 1f
    var direction = UiAnimationDirection.NORMAL
    var fillMode = UiAnimationFillMode.NONE
    var playState = UiAnimationPlayState.RUNNING
    for (part in splitTopLevelWhitespace(value)) {
        val cleaned = part.trim()
        when {
            cleaned.isDurationToken() && duration == null -> duration = parseDuration(cleaned)
            cleaned.isDurationToken() -> delay = parseDuration(cleaned)
            cleaned.isEasingToken() -> easing = parseEasing(cleaned)
            cleaned.equals("infinite", ignoreCase = true) || cleaned.toFloatOrNull() != null -> iterationCount = parseIterationCount(cleaned)
            parseAnimationDirectionOrNull(cleaned) != null -> direction = parseAnimationDirection(cleaned)
            parseAnimationFillModeOrNull(cleaned) != null -> fillMode = parseAnimationFillMode(cleaned)
            parseAnimationPlayStateOrNull(cleaned) != null -> playState = parseAnimationPlayState(cleaned)
            else -> name = unquote(cleaned)
        }
    }
    return UiAnimation(
        name = name ?: "",
        durationMillis = duration ?: 0L,
        easing = easing,
        delayMillis = delay,
        iterationCount = iterationCount,
        direction = direction,
        fillMode = fillMode,
        playState = playState,
    )
}

private fun <T> List<UiAnimation>?.patchAnimationValues(values: List<T>, patch: (UiAnimation, T) -> UiAnimation): List<UiAnimation> {
    if (values.isEmpty()) return orEmpty()
    val base = this?.takeIf { it.isNotEmpty() } ?: List(values.size) { UiAnimation("") }
    return base.mapIndexed { index, animation -> patch(animation, values[index % values.size]) }
}

private fun parseTransitions(value: String): List<UiTransition> {
    if (value.equals("none", ignoreCase = true)) return emptyList()
    return splitTopLevel(value, ',').map { entry ->
        val parts = splitTopLevelWhitespace(entry)
        UiTransition(
            property = parts[0],
            durationMillis = parseDuration(parts.getOrElse(1) { "0ms" }),
            easing = parseEasing(parts.getOrElse(2) { "linear" }),
        )
    }
}

private fun parseTyping(value: String): UiTyping? {
    val parts = splitTopLevelWhitespace(value)
    val duration = parts.firstOrNull() ?: return null
    if (duration.equals("none", ignoreCase = true) || duration.equals("off", ignoreCase = true)) return null
    val tail = parts.drop(1)
    val easing = tail.firstOrNull { it.isEasingToken() }?.let(::parseEasing) ?: TransitionEasing.LINEAR
    val delay = tail.firstOrNull { it.isDurationToken() }?.let(::parseDuration) ?: 0L
    return UiTyping(
        durationMillis = if (duration.equals("auto", ignoreCase = true)) null else parseDuration(duration),
        easing = easing,
        delayMillis = delay,
    )
}

private fun parseDuration(value: String): Long {
    val cleaned = value.trim()
    if (cleaned.endsWith("ms")) return cleaned.dropLast(2).toLong()
    if (cleaned.endsWith("s")) return (cleaned.dropLast(1).toFloat() * 1000f).toLong()
    return cleaned.toLong()
}

private fun parseIterationCount(value: String): Float {
    return if (value.equals("infinite", ignoreCase = true)) Float.POSITIVE_INFINITY else value.toFloat().coerceAtLeast(0f)
}

private fun parseAnimationDirection(value: String): UiAnimationDirection {
    return parseAnimationDirectionOrNull(value) ?: throw IllegalArgumentException("Unknown animation direction '$value'")
}

private fun parseAnimationDirectionOrNull(value: String): UiAnimationDirection? = when (value.lowercase()) {
    "normal" -> UiAnimationDirection.NORMAL
    "reverse" -> UiAnimationDirection.REVERSE
    "alternate" -> UiAnimationDirection.ALTERNATE
    "alternate-reverse" -> UiAnimationDirection.ALTERNATE_REVERSE
    else -> null
}

private fun parseAnimationFillMode(value: String): UiAnimationFillMode {
    return parseAnimationFillModeOrNull(value) ?: throw IllegalArgumentException("Unknown animation fill mode '$value'")
}

private fun parseAnimationFillModeOrNull(value: String): UiAnimationFillMode? = when (value.lowercase()) {
    "none" -> UiAnimationFillMode.NONE
    "forwards" -> UiAnimationFillMode.FORWARDS
    "backwards" -> UiAnimationFillMode.BACKWARDS
    "both" -> UiAnimationFillMode.BOTH
    else -> null
}

private fun parseAnimationPlayState(value: String): UiAnimationPlayState {
    return parseAnimationPlayStateOrNull(value) ?: throw IllegalArgumentException("Unknown animation play state '$value'")
}

private fun parseAnimationPlayStateOrNull(value: String): UiAnimationPlayState? = when (value.lowercase()) {
    "running" -> UiAnimationPlayState.RUNNING
    "paused" -> UiAnimationPlayState.PAUSED
    else -> null
}

private fun parseEasing(value: String): TransitionEasing {
    val cleaned = value.trim().lowercase()
    return when {
        cleaned == "linear" -> TransitionEasing.LINEAR
        cleaned == "ease" -> TransitionEasing.EASE_IN_OUT
        cleaned == "ease-in" -> TransitionEasing.EASE_IN
        cleaned == "ease-out" -> TransitionEasing.EASE_OUT
        cleaned == "ease-in-out" -> TransitionEasing.EASE_IN_OUT
        cleaned == "step-start" -> TransitionEasing.Steps(1, TransitionEasing.StepPosition.START)
        cleaned == "step-end" -> TransitionEasing.Steps(1, TransitionEasing.StepPosition.END)
        cleaned.startsWith("steps(") -> parseSteps(cleaned)
        cleaned.startsWith("step(") -> parseSteps(cleaned.replaceFirst("step(", "steps("))
        cleaned.startsWith("cubic-bezier(") -> parseCubicBezier(cleaned)
        else -> TransitionEasing.LINEAR
    }
}

private fun parseSteps(value: String): TransitionEasing.Steps {
    val args = functionArgs(value, "steps")
    val count = args.firstOrNull()?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val position = when (args.getOrNull(1)?.trim()?.lowercase()) {
        "start", "jump-start" -> TransitionEasing.StepPosition.START
        else -> TransitionEasing.StepPosition.END
    }
    return TransitionEasing.Steps(count, position)
}

private fun parseCubicBezier(value: String): TransitionEasing.CubicBezier {
    val args = functionArgs(value, "cubic-bezier").map { it.trim().toFloat() }
    require(args.size == 4) { "cubic-bezier requires four numbers" }
    return TransitionEasing.CubicBezier(
        x1 = args[0].coerceIn(0f, 1f),
        y1 = args[1],
        x2 = args[2].coerceIn(0f, 1f),
        y2 = args[3],
    )
}

private fun String.isDurationToken(): Boolean {
    val cleaned = trim().lowercase()
    return cleaned.removeSuffix("ms").toFloatOrNull() != null ||
            cleaned.removeSuffix("s").takeIf { cleaned.endsWith("s") }?.toFloatOrNull() != null
}

private fun String.isEasingToken(): Boolean {
    val cleaned = trim().lowercase()
    return cleaned in setOf("linear", "ease", "ease-in", "ease-out", "ease-in-out", "step-start", "step-end") ||
            cleaned.startsWith("steps(") ||
            cleaned.startsWith("step(") ||
            cleaned.startsWith("cubic-bezier(")
}

private fun parseBoolean(value: String): Boolean = when (value.lowercase()) {
    "true", "yes", "1", "enabled" -> true
    "false", "no", "0", "disabled" -> false
    else -> throw IllegalArgumentException("Expected boolean, got '$value'")
}

private fun parseTextEffects(value: String): List<UiTextEffect> {
    if (value.equals("none", ignoreCase = true)) return emptyList()
    return splitTopLevel(value, ',').map { entry ->
        parseTextEffect(entry.trim())
    }
}

private fun parseTextEffect(entry: String): UiTextEffect {
    val nameEnd = entry.indexOf('(')
    val name = if (nameEnd < 0) entry.trim().lowercase() else entry.substring(0, nameEnd).trim().lowercase()
    val args = if (nameEnd < 0) emptyList() else functionArgs(entry, name)

    return when (name) {
        "bold" -> Bold
        "italic" -> Italic
        "underline" -> Underline
        "strikethrough" -> Strikethrough
        "code" -> Code
        "link" -> Link(args.firstOrNull() ?: "")
        "color" -> TextColor(if (args.isNotEmpty()) parseColor(args.first()) else UiColor.White)
        "font-size", "size" -> TextSize(args.firstOrNull()?.parseScalarOrNull() ?: DefaultUiFontSize)
        "font-family", "font" -> TextFont(args.firstOrNull() ?: "")
        "shadow" -> parseHssShadow(args)
        "outline" -> parseHssOutline(args)
        "glow" -> parseHssGlow(args)
        "gradient" -> parseHssGradient(args)
        "rainbow" -> Rainbow()
        "pulse" -> Pulse()
        "wave" -> parseHssWave(args)
        "shake" -> parseHssShake(args)
        "wiggle" -> parseHssWiggle(args)
        "swing" -> Swing()
        "scroll" -> Scroll()
        "glitch" -> Glitch()
        else -> throw IllegalArgumentException("Unknown text effect '$name'")
    }
}

private fun String.parseScalarOrNull(): Float = parseScalar(this)

private fun parseHssShadow(args: List<String>): Shadow {
    return Shadow(
        offsetX = args.getOrNull(0)?.parseScalarOrNull() ?: 1.5f,
        offsetY = args.getOrNull(1)?.parseScalarOrNull() ?: 1.5f,
        blur = args.getOrNull(2)?.parseScalarOrNull() ?: 0f,
        color = args.getOrNull(3)?.let(::parseColor) ?: UiColor(0f, 0f, 0f, 0.7f),
    )
}

private fun parseHssOutline(args: List<String>): Outline {
    return Outline(
        width = args.getOrNull(0)?.parseScalarOrNull() ?: 1.5f,
        color = args.getOrNull(1)?.let(::parseColor) ?: UiColor(0f, 0f, 0f, 1f),
    )
}

private fun parseHssGlow(args: List<String>): Glow {
    return Glow(
        radius = args.getOrNull(0)?.parseScalarOrNull() ?: 3f,
        color = args.getOrNull(1)?.let(::parseColor) ?: UiColor(1f, 1f, 1f, 0.8f),
        quality = args.getOrNull(2)?.toIntOrNull() ?: 4,
    )
}

private fun parseHssGradient(args: List<String>): Gradient {
    val colorArgs = if (args.size >= 2) args.drop(1) else args
    val colors = colorArgs.map { parseColor(it) }
    return Gradient(
        colors = if (colors.size >= 2) colors else Gradient().colors,
        mode = when (args.firstOrNull()?.lowercase()) {
            "vertical", "v" -> GradientMode.VERTICAL
            else -> GradientMode.HORIZONTAL
        },
    )
}

private fun parseHssWave(args: List<String>): Wave {
    return Wave(
        amplitude = args.getOrNull(0)?.parseScalarOrNull() ?: 3f,
        frequency = args.getOrNull(1)?.parseScalarOrNull() ?: 1.5f,
        speed = args.getOrNull(2)?.parseScalarOrNull() ?: 2f,
    )
}

private fun parseHssShake(args: List<String>): Shake {
    return Shake(
        amplitude = args.getOrNull(0)?.parseScalarOrNull() ?: 2f,
        frequency = args.getOrNull(1)?.parseScalarOrNull() ?: 10f,
    )
}

private fun parseHssWiggle(args: List<String>): Wiggle {
    return Wiggle(
        amplitude = args.getOrNull(0)?.parseScalarOrNull() ?: 2f,
        frequency = args.getOrNull(1)?.parseScalarOrNull() ?: 2f,
        speed = args.getOrNull(2)?.parseScalarOrNull() ?: 1.5f,
        angleDegrees = args.getOrNull(3)?.parseScalarOrNull() ?: 0f,
    )
}

private fun splitWhitespace(value: String): List<String> = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

private fun splitTopLevelWhitespace(value: String): List<String> {
    val result = mutableListOf<String>()
    var start = -1
    var depth = 0
    var inString = false
    var quote = '\u0000'
    for (index in value.indices) {
        val char = value[index]
        if (start < 0 && !char.isWhitespace()) start = index
        if (start < 0) continue
        if (inString) {
            if (char == quote && value.getOrNull(index - 1) != '\\') inString = false
        } else {
            when {
                char == '\'' || char == '"' -> {
                    inString = true
                    quote = char
                }

                char == '(' -> depth++
                char == ')' -> depth--
                char.isWhitespace() && depth == 0 -> {
                    result += value.substring(start, index).trim()
                    start = -1
                }
            }
        }
    }
    if (start >= 0) result += value.substring(start).trim()
    return result.filter { it.isNotBlank() }
}

private fun functionArgs(value: String, name: String): List<String> {
    val prefix = "$name("
    require(value.startsWith(prefix) && value.endsWith(")")) { "Expected $name(...) value, got '$value'" }
    return splitTopLevel(value.substring(prefix.length, value.length - 1), ',')
}

private fun splitTopLevel(value: String, delimiter: Char): List<String> {
    val result = mutableListOf<String>()
    var start = 0
    var depth = 0
    var inString = false
    var quote = '\u0000'
    for (index in value.indices) {
        val char = value[index]
        if (inString) {
            if (char == quote && value.getOrNull(index - 1) != '\\') inString = false
        } else {
            when (char) {
                '\'', '"' -> {
                    inString = true
                    quote = char
                }

                '(' -> depth++
                ')' -> depth--
                delimiter -> if (depth == 0) {
                    result += value.substring(start, index).trim()
                    start = index + 1
                }
            }
        }
    }
    result += value.substring(start).trim()
    return result.filter { it.isNotEmpty() }
}

private fun unquote(value: String): String = value.trim().removeSurrounding("\"").removeSurrounding("'")
