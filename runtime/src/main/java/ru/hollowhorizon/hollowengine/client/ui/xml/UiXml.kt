package ru.hollowhorizon.hollowengine.client.ui.xml

import com.sunnychung.lib.multiplatform.kotlite.model.RuntimeValue
import com.sunnychung.lib.multiplatform.kotlite.model.XmlValue
import com.sunnychung.lib.multiplatform.kotlite.model.XML_TEXT_NODE_NAME
import com.sunnychung.lib.multiplatform.kotlite.model.XML_TEXT_VALUE_ATTRIBUTE
import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.ui.HollowUiResourceAccess
import ru.hollowhorizon.hollowengine.client.ui.BaseUiNode
import ru.hollowhorizon.hollowengine.client.ui.BoxNode
import ru.hollowhorizon.hollowengine.client.ui.CanvasNode
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.EntityNode
import ru.hollowhorizon.hollowengine.client.ui.ImageNode
import ru.hollowhorizon.hollowengine.client.ui.UiInlineAlign
import ru.hollowhorizon.hollowengine.client.ui.UiInlineStyle
import ru.hollowhorizon.hollowengine.client.ui.ItemNode
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.TextNode
import ru.hollowhorizon.hollowengine.client.ui.UiChildren
import ru.hollowhorizon.hollowengine.client.ui.UiEventKind
import ru.hollowhorizon.hollowengine.client.ui.UiEventPayloadTemplate
import ru.hollowhorizon.hollowengine.client.ui.UiEventSink
import ru.hollowhorizon.hollowengine.client.ui.UiClientScriptModifier
import ru.hollowhorizon.hollowengine.client.ui.UiTextContent
import ru.hollowhorizon.hollowengine.client.ui.UiTextSegment
import ru.hollowhorizon.hollowengine.client.ui.bound
import ru.hollowhorizon.hollowengine.client.ui.hss.compileStyleModifier
import ru.hollowhorizon.hollowengine.client.ui.scripting.UiClientScript

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

data class UiXmlBuildResult(
    val root: BoxNode,
    val scripts: List<UiClientScript>,
)

@Serializable
data class UiXmlTree(
    val name: String,
    val attributes: Map<String, String> = emptyMap(),
    val children: List<UiXmlTree> = emptyList(),
) {
    companion object
}

fun parseUi(source: String, options: UiXmlOptions = UiXmlOptions()): BoxNode {
    return UiXmlBuilder(options).build(parseUiXml(source))
}

fun buildUi(xml: XmlValue, options: UiXmlOptions = UiXmlOptions()): BoxNode {
    return UiXmlBuilder(options).build(UiXmlTree.from(xml))
}

class UiXmlBuilder(private val options: UiXmlOptions = UiXmlOptions()) {
    fun build(root: UiXmlTree): BoxNode {
        return buildDocument(root).root
    }

    fun buildDocument(root: UiXmlTree): UiXmlBuildResult {
        val imports = linkedMapOf<String, UiXmlTree>()
        val roots = mutableListOf<UiXmlTree>()
        val scripts = mutableListOf<UiClientScript>()
        val documents = if (root.name == DocumentElementName) root.children else listOf(root)
        for (element in documents) {
            if (element.name.equals("import", ignoreCase = true)) {
                val name = element.attributes["named"] ?: element.attributes["name"]
                    ?: throw IllegalArgumentException("UI import requires 'named'")
                val location = element.attributes["element"]
                    ?: throw IllegalArgumentException("UI import '$name' requires 'element'")
                imports[name] = loadImportedElement(location)
            } else if (element.name.equals("script", ignoreCase = true)) {
                val location = element.attributes["from"] ?: element.attributes["src"]
                    ?: throw IllegalArgumentException("UI script requires 'from'")
                scripts += UiClientScript.Resource(location, options.resources.readText(location))
            } else {
                roots += element
            }
        }
        require(roots.size == 1) { "UI document must contain exactly one root element" }
        val root = buildElement(roots.single(), imports)
        val box = root as? BoxNode ?: BoxNode().also { it.children += root }
        if (scripts.isNotEmpty()) {
            box.modifiers += UiClientScriptModifier(scripts)
        }
        return UiXmlBuildResult(box, scripts)
    }

