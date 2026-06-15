package ru.hollowhorizon.hollowengine.client.ui

import org.w3c.dom.Element
import org.w3c.dom.Node
import kotlin.math.roundToInt

internal data class SvgCssRule(
    val selectors: List<String>,
    val declarations: Map<String, String>,
)

internal fun parseSvgCssRules(root: Element): List<SvgCssRule> {
    val rules = mutableListOf<SvgCssRule>()
    fun visit(element: Element) {
        if (element.svgName() == "style") rules += parseCssRules(element.textContent.orEmpty())
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) visit(child as Element)
            child = child.nextSibling
        }
    }
    visit(root)
    return rules
}

internal fun Element.resolveSvgStyle(parent: UiSvgStyle, rules: List<SvgCssRule>): UiSvgStyle {
    var style = parent
    rules.forEach { rule ->
        if (rule.selectors.any(::matchesSimpleSelector)) style = style.applySvgDeclarations(rule.declarations)
    }
    style = style.applySvgDeclarations(presentationDeclarations())
    val inline = getAttribute("style").trim()
    if (inline.isNotEmpty()) style = style.applySvgDeclarations(parseDeclarations(inline))
    return style
}

internal fun UiSvgStyle.applySvgDeclarations(declarations: Map<String, String>): UiSvgStyle {
    var style = this
    declarations.forEach { (rawName, rawValue) ->
        val name = rawName.trim().lowercase()
        val value = rawValue.trim()
        if (value.isEmpty()) return@forEach
        style = when (name) {
            "fill" -> style.copy(fill = parseSvgColor(value, style.color))
            "stroke" -> style.copy(stroke = parseSvgColor(value, style.color))
            "stroke-width" -> style.copy(strokeWidth = parseSvgLength(value) ?: style.strokeWidth)
            "stroke-linecap" -> style.copy(strokeLineCap = parseStrokeLineCap(value, style.strokeLineCap))
            "stroke-linejoin" -> style.copy(strokeLineJoin = parseStrokeLineJoin(value, style.strokeLineJoin))
            "color" -> style.copy(color = parseSvgColor(value, style.color) ?: style.color)
            "opacity" -> style.copy(opacity = value.toFloatOrNull()?.coerceIn(0f, 1f) ?: style.opacity)
            "fill-opacity" -> style.copy(fillOpacity = value.toFloatOrNull()?.coerceIn(0f, 1f) ?: style.fillOpacity)
            "stroke-opacity" -> style.copy(strokeOpacity = value.toFloatOrNull()?.coerceIn(0f, 1f) ?: style.strokeOpacity)
            "font-family" -> style.copy(fontFamily = normalizeFontFamily(value))
            "font-size" -> style.copy(fontSize = parseSvgLength(value) ?: style.fontSize)
            "text-anchor" -> style.copy(textAnchor = parseTextAnchor(value, style.textAnchor))
            "display" -> style.copy(display = value.lowercase() != "none")
            "visibility" -> style.copy(visibility = value.lowercase() != "hidden" && value.lowercase() != "collapse")
            "clip-path" -> style.copy(clipPath = value.takeUnless { it.equals("none", ignoreCase = true) })
            "mask" -> style.copy(mask = value.takeUnless { it.equals("none", ignoreCase = true) })
            "filter" -> style.copy(filter = value.takeUnless { it.equals("none", ignoreCase = true) })
            else -> style
        }
    }
    return style
}

internal fun parseSvgTransform(value: String): UiSvgTransform {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.equals("none", ignoreCase = true)) return UiSvgTransform.Identity
    var transform = UiSvgTransform.Identity
    transformRegex.findAll(trimmed).forEach { match ->
        val name = match.groupValues[1].lowercase()
        val numbers = parseSvgNumbers(match.groupValues[2])
        val next = when (name) {
            "matrix" -> {
                require(numbers.size == 6) { "SVG matrix() transform expects six numbers" }
                UiSvgTransform(numbers[0], numbers[1], numbers[2], numbers[3], numbers[4], numbers[5])
            }
            "translate" -> UiSvgTransform.translation(numbers.getOrElse(0) { 0f }, numbers.getOrElse(1) { 0f })
            "scale" -> {
                val x = numbers.getOrElse(0) { 1f }
                UiSvgTransform.scale(x, numbers.getOrElse(1) { x })
            }
            "rotate" -> when (numbers.size) {
                1 -> UiSvgTransform.rotation(numbers[0])
                3 -> UiSvgTransform.rotation(numbers[0], numbers[1], numbers[2])
                else -> throw IllegalArgumentException("SVG rotate() transform expects one or three numbers")
            }
            "skewx" -> UiSvgTransform.skewX(numbers.getOrElse(0) { 0f })
            "skewy" -> UiSvgTransform.skewY(numbers.getOrElse(0) { 0f })
            else -> UiSvgTransform.Identity
        }
        transform *= next
    }
    return transform
}

