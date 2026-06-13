package ru.hollowhorizon.hollowengine.client.ui

class UiNodeStateStore {
    private val states = linkedMapOf<String, UiNodePersistentState>()

    fun clear() {
        states.clear()
    }

    fun save(node: UiNode) {
        val stateful = node as? UiStatefulNode ?: return
        states[UiNodeKeys.key(node)] = stateful.exportState()
    }

    fun apply(root: UiNode) {
        root.forEachNode { node ->
            val state = states[UiNodeKeys.key(node)] ?: return@forEachNode
            (node as? UiStatefulNode)?.importState(state)
        }
    }
}

interface UiStatefulNode {
    fun exportState(): UiNodePersistentState

    fun importState(state: UiNodePersistentState)
}

sealed interface UiNodePersistentState {
    val type: String
}

data class SliderPersistentState(
    val value: Float,
) : UiNodePersistentState {
    override val type: String = UiNodeType.SLIDER.typeName
}

data class CheckboxPersistentState(
    val checked: Boolean,
) : UiNodePersistentState {
    override val type: String = UiNodeType.CHECKBOX.typeName
}

data class TextFieldPersistentState(
    val value: String,
    val caret: Int,
    val selectionAnchor: Int?,
    val carets: List<Int>,
    val caretRanges: List<UiTextCaret> = emptyList(),
    val caretVisibilityRevision: Long = 0L,
) : UiNodePersistentState {
    override val type: String = UiNodeType.TEXT_FIELD.typeName
}

private fun UiNode.forEachNode(block: (UiNode) -> Unit) {
    block(this)
    children.forEach { it.forEachNode(block) }
}
