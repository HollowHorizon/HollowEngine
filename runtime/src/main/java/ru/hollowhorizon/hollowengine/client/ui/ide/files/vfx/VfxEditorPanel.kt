package ru.hollowhorizon.hollowengine.client.ui.ide.files.vfx

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.ide.HollowIdeOpenFile
import ru.hollowhorizon.hollowengine.client.ui.ide.PublishScene
import ru.hollowhorizon.hollowengine.client.ui.ide.SceneTarget
import ru.hollowhorizon.hollowengine.client.ui.ide.files.HollowIdeVfxDocument
import ru.hollowhorizon.hollowengine.client.ui.ide.files.animator.AnimatorColors
import ru.hollowhorizon.hollowengine.client.ui.ide.files.animator.AnimatorIconButton
import ru.hollowhorizon.hollowengine.client.ui.ide.files.animator.AnimatorStylesheet
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ui.PublishTimeline
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ui.TimelineTarget
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ui.hasInspectableSelection
import ru.hollowhorizon.hollowengine.client.ui.inspector.PublishInspector
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.shape.GenericShape
import ru.hollowhorizon.hollowengine.client.ui.style.UiPaint
import ru.hollowhorizon.hollowengine.client.ui.widgets.ContextMenu
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdownItem
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiKeyInput
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTreeItem
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.vfx.VfxNodeSpec
import ru.hollowhorizon.hollowengine.common.vfx.VfxNodeTypes
import kotlin.time.Duration.Companion.milliseconds

private const val AutoSaveDelayMillis = 900L

private const val PlayIcon = "hollowengine:textures/gui/icons/play.svg"
private const val PauseIcon = "hollowengine:textures/gui/icons/pause.svg"
private const val RestartIcon = "hollowengine:textures/gui/icons/reload.svg"
private const val FloorIcon = "hollowengine:textures/gui/icons/layers.svg"
private const val ShapeIcon = "hollowengine:textures/gui/icons/box.svg"
private const val AddIcon = "hollowengine:textures/gui/icons/add.svg"
private const val RemoveIcon = "hollowengine:textures/gui/icons/remove.svg"

/**
 * The editor for a `.vfx` file.
 *
 * The hierarchy goes to the [ru.hollowhorizon.hollowengine.client.ui.ide.SceneDock], curves to the timeline window and the fields to the
 * inspector.
 */
@Composable
internal fun VfxEditorPanel(file: HollowIdeOpenFile) {
    val document = file.document as HollowIdeVfxDocument
    val state = remember(document) { document.editorState { VfxEditorState(document) } }
    val session = state.session

    LaunchedEffect(document.revision) {
        file.updateDirty(document.isModified)
        session.sync()
        if (!document.isModified) return@LaunchedEffect
        delay(AutoSaveDelayMillis.milliseconds)
        if (document.isModified) file.save()
    }

    LaunchedEffect(document) {
        while (isActive) {
            withFrameNanos { frameNanos ->
                state.preview.advanceCamera(frameNanos)
                val rebuilt = state.preview.sync(document.effect, file.path)
                val sought = state.preview.applyPendingSeek()
                val delta = state.preview.deltaSinceLastFrame()
                if (session.timeline.isPlaying && delta > 0f) state.preview.advanceBy(delta)
                if (rebuilt || session.timeline.isPlaying && !sought) {
                    session.timeline.followPlayhead(state.preview.time)
                }
            }
        }
    }

    val selected = state.selected?.takeIf { document.effect.node(it) != null }

    val timelineHasSelection = session.timeline.hasInspectableSelection()
    PublishInspector(source = "vfx-${file.path}", key = selected to timelineHasSelection) {
        when {
            timelineHasSelection -> null
            selected != null -> vfxNodeInspector(document, state, selected)
            else -> vfxEffectInspector(document, state)
        }
    }

    PublishScene(
        source = "vfx-${file.path}",
        key = Triple(document.revision, state.selected, state.expanded.toList()),
    ) {
        sceneTarget(document, state)
    }

    PublishTimeline(source = "vfx-${file.path}", key = file.path) {
        TimelineTarget(
            id = "vfx-${file.path}",
            controller = session.timeline,
            refresh = {
                session.commit()
                session.applyListing()
            },
        )
    }

    Column(
        modifier = Modifier.size(100.percent, 100.percent).style(AnimatorStylesheet).background(AnimatorColors.Canvas)
            .focusScope(),
    ) {
        document.error?.let { message ->
            Text(
                vfxText("broken_file") + " " + message,
                modifier = Modifier.size(100.percent).padding(6.px).fontSize(10f).foreground(AnimatorColors.Muted),
            )
        }

        Box(mode = UiBoxMode.STACK, modifier = Modifier.size(100.percent, 0.px).grow(1f)) {
            Viewport(document, state, selected)
            Toolbar(state)
            ParticleCounter(state.preview)
        }
    }
}

