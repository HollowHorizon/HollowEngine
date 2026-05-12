package ru.hollowhorizon.hollowengine.client.ui.hss

import ru.hollowhorizon.hollowengine.client.ui.LayoutType
import ru.hollowhorizon.hollowengine.client.ui.MutableUiStyle
import ru.hollowhorizon.hollowengine.client.ui.StyleOrigin
import ru.hollowhorizon.hollowengine.client.ui.TransitionEasing
import ru.hollowhorizon.hollowengine.client.ui.UiAlign
import ru.hollowhorizon.hollowengine.client.ui.UiBindingContext
import ru.hollowhorizon.hollowengine.client.ui.UiBorder
import ru.hollowhorizon.hollowengine.client.ui.UiBoundString
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiInsets
import ru.hollowhorizon.hollowengine.client.ui.UiInputStyle
import ru.hollowhorizon.hollowengine.client.ui.UiImageFit
import ru.hollowhorizon.hollowengine.client.ui.UiLength
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.UiPaint
import ru.hollowhorizon.hollowengine.client.ui.UiPosition
import ru.hollowhorizon.hollowengine.client.ui.UiSize
import ru.hollowhorizon.hollowengine.client.ui.UiTransition
import ru.hollowhorizon.hollowengine.client.ui.UiTransform
import ru.hollowhorizon.hollowengine.client.ui.UiVec3
import ru.hollowhorizon.hollowengine.client.ui.px

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

    private fun compileDeclaration(declaration: HssDeclaration): StyleInstruction? {
        val property = declaration.property.lowercase()
        val value = declaration.value.trim()
        return when (property) {
            "layout" -> instruction { it.layout = parseLayout(value) }
            "size" -> instruction { it.size = parseSize(value) }
            "width" -> instruction { it.size = (it.size ?: UiSize()).copy(width = parseLength(value)) }
            "height" -> instruction { it.size = (it.size ?: UiSize()).copy(height = parseLength(value)) }
            "min-size" -> instruction { it.minSize = parseSize(value) }
            "max-size" -> instruction { it.maxSize = parseSize(value) }
            "padding" -> instruction { it.padding = parseInsets(value, allowAuto = false) }
            "margin" -> instruction { it.margin = parseInsets(value, allowAuto = true) }
            "gap" -> instruction { it.gap = parseLength(value) }
            "align" -> instruction { it.alignItems = parseAlign(value) }
            "align-self" -> instruction { it.alignSelf = parseAlign(value) }
            "justify-self" -> instruction { it.justifySelf = parseAlign(value) }
            "justify" -> instruction { it.justifyContent = parseAlign(value) }
            "grow" -> instruction { it.grow = value.toFloat() }
            "position" -> instruction { it.position = parsePosition(value) }
            "background" -> instruction { it.background = parsePaint(value) }
            "foreground", "color" -> instruction { it.foreground = parseColor(value) }
            "image" -> instruction { it.image = parseBoundFunction(value, "image") ?: UiBoundString(unquote(value)) }
            "shader" -> instruction { it.shader = parseBoundFunction(value, "shader") ?: UiBoundString(unquote(value)) }
            "border" -> instruction { it.border = parseBorder(value, it.border ?: UiBorder()) }
            "border-radius" -> instruction { it.border = (it.border ?: UiBorder()).copy(radius = parseScalar(value)) }
            "opacity" -> instruction { it.opacity = value.toFloat() }
            "translate" -> instruction { it.transform = (it.transform ?: UiTransform()).copy(translate = parseVec3(value)) }
            "rotate" -> instruction { it.transform = (it.transform ?: UiTransform()).copy(rotate = parseVec3(value)) }
            "scale" -> instruction { it.transform = (it.transform ?: UiTransform()).copy(scale = parseScale(value)) }
            "perspective" -> instruction { it.transform = (it.transform ?: UiTransform()).copy(perspective = parseScalar(value)) }
            "hoverable" -> inputInstruction(value) { style, enabled -> style.copy(hoverable = enabled) }
            "clickable" -> inputInstruction(value) { style, enabled -> style.copy(clickable = enabled) }
            "focusable" -> inputInstruction(value) { style, enabled -> style.copy(focusable = enabled) }
            "draggable" -> inputInstruction(value) { style, enabled -> style.copy(draggable = enabled) }
            "scrollable" -> inputInstruction(value) { style, enabled -> style.copy(scrollable = enabled) }
            "clip" -> instruction { it.clip = parseBoolean(value) }
            "layer" -> instruction { it.layer = value.toInt() }
            "image-fit", "fit" -> instruction { it.imageFit = parseImageFit(value) }
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

private fun parseLayout(value: String): LayoutType = when (value.lowercase()) {
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

private fun parseImageFit(value: String): UiImageFit = when (value.lowercase()) {
    "stretch", "strench" -> UiImageFit.STRETCH
    "contain", "fit" -> UiImageFit.CONTAIN
    "cover", "zoom" -> UiImageFit.COVER
    "none" -> UiImageFit.NONE
    else -> throw IllegalArgumentException("Unknown image fit '$value'")
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
    val cleaned = value.trim()
    if (cleaned == "auto" && allowAuto) return UiLength.Auto
    if (cleaned.endsWith("px")) return cleaned.dropLast(2).toFloat().px
    if (cleaned.endsWith("%")) return UiLength.Percent(cleaned.dropLast(1).toFloat() / 100f)
    return cleaned.toFloat().px
}

private fun parsePaint(value: String): UiPaint {
    parseBoundFunction(value, "image")?.let { return UiPaint.Image(it) }
    parseBoundFunction(value, "shader")?.let { return UiPaint.Shader(it) }
    return UiPaint.Color(parseColor(value))
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

private fun parseScale(value: String): UiVec3 {
    val parts = splitWhitespace(value).map(::parseScalar)
    val x = parts.getOrElse(0) { 1f }
    return UiVec3(x, parts.getOrElse(1) { x }, parts.getOrElse(2) { 1f })
}

private fun parseScalar(value: String): Float = value.trim().removeSuffix("px").removeSuffix("deg").toFloat()

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
