package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.layout.InvalidatingMutableList
import ru.hollowhorizon.hollowengine.client.ui.layout.InvalidatingMutableMap
import ru.hollowhorizon.hollowengine.client.ui.layout.InvalidatingMutableSet
import ru.hollowhorizon.hollowengine.client.ui.layout.UiNodeLayoutState
import ru.hollowhorizon.hollowengine.client.ui.layout.invalidateLayout
import ru.hollowhorizon.hollowengine.client.ui.style.UiBoundString
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextContent
import java.util.*

interface UiNode {
    val type: String
    val id: String?
    var layout: UiLayout
    val tags: MutableSet<String>
    val attributes: MutableMap<String, String>
    val states: MutableSet<UiState>
    val modifiers: MutableList<Modifier>
    val children: UiChildren
    val layoutState: UiNodeLayoutState
}

typealias UiChildren = MutableList<UiNode>

fun UiChildren(): UiChildren = mutableListOf()

open class BaseUiNode(
    final override val type: String,
    final override val id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
    layout: UiLayout = UiLayout.Column,
) : UiNode {
    final override val layoutState = UiNodeLayoutState()
    final override val tags: MutableSet<String> = InvalidatingMutableSet(tags) { invalidateLayout() }
    final override val attributes: MutableMap<String, String> =
        InvalidatingMutableMap(attributes) { invalidateLayout() }
    final override val states: MutableSet<UiState> = InvalidatingMutableSet { invalidateLayout() }
    final override val modifiers: MutableList<Modifier> = InvalidatingMutableList(modifiers) { invalidateLayout() }
    final override val children = UiChildren()
    final override var layout: UiLayout = layout
        set(value) {
            if (field == value) return
            field = value
            invalidateLayout()
        }

    fun add(vararg modifiers: Modifier): BaseUiNode = apply {
        this.modifiers.addAll(modifiers)
        invalidateLayout()
    }

    fun tag(vararg values: String): BaseUiNode = apply {
        tags.addAll(values.map { it.trimTagPrefix() })
        invalidateLayout()
    }

    fun state(vararg values: UiState): BaseUiNode = apply {
        states.addAll(values)
        invalidateLayout()
    }
}

class BoxNode(
    id: String? = null,
    layout: UiLayout = UiLayout.Box(),
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
) : BaseUiNode(
    UiNodeType.BOX.typeName,
    id?.trimIdPrefix(),
    tags.map { it.trimTagPrefix() },
    modifiers,
    attributes,
    layout,
)

class TextNode(
    content: UiTextContent,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
) : BaseUiNode(UiNodeType.TEXT.typeName, id?.trimIdPrefix(), tags.map { it.trimTagPrefix() }, modifiers, attributes) {
    var content: UiTextContent = content
        set(value) {
            if (field == value) return
            field = value
            invalidateLayout()
        }

    constructor(
        text: UiBoundString,
        id: String? = null,
        tags: Iterable<String> = emptyList(),
        modifiers: Iterable<Modifier> = emptyList(),
        attributes: Map<String, String> = emptyMap(),
    ) : this(UiTextContent.plain(text), id, tags, modifiers, attributes)

    var text: UiBoundString
        get() = UiBoundString(content.asTemplate())
        set(value) {
            content = UiTextContent.plain(value)
            invalidateLayout()
        }

    var hoveredLink: String? = null
}

class ImageNode(
    source: UiBoundString,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
) : BaseUiNode(UiNodeType.IMAGE.typeName, id?.trimIdPrefix(), tags.map { it.trimTagPrefix() }, modifiers, attributes) {
    var source: UiBoundString = source
        set(value) {
            if (field == value) return
            field = value
            invalidateLayout()
        }
}

class CanvasNode(
    renderer: String? = null,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
) : BaseUiNode(UiNodeType.CANVAS.typeName, id?.trimIdPrefix(), tags.map { it.trimTagPrefix() }, modifiers, attributes) {
    var renderer: String? = renderer
        set(value) {
            if (field == value) return
            field = value
            invalidateLayout()
        }
}

class ItemNode(
    item: UiBoundString,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
) : BaseUiNode(UiNodeType.ITEM.typeName, id?.trimIdPrefix(), tags.map { it.trimTagPrefix() }, modifiers, attributes) {
    var item: UiBoundString = item
        set(value) {
            if (field == value) return
            field = value
            invalidateLayout()
        }
}

class EntityNode(
    entity: UiBoundString,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
) : BaseUiNode(UiNodeType.ENTITY.typeName, id?.trimIdPrefix(), tags.map { it.trimTagPrefix() }, modifiers, attributes) {
    var entity: UiBoundString = entity
        set(value) {
            if (field == value) return
            field = value
            invalidateLayout()
        }
}

class PopupNode(
    anchor: UiPopupAnchor,
    alignment: UiPopupAlignment = UiPopupAlignment.BelowStart,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
) : BaseUiNode(
    UiNodeType.POPUP.typeName,
    id?.trimIdPrefix(),
    tags.map { it.trimTagPrefix() },
    modifiers,
    attributes,
    UiLayout.Column,
) {
    var anchor: UiPopupAnchor = anchor
        set(value) {
            if (field == value) return
            field = value
            invalidateLayout()
        }

    var alignment: UiPopupAlignment = alignment
        set(value) {
            if (field == value) return
            field = value
            invalidateLayout()
        }
}

fun UiNode.setClosingState(closing: Boolean) {
    val stack = ArrayDeque<UiNode>()
    stack.add(this)
    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        if (closing) {
            node.states += UiState.CLOSING
        } else {
            node.states -= UiState.CLOSING
        }
        node.invalidateLayout()
        for (index in node.children.indices.reversed()) {
            stack.add(node.children[index])
        }
    }
}


private fun String.trimIdPrefix() = removePrefix("#")

private fun String.trimTagPrefix() = removePrefix(".")
