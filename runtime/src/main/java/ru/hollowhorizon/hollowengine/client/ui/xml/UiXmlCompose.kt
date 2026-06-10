package ru.hollowhorizon.hollowengine.client.ui.xml

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.sunnychung.lib.multiplatform.kotlite.model.XML_TEXT_NODE_NAME
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.hss.compileStyleModifier
import ru.hollowhorizon.hollowengine.client.ui.scripting.UiClientScript

@Composable
fun UiXmlContent(root: UiXmlTree, options: UiXmlOptions = UiXmlOptions()) {
    val document = remember(root, options) { UiXmlComposeCompiler(options).compile(root) }
    val scriptModifier = document.scripts
        .takeIf { it.isNotEmpty() }
        ?.let(::UiClientScriptModifier)
    val rootElement = document.resolve(document.root)
    if (rootElement.name.equals("box", ignoreCase = true)) {
        UiXmlElement(rootElement, document, listOfNotNull(scriptModifier))
    } else {
        Box(modifier = scriptModifier) {
            UiXmlElement(rootElement, document)
        }
    }
}

private class UiXmlComposeCompiler(private val options: UiXmlOptions) {
    fun compile(root: UiXmlTree): UiXmlComposeDocument {
        val imports = linkedMapOf<String, UiXmlTree>()
        val roots = mutableListOf<UiXmlTree>()
        val scripts = mutableListOf<UiClientScript>()
        val documents = if (root.name == DocumentElementName) root.children else listOf(root)
        for (element in documents) {
            when {
                element.name.equals("import", ignoreCase = true) -> {
                    val name = element.attributes["named"] ?: element.attributes["name"]
                        ?: throw IllegalArgumentException("UI import requires 'named'")
                    val location = element.attributes["element"]
                        ?: throw IllegalArgumentException("UI import '$name' requires 'element'")
                    imports[name] = loadImportedElement(location)
                }

                element.name.equals("script", ignoreCase = true) -> {
                    val location = element.attributes["from"] ?: element.attributes["src"]
                        ?: throw IllegalArgumentException("UI script requires 'from'")
                    scripts += UiClientScript.Resource(location, options.resources.readText(location))
                }

                else -> roots += element
            }
        }
        require(roots.size == 1) { "UI document must contain exactly one root element" }
        return UiXmlComposeDocument(roots.single(), imports, scripts, options)
    }

    private fun loadImportedElement(location: String): UiXmlTree {
        val document = parseUiXml(options.resources.readText(location), location)
        val elements = document.children.filterNot { it.name.equals("import", true) }
        require(elements.size == 1) { "Imported UI '$location' must contain exactly one element root" }
        return elements.single()
    }

    private companion object {
        const val DocumentElementName = "__document"
    }
}

private data class UiXmlComposeDocument(
    val root: UiXmlTree,
    val imports: Map<String, UiXmlTree>,
    val scripts: List<UiClientScript>,
    val options: UiXmlOptions,
) {
    fun resolve(element: UiXmlTree): UiXmlTree {
        val imported = imports[element.name] ?: return element
        return imported.copy(
            attributes = imported.attributes + element.attributes,
            children = element.children.ifEmpty { imported.children },
        )
    }
}

@Composable
private fun UiXmlElement(
    element: UiXmlTree,
    document: UiXmlComposeDocument,
    extraModifiers: List<Modifier> = emptyList(),
) {
    val resolved = document.resolve(element)
    require(!resolved.name.equals("button", ignoreCase = true)) {
        "UI <button> was removed; use <box> with event handlers and nested <text> instead"
    }
    val attributes = resolved.attributes
    val modifiers = attributes.toModifiers(document.options) + extraModifiers
    val modifier = modifiers.asModifier()
    val customAttributes = attributes.customAttributes()
    val id = attributes["id"]
    val tags = attributes.tags(resolved.name)
    when (resolved.name.lowercase()) {
        "box" -> Box(id, tags, modifier, customAttributes) {
            UiXmlChildren(resolved, document)
        }

        "text" -> Text(resolved.toTextContent(), id, tags, modifier, customAttributes)
        "image" -> Image(attributes.firstValue("source", "src", "image"), id, tags, modifier, customAttributes)
        "item" -> Item(attributes.firstValue("item", "value"), id, tags, modifier, customAttributes)
        "entity" -> Entity(attributes.firstValue("entity", "value"), id, tags, modifier, customAttributes)
        "canvas" -> Canvas(attributes["renderer"], id, tags, modifier, customAttributes)
        "slider" -> Slider(
            value = attributes.readSliderValue("value", 0f),
            min = attributes.readSliderValue("min", 0f),
            max = attributes.readSliderValue("max", 1f),
            step = attributes.readSliderValue("step", 0f),
            id = id,
            tags = tags,
            modifier = modifier,
            attributes = customAttributes + attributes.onlyWidgetAttributes(SliderAttributes),
        )

        "checkbox" -> Checkbox(
            checked = attributes.readBoolean("checked", attributes.readBoolean("value")),
            variant = UiCheckboxVariant.from(attributes.firstValue("variant", "style", "type")),
            id = id,
            tags = tags,
            modifier = modifier,
            attributes = customAttributes + attributes.onlyWidgetAttributes(CheckboxAttributes),
        )

        "text-field", "textfield", "input", "textarea" -> TextField(
            value = attributes.firstValue("value", "text"),
            mode = attributes.textFieldMode(),
            filter = UiTextInputFilter.from(attributes.firstValue("filter", "input-filter")),
            multiCaret = attributes.readBoolean("multi-caret", attributes.readBoolean("multiCaret")),
            placeholder = attributes.firstValue("placeholder", "hint"),
            id = id,
            tags = tags,
            modifier = modifier,
            attributes = customAttributes + attributes.onlyWidgetAttributes(TextFieldAttributes),
        )

        else -> Element(resolved.name, id, tags, modifier, customAttributes) {
            InlineTextChild(resolved)
            UiXmlChildren(resolved, document)
        }
    }
}

