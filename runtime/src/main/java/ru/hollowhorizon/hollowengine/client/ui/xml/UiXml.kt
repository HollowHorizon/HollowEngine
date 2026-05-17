package ru.hollowhorizon.hollowengine.client.ui.xml

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.ui.HollowUiResourceAccess
import ru.hollowhorizon.hollowengine.client.ui.BaseUiNode
import ru.hollowhorizon.hollowengine.client.ui.BoxNode
import ru.hollowhorizon.hollowengine.client.ui.CanvasNode
import ru.hollowhorizon.hollowengine.client.ui.EntityNode
import ru.hollowhorizon.hollowengine.client.ui.ImageNode
import ru.hollowhorizon.hollowengine.client.ui.ItemNode
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.TextNode
import ru.hollowhorizon.hollowengine.client.ui.UiBoundString
import ru.hollowhorizon.hollowengine.client.ui.UiChildren
import ru.hollowhorizon.hollowengine.client.ui.UiEventPayloadTemplate
import ru.hollowhorizon.hollowengine.client.ui.UiEventSink
import ru.hollowhorizon.hollowengine.client.ui.bound
import ru.hollowhorizon.hollowengine.client.ui.hss.compileStyleModifier

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

fun parseUi(source: String, options: UiXmlOptions = UiXmlOptions()): BoxNode {
    return UiXmlBuilder(options).build(parseUiMarkup(source))
}

class UiXmlBuilder(private val options: UiXmlOptions = UiXmlOptions()) {
    fun build(document: UiMarkupDocument): BoxNode {
        val imports = linkedMapOf<String, UiMarkupElement>()
        val roots = mutableListOf<UiMarkupElement>()
        for (node in document.nodes) {
            val element = node as? UiMarkupElement ?: continue
            if (element.name.equals("import", ignoreCase = true)) {
                val name = element.attributes["named"] ?: element.attributes["name"]
                    ?: throw IllegalArgumentException("UI import requires 'named'")
                val location = element.attributes["element"]
                    ?: throw IllegalArgumentException("UI import '$name' requires 'element'")
                imports[name] = loadImportedElement(location)
            } else {
                roots += element
            }
        }
        require(roots.size == 1) { "UI document must contain exactly one root element" }
        val root = buildElement(roots.single(), imports)
        return root as? BoxNode ?: BoxNode().also { it.children += root }
    }

    private fun loadImportedElement(location: String): UiMarkupElement {
        val document = parseUiMarkup(options.resources.readText(location))
        val elements = document.nodes.filterIsInstance<UiMarkupElement>().filterNot { it.name.equals("import", true) }
        require(elements.size == 1) { "Imported UI '$location' must contain exactly one element root" }
        return elements.single()
    }

    private fun buildElement(element: UiMarkupElement, imports: Map<String, UiMarkupElement>): BaseUiNode {
        imports[element.name]?.let { imported ->
            val merged = imported.copy(
                attributes = imported.attributes + element.attributes,
                children = element.children.ifEmpty { imported.children },
            )
            return buildElement(merged, imports)
        }

        val attributes = element.attributes
        val modifiers = attributes.toModifiers()
        val id = attributes["id"]
        val tags = attributes.tags(element.name)
        val node = when (element.name.lowercase()) {
            "box" -> BoxNode(id, tags, modifiers)
            "text" -> TextNode(attributes["value"].orText(element).bound(), id, tags, modifiers)
            "image" -> ImageNode(attributes.firstValue("source", "src", "image").bound(), id, tags, modifiers)
            "item" -> ItemNode(attributes.firstValue("item", "value").bound(), id, tags, modifiers)
            "entity" -> EntityNode(attributes.firstValue("entity", "value").bound(), id, tags, modifiers)
            "canvas" -> CanvasNode(attributes["renderer"], id, tags, modifiers)
            "button" -> BaseUiNode("button", id, tags, modifiers).also { node ->
                appendTextIfPresent(node.children, element)
            }

            else -> BaseUiNode(element.name.lowercase(), id, tags, modifiers).also { node ->
                appendTextIfPresent(node.children, element)
            }
        }

        element.children.filterIsInstance<UiMarkupElement>().forEach { child ->
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
                name == "on-click" -> modifiers += Modifier.emitOnClick(
                    UiEventPayloadTemplate.parse(rawValue),
                    options.eventSink,
                )

                name == "on-drag" -> modifiers += Modifier.emitOnDrag(
                    UiEventPayloadTemplate.parse(rawValue),
                    options.eventSink,
                )

                else -> compileStyleModifier(name, rawValue)?.let { modifiers += it }
            }
        }
        return modifiers
    }

    private fun appendTextIfPresent(target: UiChildren, element: UiMarkupElement) {
        val text = element.textContent().trim()
        if (text.isNotEmpty()) target += TextNode(UiBoundString(text))
    }

    private fun UiMarkupElement.textContent(): String {
        return children.filterIsInstance<UiMarkupText>().joinToString("") { it.value }
    }

    private fun String?.orText(element: UiMarkupElement): String = this ?: element.textContent().trim()

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

    private companion object {
        val StructuralAttributes = setOf(
            "id",
            "tag",
            "tags",
            "class",
            "value",
            "source",
            "src",
            "image",
            "item",
            "entity",
            "renderer",
        )
    }
}