@Composable
private fun Toolbar(state: VfxEditorState) {
    val timeline = state.session.timeline
    val preview = state.preview
    Column(modifier = Modifier.position(8.px, 8.px).gap(4.px)) {
        AnimatorIconButton(
            icon = if (timeline.isPlaying) PauseIcon else PlayIcon,
            tooltip = vfxText(if (timeline.isPlaying) "pause" else "play"),
            active = timeline.isPlaying,
        ) { timeline.isPlaying = !timeline.isPlaying }
        AnimatorIconButton(RestartIcon, vfxText("restart")) {
            preview.restart()
            timeline.followPlayhead(0f)
        }
        AnimatorIconButton(FloorIcon, vfxText("toggle_floor"), active = preview.showFloor) {
            preview.showFloor = !preview.showFloor
        }
        AnimatorIconButton(ShapeIcon, vfxText("toggle_shape"), active = preview.showShape) {
            preview.showShape = !preview.showShape
        }
    }
}

/** What editor is looking at, as opposed to what it is editing. */
internal class VfxEditorState(document: HollowIdeVfxDocument) {
    val preview = VfxPreviewState()
    val session = VfxTimelineSession(document, preview).also { it.timeline.isPlaying = true }

    var selected by mutableStateOf<String?>(null)
        private set
    val expanded = mutableStateListOf<String>()

    /** Which inspector sections are open, kept here so they survive switching between nodes. */
    private val sections = mutableStateMapOf<String, Boolean>()

    /** Where the add-node menu opens from, once scene window has laid its button out. */
    var addAnchor by mutableStateOf(UiRect.Zero)
    var addMenuOpen by mutableStateOf(false)

    /** The gizmo handle being dragged, with node as it was when drag started. */
    var drag: VfxGizmoHandle? = null

    /** What dragged handle is set to right now, drawn beside it. */
    var readout by mutableStateOf<VfxGizmoReadout?>(null)

    fun isSectionOpen(key: String, default: Boolean): Boolean = sections[key] ?: default

    fun toggleSection(key: String, default: Boolean) {
        sections[key] = !(sections[key] ?: default)
    }

    fun select(id: String?) {
        selected = id
        id?.let(session::touch)
        session.timeline.clearSelection()
    }

    fun focusProperty(nodeId: String, property: String) = session.focus(nodeId, property)
}

/** The node tree, as shared scene window shows it. */
private fun sceneTarget(document: HollowIdeVfxDocument, state: VfxEditorState) = SceneTarget(
    id = "vfx-nodes",
    title = vfxText("nodes"),
    items = buildList { appendNodes(document.effect.nodes, state.expanded, state.selected) },
    onSelect = { id -> state.select(id) },
    onToggle = { id -> if (id in state.expanded) state.expanded.remove(id) else state.expanded.add(id) },
    empty = vfxText("no_nodes"),
) {
    SceneActions(document, state)
}

@Composable
private fun SceneActions(document: HollowIdeVfxDocument, state: VfxEditorState) {
    Row(modifier = Modifier.gap(2.px)) {
        Box(modifier = Modifier.onPlaced { state.addAnchor = it }) {
            AnimatorIconButton(AddIcon, vfxText("add_node")) { state.addMenuOpen = true }
        }
        if (state.selected != null) {
            AnimatorIconButton(RemoveIcon, vfxText("remove_node")) {
                state.selected?.let { id ->
                    document.edit { it.withoutNode(id) }
                    state.select(null)
                }
            }
        }
    }

    if (state.addMenuOpen) {
        ContextMenu(
            id = "vfx-add-node",
            anchorBounds = state.addAnchor,
            items = VfxNodeTypes.all.mapNotNull { type ->
                val createDefault = type.createDefault ?: return@mapNotNull null
                UiDropdownItem(type.titleKey.lang) {
                    val created = createDefault()
                    document.edit { effect -> effect.withChild(state.selected, created) }
                    state.select(created.id)
                }
            },
            onExpandedChange = { if (!it) state.addMenuOpen = false },
        )
    }
}

/**
 * The preview itself, and the gizmo over it.
 */
