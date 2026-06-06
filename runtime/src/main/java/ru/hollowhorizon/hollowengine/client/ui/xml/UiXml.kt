package ru.hollowhorizon.hollowengine.client.ui.xml

import com.sunnychung.lib.multiplatform.kotlite.model.RuntimeValue
import com.sunnychung.lib.multiplatform.kotlite.model.XML_TEXT_NODE_NAME
import com.sunnychung.lib.multiplatform.kotlite.model.XML_TEXT_VALUE_ATTRIBUTE
import com.sunnychung.lib.multiplatform.kotlite.model.XmlValue
import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.effects.*

fun interface UiResourceLoader {
    fun readText(location: String): String
}

object MinecraftUiResourceLoader : UiResourceLoader {
    override fun readText(location: String): String {
        return HollowUiResourceAccess.readText(ResourceLocation.parse(location))
    }
}

data class UiXmlOptions(
    val resources: UiResourceLoader = MinecraftUiResourceLoader,
    val eventSink: UiEventSink = UiEventSink.None,
)

@Serializable
data class UiXmlTree(
    val name: String,
    val attributes: Map<String, String> = emptyMap(),
    val children: List<UiXmlTree> = emptyList(),
) {
    companion object
}


fun UiXmlTree.Companion.from(value: XmlValue): UiXmlTree {
    return UiXmlTree(
        name = value.name,
        attributes = value.attributes.associate { it.name to it.value.asUiAttributeString() },
        children = value.children.map { UiXmlTree.from(it) },
    )
}

private fun RuntimeValue.asUiAttributeString(): String = convertToString()

internal fun UiXmlTree.isTextLiteral(): Boolean = name == XML_TEXT_NODE_NAME

internal fun UiXmlTree.toTextContent(
    style: UiInlineStyle = UiInlineStyle(),
    onlyDirectText: Boolean = true,
): UiTextContent {
    attributes["text"]?.let { return UiTextContent.plain(it) }
    val segments = children.flatMap { child ->
        when {
            child.isTextLiteral() -> listOf(UiTextSegment.Text(child.attributes.firstValue(XML_TEXT_VALUE_ATTRIBUTE, "value").normalizeInlineText().bound(), style))
            onlyDirectText && !child.isTextInlineElement() -> emptyList()
            else -> child.toInlineSegments(style)
        }
    }
    return UiTextContent(segments).trimBoundaryText()
}

