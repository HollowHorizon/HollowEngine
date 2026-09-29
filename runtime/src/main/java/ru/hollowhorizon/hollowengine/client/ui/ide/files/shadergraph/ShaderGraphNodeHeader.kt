package ru.hollowhorizon.hollowengine.client.ui.ide.files.shadergraph

import androidx.compose.runtime.Composable
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderGraphNode
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderNodeType
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.tooltipOnHover

/**
 * The title bar in the color of the category: its icon, the name, and the arrow that collapses the node.
 */
@Composable
internal fun NodeHeader(
    node: ShaderGraphNode,
    kind: ShaderNodeType,
    width: Float,
    problem: String?,
    actions: ShaderNodeActions,
) {
    val bar = ShaderNodeLayout.COLLAPSED
    Row(
        tags = listOf("sg-node-head"),
        modifier = Modifier.position(0.px, 0.px).size(width.px, bar.px)
            .alignItems(vertical = UiAlign.CENTER)
            .let { if (problem != null) it.tooltipOnHover(problem) else it },
    ) {
        Image(kind.displayIcon(), tags = listOf("sg-node-icon"))
        Text(kind.title(), tags = listOf("sg-node-title"), modifier = Modifier.size(0.px, UiLength.Auto).grow(1f))
        Box(
            id = "sg-collapse-${node.id}",
            tags = listOf("sg-node-toggle"),
            modifier = Modifier.input(hoverable = true, clickable = true).onPress { event ->
                if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) actions.toggleCollapsed(node.id)
                event.consume()
            },
        ) {
            Image(ArrowIcon, tags = listOf("sg-node-arrow"))
        }
    }
    if (node.collapsed) return
    Box(
        tags = listOf("sg-node-head-flat"),
        modifier = Modifier.position(0.px, (bar - SquaredStrip).px).size(width.px, SquaredStrip.px).inputTransparent(),
    )
    listOf(0f, width - 1f).forEach { x ->
        Box(
            tags = listOf("sg-node-head-edge"),
            modifier = Modifier.position(x.px, (bar - SquaredStrip).px).size(1.px, SquaredStrip.px).inputTransparent(),
        )
    }
    Box(
        tags = listOf("sg-node-divider"),
        modifier = Modifier.position(0.px, bar.px).size(width.px, 1.px).inputTransparent(),
    )
}

private const val ArrowIcon = "hollowengine:textures/gui/icons/graph/arrow.svg"

/** How tall the strip is that squares off the bottom corners of the title bar of an open node. */
private const val SquaredStrip = 8f
