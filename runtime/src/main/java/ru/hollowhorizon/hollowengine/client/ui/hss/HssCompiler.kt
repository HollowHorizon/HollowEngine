package ru.hollowhorizon.hollowengine.client.ui.hss

import ru.hollowhorizon.hollowengine.client.ui.*

data class CompiledHss(val rules: List<StyleRule>)

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
        return CompiledHss(rules)
    }

    internal fun compileDeclaration(declaration: HssDeclaration): StyleInstruction? {
        val property = declaration.property.lowercase()
        val value = declaration.value.trim()
        return when (property) {
            "layout" -> instruction { it.layout = parseLayout(value) }
            "size" -> instruction { it.size = parseSize(value) }
            "width" -> instruction { it.size = (it.size ?: UiSize()).copy(width = parseLength(value)) }
            "height" -> instruction { it.size = (it.size ?: UiSize()).copy(height = parseLength(value)) }
            "min-size" -> instruction { it.minSize = parseSize(value) }
            "max-size" -> instruction { it.maxSize = parseSize(value) }
            "aspect-ratio" -> instruction { it.aspectRatio = parseAspectRatio(value) }
            "padding" -> instruction { it.padding = parseInsets(value, allowAuto = false) }
            "margin" -> instruction { it.margin = parseInsets(value, allowAuto = true) }
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
            "foreground", "color" -> instruction { it.foreground = parseColor(value) }
            "image" -> instruction { it.image = parseBoundFunction(value, "image") ?: UiBoundString(unquote(value)) }
            "shader" -> instruction { it.shader = parseBoundFunction(value, "shader") ?: UiBoundString(unquote(value)) }
            "border" -> instruction { it.border = parseBorder(value, it.border ?: UiBorder()) }
            "border-radius" -> instruction { it.border = (it.border ?: UiBorder()).copy(radius = parseScalar(value)) }
            "shadow", "box-shadow" -> instruction { it.shadows = parseShadows(value) }
            "opacity" -> instruction { it.opacity = value.toFloat() }
            "translate" -> instruction {
                it.transform = (it.transform ?: UiTransform()).copy(translate = parseVec3(value))
            }

            "rotate" -> instruction { it.transform = (it.transform ?: UiTransform()).copy(rotate = parseVec3(value)) }
            "scale" -> instruction { it.transform = (it.transform ?: UiTransform()).copy(scale = parseScale(value)) }
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
            "clip" -> instruction { it.clip = parseBoolean(value) }
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
            "text-wrap", "wrap" -> instruction { it.textWrap = parseTextWrap(value) }
            "text-align" -> instruction { it.textAlign = parseTextAlign(value) }
            "font-size" -> instruction { it.fontSize = parseScalar(value).coerceAtLeast(0.0001f) }
            "transition" -> instruction { it.transitions = parseTransitions(value) }
            else -> null
        }
    }

    private fun instruction(writer: (MutableUiStyle) -> Unit) = StyleInstruction { style, _ -> writer(style) }

    private fun inputInstruction(value: String, patch: (UiInputStyle, Boolean) -> UiInputStyle) =
        instruction { style ->
            style.input = patch(style.input ?: UiInputStyle(), parseBoolean(value))
        }
}

fun compileHss(source: String, origin: StyleOrigin = StyleOrigin.STYLESHEET): CompiledHss =
    HssCompiler(origin).compile(parseHss(source))

fun compileStyleModifier(property: String, value: String): Modifier? {
    val instruction = HssCompiler().compileDeclaration(HssDeclaration(property, value)) ?: return null
    return StyleModifier { style -> instruction.apply(style, UiBindingContext()) }
}

private fun parseLayout(value: String): LayoutType = when (value.lowercase()) {
    "auto" -> LayoutType.COLUMN
    "column" -> LayoutType.COLUMN
    "row" -> LayoutType.ROW
    "grid" -> LayoutType.GRID
    "stack" -> LayoutType.STACK
    "free" -> LayoutType.FREE
    else -> throw IllegalArgumentException("Unknown layout '$value'")
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
    parseBoundFunction(value, "image")?.let { return UiPaint.Image(it) }
    parseBoundFunction(value, "url")?.let { return UiPaint.Image(it) }
    parseBoundFunction(value, "shader")?.let { return UiPaint.Shader(it) }
    parseLinearGradient(value)?.let { return it }
    if (looksLikeImageSource(value)) return UiPaint.Image(parseImageSource(value))
    return UiPaint.Color(parseColor(value))
}

private fun parseImageSource(value: String): UiBoundString {
    return parseBoundFunction(value, "image")
        ?: parseBoundFunction(value, "url")
        ?: UiBoundString(unquote(value))
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

private fun parseColor(value: String): UiColor {
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

private fun parseTransitions(value: String): List<UiTransition> = splitTopLevel(value, ',').map { entry ->
    val parts = splitWhitespace(entry)
    UiTransition(
        property = parts[0],
        durationMillis = parseDuration(parts.getOrElse(1) { "0ms" }),
        easing = parseEasing(parts.getOrElse(2) { "linear" }),
    )
}

private fun parseDuration(value: String): Long {
    val cleaned = value.trim()
    if (cleaned.endsWith("ms")) return cleaned.dropLast(2).toLong()
    if (cleaned.endsWith("s")) return (cleaned.dropLast(1).toFloat() * 1000f).toLong()
    return cleaned.toLong()
}

private fun parseEasing(value: String): TransitionEasing = when (value.lowercase()) {
    "linear" -> TransitionEasing.LINEAR
    "ease-in" -> TransitionEasing.EASE_IN
    "ease-out" -> TransitionEasing.EASE_OUT
    "ease-in-out" -> TransitionEasing.EASE_IN_OUT
    else -> TransitionEasing.LINEAR
}

private fun parseBoolean(value: String): Boolean = when (value.lowercase()) {
    "true", "yes", "1", "enabled" -> true
    "false", "no", "0", "disabled" -> false
    else -> throw IllegalArgumentException("Expected boolean, got '$value'")
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
