package ru.hollowhorizon.hollowengine.client.ui.xml

import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.UiEventKind
import ru.hollowhorizon.hollowengine.client.ui.attribute
import ru.hollowhorizon.hollowengine.client.ui.eventScript
import ru.hollowhorizon.hollowengine.client.ui.style
import ru.hollowhorizon.hollowengine.client.ui.style.compileStyleModifier

class UiXmlAttributeRegistry private constructor(
    private val handlers: List<UiXmlAttributeHandler>,
) {
    constructor() : this(defaultUiXmlAttributeHandlers())

    fun resolve(attributes: Map<String, String>, options: UiXmlOptions): UiXmlResolvedAttributes {
        val modifiers = mutableListOf<Modifier>()
        val customAttributes = linkedMapOf<String, String>()

        for ((rawName, value) in attributes) {
            val name = rawName.toUiXmlAttributeName()
            val context = UiXmlAttributeContext(rawName, name, value, options)
            val contribution = handlers.firstNotNullOfOrNull { handler -> handler.resolve(context) }
            if (contribution != null) {
                modifiers += contribution.modifiers
                if (contribution.exposeAsCustomAttribute) customAttributes[rawName] = value
                continue
            }

            modifiers += Modifier.attribute(name, value)
            customAttributes[rawName] = value
        }

        return UiXmlResolvedAttributes(modifiers, customAttributes)
    }

    fun withHandler(handler: UiXmlAttributeHandler): UiXmlAttributeRegistry {
        return UiXmlAttributeRegistry(listOf(handler) + handlers)
    }

    companion object {
        val Default = UiXmlAttributeRegistry()
    }
}

fun interface UiXmlAttributeHandler {
    fun resolve(context: UiXmlAttributeContext): UiXmlAttributeContribution?
}

data class UiXmlAttributeContext(
    val rawName: String,
    val name: String,
    val value: String,
    val options: UiXmlOptions,
)

data class UiXmlAttributeContribution(
    val modifiers: List<Modifier> = emptyList(),
    val exposeAsCustomAttribute: Boolean = false,
) {
    constructor(modifier: Modifier) : this(listOf(modifier))

    companion object {
        val Consumed = UiXmlAttributeContribution()
    }
}

data class UiXmlResolvedAttributes(
    val modifiers: List<Modifier>,
    val customAttributes: Map<String, String>,
)

internal fun String.toUiXmlAttributeName(): String {
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

private fun defaultUiXmlAttributeHandlers(): List<UiXmlAttributeHandler> = listOf(
    UiXmlAttributeHandler { context ->
        if (context.name in StructuralAttributes) UiXmlAttributeContribution.Consumed else null
    },
    UiXmlAttributeHandler { context ->
        if (context.name == "style") UiXmlAttributeContribution(Modifier.style(context.value)) else null
    },
    UiXmlAttributeHandler { context ->
        context.name.toEventKind()?.let { kind ->
            UiXmlAttributeContribution(Modifier.eventScript(kind, context.value.trim(), context.options.eventSink))
        }
    },
    UiXmlAttributeHandler { context ->
        compileStyleModifier(context.name, context.value)?.let(::UiXmlAttributeContribution)
    },
)

private fun String.toEventKind(): UiEventKind? = UiEventKind.fromAttribute(this)

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
    "mode",
    "anchor",
    "anchor-id",
    "placement",
    "align",
    "alignment",
    "offset-x",
    "offset-y",
    "cursor-x",
    "cursor-y",
)
