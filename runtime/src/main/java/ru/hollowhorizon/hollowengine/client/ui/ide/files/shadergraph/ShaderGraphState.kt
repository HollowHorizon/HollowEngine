package ru.hollowhorizon.hollowengine.client.ui.ide.files.shadergraph

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderDiagnostic
import ru.hollowhorizon.hollowengine.client.ui.graph.GraphViewState

/** What is selected: some nodes, or one link, by its position among the links. */
internal data class ShaderGraphSelection(val nodes: Set<String> = emptySet(), val link: Int? = null) {
    val single: String? get() = nodes.singleOrNull()

    operator fun contains(node: String): Boolean = node in nodes

    companion object {
        val None = ShaderGraphSelection()

        fun of(node: String) = ShaderGraphSelection(setOf(node))
        fun ofLink(index: Int) = ShaderGraphSelection(link = index)
    }
}

/** What editor of one file keeps while file stays open: view and selection. */
internal class ShaderGraphEditorState {
    val view = GraphViewState()
    var selection by mutableStateOf(ShaderGraphSelection.None)

    /** What is wrong with the graph as it last compiled, for the inspector to list. */
    var diagnostics by mutableStateOf(emptyList<ShaderDiagnostic>())
}

/**
 * A link being drawn from a pin toward pointer: where pointer is on canvas and screen,
 * and where menu opens if it is dropped on nothing.
 */
internal data class PendingLink(
    val node: String,
    val pin: String,
    val fromOutput: Boolean,
    val x: Float,
    val y: Float,
    val screenX: Float,
    val screenY: Float,
)
