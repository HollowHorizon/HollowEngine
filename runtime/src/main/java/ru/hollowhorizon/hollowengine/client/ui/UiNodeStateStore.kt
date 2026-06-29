package ru.hollowhorizon.hollowengine.client.ui

import java.util.*

class UiNodeStateStore {
    private val states = linkedMapOf<String, UiNodePersistentState>()

    fun clear() {
        states.clear()
    }

    fun save(node: UiStatefulNode) {
        states[UiNodeKeys.key(node)] = node.exportState()
    }

    fun apply(root: UiNode) {
        root.forEachNode { node ->
            val state = states[UiNodeKeys.key(node)] ?: return@forEachNode
            (node as? UiStatefulNode)?.importState(state)
        }
    }
}

interface UiStatefulNode: UiNode {
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

data class TextFieldHistoryState(
    val value: String,
    val caret: Int,
    val selectionAnchor: Int?,
    val caretRanges: List<UiTextCaret>,
)

data class TextFieldPersistentState(
    val value: String,
    val caret: Int,
    val selectionAnchor: Int?,
    val carets: List<Int>,
    val caretRanges: List<UiTextCaret> = emptyList(),
    val caretVisibilityRevision: Long = 0L,
    val completionItems: List<UiTextCompletion> = emptyList(),
    val completionActive: Boolean = false,
    val completionAutoOpenPending: Boolean = false,
    val completionAnchor: Int = value.length,
    val completionSelectedIndex: Int = 0,
    val completionReplacementStart: Int = value.length,
    val completionReplacementEnd: Int = value.length,
    val completionLineStart: Int = 0,
    val completionLineEnd: Int = value.length,
    val undoHistory: List<TextFieldHistoryState> = emptyList(),
    val redoHistory: List<TextFieldHistoryState> = emptyList(),
) : UiNodePersistentState {
    override val type: String = UiNodeType.TEXT_FIELD.typeName
}

private fun UiNode.forEachNode(block: (UiNode) -> Unit) {
    val stack = ArrayDeque<UiNode>()
    stack.add(this)
    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        block(node)
        for (index in node.children.indices.reversed()) {
            stack.add(node.children[index])
        }
    }
}
