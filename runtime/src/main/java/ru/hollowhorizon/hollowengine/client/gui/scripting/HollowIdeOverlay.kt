package ru.hollowhorizon.hollowengine.client.gui.scripting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.docking.*
import ru.hollowhorizon.hollowengine.client.ui.render.MinecraftUiRenderer
import ru.hollowhorizon.hollowengine.client.ui.render.UiRenderTarget
import ru.hollowhorizon.hollowengine.client.ui.widgets.*
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.config.EditMode
import ru.hollowhorizon.hollowengine.common.config.HollowEngineConfig
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderTickEvent
import ru.hollowhorizon.hollowengine.common.util.PlayerPermissions
import ru.hollowhorizon.hollowengine.common.utils.openUrl

internal const val ProjectTreeId = "ide-project-tree"
internal const val EditorWelcomeId = "ide-code-editor"
internal const val LogoIcon = "hollowengine:textures/gui/logo/logo.svg"
internal const val CodeIcon = "hollowengine:textures/gui/icons/code_editor.svg"
internal const val ProjectIcon = "hollowengine:textures/gui/icons/folder.svg"
internal const val SearchIcon = "hollowengine:textures/gui/icons/search.svg"
private const val ToolbarHeight = 32f
private const val StylesheetCheckIntervalMillis = 250L
private const val NanosPerMillisecond = 1_000_000L
private const val MinEditorFontSize = 6f
private const val MaxEditorFontSize = 36f

@ClientOnly
object HollowIdeOverlay {
    var useHollowUiOverlay: Boolean = true

    private val model = HollowIdeModel()
    private val dock = DockingState()
    private val surface = HollowUiSurface()
    private val renderer = MinecraftUiRenderer()
    private val input = HollowUiInputController()
    private var initialized = false
    private var activeButton: Int? = null
    private var lastFrame: HollowUiFrame? = null
    private var uiDirty = true
    private var lastWidth = -1
    private var lastHeight = -1
    private var lastFrameMouseX = Float.NaN
    private var lastFrameMouseY = Float.NaN
    private var lastStylesheetRevision = 0L
    private var nextStylesheetCheckMillis = 0L
    private var lastMouseX = 0f
    private var lastMouseY = 0f
    private var collapsed by mutableStateOf(true)
    private var filter by mutableStateOf("")
    private var openDropdown by mutableStateOf<String?>(null)
    private var statusText by mutableStateOf("")
    private val diagnosticsPanels = mutableStateMapOf<String, Boolean>()
    private val diagnosticsPanelHeights = mutableStateMapOf<String, Float>()
    private var editorFontSize by mutableStateOf(HollowEngineConfig.ideEditorFontSize)
    private var editorAnalysisRevision by mutableStateOf(0)
    private val editorSessions = mutableMapOf<String, HollowIdeEditorSession>()
    private val editorOverlays = HollowIdeEditorOverlays(input, ::setScrollImmediate) { invalidateUi() }

    fun isVisible(): Boolean = useHollowUiOverlay && isAvailable()

    fun isMouseOver(x: Float, y: Float): Boolean {
        if (!isVisible()) return false
        val point = hollowIdeOverlayPoint(x, y)
        return if (collapsed) point.x <= 44f && point.y <= ToolbarHeight else true
    }

    fun hasFocusedInput(): Boolean = isVisible() && input.focusedKey != null

    fun handleMouseMove(x: Float, y: Float): Boolean {
        if (!isVisible()) return false
        val point = hollowIdeOverlayPoint(x, y)
        if (activeButton == null) {
            lastMouseX = point.x
            lastMouseY = point.y
            val frame = lastFrame ?: currentFrameForInput() ?: return false
            val hoverChanged = input.updateHover(frame, point.x, point.y, ::dispatchUiEvent)
            if (hoverChanged || editorOverlays.needsPointerUpdate(frame)) invalidateUi()
            return false
        }
        val frame = lastFrame ?: currentFrameForInput() ?: return false
        val deltaX = point.x - lastMouseX
        val deltaY = point.y - lastMouseY
        lastMouseX = point.x
        lastMouseY = point.y
        val scrollbarResult = input.scrollbarMouseDragged(frame, point.x, point.y, ::setScrollImmediate)
        if (scrollbarResult.handled) {
            invalidateUi()
            return true
        }
        val result = input.mouseDragged(frame, point.x, point.y, activeButton ?: 0, deltaX, deltaY, ::dispatchUiEvent)
        if (result.handled) invalidateUi()
        return result.handled || input.hasScrollbarDrag()
    }