    private fun loadImportedElement(location: String): UiXmlTree {
        val document = parseUiXml(options.resources.readText(location), location)
        val elements = document.children.filterNot { it.name.equals("import", true) }
        require(elements.size == 1) { "Imported UI '$location' must contain exactly one element root" }
        return elements.single()
    }

    private fun buildElement(element: UiXmlTree, imports: Map<String, UiXmlTree>): BaseUiNode {
        imports[element.name]?.let { imported ->
            val merged = imported.copy(
                attributes = imported.attributes + element.attributes,
                children = element.children.ifEmpty { imported.children },
            )
            return buildElement(merged, imports)
        }
        require(!element.name.equals("button", ignoreCase = true)) {
            "UI <button> was removed; use <box> with event handlers and nested <text> instead"
        }

        val attributes = element.attributes
        val modifiers = attributes.toModifiers()
        val customAttributes = attributes.customAttributes()
        val id = attributes["id"]
        val tags = attributes.tags(element.name)
        val node = when (element.name.lowercase()) {
            "box" -> BoxNode(id, tags, modifiers, customAttributes)
            "text" -> TextNode(element.toTextContent(), id, tags, modifiers, customAttributes)
            "image" -> ImageNode(attributes.firstValue("source", "src", "image").bound(), id, tags, modifiers, customAttributes)
            "item" -> ItemNode(attributes.firstValue("item", "value").bound(), id, tags, modifiers, customAttributes)
            "entity" -> EntityNode(attributes.firstValue("entity", "value").bound(), id, tags, modifiers, customAttributes)
            "canvas" -> CanvasNode(attributes["renderer"], id, tags, modifiers, customAttributes)
            else -> BaseUiNode(element.name.lowercase(), id, tags, modifiers, customAttributes).also { node ->
                appendInlineTextIfPresent(node.children, element)
            }
        }

        if (node is TextNode) return node
        element.children.filterNot { it.isTextLiteral() || it.isTextInlineElement() }.forEach { child ->
            node.children += buildElement(child, imports)
        }
        return node
    }

    private fun Map<String, String>.toModifiers(): List<Modifier> {
        val modifiers = mutableListOf<Modifier>()
        for ((rawName, rawValue) in this) {
            val name = rawName.toModifierName()
            when {
                name in StructuralAttributes -> Unit
                name == "style" -> modifiers += Modifier.style(rawValue)
                name.toEventKind() != null -> modifiers += eventModifier(name.toEventKind()!!, rawValue)

                else -> compileStyleModifier(name, rawValue)?.let { modifiers += it }
            }
        }
        return modifiers
    }

    private fun Map<String, String>.customAttributes(): Map<String, String> {
        return filterKeys { rawName ->
            val name = rawName.toModifierName()
            name !in StructuralAttributes &&
                    name != "style" &&
                    name.toEventKind() == null &&
                    compileStyleModifier(name, this.getValue(rawName)) == null
        }
    }

    private fun eventModifier(kind: UiEventKind, rawValue: String): Modifier {
        val trimmed = rawValue.trim()
        if (trimmed.startsWith("{")) {
            return Modifier.emitOn(kind, UiEventPayloadTemplate.parse(trimmed), options.eventSink)
        }
        return Modifier.eventScript(kind, trimmed, options.eventSink)
    }

    private fun appendInlineTextIfPresent(target: UiChildren, element: UiXmlTree) {
        if (element.children.none { it.isTextLiteral() || it.isTextInlineElement() }) return
        val content = element.toTextContent(onlyDirectText = true).trimBoundaryText()
        if (content.asTemplate().isNotBlank()) target += TextNode(content)
    }

    private fun Map<String, String>.firstValue(vararg names: String): String =
        names.firstNotNullOfOrNull { this[it] } ?: ""

    private fun Map<String, String>.tags(elementName: String): List<String> {
        val explicit = listOfNotNull(this["tag"], this["tags"], this["class"])
            .flatMap { it.split(Regex("\\s+")) }
            .filter { it.isNotBlank() }
        return (explicit + elementName.lowercase()).distinct()
    }