@Composable
private fun UiXmlChildren(element: UiXmlTree, document: UiXmlComposeDocument) {
    element.children
        .filterNot { it.isTextLiteral() || it.isTextInlineElement() }
        .forEachIndexed { index, child ->
            key(child.composeKey(index)) {
                UiXmlElement(child, document)
            }
        }
}

@Composable
private fun InlineTextChild(element: UiXmlTree) {
    if (element.children.none { it.isTextLiteral() || it.isTextInlineElement() }) return
    val content = element.toTextContent(onlyDirectText = true).trimBoundaryText()
    if (content.asTemplate().isNotBlank()) {
        Text(content)
    }
}

private fun UiXmlTree.composeKey(index: Int): String {
    return attributes["id"] ?: "$index:${name.lowercase()}:${attributes["tag"]}:${attributes["tags"]}:${attributes["class"]}"
}

private fun Map<String, String>.toModifiers(options: UiXmlOptions): List<Modifier> {
    val modifiers = mutableListOf<Modifier>()
    for ((rawName, rawValue) in this) {
        val name = rawName.toModifierName()
        when {
            name in StructuralAttributes -> Unit
            name == "style" -> modifiers += Modifier.style(rawValue)
            name.toEventKind() != null -> modifiers += eventModifier(name.toEventKind()!!, rawValue, options)
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
                compileStyleModifier(name, getValue(rawName)) == null
    }
}

private fun eventModifier(kind: UiEventKind, rawValue: String, options: UiXmlOptions): Modifier {
    val trimmed = rawValue.trim()
    if (trimmed.startsWith("{")) {
        return Modifier.emitOn(kind, UiEventPayloadTemplate.parse(trimmed), options.eventSink)
    }
    return Modifier.eventScript(kind, trimmed, options.eventSink)
}

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

private fun List<Modifier>.asModifier(): Modifier? = when (size) {
    0 -> null
    1 -> single()
    else -> Modifier.then(*toTypedArray())
}

private fun Map<String, String>.firstValue(vararg names: String): String {
    return names.firstNotNullOfOrNull { this[it] } ?: ""
}

private fun Map<String, String>.onlyWidgetAttributes(names: Set<String>): Map<String, String> {
    return filterKeys { it in names }
}

private fun Map<String, String>.textFieldMode(): UiTextFieldMode {
    if (readBoolean("multiline") || readBoolean("multi-line")) return UiTextFieldMode.MULTI_LINE
    return UiTextFieldMode.from(firstValue("mode", "multiline", "multi-line"))
}

private fun UiTextSegment.Text.trimStart(): UiTextSegment.Text {
    return copy(value = value.template.trimStart().bound())
}

private fun UiTextSegment.Text.trimEnd(): UiTextSegment.Text {
    return copy(value = value.template.trimEnd().bound())
}

private fun UiTextContent.trimBoundaryText(): UiTextContent {
    val next = segments.toMutableList()
    val firstText = next.indexOfFirst { it is UiTextSegment.Text }
    if (firstText >= 0) next[firstText] = (next[firstText] as UiTextSegment.Text).trimStart()
    val lastText = next.indexOfLast { it is UiTextSegment.Text }
    if (lastText >= 0) next[lastText] = (next[lastText] as UiTextSegment.Text).trimEnd()
    return UiTextContent(next.filterNot { it is UiTextSegment.Text && it.value.template.isEmpty() })
}

private val StructuralAttributes = setOf(
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

private val SliderAttributes = setOf("value", "min", "max", "step")
private val CheckboxAttributes = setOf("checked", "value", "variant", "style", "type")
private val TextFieldAttributes = setOf(
    "value",
    "text",
    "mode",
    "multiline",
    "multi-line",
    "filter",
    "input-filter",
    "multi-caret",
    "multiCaret",
    "placeholder",
    "hint",
)
