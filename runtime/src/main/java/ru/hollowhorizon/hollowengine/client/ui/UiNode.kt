package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.nbt.CompoundTag

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
    final override val layoutState = UiNodeLayoutState(this)
    final override val tags: MutableSet<String> = InvalidatingMutableSet(tags) { invalidateLayout() }
    final override val attributes: MutableMap<String, String> = InvalidatingMutableMap(attributes) { invalidateLayout() }
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
    if (closing) {
        states += UiState.CLOSING
    } else {
        states -= UiState.CLOSING
    }
    invalidateLayout()
    children.forEach { it.setClosingState(closing) }
}

data class UiBindingContext(val root: CompoundTag = CompoundTag()) {
    fun resolve(template: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < template.length) {
            val open = template.indexOf('{', index)
            if (open < 0) {
                result.append(template.substring(index))
                break
            }
            val close = template.indexOf('}', open + 1)
            if (close < 0) {
                result.append(template.substring(index))
                break
            }
            val content = template.substring(open + 1, close)
            result.append(template.substring(index, open))
            if (content.isInlineImageDimension()) {
                result.append(template.substring(open, close + 1))
            } else {
                result.append(readPath(content))
            }
            index = close + 1
        }
        return result.toString()
    }

    private fun readPath(path: String): String {
        val parts = path.split('.').filter { it.isNotBlank() }
        if (parts.isEmpty()) return ""
        var tag = root
        for (part in parts.dropLast(1)) {
            if (!tag.contains(part)) return ""
            tag = tag.getCompound(part)
        }
        val key = parts.last()
        if (!tag.contains(key)) return ""
        return tag.getString(key)
    }

    private fun String.isInlineImageDimension(): Boolean {
        val parts = split(',').map { it.trim() }
        val size = parts.firstOrNull() ?: return false
        val dimensions = size.split('x', 'X').map { it.trim().removeSuffix("px") }
        if (dimensions.size !in 1..2) return false
        if (dimensions.any { it.toFloatOrNull() == null }) return false
        val align = parts.getOrNull(1) ?: return true
        return align in setOf("baseline", "middle", "top", "bottom")
    }
}

fun UiBindingContext.withPointer(x: Float, y: Float): UiBindingContext {
    val next = root.copy()
    val mouse = next.getCompound("mouse").copy()
    mouse.putFloat("x", x)
    mouse.putFloat("y", y)
    next.put("mouse", mouse)
    return UiBindingContext(next)
}

fun UiBindingContext.pointerX(default: Float = 0f): Float {
    if (!root.contains("mouse")) return default
    val mouse = root.getCompound("mouse")
    return if (mouse.contains("x")) mouse.getFloat("x") else default
}

fun UiBindingContext.pointerY(default: Float = 0f): Float {
    if (!root.contains("mouse")) return default
    val mouse = root.getCompound("mouse")
    return if (mouse.contains("y")) mouse.getFloat("y") else default
}

private fun Modifier?.asList(): List<Modifier> = if (this == null) emptyList() else listOf(this)

private fun String.trimIdPrefix() = removePrefix("#")

private fun String.trimTagPrefix() = removePrefix(".")