    fun handleMouseButton(x: Float, y: Float, button: Int, action: Int, modifiers: Int): Boolean {
        if (!isVisible()) return false
        val point = hollowIdeOverlayPoint(x, y)
        val frame = currentFrameForInput() ?: return false
        return when (action) {
            GLFW.GLFW_PRESS -> mousePressed(frame, point.x, point.y, button)
            GLFW.GLFW_RELEASE -> mouseReleased(frame, point.x, point.y, button)
            else -> false
        }
    }

    fun handleMouseScroll(x: Float, y: Float, scrollX: Double, scrollY: Double): Boolean {
        if (!isVisible()) return false
        val point = hollowIdeOverlayPoint(x, y)
        val frame = currentFrameForInput() ?: return false
        editorOverlays.completionScrollTargetAt(frame, point.x, point.y)?.let { target ->
            val range = frame.layout[target].scrollRange
            val delta = scrollWheelDelta(range, scrollX, scrollY, hollowIdeHorizontalScrollModifierDown())
            surface.scroll(target, delta.x * 32f, delta.y * 32f)
            invalidateUi()
            return true
        }
        val target = input.scrollTargetAt(frame, point.x, point.y) ?: return false
        if (target is TextFieldNode && hollowIdeControlModifierDown()) {
            val editorId = target.id?.removePrefix("editor-")
            if (editorId != null && editorId != target.id) {
                val current = editorFontSize
                val next = (current + scrollY.toFloat()).coerceIn(MinEditorFontSize, MaxEditorFontSize)
                if (current != next) {
                    editorFontSize = next
                    HollowEngineConfig.ideEditorFontSize = next
                    invalidateUi()
                    return true
                }
            } else {
                invalidateUi()
                return true
            }
        }
        val range = frame.layout[target].scrollRange
        val delta = scrollWheelDelta(range, scrollX, scrollY, hollowIdeHorizontalScrollModifierDown())
        val event = UiEvent(
            kind = UiEventKind.SCROLL,
            node = target,
            x = point.x,
            y = point.y,
            scrollX = delta.x,
            scrollY = delta.y,
        )
        if (dispatchUiEvent(event) && event.consumed) {
            invalidateUi()
            return true
        }
        surface.scroll(target, delta.x * 32f, delta.y * 32f)
        invalidateUi()
        return true
    }

