package ru.hollowhorizon.hollowengine.client.ui.xml

import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.ui.HollowUiResourceAccess
import ru.hollowhorizon.hollowengine.client.ui.UiAlign
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiEventSink
import ru.hollowhorizon.hollowengine.client.ui.text.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextFieldMode
import ru.hollowhorizon.hollowengine.client.ui.widgets.readBoolean

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
    val attributes: UiXmlAttributeRegistry = UiXmlAttributeRegistry.Default,
)

@Serializable
data class UiXmlTree(
    val name: String,
    val attributes: Map<String, String> = emptyMap(),
    val children: List<UiXmlTree> = emptyList(),
) {
    companion object
}


internal fun UiXmlTree.isTextLiteral(): Boolean = name == "#text"

internal fun UiXmlTree.isInlineBreak(): Boolean = name.lowercase() in setOf("br", "break")

internal fun UiXmlTree.isInlineImage(): Boolean = name.lowercase() in setOf("img", "image")

internal fun UiXmlTree.isInlinePause(): Boolean = name.lowercase() == "pause"

internal fun UiXmlTree.textLiteral(): String =
    attributes.firstValue("#text", "value").normalizeInlineText()

internal fun UiXmlTree.inlineTagEffects(): List<UiTextEffect> {
    val attrEffects = inlineAttributeEffects()
    val tagEffects: List<UiTextEffect> = when (name.lowercase()) {
        "span" -> emptyList()
        "b", "bold" -> listOf(Bold)
        "i", "italic" -> listOf(Italic)
        "u", "underline" -> listOf(Underline)
        "s", "strike", "strikethrough" -> listOf(Strikethrough)
        "code" -> listOf(Code)
        "color" -> attributes.firstValue("value", "color").takeIf { it.isNotBlank() }
            ?.let(::parseInlineColor)?.let { listOf(TextColor(it)) } ?: emptyList()

        "size" -> attributes.firstValue("value", "fontSize", "font-size", "size").parseInlineSize()
            ?.let { listOf(TextSize(it)) } ?: emptyList()

        "a", "link" -> listOf(Link(attributes.firstValue("href", "to", "value")), Underline)
        "font" -> attributes.firstValue("family", "name", "value").takeIf { it.isNotBlank() }
            ?.let { listOf(TextFont(it)) } ?: emptyList()
        // TODO: Typing/typewriter reveal was removed (to be redone on Compose+coroutines); render as text.
        "typewriter", "typing" -> emptyList()
        "shadow" -> listOf(parseShadowEffect(attributes))
        "outline" -> listOf(parseOutlineEffect(attributes))
        "glow" -> listOf(parseGlowEffect(attributes))
        "gradient" -> listOf(parseGradientEffect(attributes))
        "rainbow" -> listOf(parseRainbowEffect(attributes))
        "pulse" -> listOf(parsePulseEffect(attributes))
        "wave" -> listOf(parseWaveEffect(attributes))
        "shake" -> listOf(parseShakeEffect(attributes))
        "wiggle" -> listOf(parseWiggleEffect(attributes))
        "swing" -> listOf(parseSwingEffect(attributes))
        "scroll" -> listOf(parseScrollEffect(attributes))
        "glitch" -> listOf(parseGlitchEffect(attributes))
        else -> emptyList()
    }
    return attrEffects + tagEffects
}

private fun UiXmlTree.inlineAttributeEffects(): List<UiTextEffect> {
    val effects = mutableListOf<UiTextEffect>()
    attributes.firstValue("size", "font-size", "fontSize").parseInlineSize()?.let { effects += TextSize(it) }
    attributes.firstValue("color", "foreground").takeIf { it.isNotBlank() }?.let(::parseInlineColor)?.let {
        effects += TextColor(it)
    }
    attributes.firstValue("font", "font-family", "fontFamily").takeIf { it.isNotBlank() }?.let {
        effects += TextFont(it)
    }
    return effects
}

internal class XmlInlineImage(
    val source: String,
    val width: Float,
    val height: Float,
    val align: UiAlign,
)

internal fun UiXmlTree.inlineImage(): XmlInlineImage {
    val width = attributes.firstValue("width", default = "16px").parseInlineSize() ?: 16f
    return XmlInlineImage(
        source = attributes.firstValue("source", "src", "image"),
        width = width,
        height = attributes.firstValue("height", default = "${width}px").parseInlineSize() ?: width,
        align = parseInlineImageAlign(attributes.firstValue("align", default = "baseline")),
    )
}

internal fun UiXmlTree.withInlineWidgetId(index: Int = 0): UiXmlTree {
    if ("id" in attributes) return this
    return copy(attributes = attributes + ("id" to inlineWidgetId(index)))
}

internal fun UiXmlTree.inlineWidgetId(index: Int = 0): String {
    return attributes["id"] ?: "__inline_${index}_${name.lowercase()}_${attributes.hashCode().toUInt().toString(16)}"
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

private fun parseInlineImageAlign(value: String): UiAlign = when (value.lowercase()) {
    "top" -> UiAlign.START
    "bottom" -> UiAlign.END
    // "middle"/"baseline"/default centre the atom on the line.
    else -> UiAlign.CENTER
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
    val red = if (text.length == 8) number shr 24 and 0xFF else number shr 16 and 0xFF
    val green = if (text.length == 8) number shr 16 and 0xFF else number shr 8 and 0xFF
    val blue = if (text.length == 8) number shr 8 and 0xFF else number and 0xFF
    val alpha = if (text.length == 8) (number and 0xFF).toFloat() / 255f else 1f
    return UiColor(red / 255f, green / 255f, blue / 255f, alpha)
}
