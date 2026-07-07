package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextCaret
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextCompletion
import java.util.*

/**
 * Preserves widget runtime state (typed text, caret, slider/checkbox value) across
 * recompositions. Nodes with a stable `id` are keyed by that id, so their state survives
 * even when Compose replaces the node instance (e.g. an IDE editor re-parented by a dock
 * move); nodes without an id fall back to instance identity, which Compose keeps stable
 * for reused slots.
 */
class UiNodeStateStore {
    private val byId = HashMap<String, UiNodePersistentState>()
    private val byInstance = WeakHashMap<UiStatefulNode, UiNodePersistentState>()

    fun clear() {
        byId.clear()
        byInstance.clear()
    }

    fun save(node: UiStatefulNode) {
        val state = node.exportState()
        val key = node.stateKey()
        if (key != null) byId[key] = state else byInstance[node] = state
    }

    fun apply(root: UiNode) {
        root.forEachNode { node ->
            val statefulNode = node as? UiStatefulNode ?: return@forEachNode
            val key = statefulNode.stateKey()
            val state = if (key != null) byId[key] else byInstance[statefulNode]
            state ?: return@forEachNode
            if (state.type != statefulNode.type) return@forEachNode
            statefulNode.importState(state)
        }
    }

    private fun UiStatefulNode.stateKey(): String? = id?.let { "$type#$it" }
}

interface UiStatefulNode : UiNode {
    fun exportState(): UiNodePersistentState

    fun importState(state: UiNodePersistentState)
}

sealed interface UiNodePersistentState {
    val type: String
}

data class SliderPersistentState(
    val value: Float,
) : UiNodePersistentState {
    override val type: String = UiSliderType
}

data class CheckboxPersistentState(
    val checked: Boolean,
) : UiNodePersistentState {
    override val type: String = UiCheckboxType
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
    override val type: String = UiTextFieldType
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
