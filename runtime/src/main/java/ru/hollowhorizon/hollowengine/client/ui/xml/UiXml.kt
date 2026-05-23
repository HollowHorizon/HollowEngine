package ru.hollowhorizon.hollowengine.client.ui.xml

import com.sunnychung.lib.multiplatform.kotlite.model.RuntimeValue
import com.sunnychung.lib.multiplatform.kotlite.model.XmlValue
import kotlinx.serialization.Serializable
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
import ru.hollowhorizon.hollowengine.client.ui.UiEventKind
import ru.hollowhorizon.hollowengine.client.ui.UiEventPayloadTemplate
import ru.hollowhorizon.hollowengine.client.ui.UiEventSink
import ru.hollowhorizon.hollowengine.client.ui.UiClientScriptModifier
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
    return UiXmlBuilder(options).build(UiXmlTree.from(parseUiMarkup(source)))
}

fun buildUi(xml: XmlValue, options: UiXmlOptions = UiXmlOptions()): BoxNode {
    return UiXmlBuilder(options).build(UiXmlTree.from(xml))
}

class UiXmlBuilder(private val options: UiXmlOptions = UiXmlOptions()) {
    fun build(document: UiMarkupDocument): BoxNode {
        return build(UiXmlTree.from(document))
    }

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
        val document = parseUiMarkup(options.resources.readText(location))
        val elements = document.nodes.filterIsInstance<UiMarkupElement>().filterNot { it.name.equals("import", true) }
        require(elements.size == 1) { "Imported UI '$location' must contain exactly one element root" }
        return UiXmlTree.from(elements.single())
    }

    private fun buildElement(element: UiXmlTree, imports: Map<String, UiXmlTree>): BaseUiNode {
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
            "text" -> TextNode(attributes.firstValue("text", "value").bound(), id, tags, modifiers)
            "image" -> ImageNode(attributes.firstValue("source", "src", "image").bound(), id, tags, modifiers)
            "item" -> ItemNode(attributes.firstValue("item", "value").bound(), id, tags, modifiers)
            "entity" -> EntityNode(attributes.firstValue("entity", "value").bound(), id, tags, modifiers)
            "canvas" -> CanvasNode(attributes["renderer"], id, tags, modifiers)
            "button" -> BaseUiNode("button", id, tags, modifiers).also { node ->
                appendTextIfPresent(node.children, attributes.firstValue("text", "value"))
            }

            else -> BaseUiNode(element.name.lowercase(), id, tags, modifiers).also { node ->
                appendTextIfPresent(node.children, attributes.firstValue("text", "value"))
            }
        }

        element.children.forEach { child ->
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

    private fun eventModifier(kind: UiEventKind, rawValue: String): Modifier {
        val trimmed = rawValue.trim()
        if (trimmed.startsWith("{")) {
            return Modifier.emitOn(kind, UiEventPayloadTemplate.parse(trimmed), options.eventSink)
        }
        return Modifier.eventScript(kind, trimmed, options.eventSink)
    }

    private fun appendTextIfPresent(target: UiChildren, text: String) {
        val text = text.trim()
        if (text.isNotEmpty()) target += TextNode(UiBoundString(text))
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

fun UiXmlTree.Companion.from(document: UiMarkupDocument): UiXmlTree {
    val elements = document.nodes.filterIsInstance<UiMarkupElement>().map { UiXmlTree.from(it) }
    return UiXmlTree("__document", children = elements)
}

fun UiXmlTree.Companion.from(element: UiMarkupElement): UiXmlTree {
    val attributes = element.attributes.toMutableMap()
    val text = element.children.filterIsInstance<UiMarkupText>().joinToString("") { it.value }.trim()
    if (text.isNotEmpty() && "text" !in attributes && "value" !in attributes) {
        attributes["text"] = text
    }
    return UiXmlTree(
        name = element.name,
        attributes = attributes,
        children = element.children.filterIsInstance<UiMarkupElement>().map { UiXmlTree.from(it) },
    )
}

fun UiXmlTree.Companion.from(value: XmlValue): UiXmlTree {
    return UiXmlTree(
        name = value.name,
        attributes = value.attributes.associate { it.name to it.value.asUiAttributeString() },
        children = value.children.map { UiXmlTree.from(it) },
    )
}

private fun RuntimeValue.asUiAttributeString(): String = convertToString()