    fun handleKey(key: Int, scanCode: Int, action: Int, modifiers: Int): Boolean {
        if (!isVisible()) return false
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) return hasFocusedInput()
        val frame = currentFrameForInput() ?: return false
        val result = input.keyPressed(frame, key, scanCode, modifiers, ::dispatchUiEvent)
        if (result.handled) {
            editorOverlays.update(frame, lastMouseX, lastMouseY)
            invalidateUi()
        }
        return result.handled
    }

    fun handleChar(codePoint: Int, modifiers: Int): Boolean {
        if (!isVisible()) return false
        val frame = currentFrameForInput() ?: return false
        val result = input.charTyped(frame, codePoint.toChar(), modifiers, ::dispatchUiEvent)
        if (result.handled) {
            editorOverlays.update(frame, lastMouseX, lastMouseY)
            invalidateUi()
        }
        return result.handled
    }

    @SubscribeEvent
    fun render(event: RenderTickEvent.Blit) {
        if (!isVisible()) return
        renderOverlay(currentBlitTarget())
    }

    private fun initialize() {
        if (initialized) return
        initialized = true
        dock.open(DockItem(ProjectTreeId, "hollowengine.gui.ide.project_tree".lang, ProjectIcon, closable = false))
        val projectStack = dock.stackIdOf(ProjectTreeId)
        dock.open(
            DockItem(EditorWelcomeId, "Code Editor", CodeIcon, closable = false),
            DockTarget(projectStack, DockPlacement.RIGHT),
        )
        (dock.root as? DockNode.Split)?.let { split ->
            dock.setSplitFraction(split.id, 0.28f)
        }
        surface.setContent { Content() }
    }

    @Composable
    private fun Content() {
        Box(
            id = "ide-root",
            modifier = Modifier.then(
                Modifier.style("hollowengine:ui/styles/ide.hss"),
                Modifier.style("hollowengine:ui/styles/widgets.hss"),
                Modifier.size(100.percent, 100.percent),
            ),
        ) {
            if (collapsed) {
                GearButton()
            } else {
                Column(modifier = Modifier.size(100.percent, 100.percent)) {
                    Toolbar()
                    DockSpace(
                        state = dock,
                        id = "ide-dock",
                        modifier = Modifier.then(
                            Modifier.size(100.percent, 0.px),
                            Modifier.grow(1f),
                        ),
                        content = { item -> DockContent(item) },
                    )
                }
            }
        }
    }

    @Composable
    private fun GearButton() {
        Box(
            id = "ide-logo",
            modifier = Modifier.then(
                Modifier.input(hoverable = true, clickable = true),
                Modifier.cursor(UiCursorShape.HAND),
                Modifier.onClick { event ->
                    collapsed = !collapsed
                    openDropdown = null
                    event.consume()
                },
            ),
        ) {
            Image(LogoIcon, tags = listOf("ide-logo-icon"))
        }
    }

    @Composable
    private fun Toolbar() {
        Row(
            id = "ide-toolbar",
            modifier = Modifier.alignItems(vertical = UiAlign.CENTER),
        ) {
            GearButton()
            ToolbarMenus()
            Box(modifier = Modifier.then(Modifier.size(0.px, 100.percent), Modifier.grow(1f)))
            OpenFilesDropdown()
        }
    }

    @Composable
    private fun ToolbarMenus() {
        UiDropdown(
            id = "ide-file-menu",
            label = "hollowengine.gui.ide.file".lang,
            expanded = openDropdown == "file",
            onExpandedChange = { openDropdown = if (it) "file" else null },
            items = hollowIdeFileMenuItems(model, dock, ::focusedFile),
        )
        UiDropdown(
            id = "ide-windows-menu",
            label = "hollowengine.gui.ide.windows".lang,
            expanded = openDropdown == "windows",
            onExpandedChange = { openDropdown = if (it) "windows" else null },
            items = hollowIdeWindowMenuItems(model, dock),
        )
        if (Minecraft.getInstance().player?.hasPermissions(PlayerPermissions.GAMEMASTER) == true) {
            UiDropdown(
                id = "ide-tools-menu",
                label = "hollowengine.gui.ide.tools".lang,
                expanded = openDropdown == "tools",
                onExpandedChange = { openDropdown = if (it) "tools" else null },
                items = hollowIdeToolMenuItems(),
            )
        }
        UiDropdown(
            id = "ide-help-menu",
            label = "hollowengine.gui.ide.help".lang,
            expanded = openDropdown == "help",
            onExpandedChange = { openDropdown = if (it) "help" else null },
            items = hollowIdeHelpMenuItems(),
        )
    }

    @Composable
    private fun OpenFilesDropdown() {
        val files = model.files.values.toList()
        if (files.isEmpty()) {
            Text(statusText, tags = listOf("ide-status"))
            return
        }
        UiDropdown(
            id = "ide-open-files",
            label = dock.focusedItemId?.let { id -> files.firstOrNull { it.id == id }?.title }
                ?: "hollowengine.gui.ide.file_picker.empty".lang,
            expanded = openDropdown == "open-files",
            onExpandedChange = { openDropdown = if (it) "open-files" else null },
            items = files.map { file ->
                UiDropdownItem(
                    label = file.title,
                    icon = file.dockItem().icon,
                    onClick = { dock.focus(file.id) },
                )
            },
        )
    }

    @Composable
    private fun DockContent(item: DockItem) {
        when (item.id) {
            ProjectTreeId -> ProjectTree()
            EditorWelcomeId -> EmptyEditor()
            else -> model.files.values.firstOrNull { it.id == item.id }?.let { file -> FileEditor(file) } ?: EmptyEditor()
        }
    }

    @Composable
    private fun ProjectTree() {
        Column(tags = listOf("ide-panel", "project-tree-panel")) {
            Row(tags = listOf("project-filter-row")) {
                Image(SearchIcon, tags = listOf("project-filter-icon"))
                TextField(
                    value = filter,
                    placeholder = "hollowengine.message.filter".lang,
                    onChange = { filter = it },
                    tags = listOf("project-filter"),
                )
            }
            UiTreeView(
                items = model.visibleTreeItems(filter),
                onToggle = { item -> model.toggle(item.payload) },
                onSelect = ::openTreeItem,
            )
        }
    }

    @Composable
    private fun FileEditor(file: HollowIdeOpenFile) {
        val editorSession = remember(file.path) {
            editorSessions.getOrPut(file.path) {
                HollowIdeEditorSession(file.path) {
                    editorAnalysisRevision++
                    invalidateUi()
                }
            }
        }
        val analysisRevision = editorAnalysisRevision.toLong() + editorSession.revision
        val diagnostics = editorSession.diagnostics(file.text)
        val inlayHints = editorSession.inlayHints(file.text)
        val fontSize = editorFontSize
        val editorId = "editor-${file.id}"
        Column(tags = listOf("ide-editor-shell")) {
            Box(
                id = "editor-stack-${file.id}",
                mode = UiBoxMode.STACK,
                tags = listOf("ide-editor-stack"),
                modifier = Modifier.grow(1f),
            ) {
                UiCodeEditor(
                    value = file.text,
                    onChange = { text ->
                        model.updateText(file.path, text)
                        editorSession.requestAnalysis(text, text.length)
                        dock.updateItem(file.dockItem())
                    },
                    highlighter = editorSession.highlighter,
                    completions = editorSession.completions,
                    diagnostics = diagnostics,
                    inlayHints = inlayHints,
                    inlayRevision = analysisRevision,
                    id = editorId,
                    attributes = mapOf("analysis-revision" to analysisRevision.toString()),
                    modifier = Modifier.then(
                        Modifier.size(100.percent, 100.percent),
                        Modifier.fontSize(fontSize),
                    ),
                )
                editorOverlays.CompletionPopup(file.id)
                editorOverlays.DiagnosticTooltip(file.id)
                HollowIdeDiagnosticsBadge(file.id, diagnostics) { id ->
                    diagnosticsPanels[id] = diagnosticsPanels[id] != true
                }
            }
            if (diagnosticsPanels[file.id] == true) {
                val height = diagnosticsPanelHeights[file.id] ?: DefaultDiagnosticsPanelHeight
                HollowIdeDiagnosticsPanel(file.id, diagnostics, height) { id, delta ->
                    diagnosticsPanelHeights[id] = ((diagnosticsPanelHeights[id] ?: DefaultDiagnosticsPanelHeight) + delta)
                        .coerceIn(
                            MinDiagnosticsPanelHeight,
                            MaxDiagnosticsPanelHeight,
                        )
                }
            }
        }
    }

    @Composable
    private fun EmptyEditor() {
        Column(tags = listOf("ide-empty-editor")) {
            Text("Open a text file from Project Tree", tags = listOf("ide-empty-title"))
            Text(statusText, tags = listOf("ide-status"))
        }
    }

    private fun openTreeItem(item: UiTreeItem<HollowIdeFileNode>) {
        when (val result = model.open(item.payload)) {
            HollowIdeOpenResult.Directory -> Unit
            HollowIdeOpenResult.Unsupported -> statusText = "Unsupported or binary file: ${item.payload.path}"
            is HollowIdeOpenResult.File -> openFileDockItem(result.file)
        }
    }

    private fun openFileDockItem(file: HollowIdeOpenFile) {
        statusText = ""
        if (!dock.contains(file.id)) {
            val anchor = editorAnchor()
            dock.open(file.dockItem(), anchor?.let { DockTarget(it) } ?: DockTarget.Root)
        } else {
            dock.updateItem(file.dockItem())
            dock.focus(file.id)
        }
        if (dock.contains(EditorWelcomeId)) dock.close(EditorWelcomeId)
    }

    private fun editorAnchor(): String? {
        return dock.stackIdOf(EditorWelcomeId)
            ?: model.files.values.firstOrNull { dock.contains(it.id) }?.let { dock.stackIdOf(it.id) }
    }

    private fun focusedFile(): HollowIdeOpenFile? {
        val focused = dock.focusedItemId ?: return null
        return model.files.values.firstOrNull { it.id == focused }
    }

    private fun mousePressed(frame: HollowUiFrame, mouseX: Float, mouseY: Float, button: Int): Boolean {
        activeButton = button
        lastMouseX = mouseX
        lastMouseY = mouseY
        if (button == 0 && editorOverlays.closeCompletionsOutside(frame, mouseX, mouseY)) {
            invalidateUi()
        }
        val scrollbarResult = input.scrollbarMouseClicked(frame, mouseX, mouseY, button, ::setScrollImmediate)
        if (scrollbarResult.handled) {
            invalidateUi()
            return true
        }
        val result = input.mouseClicked(frame, mouseX, mouseY, button, ::dispatchUiEvent, ::openUrl)
        if (result.handled) invalidateUi()
        if (!result.handled) activeButton = null
        return result.handled
    }

    private fun mouseReleased(frame: HollowUiFrame, mouseX: Float, mouseY: Float, button: Int): Boolean {
        val hadActivePointer = activeButton != null || input.hasScrollbarDrag()
        val result = input.mouseReleased(frame, mouseX, mouseY, button, ::dispatchUiEvent)
        activeButton = null
        if (result.handled || hadActivePointer) invalidateUi()
        return result.handled || hadActivePointer
    }

    private fun renderOverlay(target: UiRenderTarget) {
        val nowMillis = System.currentTimeMillis()
        val current = currentFrame(nowMillis)
        val hoverChanged = input.updateHover(current, lastMouseX, lastMouseY, ::dispatchUiEvent)
        if (hoverChanged || current.requiresContinuousRefresh()) invalidateUi()
        input.dispatchHover(current, lastMouseX, lastMouseY, ::dispatchUiEvent)
        renderer.render(current.commands, target)
    }

    private fun currentBlitTarget(): UiRenderTarget {
        val viewport = IntArray(4)
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
        val window = Minecraft.getInstance().window
        val logicalWidth = window.guiScaledWidth.toFloat()
        val logicalHeight = window.guiScaledHeight.toFloat()
        return UiRenderTarget(
            framebufferId = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
            x = viewport[0],
            y = viewport[1],
            width = viewport[2],
            height = viewport[3],
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
            scale = viewport[2] / logicalWidth,
        )
    }

    private fun invalidateUi() {
        uiDirty = true
    }

    private fun currentFrame(nowMillis: Long = System.currentTimeMillis()): HollowUiFrame {
        initialize()
        val window = Minecraft.getInstance().window
        val uiChanged = surface.applyPendingChanges(nowMillis * NanosPerMillisecond)
        if (uiChanged) uiDirty = true
        val sizeChanged = window.guiScaledWidth != lastWidth || window.guiScaledHeight != lastHeight
        val pointerChanged = lastMouseX != lastFrameMouseX || lastMouseY != lastFrameMouseY
        val needsPointerRebuild = pointerChanged && (
                surface.root.hasLiveCursorPopup() ||
                        lastFrame?.let(editorOverlays::needsPointerUpdate) == true
                )
        val stylesheetChanged = stylesheetChanged(nowMillis)
        return if (lastFrame == null || uiDirty || sizeChanged || uiChanged || needsPointerRebuild || stylesheetChanged) {
            refreshFrame(nowMillis)
        } else {
            lastFrame!!
        }
    }

    private fun currentFrameForInput(): HollowUiFrame? {
        if (!isVisible()) return lastFrame
        return lastFrame ?: currentFrame()
    }

    private fun refreshFrame(nowMillis: Long = System.currentTimeMillis()): HollowUiFrame {
        initialize()
        val window = Minecraft.getInstance().window
        var root = surface.composeRoot(nowMillis * NanosPerMillisecond)
        input.prepareRoot(root, closing = false)
        var frame = surface.frame(
            root = root,
            width = window.guiScaledWidth.toFloat(),
            height = window.guiScaledHeight.toFloat(),
            bindings = UiBindingContext().withPointer(lastMouseX, lastMouseY),
            nowMillis = nowMillis,
        )
        if (editorOverlays.update(frame, lastMouseX, lastMouseY)) {
            root = surface.composeRoot(nowMillis * NanosPerMillisecond)
            input.prepareRoot(root, closing = false)
            frame = surface.frame(
                root = root,
                width = window.guiScaledWidth.toFloat(),
                height = window.guiScaledHeight.toFloat(),
                bindings = UiBindingContext().withPointer(lastMouseX, lastMouseY),
                nowMillis = nowMillis,
            )
            editorOverlays.update(frame, lastMouseX, lastMouseY)
        }
        return frame.also {
            lastFrame = it
            uiDirty = false
            lastWidth = window.guiScaledWidth
            lastHeight = window.guiScaledHeight
            lastFrameMouseX = lastMouseX
            lastFrameMouseY = lastMouseY
            lastStylesheetRevision = root.stylesheetRevision()
        }
    }

    private fun stylesheetChanged(nowMillis: Long): Boolean {
        if (lastFrame == null || nowMillis < nextStylesheetCheckMillis) return false
        nextStylesheetCheckMillis = nowMillis + StylesheetCheckIntervalMillis
        return surface.root.stylesheetRevision() != lastStylesheetRevision
    }

    private fun dispatchUiEvent(event: UiEvent): Boolean {
        return dispatchNode(event)
    }

    private fun dispatchNode(event: UiEvent): Boolean {
        event.variables = UiBindingContext().root
        return event.node.dispatch(event)
    }

    private fun setScrollImmediate(node: UiNode, offset: UiScrollOffset) {
        surface.setScrollImmediate(node, offset.x, offset.y)
    }

    private fun isAvailable(): Boolean {
        return when (HollowEngineConfig.editMode) {
            EditMode.DISABLED -> false
            EditMode.CHAT_ONLY -> Minecraft.getInstance().screen is ChatScreen
            else -> true
        }
    }

}

private const val DefaultDiagnosticsPanelHeight = 160f
private const val MinDiagnosticsPanelHeight = 90f
private const val MaxDiagnosticsPanelHeight = 360f