private fun UiXmlTree.toInlineSegments(style: UiInlineStyle): List<UiTextSegment> {
    val name = name.lowercase()
    val styled = style.withInlineAttributes(attributes)
    return when (name) {
        "span" -> inlineTextOrChildren(styled)
        "b", "bold" -> inlineTextOrChildren(styled.withBold())
        "i", "italic" -> inlineTextOrChildren(styled.withItalic())
        "u", "underline" -> inlineTextOrChildren(styled.withUnderline())
        "s", "strike", "strikethrough" -> inlineTextOrChildren(styled.withStrikethrough())
        "code" -> inlineTextOrChildren(styled.withCode())
        "color" -> inlineTextOrChildren(
            attributes.firstValue("value", "color").takeIf { it.isNotBlank() }
                ?.let(::parseInlineColor)?.let(styled::withColor) ?: styled
        )
        "size" -> inlineTextOrChildren(
            attributes.firstValue("value", "fontSize", "font-size", "size").parseInlineSize()
                ?.let(styled::withFontSize) ?: styled
        )
        "a", "link" -> {
            val url = attributes.firstValue("href", "to", "value")
            inlineTextOrChildren(styled.withLink(url).withUnderline())
        }
        "font" -> inlineTextOrChildren(
            attributes.firstValue("family", "name", "value").takeIf { it.isNotBlank() }
                ?.let(styled::withFontFamily) ?: styled
        )
        "typewriter", "typing" -> inlineTextOrChildren(
            styled.copy(effects = styled.effects + Typewriter)
        )
        "shadow" -> inlineTextOrChildren(
            styled.copy(effects = styled.effects + parseShadowEffect(attributes))
        )
        "outline" -> inlineTextOrChildren(
            styled.copy(effects = styled.effects + parseOutlineEffect(attributes))
        )
        "glow" -> inlineTextOrChildren(
            styled.copy(effects = styled.effects + parseGlowEffect(attributes))
        )
        "gradient" -> inlineTextOrChildren(
            styled.copy(effects = styled.effects + parseGradientEffect(attributes))
        )
        "rainbow" -> inlineTextOrChildren(
            styled.copy(effects = styled.effects + parseRainbowEffect(attributes))
        )
        "pulse" -> inlineTextOrChildren(
            styled.copy(effects = styled.effects + parsePulseEffect(attributes))
        )
        "wave" -> inlineTextOrChildren(
            styled.copy(effects = styled.effects + parseWaveEffect(attributes))
        )
        "shake" -> inlineTextOrChildren(
            styled.copy(effects = styled.effects + parseShakeEffect(attributes))
        )
        "wiggle" -> inlineTextOrChildren(
            styled.copy(effects = styled.effects + parseWiggleEffect(attributes))
        )
        "swing" -> inlineTextOrChildren(
            styled.copy(effects = styled.effects + parseSwingEffect(attributes))
        )
        "scroll" -> inlineTextOrChildren(
            styled.copy(effects = styled.effects + parseScrollEffect(attributes))
        )
        "glitch" -> inlineTextOrChildren(
            styled.copy(effects = styled.effects + parseGlitchEffect(attributes))
        )
        "br", "break" -> listOf(UiTextSegment.Text("\n".bound(), styled))
        "pause" -> listOf(UiTextSegment.Pause(parseInlineDuration(attributes.firstValue("delay", "duration", "value", default = "0ms"))))
        "img", "image" -> listOf(
            UiTextSegment.Image(
                source = attributes.firstValue("source", "src", "image").bound(),
                width = attributes.firstValue("width", default = "16px").parseInlineSize() ?: 16f,
                height = attributes.firstValue("height", default = attributes.firstValue("width", default = "16px")).parseInlineSize() ?: 16f,
                align = parseInlineAlign(attributes.firstValue("align", default = "baseline")),
                alt = attributes.firstValue("alt"),
            )
        )

        else -> throw IllegalArgumentException("Unsupported inline text tag '$name'")
    }
}

private fun UiInlineStyle.withInlineAttributes(attributes: Map<String, String>): UiInlineStyle {
    var style = this
    attributes.firstValue("size", "font-size", "fontSize").parseInlineSize()?.let { style = style.withFontSize(it) }
    attributes.firstValue("color", "foreground").takeIf { it.isNotBlank() }?.let(::parseInlineColor)?.let {
        style = style.withColor(it)
    }
    attributes.firstValue("font", "font-family", "fontFamily").takeIf { it.isNotBlank() }?.let {
        style = style.withFontFamily(it)
    }
    return style
}

private fun UiXmlTree.inlineTextOrChildren(style: UiInlineStyle): List<UiTextSegment> {
    val text = attributes.firstValue("text").takeIf { it.isNotEmpty() }
        ?: return inlineChildren(style)
    return listOf(UiTextSegment.Text(text.bound(), style))
}

private fun UiXmlTree.inlineChildren(style: UiInlineStyle): List<UiTextSegment> {
    return children.flatMap { child ->
        if (child.isTextLiteral()) {
            listOf(UiTextSegment.Text(child.attributes.firstValue(XML_TEXT_VALUE_ATTRIBUTE, "value").normalizeInlineText().bound(), style))
        } else {
            child.toInlineSegments(style)
        }
    }
}

internal fun UiXmlTree.isTextInlineElement(): Boolean {
    return name.lowercase() in setOf(
        "span",
        "b", "bold",
        "i", "italic",
        "u", "underline",
        "s", "strike", "strikethrough",
        "code",
        "color",
        "size",
        "a", "link",
        "font",
        "typewriter", "typing",
        "shadow", "outline", "glow",
        "gradient", "rainbow", "pulse",
        "wave", "shake", "wiggle", "swing",
        "scroll", "glitch",
        "br", "break",
        "pause",
        "img", "image",
    )
}

