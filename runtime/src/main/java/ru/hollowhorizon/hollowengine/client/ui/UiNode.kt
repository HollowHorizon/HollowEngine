package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.nbt.CompoundTag

interface UiNode {
    val type: String
    val id: String?
    val tags: MutableSet<String>
    val attributes: MutableMap<String, String>
    val states: MutableSet<UiState>
    val modifiers: MutableList<Modifier>
    val children: UiChildren
}

typealias UiChildren = MutableList<UiNode>

fun UiChildren(): UiChildren = mutableListOf()

open class BaseUiNode(
    final override val type: String,
    final override val id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
) : UiNode {
    final override val tags: MutableSet<String> = tags.toMutableSet()
    final override val attributes: MutableMap<String, String> = attributes.toMutableMap()
    final override val states: MutableSet<UiState> = mutableSetOf()
    final override val modifiers: MutableList<Modifier> = modifiers.toMutableList()
    final override val children = UiChildren()

    fun add(vararg modifiers: Modifier): BaseUiNode = apply { this.modifiers.addAll(modifiers) }

    fun tag(vararg values: String): BaseUiNode = apply { tags.addAll(values.map { it.trimTagPrefix() }) }

    fun state(vararg values: UiState): BaseUiNode = apply { states.addAll(values) }
}

class BoxNode(
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
) : BaseUiNode(UiNodeType.BOX.typeName, id?.trimIdPrefix(), tags.map { it.trimTagPrefix() }, modifiers, attributes)

class TextNode(
    var content: UiTextContent,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
) : BaseUiNode(UiNodeType.TEXT.typeName, id?.trimIdPrefix(), tags.map { it.trimTagPrefix() }, modifiers, attributes) {
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
        }

    var hoveredLink: String? = null
}

class ImageNode(
    var source: UiBoundString,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
) : BaseUiNode(UiNodeType.IMAGE.typeName, id?.trimIdPrefix(), tags.map { it.trimTagPrefix() }, modifiers, attributes)

class CanvasNode(
    var renderer: String? = null,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
) : BaseUiNode(UiNodeType.CANVAS.typeName, id?.trimIdPrefix(), tags.map { it.trimTagPrefix() }, modifiers, attributes)

class ItemNode(
    var item: UiBoundString,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
) : BaseUiNode(UiNodeType.ITEM.typeName, id?.trimIdPrefix(), tags.map { it.trimTagPrefix() }, modifiers, attributes)

class EntityNode(
    var entity: UiBoundString,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
) : BaseUiNode(UiNodeType.ENTITY.typeName, id?.trimIdPrefix(), tags.map { it.trimTagPrefix() }, modifiers, attributes)

@DslMarker
annotation class HollowUiDsl

@HollowUiDsl
@Deprecated(
    message = "Use Compose UI functions with HollowUiComposition or HollowComposeUiRuntime.",
)
class UiScope(private val target: UiChildren) {
    fun Node(node: UiNode): UiNode {
        target += node
        return node
    }

    fun Box(
        id: String? = null,
        tags: Iterable<String> = emptyList(),
        modifier: Modifier? = null,
        block: UiScope.() -> Unit = {},
    ): BoxNode {
        val node = BoxNode(id, tags, modifier.asList())
        target += node
        UiScope(node.children).block()
        return node
    }

    fun Text(
        value: String,
        id: String? = null,
        tags: Iterable<String> = emptyList(),
        modifier: Modifier? = null,
    ): TextNode {
        val node = TextNode(value.bound(), id, tags, modifier.asList())
        target += node
        return node
    }

    fun Image(
        source: String,
        id: String? = null,
        tags: Iterable<String> = emptyList(),
        modifier: Modifier? = null,
    ): ImageNode {
        val node = ImageNode(source.bound(), id, tags, modifier.asList())
        target += node
        return node
    }

    fun Canvas(
        renderer: String? = null,
        id: String? = null,
        tags: Iterable<String> = emptyList(),
        modifier: Modifier? = null,
    ): CanvasNode {
        val node = CanvasNode(renderer, id, tags, modifier.asList())
        target += node
        return node
    }

    fun Slider(
        value: Float = 0f,
        min: Float = 0f,
        max: Float = 1f,
        step: Float = 0f,
        id: String? = null,
        tags: Iterable<String> = emptyList(),
        modifier: Modifier? = null,
    ): SliderNode {
        val node = SliderNode(value, min, max, step, id, tags, modifier.asList())
        target += node
        return node
    }

    fun Checkbox(
        checked: Boolean = false,
        variant: UiCheckboxVariant = UiCheckboxVariant.CHECKBOX,
        id: String? = null,
        tags: Iterable<String> = emptyList(),
        modifier: Modifier? = null,
    ): CheckboxNode {
        val node = CheckboxNode(checked, variant, id, tags, modifier.asList())
        target += node
        return node
    }

    fun TextField(
        value: String = "",
        mode: UiTextFieldMode = UiTextFieldMode.SINGLE_LINE,
        filter: UiTextInputFilter = UiTextInputFilter.ANY,
        multiCaret: Boolean = false,
        id: String? = null,
        tags: Iterable<String> = emptyList(),
        modifier: Modifier? = null,
    ): TextFieldNode {
        val node = TextFieldNode(value, mode, filter, multiCaret, id, tags, modifier.asList())
        target += node
        return node
    }

    fun Item(
        item: String,
        id: String? = null,
        tags: Iterable<String> = emptyList(),
        modifier: Modifier? = null,
    ): ItemNode {
        val node = ItemNode(item.bound(), id, tags, modifier.asList())
        target += node
        return node
    }

    fun Entity(
        entity: String,
        id: String? = null,
        tags: Iterable<String> = emptyList(),
        modifier: Modifier? = null,
    ): EntityNode {
        val node = EntityNode(entity.bound(), id, tags, modifier.asList())
        target += node
        return node
    }
}

@Deprecated(
    message = "Use Compose UI functions with HollowUiComposition or HollowComposeUiRuntime.",
)
fun HollowUi(
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    block: UiScope.() -> Unit = {},
): BoxNode {
    val root = BoxNode(id, tags, modifier.asList())
    UiScope(root.children).block()
    return root
}

fun UiNode.setClosingState(closing: Boolean) {
    if (closing) {
        states += UiState.CLOSING
    } else {
        states -= UiState.CLOSING
    }
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

private fun Modifier?.asList(): List<Modifier> = if (this == null) emptyList() else listOf(this)

private fun String.trimIdPrefix() = removePrefix("#")

private fun String.trimTagPrefix() = removePrefix(".")
