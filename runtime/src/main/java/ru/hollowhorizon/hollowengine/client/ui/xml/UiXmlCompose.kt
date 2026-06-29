package ru.hollowhorizon.hollowengine.client.ui.xml

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.hss.compileStyleModifier

@Composable
fun UiXmlContent(root: UiXmlTree, options: UiXmlOptions = UiXmlOptions()) {
    val document = remember(root, options) { UiXmlComposeCompiler(options).compile(root) }

    val rootElement = document.resolve(document.root)
    if (rootElement.name.isLayoutContainer()) {
        UiXmlElement(rootElement, document, listOfNotNull())
    } else {
        Column {
            UiXmlElement(rootElement, document)
        }
    }
}

private class UiXmlComposeCompiler(private val options: UiXmlOptions) {
    fun compile(root: UiXmlTree): UiXmlComposeDocument {
        val imports = linkedMapOf<String, UiXmlTree>()
        val roots = mutableListOf<UiXmlTree>()
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

                else -> roots += element
            }
        }
        require(roots.size == 1) { "UI document must contain exactly one root element" }
        return UiXmlComposeDocument(roots.single(), imports, options)
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
        "box" -> Box(id, attributes.boxMode(), tags, modifier, customAttributes) {
            UiXmlChildren(resolved, document)
        }

        "column" -> Column(id, tags, modifier, customAttributes) {
            UiXmlChildren(resolved, document)
        }

        "lazy-column", "lazycolumn" -> LazyColumn(id, tags, modifier, customAttributes) {
            UiXmlChildren(resolved, document)
        }

        "row" -> Row(id, tags, modifier, customAttributes) {
            UiXmlChildren(resolved, document)
        }

        "lazy-row", "lazyrow" -> LazyRow(id, tags, modifier, customAttributes) {
            UiXmlChildren(resolved, document)
        }

        "text" -> Text(resolved.toTextContent(), id, tags, modifier, customAttributes) {
            UiXmlInlineWidgetChildren(resolved, document)
        }
        "image" -> Image(attributes.firstValue("source", "src", "image"), id, tags, modifier, customAttributes)
        "item" -> Item(attributes.firstValue("item", "value"), id, tags, modifier, customAttributes)
        "entity" -> Entity(attributes.firstValue("entity", "value"), id, tags, modifier, customAttributes)
        "canvas" -> Canvas(attributes["renderer"], id, tags, modifier, customAttributes)
        "popup" -> Popup(
            anchor = attributes.popupAnchor(),
            alignment = attributes.popupAlignment(),
            id = id,
            tags = tags,
            modifier = modifier,
            attributes = customAttributes,
        ) {
            UiXmlChildren(resolved, document)
        }

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
private fun UiXmlInlineWidgetChildren(element: UiXmlTree, document: UiXmlComposeDocument) {
    element.children
        .filterNot { it.isTextLiteral() || it.isTextInlineElement() }
        .forEachIndexed { index, child ->
            key(child.composeKey(index)) {
                UiXmlElement(child.withInlineWidgetId(index), document)
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

private fun String.isLayoutContainer(): Boolean {
    return equals("box", ignoreCase = true) ||
            equals("column", ignoreCase = true) ||
            equals("lazy-column", ignoreCase = true) ||
            equals("lazycolumn", ignoreCase = true) ||
            equals("row", ignoreCase = true) ||
            equals("lazy-row", ignoreCase = true) ||
            equals("lazyrow", ignoreCase = true)
}

private fun Map<String, String>.boxMode(): UiBoxMode {
    return when (firstValue("mode").lowercase()) {
        "", "free" -> UiBoxMode.FREE
        "stack" -> UiBoxMode.STACK
        else -> throw IllegalArgumentException("Unknown box mode '${firstValue("mode")}'")
    }
}

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

private fun Map<String, String>.popupAnchor(): UiPopupAnchor {
    val anchor = firstValue("anchor", "target", "for").ifBlank { "parent" }.trim()
    return when {
        anchor.equals("parent", ignoreCase = true) -> UiPopupAnchor.Parent
        anchor.equals("cursor", ignoreCase = true) -> {
            val x = firstValue("cursor-x", "x")
            val y = firstValue("cursor-y", "y")
            if (x.isBlank() && y.isBlank()) {
                UiPopupAnchor.Cursor()
            } else {
                UiPopupAnchor.Cursor(x.parsePopupFloat(), y.parsePopupFloat())
            }
        }

        else -> UiPopupAnchor.Node(anchor.removePrefix("#"))
    }
}

private fun Map<String, String>.popupAlignment(): UiPopupAlignment {
    val preset = firstValue("placement", "align", "alignment").ifBlank { "below-start" }
    val base = when (preset.lowercase()) {
        "cursor" -> UiPopupAlignment.Cursor
        "above-start" -> UiPopupAlignment(anchorVertical = UiAlign.START, popupVertical = UiAlign.END)
        "above-end" -> UiPopupAlignment(
            anchorHorizontal = UiAlign.END,
            anchorVertical = UiAlign.START,
            popupHorizontal = UiAlign.END,
            popupVertical = UiAlign.END,
        )

        "below-end" -> UiPopupAlignment(anchorHorizontal = UiAlign.END, popupHorizontal = UiAlign.END)
        "right-start" -> UiPopupAlignment(anchorHorizontal = UiAlign.END, anchorVertical = UiAlign.START)
        "left-start" -> UiPopupAlignment(
            anchorHorizontal = UiAlign.START,
            anchorVertical = UiAlign.START,
            popupHorizontal = UiAlign.END,
            popupVertical = UiAlign.START,
        )

        else -> UiPopupAlignment.BelowStart
    }
    return base.copy(
        offsetX = firstValue("offset-x", "offsetX").ifBlank { base.offsetX.toString() }.parsePopupFloat(),
        offsetY = firstValue("offset-y", "offsetY").ifBlank { base.offsetY.toString() }.parsePopupFloat(),
    )
}

private fun String.parsePopupFloat(): Float = trim().removeSuffix("px").toFloatOrNull() ?: 0f

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
    "#text",
    "source",
    "src",
    "image",
    "text",
    "item",
    "entity",
    "renderer",
    "mode",
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
