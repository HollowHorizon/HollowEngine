package ru.hollowhorizon.hollowengine.client.ui.ide.files.shadergraph

import androidx.compose.runtime.*
import com.mojang.blaze3d.systems.RenderSystem
import kotlinx.coroutines.delay
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.shadergraph.*
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.graph.*
import ru.hollowhorizon.hollowengine.client.ui.ide.HollowIdeOpenFile
import ru.hollowhorizon.hollowengine.client.ui.ide.files.HollowIdeShaderGraphDocument
import ru.hollowhorizon.hollowengine.client.ui.inspector.PublishInspector
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.widgets.ContextMenu
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdownItem
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiKeyInput
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.client.vfx.render.VfxGraphMaterials
import kotlin.math.hypot
import kotlin.time.Duration.Companion.milliseconds

internal const val ShaderGraphStylesheet = "hollowengine:ui/styles/shader-graph.hss"
private const val AutoSaveDelayMillis = 900L

/**
 * Editor of material file: nodes with pins on a canvas, links drawn from pin to pin, and
 * what each node computes drawn right on it.
 */
@Composable
internal fun ShaderGraphEditor(file: HollowIdeOpenFile) {
    val document = file.document as HollowIdeShaderGraphDocument
    val state = remember(document) { document.editorState { ShaderGraphEditorState() } }
    val view = state.view
    val previews = remember(document) { ShaderGraphPreviews() }
    DisposableEffect(previews) { onDispose { RenderSystem.recordRenderCall(previews::release) } }

    var link by remember(document) { mutableStateOf<PendingLink?>(null) }
    var menu by remember(document) { mutableStateOf<GraphMenu?>(null) }
    var insertInto by remember(document) { mutableStateOf<Int?>(null) }
    var previewError by remember(document) { mutableStateOf<ShaderDiagnostic?>(null) }
    val dragOrigins = remember(document) { HashMap<String, Pair<Float, Float>>() }

    LaunchedEffect(document.revision) {
        file.updateDirty(document.isModified)
        if (!document.isModified) return@LaunchedEffect
        delay(AutoSaveDelayMillis.milliseconds)
        if (document.isModified) file.save()
    }
    LaunchedEffect(document, document.isModified) {
        if (document.isModified) return@LaunchedEffect
        val location = VfxGraphMaterials.locationOf(file.path)
        RenderSystem.recordRenderCall { VfxGraphMaterials.changed(location) }
    }
    LaunchedEffect(previews) {
        while (true) {
            withFrameNanos { }
            val error = previews.error
            if (error != previewError) previewError = error
        }
    }

    val graph = document.graph
    val code = remember(document.revision) { ShaderGraphCompiler.compile(graph) }
    val diagnostics = code.diagnostics + listOfNotNull(previewError)
    val problems = diagnostics.filter { it.node != null }.groupBy { it.node!! }
    previews.show(graph, document.revision)

    val selection = state.selection
    SideEffect { state.diagnostics = diagnostics }
    PublishInspector(source = "shadergraph-${file.path}", key = selection) {
        shaderGraphInspectorTarget(document, selection, state)
    }

    val boxes = LinkedHashMap<String, ShaderNodeBox>()
    graph.nodes.forEach { node ->
        ShaderNodeTypes.of(node.type)?.let { boxes[node.id] = ShaderNodeLayout.of(graph, node, it, code.types) }
    }
    val pins = boxes.values.flatMap { it.pins }
    val curves = graph.links.mapIndexedNotNull { index, each ->
        val from = pins.firstOrNull { it.node == each.from && it.output && it.name == each.output }
            ?: return@mapIndexedNotNull null
        val to = pins.firstOrNull { it.node == each.to && !it.output && it.name == each.input }
            ?: return@mapIndexedNotNull null
        index to GraphCurves.betweenPins(
            view.toCanvasX(from.x), view.toCanvasY(from.y), view.toCanvasX(to.x), view.toCanvasY(to.y), view.zoom
        )
    }

    fun select(next: ShaderGraphSelection) {
        state.selection = next
    }

    fun pinAt(x: Float, y: Float, outputs: Boolean): ShaderPin? =
        pins.filter { it.output == outputs }.map { it to hypot(view.toCanvasX(it.x) - x, view.toCanvasY(it.y) - y) }
            .filter { it.second <= PIN_REACH * view.zoom.coerceAtLeast(1f) }.minByOrNull { it.second }?.first

    fun nodeAt(x: Float, y: Float): ShaderNodeBox? = boxes.values.lastOrNull { it.rect.toCanvas(view).contains(x, y) }

    /** A node of [kind] added at ([x], [y]) of the graph and selected. */
    fun addNode(kind: ShaderNodeType, x: Float, y: Float): ShaderGraphNode {
        val node = ShaderGraphNode(document.graph.freeNodeId(kind.id), kind.id, x, y)
        document.edit { it.withNode(node) }
        select(ShaderGraphSelection.of(node.id))
        return node
    }

    /**
     * A link dropped on nothing: the add menu, showing only what can take the link, and the node
     * picked from it placed where the link was dropped and linked.
     */
    fun offerNodeFor(pending: PendingLink) {
        val graphX = view.toGraphX(pending.x)
        val graphY = view.toGraphY(pending.y)
        val items = if (pending.fromOutput) {
            val carried = code.types.output(pending.node, pending.pin) ?: ShaderType.FLOAT
            addNodeItems({ it.inputFor(carried) != null }) { kind ->
                val node = addNode(kind, graphX, graphY - ShaderNodeLayout.HEADER)
                kind.inputFor(carried, node)
                    ?.let { pin -> document.edit { it.withLink(pending.node, pending.pin, node.id, pin.name) } }
            }
        } else {
            val into =
                graph.node(pending.node)?.let { node -> ShaderNodeTypes.of(node.type)?.input(node, pending.pin)?.type }
                    ?: return
            addNodeItems({ it.outputFor(into) != null }) { kind ->
                val node = addNode(kind, graphX - ShaderNodeLayout.WIDTH, graphY - ShaderNodeLayout.HEADER)
                kind.outputFor(into)
                    ?.let { output -> document.edit { it.withLink(node.id, output.name, pending.node, pending.pin) } }
            }
        }
        menu = GraphMenu(pending.screenX, pending.screenY, items)
    }

    /**
     * Ends a link being drawn: dropped on a pin, it links to it; on a node, to the first of its pins
     * that takes it; on nothing, it offers the nodes that could.
     */
    fun finishLink() {
        val pending = link ?: return
        link = null
        val pin = pinAt(pending.x, pending.y, outputs = !pending.fromOutput)
        val node = nodeAt(pending.x, pending.y)?.takeIf { it.node.id != pending.node }
        when {
            pin != null -> document.edit {
                if (pending.fromOutput) it.withLink(pending.node, pending.pin, pin.node, pin.name)
                else it.withLink(pin.node, pin.name, pending.node, pending.pin)
            }

            node != null && pending.fromOutput -> {
                val carried = code.types.output(pending.node, pending.pin) ?: ShaderType.FLOAT
                val free = node.kind.inputs(node.node).filter { graph.linkInto(node.node.id, it.name) == null }
                val target =
                    free.firstOrNull { it.type.fixed == carried } ?: free.firstOrNull { it.type.accepts(carried) }
                if (target != null) document.edit { it.withLink(pending.node, pending.pin, node.node.id, target.name) }
            }

            node == null -> offerNodeFor(pending)
        }
        document.endGesture()
    }

    /** Puts [nodeId] into the link at [index]: whatever fed the link feeds the node, and the node feeds where the link went. */
    fun insert(index: Int, nodeId: String) {
        val current = document.graph
        val target = current.links.getOrNull(index) ?: return
        val node = current.node(nodeId) ?: return
        val kind = ShaderNodeTypes.of(node.type) ?: return
        val input = kind.inputFor(code.types.output(target.from, target.output) ?: ShaderType.FLOAT, node) ?: return
        val into = current.node(target.to)?.let { ShaderNodeTypes.of(it.type)?.input(it, target.input)?.type } ?: return
        val output = kind.outputFor(into) ?: return
        document.edit {
            it.withoutLinkAt(index).withLink(target.from, target.output, nodeId, input.name)
                .withLink(nodeId, output.name, target.to, target.input)
        }
    }

    /** Ends a drag of nodes: one dropped on a link goes into it, and the whole drag is one step back. */
    fun finishDrag() {
        if (dragOrigins.isEmpty()) return
        insertInto?.let { index -> dragOrigins.keys.singleOrNull()?.let { insert(index, it) } }
        insertInto = null
        dragOrigins.clear()
        document.endGesture()
    }

    fun deleteSelection() {
        val index = selection.link
        if (index != null) {
            document.edit { it.withoutLinkAt(index) }
        } else {
            val removable = selection.nodes.filter { boxes[it]?.kind?.master == null }.toSet()
            if (removable.isEmpty()) return
            document.edit { it.withoutNodes(removable) }
        }
        select(ShaderGraphSelection.None)
    }

    fun duplicateSelection() {
        val copied = selection.nodes.filter { boxes[it]?.kind?.master == null }.toSet()
        if (copied.isEmpty()) return
        val (next, copies) = document.graph.withDuplicates(copied)
        document.edit { next }
        select(ShaderGraphSelection(copies.toSet()))
    }

    fun handleKey(input: UiKeyInput): Boolean {
        when {
            input.key == GLFW.GLFW_KEY_DELETE -> deleteSelection()
            input.control && input.key == GLFW.GLFW_KEY_D -> duplicateSelection()
            input.control && input.key == GLFW.GLFW_KEY_A -> select(ShaderGraphSelection(boxes.keys.toSet()))
            input.control && input.key == GLFW.GLFW_KEY_Z && !input.shift -> document.undo()
            input.control && (input.key == GLFW.GLFW_KEY_Y || input.shift && input.key == GLFW.GLFW_KEY_Z) -> document.redo()
            input.key == GLFW.GLFW_KEY_ESCAPE -> if (link != null) {
                link = null
                document.endGesture()
            } else {
                select(ShaderGraphSelection.None)
            }

            else -> return false
        }
        return true
    }

    val actions = object : ShaderNodeActions {
        override fun pressNode(node: String, gesture: GraphNodeGesture) {
            menu = null
            when {
                gesture.shift || gesture.control -> {
                    val nodes = if (node in selection) selection.nodes - node else selection.nodes + node
                    select(ShaderGraphSelection(nodes))
                }

                node !in selection -> select(ShaderGraphSelection.of(node))
            }
            dragOrigins.clear()
            state.selection.nodes.forEach { id -> document.graph.node(id)?.let { dragOrigins[id] = it.x to it.y } }
            document.beginGesture()
        }

        override fun dragNode(node: String, gesture: GraphNodeGesture) {
            if (link != null || dragOrigins.isEmpty()) return
            document.edit { graph ->
                graph.withNodesAt(dragOrigins.mapValues { (_, at) -> at.first + gesture.graphDeltaX to at.second + gesture.graphDeltaY })
            }
            val lone = dragOrigins.keys.singleOrNull()
                ?.takeIf { id -> document.graph.links.none { it.from == id || it.to == id } }
            insertInto = lone?.let { GraphCurves.nearest(curves, gesture.canvasX, gesture.canvasY, INSERT_REACH) }
        }

        override fun releaseNode(node: String) = finishDrag()

        override fun nodeMenu(node: String, screenX: Float, screenY: Float) {
            if (node !in selection) select(ShaderGraphSelection.of(node))
            val box = boxes[node] ?: return
            menu = GraphMenu(
                screenX, screenY,
                nodeItems(
                    box.node, box.kind,
                    onPreview = { shown -> document.edit { it.withPreview(node, shown) } },
                    onDuplicate = ::duplicateSelection,
                    onDelete = ::deleteSelection,
                ),
            )
        }

        override fun pressPin(pin: ShaderPin, screenX: Float, screenY: Float) {
            menu = null
            document.beginGesture()
            val x = view.toCanvasX(pin.x)
            val y = view.toCanvasY(pin.y)
            val existing = if (pin.output) null else graph.linkInto(pin.node, pin.name)
            link = when {
                pin.output -> PendingLink(pin.node, pin.name, true, x, y, screenX, screenY)
                existing != null -> {
                    document.edit { it.withoutLinkInto(pin.node, pin.name) }
                    PendingLink(existing.from, existing.output, true, x, y, screenX, screenY)
                }

                else -> PendingLink(pin.node, pin.name, false, x, y, screenX, screenY)
            }
        }

        override fun dragPin(canvasX: Float, canvasY: Float, screenX: Float, screenY: Float) {
            link = link?.copy(x = canvasX, y = canvasY, screenX = screenX, screenY = screenY)
        }

        override fun releasePin() = finishLink()

        override fun setValue(node: String, pin: String, values: List<Float>) =
            document.edit { it.withValue(node, pin, values) }

        override fun setOption(node: String, option: String, value: String) =
            document.edit { it.withOption(node, option, value) }

        override fun openMenu(screenX: Float, screenY: Float, items: List<UiDropdownItem>) {
            menu = GraphMenu(screenX, screenY, items)
        }

        override fun beginGesture() = document.beginGesture()
        override fun endGesture() = document.endGesture()
    }

    Box(
        mode = UiBoxMode.STACK,
        modifier = Modifier.size(100.percent, 100.percent).style(ShaderGraphStylesheet).focusScope()
            .drawBehind(key = previews) { drawGl { previews.render() } },
    ) {
        GraphCanvas(
            id = ShaderGraphCanvasId,
            view = view,
            modifier = Modifier.size(100.percent, 100.percent),
            edges = curves.map { (index, curve) ->
                val each = graph.links[index]
                GraphEdge(
                    key = index,
                    curve = curve,
                    color = code.types.output(each.from, each.output)?.color() ?: GraphLinkColor,
                    selected = selection.link == index || insertInto == index,
                )
            },
            link = link?.let { pending ->
                val pin =
                    pins.firstOrNull { it.node == pending.node && it.name == pending.pin && it.output == pending.fromOutput }
                        ?: return@let null
                val fromX = view.toCanvasX(pin.x)
                val fromY = view.toCanvasY(pin.y)
                val type = if (pending.fromOutput) code.types.output(pin.node, pin.name) else code.types.input(
                    pin.node, pin.name
                )
                val curve =
                    if (pending.fromOutput) GraphCurves.betweenPins(fromX, fromY, pending.x, pending.y, view.zoom)
                    else GraphCurves.betweenPins(pending.x, pending.y, fromX, fromY, view.zoom)
                GraphLinkPreview(fromX, fromY, pending.x, pending.y, type?.color() ?: GraphLinkColor, curve)
            },
            minimap = boxes.values.map { box ->
                GraphMiniMapItem(
                    box.rect, if (box.node.id in selection) GraphSelectedColor else GraphLinkColor, box.kind.title()
                )
            },
            onBackgroundClick = {
                menu = null
                select(ShaderGraphSelection.None)
            },
            onEdgeClick = { index -> select(ShaderGraphSelection.ofLink(index as Int)) },
            onContextMenu = { pointer, edge ->
                menu = if (edge is Int) {
                    select(ShaderGraphSelection.ofLink(edge))
                    GraphMenu(
                        pointer.screenX,
                        pointer.screenY,
                        listOf(UiDropdownItem(graphText("delete_link"), shortcut = "Del") {
                            document.edit { it.withoutLinkAt(edge) }
                            select(ShaderGraphSelection.None)
                        })
                    )
                } else {
                    GraphMenu(
                        pointer.screenX,
                        pointer.screenY,
                        addNodeItems { kind -> addNode(kind, pointer.graphX, pointer.graphY) })
                }
            },
            onRelease = {
                finishLink()
                finishDrag()
            },
            onSelectArea = { area, modifiers ->
                val covered = boxes.values.filter { it.rect.intersects(area) }.map { it.node.id }.toSet()
                val additive = modifiers and (GLFW.GLFW_MOD_SHIFT or GLFW.GLFW_MOD_CONTROL) != 0
                select(ShaderGraphSelection(if (additive) selection.nodes + covered else covered))
            },
            onKey = ::handleKey,
        ) {
            boxes.values.sortedBy { it.node.id in selection }.forEach { box ->
                key(box.node.id) {
                    ShaderNodeView(
                        graph = graph,
                        box = box,
                        types = code.types,
                        view = view,
                        selected = box.node.id in selection,
                        problem = problems[box.node.id]?.joinToString("\n") { problemText(it) },
                        previews = previews,
                        actions = actions,
                    )
                }
            }
        }

        menu?.let { open ->
            ContextMenu(
                id = "shader-graph-menu",
                anchorBounds = UiRect(open.x, open.y, 0f, 0f),
                items = open.items,
                onExpandedChange = { if (!it) menu = null },
            )
        }
    }
}

internal fun graphText(name: String): String = "hollowengine.gui.shadergraph.$name".lang

/** How far from a pin, in canvas pixels at zoom 1, a link can be dropped and still land on it. */
private const val PIN_REACH = 12f

/** How close to a link, in canvas pixels, a lone node has to be dragged to go into it. */
private const val INSERT_REACH = 10f