private fun String.normalizeInlineText(): String {
    return replace(Regex("[ \\t]*[\\r\\n]+[ \\t\\r\\n]*"), " ")
}

private fun UiTextContent.trimBoundaryText(): UiTextContent {
    val next = segments.toMutableList()
    val firstText = next.indexOfFirst { it is UiTextSegment.Text }
    if (firstText >= 0) {
        val segment = next[firstText] as UiTextSegment.Text
        next[firstText] = segment.copy(value = segment.value.template.trimStart().bound())
    }
    val lastText = next.indexOfLast { it is UiTextSegment.Text }
    if (lastText >= 0) {
        val segment = next[lastText] as UiTextSegment.Text
        next[lastText] = segment.copy(value = segment.value.template.trimEnd().bound())
    }
    return UiTextContent(next.filterNot { it is UiTextSegment.Text && it.value.template.isEmpty() })
}

private fun Map<String, String>.firstValue(vararg names: String, default: String = ""): String {
    return names.firstNotNullOfOrNull { this[it] } ?: default
}

private fun Map<String, String>.onlyWidgetAttributes(names: Set<String>): Map<String, String> {
    return filterKeys { it in names }
}

private fun Map<String, String>.textFieldMode(): UiTextFieldMode {
    if (readBoolean("multiline") || readBoolean("multi-line")) return UiTextFieldMode.MULTI_LINE
    return UiTextFieldMode.from(firstValue("mode", "multiline", "multi-line"))
}

private fun String.parseInlineSize(): Float? = trim().removeSuffix("px").toFloatOrNull()

private fun parseInlineDuration(value: String): Long {
    val cleaned = value.trim()
    if (cleaned.endsWith("ms")) return cleaned.dropLast(2).toLong()
    if (cleaned.endsWith("s")) return (cleaned.dropLast(1).toFloat() * 1000f).toLong()
    return cleaned.toLong()
}

private fun parseInlineAlign(value: String): UiInlineAlign = when (value.lowercase()) {
    "middle" -> UiInlineAlign.MIDDLE
    "top" -> UiInlineAlign.TOP
    "bottom" -> UiInlineAlign.BOTTOM
    else -> UiInlineAlign.BASELINE
}



private fun parseShadowEffect(attrs: Map<String, String>): Shadow {
    return Shadow(
        offsetX = attrs["x"]?.parseScalarOrNull() ?: attrs["offset-x"]?.parseScalarOrNull() ?: 1.5f,
        offsetY = attrs["y"]?.parseScalarOrNull() ?: attrs["offset-y"]?.parseScalarOrNull() ?: 1.5f,
        blur = attrs["blur"]?.parseScalarOrNull() ?: 0f,
        color = parseInlineColor(attrs.firstValue("color")) ?: UiColor(0f, 0f, 0f, 0.7f),
    )
}

private fun parseOutlineEffect(attrs: Map<String, String>): Outline {
    return Outline(
        width = attrs.firstValue("width", "size").parseScalarOrNull() ?: 1.5f,
        color = parseInlineColor(attrs.firstValue("color")) ?: UiColor(0f, 0f, 0f, 1f),
    )
}

private fun parseGlowEffect(attrs: Map<String, String>): Glow {
    return Glow(
        radius = attrs.firstValue("radius", "size").parseScalarOrNull() ?: 3f,
        color = parseInlineColor(attrs.firstValue("color")) ?: UiColor(1f, 1f, 1f, 0.8f),
        quality = attrs.firstValue("quality", "rings").toIntOrNull() ?: 4,
    )
}

private fun parseGradientEffect(attrs: Map<String, String>): Gradient {
    val colors = attrs.firstValue("colors", "palette").split(",").mapNotNull { parseInlineColor(it.trim()) }
    return Gradient(
        colors = if (colors.size >= 2) colors else Gradient().colors,
        mode = when (attrs.firstValue("mode", "direction").lowercase()) {
            "vertical", "v" -> GradientMode.VERTICAL
            else -> GradientMode.HORIZONTAL
        },
        speed = attrs["speed"]?.parseScalarOrNull() ?: 0f,
        phaseOffset = attrs["phase"]?.parseScalarOrNull() ?: 0f,
    )
}