    private fun String.toModifierName(): String {
        val result = StringBuilder()
        forEachIndexed { index, char ->
            if (char.isUpperCase()) {
                if (index > 0) result.append('-')
                result.append(char.lowercaseChar())
            } else {
                result.append(char.lowercaseChar())
            }
        }
        return result.toString()
    }

    private fun String.toEventKind(): UiEventKind? = UiEventKind.fromAttribute(this)

    private companion object {
        val StructuralAttributes = setOf(
            "id",
            "tag",
            "tags",
            "class",
            "value",
            XML_TEXT_NODE_NAME,
            "source",
            "src",
            "image",
            "text",
            "item",
            "entity",
            "renderer",
        )

        private const val DocumentElementName = "__document"
    }
}

fun UiXmlTree.Companion.from(value: XmlValue): UiXmlTree {
    return UiXmlTree(
        name = value.name,
        attributes = value.attributes.associate { it.name to it.value.asUiAttributeString() },
        children = value.children.map { UiXmlTree.from(it) },
    )
}

private fun RuntimeValue.asUiAttributeString(): String = convertToString()

private fun UiXmlTree.isTextLiteral(): Boolean = name == XML_TEXT_NODE_NAME

private fun UiXmlTree.toTextContent(
    style: UiInlineStyle = UiInlineStyle(),
    onlyDirectText: Boolean = true,
): UiTextContent {
    attributes["text"]?.let { return UiTextContent.plain(it) }
    val segments = children.flatMap { child ->
        when {
            child.isTextLiteral() -> listOf(UiTextSegment.Text(child.attributes.firstValue(XML_TEXT_VALUE_ATTRIBUTE, "value").bound(), style))
            onlyDirectText && !child.isTextInlineElement() -> emptyList()
            else -> child.toInlineSegments(style)
        }
    }
    return UiTextContent(segments).trimBoundaryText()
}

private fun UiXmlTree.toInlineSegments(style: UiInlineStyle): List<UiTextSegment> {
    val name = name.lowercase()
    return when (name) {
        "span" -> inlineTextOrChildren(style)
        "b", "bold" -> inlineTextOrChildren(style.copy(bold = true))
        "i", "italic" -> inlineTextOrChildren(style.copy(italic = true))
        "u", "underline" -> inlineTextOrChildren(style.copy(underline = true))
        "s", "strike", "strikethrough" -> inlineTextOrChildren(style.copy(strikethrough = true))
        "code" -> inlineTextOrChildren(style.copy(code = true))
        "color" -> inlineTextOrChildren(
            style.copy(color = attributes.firstValue("value", "color").takeIf { it.isNotBlank() }?.let(::parseInlineColor))
        )
        "size" -> inlineTextOrChildren(
            style.copy(fontSize = attributes.firstValue("value", "fontSize", "font-size", "size").parseInlineSize())
        )
        "a", "link" -> inlineTextOrChildren(style.copy(link = attributes.firstValue("href", "to", "value"), underline = true))
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

private fun UiXmlTree.inlineTextOrChildren(style: UiInlineStyle): List<UiTextSegment> {
    val text = attributes.firstValue("text").takeIf { it.isNotEmpty() }
        ?: return inlineChildren(style)
    return listOf(UiTextSegment.Text(text.bound(), style))
}

private fun UiXmlTree.inlineChildren(style: UiInlineStyle): List<UiTextSegment> {
    return children.flatMap { child ->
        if (child.isTextLiteral()) {
            listOf(UiTextSegment.Text(child.attributes.firstValue(XML_TEXT_VALUE_ATTRIBUTE, "value").bound(), style))
        } else {
            child.toInlineSegments(style)
        }
    }
}

private fun UiXmlTree.isTextInlineElement(): Boolean {
    return name.lowercase() in setOf(
        "span",
        "b",
        "bold",
        "i",
        "italic",
        "u",
        "underline",
        "s",
        "strike",
        "strikethrough",
        "code",
        "color",
        "size",
        "a",
        "link",
        "pause",
        "img",
        "image",
    )
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