internal fun parseSvgNumbers(value: String): List<Float> {
    return numberRegex.findAll(value).map { it.value.toFloat() }.toList()
}

internal fun parseSvgLength(value: String, percentReference: Float? = null): Float? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.endsWith("%")) {
        val percent = trimmed.dropLast(1).toFloatOrNull() ?: return null
        return percentReference?.let { it * percent / 100f }
    }
    val normalized = trimmed
        .removeSuffix("px")
        .removeSuffix("pt")
        .removeSuffix("em")
        .removeSuffix("rem")
    return normalized.toFloatOrNull()
}

internal fun parseSvgColor(value: String, currentColor: UiColor = UiColor.Black): UiColor? {
    val trimmed = value.trim()
    if (trimmed.equals("none", ignoreCase = true)) return null
    if (trimmed.equals("currentColor", ignoreCase = true)) return currentColor
    if (trimmed.startsWith("url(", ignoreCase = true)) return null
    if (trimmed.startsWith("#")) return parseHexColor(trimmed)
    if (trimmed.startsWith("rgb", ignoreCase = true)) return parseRgbColor(trimmed)
    return namedSvgColors[trimmed.lowercase()]
}

internal fun Element.svgName(): String {
    return (localName ?: tagName).substringAfter(':').lowercase()
}

internal fun Element.hasNonEmptyAttribute(name: String): Boolean {
    return hasAttribute(name) && getAttribute(name).isNotBlank()
}

internal fun Element.svgId(): String? {
    return getAttribute("id").trim().takeIf(String::isNotEmpty)
}

internal fun Element.svgLength(attribute: String, percentReference: Float? = null): Float? {
    val value = getAttribute(attribute).trim()
    if (value.isEmpty()) return null
    return parseSvgLength(value, percentReference)
}

internal fun parseUrlReference(value: String?): String? {
    val clean = value?.trim().orEmpty()
    if (clean.isEmpty()) return null
    val url = urlRegex.matchEntire(clean)?.groupValues?.get(1)?.trim()?.trim('"', '\'')
    return url ?: clean.takeIf { it.startsWith("#") || it.contains(".svg#", ignoreCase = true) }
}

private fun parseCssRules(css: String): List<SvgCssRule> {
    val clean = css.replace(cssCommentRegex, "")
    return cssRuleRegex.findAll(clean).mapNotNull { match ->
        val selectors = match.groupValues[1].split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
        val declarations = parseDeclarations(match.groupValues[2])
        if (selectors.isEmpty() || declarations.isEmpty()) null else SvgCssRule(selectors, declarations)
    }.toList()
}

private fun parseDeclarations(source: String): Map<String, String> {
    return source.split(';')
        .mapNotNull { declaration ->
            val separator = declaration.indexOf(':')
            if (separator < 0) return@mapNotNull null
            val name = declaration.substring(0, separator).trim().lowercase()
            val value = declaration.substring(separator + 1).trim()
            if (name.isEmpty() || value.isEmpty()) null else name to value
        }
        .toMap()
}

private fun Element.presentationDeclarations(): Map<String, String> {
    val declarations = linkedMapOf<String, String>()
    presentationAttributes.forEach { attribute ->
        if (hasNonEmptyAttribute(attribute)) declarations[attribute] = getAttribute(attribute)
    }
    return declarations
}

private fun Element.matchesSimpleSelector(selector: String): Boolean {
    val clean = selector.substringAfterLast('>').substringAfterLast(' ').trim()
    if (clean.isEmpty() || clean == "*") return true
    val id = svgId()
    val classes = getAttribute("class").split(whitespaceRegex).filter(String::isNotEmpty).toSet()
    val tag = svgName()
    return clean.split(simpleSelectorBoundary).filter(String::isNotEmpty).all { part ->
        when {
            part.startsWith("#") -> id == part.drop(1)
            part.startsWith(".") -> part.drop(1) in classes
            else -> tag == part.lowercase()
        }
    }
}

