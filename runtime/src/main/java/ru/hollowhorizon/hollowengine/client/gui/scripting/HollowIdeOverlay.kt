package ru.hollowhorizon.hollowengine.client.gui.scripting

import androidx.compose.runtime.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene.CutsceneEditorSessions
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.CutsceneTimelineDock
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.docking.*
import ru.hollowhorizon.hollowengine.client.ui.render.MinecraftUiRenderer
import ru.hollowhorizon.hollowengine.client.ui.render.UiRenderTarget
import ru.hollowhorizon.hollowengine.client.ui.widgets.TextFieldNode
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiCodeEditor
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdown
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdownItem
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTreeView
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.config.EditMode
import ru.hollowhorizon.hollowengine.common.config.HollowEngineConfig
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderTickEvent
import ru.hollowhorizon.hollowengine.common.scripting.ide.DefinitionLocation
import ru.hollowhorizon.hollowengine.common.util.PlayerPermissions

internal const val ProjectTreeId = "ide-project-tree"
internal const val EditorWelcomeId = "ide-code-editor"
internal const val CutsceneTimelineId = "ide-cutscene-timeline"
internal const val LogoIcon = "hollowengine:textures/gui/logo/logo.svg"
internal const val CodeIcon = "hollowengine:textures/gui/icons/code_editor.svg"
internal const val ProjectIcon = "hollowengine:textures/gui/icons/folder.svg"
internal const val SearchIcon = "hollowengine:textures/gui/icons/search.svg"
internal const val CutsceneIcon = "hollowengine:textures/gui/icons/film.svg"
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
    private var initialized = false
    private var activeButton: Int? = null
    private var collapsed by mutableStateOf(true)
    private var filter by mutableStateOf("")
    private var openDropdown by mutableStateOf<String?>(null)
    private var statusText by mutableStateOf("")
    private val project = HollowIdeProjectController(
        model = model,
        focusProjectTree = { dock.focus(ProjectTreeId) },
        shortcutsActive = { model.selectedTreePath.isNotBlank() && surface.runtime.focusedKey == null && dock.focusedItemId == ProjectTreeId },
        closeDockItem = { dock.close(it) },
        openFile = { openFileDockItem(it) },
        setStatus = { statusText = it },
        pointerX = { surface.runtime.mouseX },
        pointerY = { surface.runtime.mouseY },
    )
    private val diagnosticsPanels = mutableStateMapOf<String, Boolean>()
    private val diagnosticsPanelHeights = mutableStateMapOf<String, Float>()
    private var editorFontSize by mutableStateOf(HollowEngineConfig.ideEditorFontSize)
    private var editorAnalysisRevision by mutableStateOf(0)
    private val editorSessions = mutableMapOf<String, HollowIdeEditorSession>()
    private val editorOverlays = HollowIdeEditorOverlays(surface.runtime)

    private var lastMouseX = 0f
    private var lastMouseY = 0f

    init {
        initialize()
    }

    fun isVisible(): Boolean = useHollowUiOverlay && isAvailable()

    fun isMouseOver(x: Float, y: Float): Boolean {
        if (!isVisible()) return false
        val point = hollowIdeOverlayPoint(x, y)
        return if (collapsed) point.x <= 44f && point.y <= ToolbarHeight else true
    }

    fun hasFocusedInput(): Boolean = isVisible() && surface.runtime.isAnyFocused

    fun handleMouseMove(x: Float, y: Float): Boolean {
        if (!isVisible()) return false
        val point = hollowIdeOverlayPoint(x, y)
        val deltaX = point.x - lastMouseX
        val deltaY = point.y - lastMouseY
        lastMouseX = point.x
        lastMouseY = point.y
        val button = activeButton ?: return false
        return surface.runtime.mouseDragged(point.x, point.y, button, deltaX, deltaY)
    }

    fun handleMouseButton(x: Float, y: Float, button: Int, action: Int): Boolean {
        if (!isVisible()) return false
        val point = hollowIdeOverlayPoint(x, y)

        return when (action) {
            GLFW.GLFW_PRESS -> {
                val result = surface.runtime.mouseClicked(point.x, point.y, button)
                if (result) activeButton = button
                result
            }

            GLFW.GLFW_RELEASE -> {
                activeButton = null
                surface.runtime.mouseReleased(point.x, point.y, button)
            }

            else -> false
        }
    }

    fun handleMouseScroll(x: Float, y: Float, scrollX: Double, scrollY: Double): Boolean {
        if (!isVisible()) return false
        val point = hollowIdeOverlayPoint(x, y)
        return surface.runtime.mouseScrolled(point.x, point.y, scrollX.toFloat(), scrollY.toFloat())
    }

    fun handleKey(key: Int, scanCode: Int, action: Int, modifiers: Int): Boolean {
        if (!isVisible()) return false
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) return hasFocusedInput()
        if (action == GLFW.GLFW_PRESS && project.handleNameDialogKey(key)) return true
        if (action == GLFW.GLFW_PRESS && project.handleShortcut(key, modifiers)) return true
        if (action == GLFW.GLFW_PRESS && handleDockShortcut(key, modifiers)) return true
        if (action == GLFW.GLFW_PRESS && dock.focusedItemId == CutsceneTimelineId &&
            CutsceneEditorSessions.default.onHollowUiKey(key, modifiers)
        ) {
            return true
        }
        if (action == GLFW.GLFW_PRESS && key == GLFW.GLFW_KEY_F4 && goToDefinition()) {
            return true
        }
        val result = surface.runtime.keyPressed(key, scanCode, modifiers)
        if (result) {
            editorOverlays.update(surface.runtime.lastFrame ?: return true, lastMouseX, lastMouseY)
        }
        return result
    }

    fun handleChar(codePoint: Int, modifiers: Int): Boolean {
        if (!isVisible()) return false
        val result = surface.runtime.charTyped(codePoint.toChar(), modifiers)
        if (result) {
            editorOverlays.update(surface.runtime.lastFrame ?: return true, lastMouseX, lastMouseY)
        }
        return result
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
            modifier = Modifier.style("hollowengine:ui/styles/ide.hss")
                .style("hollowengine:ui/styles/widgets.hss")
                .size(100.percent, 100.percent)
        ) {
            if (collapsed) {
                GearButton()
            } else {
                Column(modifier = Modifier.size(100.percent, 100.percent)) {
                    Toolbar()
                    DockSpace(
                        state = dock,
                        id = "ide-dock",
                        modifier = Modifier.size(100.percent, 0.px)
                            .grow(1f),
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
            modifier = Modifier.input(hoverable = true, clickable = true)
                .cursor(UiCursorShape.HAND)
                .onClick { event ->
                    collapsed = !collapsed
                    openDropdown = null
                    event.consume()
                }
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
            Box(modifier = Modifier.size(0.px, 100.percent).grow(1f))
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
            CutsceneTimelineId -> CutsceneTimelineDock(CutsceneEditorSessions.default)
            else -> model.files.values.firstOrNull { it.id == item.id }?.let { file -> FileEditor(file) }
                ?: EmptyEditor()
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
                onToggle = project::toggle,
                onSelect = project::select,
            )
            HollowIdeProjectContextMenu(
                menu = project.contextMenu,
                onCreateFile = project::openCreateFileDialog,
                onCreateFolder = project::openCreateFolderDialog,
                onRename = project::openRenameDialog,
                onCopy = { project.copy(it, cut = false) },
                onCut = { project.copy(it, cut = true) },
                onPaste = project::pasteInto,
                onShowInExplorer = project::showInExplorer,
                onDelete = project::delete,
            )
            HollowIdeProjectNameDialog(
                dialog = project.nameDialog,
                onNameChange = project::updateNameDialog,
                onConfirm = project::applyNameDialog,
                onCancel = project::cancelNameDialog,
            )
        }
    }

    @Composable
    private fun FileEditor(file: HollowIdeOpenFile) {
        val editorSession = remember(file.path) {
            editorSessions.getOrPut(file.path) {
                HollowIdeEditorSession(file.path) {
                    editorAnalysisRevision++
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
                    completions = if (file.readOnly) null else editorSession.completions,
                    diagnostics = diagnostics,
                    inlayHints = inlayHints,
                    inlayRevision = analysisRevision,
                    readOnly = file.readOnly,
                    id = editorId,
                    attributes = mapOf("analysis-revision" to analysisRevision.toString()),
                    modifier = Modifier.size(100.percent, 100.percent)
                        .fontSize(fontSize)
                        .onFocus {
                            dock.focus(file.id)
                        }
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
                    diagnosticsPanelHeights[id] =
                        ((diagnosticsPanelHeights[id] ?: DefaultDiagnosticsPanelHeight) + delta)
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

    private fun handleDockShortcut(key: Int, modifiers: Int): Boolean {
        val command = modifiers and GLFW.GLFW_MOD_CONTROL != 0
        if (!command || key != GLFW.GLFW_KEY_W) return false
        return dock.closeFocused()
    }

    private fun editorAnchor(): String? {
        return dock.stackIdOf(EditorWelcomeId)
            ?: model.files.values.firstOrNull { dock.contains(it.id) }?.let { dock.stackIdOf(it.id) }
    }

    private fun focusedFile(): HollowIdeOpenFile? {
        val focused = dock.focusedItemId ?: return null
        return model.files.values.firstOrNull { it.id == focused }
    }

    private fun goToDefinition(): Boolean {
        val file = focusedEditorFile() ?: return false
        val editorKey = "editor-${file.id}"
        val frame = surface.runtime.lastFrame ?: return false
        val editor = frame.nodeByKey(editorKey) as? TextFieldNode ?: return false
        if (surface.runtime.focusedKey != editorKey) surface.runtime.focus(editorKey)
        val session = editorSessions.getOrPut(file.path) {
            HollowIdeEditorSession(file.path) {
                editorAnalysisRevision++
            }
        }
        statusText = ""
        session.resolveDefinition(file.text, editor.caret) { definition ->
            if (definition == null) {
                statusText = "Definition not found"
                return@resolveDefinition
            }
            openDefinition(definition)
        }
        return true
    }

    private fun focusedEditorFile(): HollowIdeOpenFile? {
        surface.runtime.focusedKey?.removePrefix("editor-")?.takeIf { it != surface.runtime.focusedKey }
            ?.let { editorFileId ->
                model.files.values.firstOrNull { it.id == editorFileId }?.let { return it }
            }
        return focusedFile()
    }

    private fun openDefinition(definition: DefinitionLocation) {
        val file = if (definition.text != null || definition.readOnly) {
            model.openReadOnly(definition.path, definition.text.orEmpty())
        } else {
            when (val result = model.openFile(definition.path)) {
                HollowIdeOpenResult.Unsupported -> {
                    statusText = "Unsupported definition target: ${definition.path}"
                    return
                }

                is HollowIdeOpenResult.File -> result.file
                HollowIdeOpenResult.Directory -> return
            }
        }
        openFileDockItem(file)
        focusEditorAt(file, definition.offset)
    }

    private fun focusEditorAt(file: HollowIdeOpenFile, offset: Int) {
        Minecraft.getInstance().execute {
            val frame = surface.runtime.lastFrame ?: return@execute
            val editorKey = "editor-${file.id}"
            val editor = frame.nodeByKey(editorKey) as? TextFieldNode ?: return@execute
            editor.moveCaret(offset)
            surface.runtime.saveState(editor)
            surface.runtime.focus(editorKey)
        }
    }

    private fun renderOverlay(target: UiRenderTarget) {
        val window = Minecraft.getInstance().window
        val frame =
            surface.frame(window.width.toFloat(), window.height.toFloat(), lastMouseX, lastMouseY, System.nanoTime())
        renderer.render(frame, target)
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

    private fun isAvailable(): Boolean {
        return when (HollowEngineConfig.editMode) {
            EditMode.DISABLED -> false
            EditMode.CHAT_ONLY -> Minecraft.getInstance().screen is ChatScreen
            else -> true
        }
    }

}

private fun HollowUiFrame.hasPopupAncestor(node: UiNode): Boolean {
    var current: UiNode? = node
    while (current != null) {
        if (current is PopupNode) return true
        current = parentOf(current)
    }
    return false
}

private fun HollowUiFrame.parentOf(child: UiNode): UiNode? {
    val stack = ArrayDeque<UiNode>()
    stack.add(root)
    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        if (child in node.children) return node
        for (index in node.children.indices.reversed()) {
            stack.add(node.children[index])
        }
    }
    return null
}

private const val DefaultDiagnosticsPanelHeight = 160f
private const val MinDiagnosticsPanelHeight = 90f
private const val MaxDiagnosticsPanelHeight = 360f
