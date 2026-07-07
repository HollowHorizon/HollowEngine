package ru.hollowhorizon.hollowengine.client.ui.xml

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextEffect
import ru.hollowhorizon.hollowengine.client.ui.widgets.*

@Composable
fun UiXmlContent(root: UiXmlTree, options: UiXmlOptions = UiXmlOptions()) {
    val anchorBounds = remember(root) { mutableStateMapOf<String, UiRect>() }
    val document = remember(root, options, anchorBounds) { UiXmlComposeCompiler(options, anchorBounds).compile(root) }

    val rootElement = document.resolve(document.root)
    if (rootElement.name.isLayoutContainer()) {
        UiXmlElement(rootElement, document, listOfNotNull())
    } else {
        Column {
            UiXmlElement(rootElement, document)
        }
    }
}

private class UiXmlComposeCompiler(
    private val options: UiXmlOptions,
    private val anchorBounds: MutableMap<String, UiRect>,
) {
    private val imports = linkedMapOf<String, UiXmlTree>()

    fun compile(root: UiXmlTree): UiXmlComposeDocument {
        imports.clear()
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

                element.name.equals("script", ignoreCase = true) -> Unit

                else -> roots += element
            }
        }
        require(roots.size == 1) { "UI document must contain exactly one root element" }
        val rootElement = roots.single()
        return UiXmlComposeDocument(
            rootElement,
            imports,
            options,
            anchorBounds,
            collectPopupAnchorIds(rootElement),
        )
    }

    private fun loadImportedElement(location: String): UiXmlTree {
        val document = parseUiXml(options.resources.readText(location), location)
        val elements = document.children.filterNot { it.name.equals("import", true) || it.name.equals("script", true) }
        require(elements.size == 1) { "Imported UI '$location' must contain exactly one element root" }
        return elements.single()
    }

    private fun collectPopupAnchorIds(element: UiXmlTree): Set<String> {
        val anchors = linkedSetOf<String>()
        collectPopupAnchorIds(element, anchors)
        return anchors
    }

    private fun collectPopupAnchorIds(element: UiXmlTree, anchors: MutableSet<String>) {
        val resolved = resolve(element)
        if (resolved.name.equals("popup", ignoreCase = true)) {
            resolved.attributes.firstValue("anchor", "anchor-id", "anchorId")
                .takeIf { it.isNotBlank() }
                ?.let { anchors += it }
        }
        resolved.children.forEach { child -> collectPopupAnchorIds(child, anchors) }
    }

    private fun resolve(element: UiXmlTree): UiXmlTree {
        val imported = imports[element.name] ?: return element
        return imported.copy(
            attributes = imported.attributes + element.attributes,
            children = element.children.ifEmpty { imported.children },
        )
    }

    private companion object {
        const val DocumentElementName = "__document"
    }
}

private data class UiXmlComposeDocument(
    val root: UiXmlTree,
    val imports: Map<String, UiXmlTree>,
    val options: UiXmlOptions,
    val anchorBounds: MutableMap<String, UiRect>,
    val anchorIds: Set<String>,
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
    val resolvedAttributes = document.options.attributes.resolve(attributes, document.options)
    val modifiers = (resolvedAttributes.modifiers + extraModifiers).toMutableList()
    val customAttributes = resolvedAttributes.customAttributes
    val id = attributes["id"]
    modifiers.trackXmlAnchor(id, document)
    val modifier = CompositeModifier(modifiers)
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

        "text" -> Text(id, tags, modifier, customAttributes) {
            UiXmlInlineContent(resolved, emptyList(), document, inlineWidgets = true)
        }

        "image" -> Image(attributes.firstValue("source", "src", "image"), id, tags, modifier, customAttributes)
        "item" -> Item(attributes.firstValue("item", "value"), id, tags, modifier, customAttributes)
        "entity" -> Entity(attributes.firstValue("entity", "value"), id, tags, modifier, customAttributes)
        "popup" -> Popup(
            anchorBounds = attributes.popupAnchorBounds(document, LocalPointer.current),
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
            InlineTextChild(resolved, document)
            UiXmlChildren(resolved, document)
        }
    }
}

private fun MutableList<Modifier>.trackXmlAnchor(id: String?, document: UiXmlComposeDocument) {
    if (id == null || id !in document.anchorIds) return
    add(Modifier.onPlaced { bounds -> document.anchorBounds[id] = bounds })
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

/**
 * Emits an element's inline text as [Span]s (with accumulated [effects]), inline `<img>` as
 * [Image] atoms and, when [inlineWidgets] is set (inside `<text>`), any block child as an inline
 * widget flowing in the line. Inline elements recurse, accumulating their effects onto each span.
 */
@Composable
private fun UiXmlInlineContent(
    element: UiXmlTree,
    effects: List<UiTextEffect>,
    document: UiXmlComposeDocument,
    inlineWidgets: Boolean,
) {
    element.attributes["text"]?.let { literal ->
        if (literal.isNotEmpty()) Span(literal, modifier = effects.asTextModifier())
        return
    }
    element.children.forEachIndexed { index, child ->
        key(child.composeKey(index)) {
            when {
                child.isTextLiteral() -> {
                    val text = child.textLiteral()
                    if (text.isNotEmpty()) Span(text, modifier = effects.asTextModifier())
                }

                child.isInlineBreak() -> Span("\n", modifier = effects.asTextModifier())
                child.isInlinePause() -> Unit
                child.isInlineImage() -> UiXmlInlineImage(child)
                child.isTextInlineElement() ->
                    UiXmlInlineContent(child, effects + child.inlineTagEffects(), document, inlineWidgets)

                inlineWidgets -> UiXmlElement(child.withInlineWidgetId(index), document)
            }
        }
    }
}

@Composable
private fun UiXmlInlineImage(element: UiXmlTree) {
    val image = element.inlineImage()
    Image(
        image.source,
        modifier = Modifier.size(image.width.px, image.height.px).align(vertical = image.align),
    )
}

private fun List<UiTextEffect>.asTextModifier(): Modifier? =
    if (isEmpty()) null else Modifier.textEffects(*toTypedArray())

@Composable
private fun InlineTextChild(element: UiXmlTree, document: UiXmlComposeDocument) {
    if (element.children.none { it.isTextLiteral() || it.isTextInlineElement() }) return
    Text {
        UiXmlInlineContent(element, emptyList(), document, inlineWidgets = false)
    }
}

private fun UiXmlTree.composeKey(index: Int): String {
    return attributes["id"]
        ?: "$index:${name.lowercase()}:${attributes["tag"]}:${attributes["tags"]}:${attributes["class"]}"
}

private fun Map<String, String>.tags(elementName: String): List<String> {
    val explicit = listOfNotNull(this["tag"], this["tags"], this["class"])
        .flatMap { it.split(Regex("\\s+")) }
        .filter { it.isNotBlank() }
    return (explicit + elementName.lowercase()).distinct()
}

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

private fun Map<String, String>.popupAnchorBounds(document: UiXmlComposeDocument, pointer: UiPointer): UiRect {
    val anchorId = firstValue("anchor", "anchor-id", "anchorId")
    if (anchorId.isNotBlank()) return document.anchorBounds[anchorId] ?: UiRect.Zero

    val x = firstValue("cursor-x", "x")
    val y = firstValue("cursor-y", "y")
    val px = if (x.isBlank()) pointer.x else x.parsePopupFloat()
    val py = if (y.isBlank()) pointer.y else y.parsePopupFloat()
    return UiRect(px, py, 0f, 0f)
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