@Composable
private fun Viewport(document: HollowIdeVfxDocument, state: VfxEditorState, selected: String?) {
    val preview = state.preview
    val node = selected?.let { document.effect.node(it) }
    val runtime = selected?.let { preview.instance?.node(it) }
    val driven = selected?.let { vfxDrivenLookup(document, preview, it) } ?: { null }

    val gizmoKey = listOf(
        preview.yaw, preview.pitch, preview.distance, preview.targetX, preview.targetY, preview.targetZ,
        preview.viewportWidth, preview.viewportHeight, preview.time, preview.showShape, preview.revision, node,
    )
    val gizmo = remember(gizmoKey) {
        if (node == null) VfxGizmo.EMPTY else VfxGizmos.build(preview, node, runtime, preview.showShape, driven)
    }

    Box(
        id = "vfx-viewport",
        mode = UiBoxMode.STACK,
        modifier = Modifier.size(100.percent, 100.percent).clip().onPlaced { rect ->
            preview.viewportWidth = rect.width
            preview.viewportHeight = rect.height
        }.input(hoverable = true, draggable = true).cursor(UiCursorShape.HAND).focus()
            .onKeyInput { input -> if (handleHistoryKeys(document, input)) input.consume() }.onPress { event ->
                val left = event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                val handle = if (left) gizmo.handleAt(event.localX, event.localY) else null
                state.drag = handle
                state.readout = null
                if (handle != null) document.beginGesture() else preview.beginCameraDrag()
            }.onDrag { event ->
                val handle = state.drag
                val current = selected?.let { document.effect.node(it) }
                when {
                    handle != null && current != null -> {
                        val fine = event.modifiers and GLFW.GLFW_MOD_SHIFT != 0
                        val snap = event.modifiers and GLFW.GLFW_MOD_CONTROL != 0
                        val distance = VfxGizmos.dragDistance(handle, event.dragTotalX, event.dragTotalY, fine, snap)
                        document.edit { it.withNode(handle.apply(current, distance)) }
                        state.readout = VfxGizmoReadout(handle.x, handle.y, handle.readout(distance))
                    }

                    event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT -> preview.pan(event.dragTotalX, event.dragTotalY)
                    else -> preview.orbit(event.dragTotalX, event.dragTotalY)
                }
                event.consume()
            }.onRelease {
                if (state.drag != null) document.endGesture()
                state.drag = null
                state.readout = null
            }.onScroll { event ->
                preview.zoom(event.scrollY)
                event.consume()
            }.drawBehind(key = preview) {
                drawGl { preview.render(rect, poseStack) }
            },
    ) {
        Box(
            modifier = Modifier.size(100.percent, 100.percent).inputTransparent()
                .drawBehind(key = gizmo to state.drag?.id) { drawGizmo(gizmo, state.drag?.id) },
        )
        state.readout?.let { readout ->
            Text(
                formatNumber(readout.value),
                tags = listOf("vfx-gizmo-readout"),
                modifier = Modifier.position((readout.x + 10f).px, (readout.y - 20f).px).inputTransparent(),
            )
        }
    }
}

private fun handleHistoryKeys(document: HollowIdeVfxDocument, input: UiKeyInput): Boolean {
    if (input.repeat || !input.control) return false
    return when (input.key) {
        GLFW.GLFW_KEY_Z if input.shift -> document.redo()
        GLFW.GLFW_KEY_Z -> document.undo()
        GLFW.GLFW_KEY_Y -> document.redo()
        else -> false
    }
}

/** The number beside the handle being dragged. */
internal class VfxGizmoReadout(val x: Float, val y: Float, val value: Float)

private fun UiCanvasDrawScope.drawGizmo(gizmo: VfxGizmo, active: String?) {
    gizmo.lines.groupBy { it.color }.forEach { (color, lines) ->
        val path = GenericShape {
            lines.forEach { line ->
                moveTo(line.x0, line.y0)
                lineTo(line.x1, line.y1)
            }
        }
        drawShape(path, bounds, UiPaint.Color(color), UiDrawStyle.Stroke(width = 1.4f))
    }
    gizmo.handles.forEach { handle ->
        val half = if (handle.id == active) 5f else 4f
        drawRect(
            UiRect(handle.x - half - 1f, handle.y - half - 1f, half * 2f + 2f, half * 2f + 2f),
            UiPaint.Color(HandleOutline),
            radius = 2f,
        )
        drawRect(
            UiRect(handle.x - half, handle.y - half, half * 2f, half * 2f), UiPaint.Color(handle.color), radius = 1.5f
        )
    }
}

@Composable
private fun ParticleCounter(preview: VfxPreviewState) {
    if (preview.viewportHeight <= 0f) return
    Text(
        vfxText("particles") + ": " + preview.liveParticles,
        modifier = Modifier.position(8.px, (preview.viewportHeight - 18f).px).padding(4.px, 1.px)
            .background(CounterBackground).borderRadius(3f).fontSize(9f).foreground(AnimatorColors.Muted)
            .inputTransparent(),
    )
}

private val HandleOutline = UiColor(0.05f, 0.05f, 0.06f, 0.9f)
private val CounterBackground = UiColor(0.06f, 0.07f, 0.08f, 0.7f)

private fun MutableList<UiTreeItem<Any?>>.appendNodes(
    nodes: List<VfxNodeSpec>,
    expanded: List<String>,
    selected: String?,
    depth: Int = 0,
) {
    nodes.forEach { node ->
        val type = VfxNodeTypes.of(node)
        add(
            UiTreeItem(
                id = node.id,
                label = if (node.enabled) node.name else "${node.name} (${vfxText("off")})",
                depth = depth,
                payload = node,
                icon = type?.icon,
                hasChildren = node.children.isNotEmpty(),
                expanded = node.id in expanded,
                selected = node.id == selected,
            )
        )
        if (node.children.isNotEmpty() && node.id in expanded) {
            appendNodes(node.children, expanded, selected, depth + 1)
        }
    }
}
