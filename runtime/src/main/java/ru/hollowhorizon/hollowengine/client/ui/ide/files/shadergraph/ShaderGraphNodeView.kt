package ru.hollowhorizon.hollowengine.client.ui.ide.files.shadergraph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderGraph
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderGraphTypes
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderType
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.graph.GraphNode
import ru.hollowhorizon.hollowengine.client.ui.graph.GraphNodeGesture
import ru.hollowhorizon.hollowengine.client.ui.graph.GraphViewState

internal const val ShaderGraphCanvasId = "shader-graph-canvas"

/** What a node and its pins do when pressed and dragged. */
internal interface ShaderNodeActions : ShaderFieldActions {
    fun pressNode(node: String, gesture: GraphNodeGesture)
    fun dragNode(node: String, gesture: GraphNodeGesture)
    fun releaseNode(node: String)
    fun nodeMenu(node: String, screenX: Float, screenY: Float)
    fun toggleCollapsed(node: String)
    fun pressPin(pin: ShaderPin, screenX: Float, screenY: Float)
    fun dragPin(canvasX: Float, canvasY: Float, screenX: Float, screenY: Float)
    fun releasePin()
}

/**
 * One node on the canvas, laid out as [box] says: the title, a row per output, option and input, the
 * preview, and the pins on its edges.
 */
@Composable
internal fun ShaderNodeView(
    graph: ShaderGraph,
    box: ShaderNodeBox,
    types: ShaderGraphTypes,
    view: GraphViewState,
    selected: Boolean,
    problem: String?,
    previews: ShaderGraphPreviews,
    actions: ShaderNodeActions,
) {
    val node = box.node
    val kind = box.kind
    GraphNode(
        id = "sg-node-${node.id}",
        canvasId = ShaderGraphCanvasId,
        view = view,
        x = node.x,
        y = node.y,
        width = box.width,
        height = box.height,
        selected = selected,
        tags = listOfNotNull(
            "sg-node",
            "sg-${kind.category.name.lowercase()}",
            "collapsed".takeIf { node.collapsed },
            "error".takeIf { problem != null },
        ),
        onPress = { actions.pressNode(node.id, it) },
        onDrag = { actions.dragNode(node.id, it) },
        onRelease = { actions.releaseNode(node.id) },
        onContextMenu = { x, y -> actions.nodeMenu(node.id, x, y) },
    ) {
        Box(mode = UiBoxMode.STACK, modifier = Modifier.size(100.percent, 100.percent)) {
            NodeHeader(node, kind, box.width, problem, actions)
            box.rows.forEach { row -> NodeRow(graph, box, row, types, actions) }
            if (box.preview > 0f) {
                Box(
                    tags = listOf("sg-node-preview"),
                    modifier = Modifier.position(ShaderNodeLayout.INSET.px, box.previewTop.px)
                        .size(box.preview.px, box.preview.px)
                        .drawBehind(key = node.id) { drawTexture(bounds, { previews.texture(node.id) }, flipY = true) },
                )
            }
            if (!node.collapsed) box.pins.forEach { pin ->
                val type = if (pin.output) types.output(pin.node, pin.name) else types.input(pin.node, pin.name)
                val connected = graph.links.any {
                    if (pin.output) it.from == pin.node && it.output == pin.name else it.to == pin.node && it.input == pin.name
                }
                ShaderPinView(pin, node.x, node.y, type, connected, actions)
            }
        }
    }
}

@Composable
private fun NodeRow(
    graph: ShaderGraph,
    box: ShaderNodeBox,
    row: ShaderNodeRow,
    types: ShaderGraphTypes,
    actions: ShaderNodeActions,
) {
    val node = box.node
    Row(
        tags = listOf("sg-node-row"),
        modifier = Modifier.position(0.px, row.top.px).size(box.width.px, row.height.px)
            .padding(ShaderNodeLayout.INSET.px, 0.px)
            .alignItems(vertical = UiAlign.CENTER),
    ) {
        when (row) {
            is ShaderNodeRow.Output -> {
                Box(modifier = Modifier.size(0.px, 1.px).grow(1f))
                Text(row.name, tags = listOf("sg-pin-label", "output"))
            }

            is ShaderNodeRow.Input -> {
                Text(row.pin.name, tags = listOf("sg-pin-label"))
                val reads = row.pin.fallback?.takeIf { graph.linkInto(node.id, row.pin.name) == null }
                if (reads != null) Text(reads.name.lowercase(), tags = listOf("sg-pin-hint"))
            }

            is ShaderNodeRow.Value -> {
                val values =
                    node.values[row.pin.name]?.takeIf { it.isNotEmpty() } ?: row.pin.default.ifEmpty { listOf(0f) }
                ValueFields(node, row.pin, values, row.components, actions)
            }

            is ShaderNodeRow.Option -> box.kind.option(row.name)
                ?.let { option -> OptionField(graph, node, option, actions) }
        }
    }
}

/** A pin on the edge of its node, in the color of what it carries, filled once something is linked. */
@Composable
private fun ShaderPinView(
    pin: ShaderPin,
    nodeX: Float,
    nodeY: Float,
    type: ShaderType?,
    connected: Boolean,
    actions: ShaderNodeActions,
) {
    val half = ShaderNodeLayout.PIN / 2f
    val held = remember { booleanArrayOf(false) }
    fun canvas(event: UiEvent) = event.ancestorLocalPositions[ShaderGraphCanvasId]
    Box(
        id = "sg-pin-${pin.node}-${if (pin.output) "out" else "in"}-${pin.name}",
        tags = listOfNotNull("sg-pin", type?.tag(), "connected".takeIf { connected }),
        modifier = Modifier.position((pin.x - nodeX - half).px, (pin.y - nodeY - half).px)
            .size(ShaderNodeLayout.PIN.px, ShaderNodeLayout.PIN.px)
            .input(hoverable = true, clickable = true, draggable = true).onPress { event ->
                if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return@onPress
                held[0] = true
                actions.pressPin(pin, event.x, event.y)
                event.consume()
            }.onDrag { event ->
                if (!held[0]) return@onDrag
                canvas(event)?.let { actions.dragPin(it.x, it.y, event.x, event.y) }
                event.consume()
            }.onRelease { event ->
                if (!held[0]) return@onRelease
                held[0] = false
                actions.releasePin()
                event.consume()
            },
    )
}