private fun parseHexColor(value: String): UiColor {
    val hex = value.drop(1)
    val expanded = when (hex.length) {
        3 -> hex.flatMap { listOf(it, it) }.joinToString("")
        4 -> hex.flatMap { listOf(it, it) }.joinToString("")
        else -> hex
    }
    require(expanded.length == 6 || expanded.length == 8) { "Unsupported SVG color '$value'" }
    val red = expanded.substring(0, 2).toInt(16)
    val green = expanded.substring(2, 4).toInt(16)
    val blue = expanded.substring(4, 6).toInt(16)
    val alpha = expanded.takeIf { it.length == 8 }?.substring(6, 8)?.toInt(16) ?: 255
    return UiColor(red / 255f, green / 255f, blue / 255f, alpha / 255f)
}

private fun parseRgbColor(value: String): UiColor {
    val args = value.substringAfter('(').substringBeforeLast(')').split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
    require(args.size == 3 || args.size == 4) { "SVG rgb()/rgba() expects three or four channels" }
    val red = parseColorChannel(args[0])
    val green = parseColorChannel(args[1])
    val blue = parseColorChannel(args[2])
    val alpha = args.getOrNull(3)?.let(::parseAlphaChannel) ?: 1f
    return UiColor(red, green, blue, alpha)
}

private fun parseColorChannel(value: String): Float {
    return if (value.endsWith("%")) {
        (value.dropLast(1).toFloat() / 100f).coerceIn(0f, 1f)
    } else {
        (value.toFloat().roundToInt() / 255f).coerceIn(0f, 1f)
    }
}

private fun parseAlphaChannel(value: String): Float {
    return if (value.endsWith("%")) value.dropLast(1).toFloat() / 100f else value.toFloat()
}

private fun parseStrokeLineCap(value: String, fallback: UiSvgStrokeLineCap): UiSvgStrokeLineCap {
    return when (value.lowercase()) {
        "butt" -> UiSvgStrokeLineCap.BUTT
        "round" -> UiSvgStrokeLineCap.ROUND
        "square" -> UiSvgStrokeLineCap.SQUARE
        else -> fallback
    }
}

private fun parseStrokeLineJoin(value: String, fallback: UiSvgStrokeLineJoin): UiSvgStrokeLineJoin {
    return when (value.lowercase()) {
        "miter" -> UiSvgStrokeLineJoin.MITER
        "round" -> UiSvgStrokeLineJoin.ROUND
        "bevel" -> UiSvgStrokeLineJoin.BEVEL
        else -> fallback
    }
}

private fun parseTextAnchor(value: String, fallback: UiSvgTextAnchor): UiSvgTextAnchor {
    return when (value.lowercase()) {
        "start" -> UiSvgTextAnchor.START
        "middle" -> UiSvgTextAnchor.MIDDLE
        "end" -> UiSvgTextAnchor.END
        else -> fallback
    }
}

private fun normalizeFontFamily(value: String): String {
    return value.trim().takeIf { it.isNotBlank() } ?: "Serif"
}

private val presentationAttributes = listOf(
    "fill",
    "stroke",
    "stroke-width",
    "stroke-linecap",
    "stroke-linejoin",
    "color",
    "opacity",
    "fill-opacity",
    "stroke-opacity",
    "font-family",
    "font-size",
    "text-anchor",
    "display",
    "visibility",
    "clip-path",
    "mask",
    "filter",
)

private val namedSvgColors = mapOf(
    "black" to UiColor.Black,
    "white" to UiColor.White,
    "transparent" to UiColor.Transparent,
    "red" to UiColor(1f, 0f, 0f, 1f),
    "green" to UiColor(0f, 0.5f, 0f, 1f),
    "blue" to UiColor(0f, 0f, 1f, 1f),
    "yellow" to UiColor(1f, 1f, 0f, 1f),
    "cyan" to UiColor(0f, 1f, 1f, 1f),
    "magenta" to UiColor(1f, 0f, 1f, 1f),
    "gray" to UiColor(0.5f, 0.5f, 0.5f, 1f),
    "grey" to UiColor(0.5f, 0.5f, 0.5f, 1f),
    "orange" to UiColor(1f, 0.647f, 0f, 1f),
    "purple" to UiColor(0.5f, 0f, 0.5f, 1f),
)

private val transformRegex = Regex("([A-Za-z]+)\\s*\\(([^)]*)\\)")
private val numberRegex = Regex("[+-]?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][+-]?\\d+)?")
private val urlRegex = Regex("url\\((.*?)\\)", RegexOption.IGNORE_CASE)
private val cssCommentRegex = Regex("/\\*.*?\\*/", setOf(RegexOption.DOT_MATCHES_ALL))
private val cssRuleRegex = Regex("([^{}]+)\\{([^{}]*)}")
private val whitespaceRegex = Regex("\\s+")
private val simpleSelectorBoundary = Regex("(?=[.#])")