private fun parseRainbowEffect(attrs: Map<String, String>): Rainbow {
    return Rainbow(
        frequency = attrs["frequency"]?.parseScalarOrNull() ?: 0.5f,
        saturation = attrs["saturation"]?.parseScalarOrNull() ?: 1f,
        brightness = attrs["brightness"]?.parseScalarOrNull() ?: 1f,
        speed = attrs["speed"]?.parseScalarOrNull() ?: 0.5f,
        phaseOffset = attrs["phase"]?.parseScalarOrNull() ?: 0f,
    )
}

private fun parsePulseEffect(attrs: Map<String, String>): Pulse {
    return Pulse(
        frequency = attrs["frequency"]?.parseScalarOrNull() ?: 1.5f,
        amplitude = attrs["amplitude"]?.parseScalarOrNull() ?: 0.4f,
        minAlpha = attrs.firstValue("min-alpha", "minAlpha").parseScalarOrNull() ?: 0.3f,
    )
}

private fun parseWaveEffect(attrs: Map<String, String>): Wave {
    return Wave(
        amplitude = attrs["amplitude"]?.parseScalarOrNull() ?: 3f,
        frequency = attrs["frequency"]?.parseScalarOrNull() ?: 1.5f,
        speed = attrs["speed"]?.parseScalarOrNull() ?: 2f,
        phaseOffset = attrs["phase"]?.parseScalarOrNull() ?: 0f,
    )
}

private fun parseShakeEffect(attrs: Map<String, String>): Shake {
    return Shake(
        amplitude = attrs["amplitude"]?.parseScalarOrNull() ?: 2f,
        frequency = attrs["frequency"]?.parseScalarOrNull() ?: 10f,
        seed = attrs["seed"]?.parseScalarOrNull() ?: 42f,
    )
}

private fun parseWiggleEffect(attrs: Map<String, String>): Wiggle {
    return Wiggle(
        amplitude = attrs["amplitude"]?.parseScalarOrNull() ?: 2f,
        frequency = attrs["frequency"]?.parseScalarOrNull() ?: 2f,
        speed = attrs["speed"]?.parseScalarOrNull() ?: 1.5f,
        angleDegrees = attrs.firstValue("angle", "direction").parseScalarOrNull() ?: 0f,
    )
}

private fun parseSwingEffect(attrs: Map<String, String>): Swing {
    return Swing(
        amplitude = attrs["amplitude"]?.parseScalarOrNull() ?: 5f,
        frequency = attrs["frequency"]?.parseScalarOrNull() ?: 0.8f,
        speed = attrs["speed"]?.parseScalarOrNull() ?: 1.2f,
    )
}

private fun parseScrollEffect(attrs: Map<String, String>): Scroll {
    return Scroll(
        speed = attrs["speed"]?.parseScalarOrNull() ?: 30f,
        pauseAtEnd = attrs["pause"]?.parseScalarOrNull() ?: 1f,
    )
}

private fun parseGlitchEffect(attrs: Map<String, String>): Glitch {
    return Glitch(
        frequency = attrs["frequency"]?.parseScalarOrNull() ?: 3f,
        intensity = attrs["intensity"]?.parseScalarOrNull() ?: 2f,
    )
}

private fun String?.parseScalarOrNull(): Float? = this?.trim()?.removeSuffix("px")?.toFloatOrNull()

private fun parseInlineColor(value: String): UiColor? {
    val text = value.trim().removePrefix("#")
    if (text.length != 6 && text.length != 8) return null
    val number = text.toLongOrNull(16) ?: return null
    val red = if (text.length == 8) (number shr 24) and 0xFF else (number shr 16) and 0xFF
    val green = if (text.length == 8) (number shr 16) and 0xFF else (number shr 8) and 0xFF
    val blue = if (text.length == 8) (number shr 8) and 0xFF else number and 0xFF
    val alpha = if (text.length == 8) (number and 0xFF).toFloat() / 255f else 1f
    return UiColor(red / 255f, green / 255f, blue / 255f, alpha)
}
