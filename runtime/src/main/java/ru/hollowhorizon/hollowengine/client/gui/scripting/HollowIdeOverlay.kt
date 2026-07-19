package ru.hollowhorizon.hollowengine.client.gui.scripting

import androidx.compose.runtime.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import ru.hollowhorizon.hollowengine.client.editor.TransformGizmoEditor
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.image.HollowIdeImageEditor
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.video.HollowIdeVideoEditor
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.HollowIdeConsolePanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.ModelEditorPanel
import ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene.CutsceneEditorSessions
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.CutscenePropertiesDock
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.CutsceneTimelineDock
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.CutsceneViewportDock
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.docking.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.render.MinecraftUiRenderer
import ru.hollowhorizon.hollowengine.client.ui.render.UiRenderTarget
import ru.hollowhorizon.hollowengine.client.ui.widgets.*
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.config.EditMode
import ru.hollowhorizon.hollowengine.common.config.HollowEngineConfig
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderTickEvent
import ru.hollowhorizon.hollowengine.common.scripting.ide.DefinitionLocation

internal const val ProjectTreeId = "ide-project-tree"
internal const val ConsoleId = "ide-console"
internal const val CutsceneTimelineId = "ide-cutscene-timeline"
internal const val CutscenePropertiesId = "ide-cutscene-properties"
internal const val CutsceneViewportId = "ide-cutscene-viewport"
internal const val UiProfilerId = "ide-ui-profiler"
internal const val LogoIcon = "hollowengine:textures/gui/logo/logo.svg"
internal const val ProjectIcon = "hollowengine:textures/gui/icons/folder.svg"
internal const val ConsoleIcon = "hollowengine:textures/gui/icons/console.svg"
internal const val SearchIcon = "hollowengine:textures/gui/icons/search.svg"
internal const val CutsceneIcon = "hollowengine:textures/gui/icons/film.svg"

@ClientOnly
object HollowIdeOverlay {
    var useHollowUiOverlay: Boolean = true

    private val fileTypes = HollowIdeFileTypeRegistry().apply {
        registerBuiltinFileTypes(
            modelEditor = { file -> ModelEditorPanel(file.path) },
            imageEditor = { file -> HollowIdeImageEditor(file, file::save) },
            videoEditor = { file -> HollowIdeVideoEditor(file) },
            textEditor = { file -> FileEditor(file) },
        )
    }
    private val model = HollowIdeModel(fileTypes)
    private val dock = DockingState()
    private val surface = HollowUiSurface()
    private val renderer = MinecraftUiRenderer()
    private val pipeline = PipelinedUiFrameBuilder()

    private const val PIPELINE_FRAMES = true
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
    private val editorStates = mutableMapOf<String, TextFieldState>()

    private var lastMouseX = 0f
    private var lastMouseY = 0f

    init {
        initialize()
    }

    /** Registers a new IDE file type. IDs must be unique; higher priorities are matched first. */
    fun registerFileType(type: HollowIdeFileType) {
        fileTypes.register(type)
    }

    fun isVisible(): Boolean = useHollowUiOverlay && isAvailable()

    fun isMouseOver(x: Float, y: Float): Boolean {
        if (!isVisible()) return false
        val point = hollowIdeOverlayPoint(x, y)
        return surface.runtime.lastFrame?.hitsVisible(point.x, point.y) ?: false
    }

    fun hasFocusedInput(): Boolean = isVisible() && surface.runtime.isAnyFocused

    fun handleMouseMove(x: Float, y: Float): Boolean {
        if (!isVisible()) return false
        pipeline.await()
        val point = hollowIdeOverlayPoint(x, y)
        val deltaX = point.x - lastMouseX
        val deltaY = point.y - lastMouseY
        lastMouseX = point.x
        lastMouseY = point.y
        val button = activeButton ?: return false
        return surface.runtime.mouseDragged(point.x, point.y, button, deltaX, deltaY, currentUiKeyModifiers())
    }

    fun handleMouseButton(x: Float, y: Float, button: Int, action: Int): Boolean {
        if (!isVisible()) return false
        pipeline.await()
        val point = hollowIdeOverlayPoint(x, y)

        return when (action) {
            GLFW.GLFW_PRESS -> {
                focusDockContentAt(point.x, point.y)
                val result = surface.runtime.mouseClicked(point.x, point.y, button, currentUiKeyModifiers())
                if (result) activeButton = button
                result
            }

            GLFW.GLFW_RELEASE -> {
                activeButton = null
                surface.runtime.mouseReleased(point.x, point.y, button, currentUiKeyModifiers())
            }

            else -> false
        }
    }

    fun handleMouseScroll(x: Float, y: Float, scrollX: Double, scrollY: Double): Boolean {
        if (!isVisible()) return false
        pipeline.await()
        val point = hollowIdeOverlayPoint(x, y)
        return surface.runtime.mouseScrolled(
            point.x,
            point.y,
            scrollX.toFloat(),
            scrollY.toFloat(),
            currentUiKeyModifiers(),
        )
    }

    fun handleKey(key: Int, scanCode: Int, action: Int, modifiers: Int): Boolean {
        if (!isVisible() || collapsed) return false
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) return false
        pipeline.await()
        return surface.runtime.keyPressed(key, scanCode, modifiers, repeat = action == GLFW.GLFW_REPEAT)
    }

    private fun focusDockContentAt(x: Float, y: Float) {
        var node = surface.runtime.lastFrame?.hitTest(x, y)?.node ?: return
        while (true) {
            val id = node.id
            if (id != null && id.endsWith("-content") && dock.focusContent(id)) return
            node = node.layoutState.parentNode ?: return
        }
    }

    fun handleChar(codePoint: Int, modifiers: Int): Boolean {
        if (!isVisible() || collapsed) return false
        pipeline.await()
        return surface.runtime.charTyped(codePoint.toChar(), modifiers)
    }

    @SubscribeEvent
    fun render(event: RenderTickEvent.Blit) {
        if (!isVisible()) return
        renderOverlay(currentBlitTarget())
    }

    private fun initialize() {
        if (initialized) return
        initialized = true
        dock.open(DockItem(ProjectTreeId, "hollowengine.gui.ide.project_tree".lang, ProjectIcon))
        surface.setContent { Content() }
    }

    @Composable
    private fun Content() {
        Box(
            id = "ide-root",
            modifier = Modifier.style("hollowengine:ui/styles/ide.hss")
                .style("hollowengine:ui/styles/widgets.hss")
                .size(100.percent, 100.percent)
                .focusScope()
                .onKeyInput { input ->
                    val handled = !input.repeat && (
                            project.handleNameDialogKey(input.key) ||
                                    project.handleShortcut(input.key, input.modifiers) ||
                                    handleDockShortcut(input.key, input.modifiers) ||
                                    input.key == GLFW.GLFW_KEY_F4 && goToDefinition()
                            )
                    if (handled) input.consume()
                }
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
        var popup by remember { mutableStateOf(false) }
        var anchorBounds by remember { mutableStateOf(UiRect.Zero) }
        Box(
            id = "ide-logo",
            modifier = Modifier.cursor(UiCursorShape.HAND)
                .onClick { event ->
                    if (event.isLeftClick()) {
                        collapsed = !collapsed
                        openDropdown = null
                        event.consume()
                    } else if (event.isRightClick()) {
                        popup = true
                    }
                }
                .onPlaced {
                    anchorBounds = it
                }
        ) {
            Image(LogoIcon, tags = listOf("ide-logo-icon"))
        }
        if (popup) {
            ContextMenu(
                "ide-editor-menu", anchorBounds, listOf(
                    UiDropdownItem("Show always") {
                        HollowEngineConfig.editMode = EditMode.ENABLED
                        if (collapsed) {
                            collapsed = false
                        }
                    },
                    UiDropdownItem("Collapse") {
                        if (!collapsed) {
                            collapsed = true
                        }
                    },
                    UiDropdownItem("Hide") {
                        HollowEngineConfig.editMode = EditMode.DISABLED
                    },
                    UiDropdownItem("Show only in chat menu") {
                        HollowEngineConfig.editMode = EditMode.CHAT_ONLY
                    }
                )) { popup = it }
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
        UiDropdown(
            id = "ide-tools-menu",
            label = "hollowengine.gui.ide.tools".lang,
            expanded = openDropdown == "tools",
            onExpandedChange = { openDropdown = if (it) "tools" else null },
            items = hollowIdeToolMenuItems(dock, surface.runtime.profiler),
        )
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
            ConsoleId -> HollowIdeConsolePanel()
            CutsceneTimelineId -> CutsceneTimelineDock(
                session = CutsceneEditorSessions.default,
                keyboardActive = dock.focusedItemId == CutsceneTimelineId,
            )

            CutscenePropertiesId -> CutscenePropertiesDock(CutsceneEditorSessions.default)
            CutsceneViewportId -> CutsceneViewportDock()
            UiProfilerId -> HollowIdeUiProfilerPanel(surface.runtime.profiler)
            else -> model.files.values.firstOrNull { it.id == item.id }?.let { file ->
                file.type.editor(file)
                LaunchedEffect(file.dirty) {
                    dock.updateItem(file.dockItem())
                }
            } ?: EmptyEditor()
        }
    }

    @Composable
    private fun ProjectTree() {
        Column(tags = listOf("ide-panel", "project-tree-panel"), modifier = Modifier.size(100.percent, 100.percent)) {
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
                onDismiss = { project.closePopups() },
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
        val editorState = editorStates.getOrPut(file.path) { TextFieldState(file.text, multiline = true) }
        val analysisRevision = editorAnalysisRevision.toLong() + editorSession.revision
        val diagnostics = editorSession.diagnostics(file.text)
        val inlayHints = editorSession.inlayHints(file.text)
        val fontSize = editorFontSize
        val editorId = "editor-${file.id}"
        Column(tags = listOf("ide-editor-shell"), modifier = Modifier.size(100.percent, 100.percent)) {
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
                    fontSize = fontSize,
                    state = editorState,
                    id = editorId,
                    attributes = mapOf("analysis-revision" to analysisRevision.toString()),
                    modifier = Modifier.size(100.percent, 100.percent)
                        .onFocus {
                            dock.focus(file.id)
                        }
                )
                HollowIdeDiagnosticsBadge(file.id, diagnostics) { id ->
                    diagnosticsPanels[id] = diagnosticsPanels[id] != true
                }
            }
            if (diagnosticsPanels[file.id] == true) {
                val height = diagnosticsPanelHeights[file.id] ?: DefaultDiagnosticsPanelHeight
                HollowIdeDiagnosticsPanel(file.id, diagnostics, height) { id, requestedHeight ->
                    diagnosticsPanelHeights[id] = requestedHeight.coerceIn(
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
            Text("Open a file from Project Tree", tags = listOf("ide-empty-title"))
            Text(statusText, tags = listOf("ide-status"))
        }
    }

    private fun openFileDockItem(file: HollowIdeOpenFile) {
        statusText = ""
        if (!dock.contains(file.id)) {
            val hadOpenEditor = model.files.values.any { dock.contains(it.id) }
            dock.open(file.dockItem(), editorTarget())
            if (!hadOpenEditor) {
                (dock.root as? DockNode.Split)?.let { split -> dock.setSplitFraction(split.id, 0.28f) }
            }
        } else {
            dock.updateItem(file.dockItem())
            dock.focus(file.id)
        }
    }

    private fun handleDockShortcut(key: Int, modifiers: Int): Boolean {
        val command = modifiers and GLFW.GLFW_MOD_CONTROL != 0
        if (!command || key != GLFW.GLFW_KEY_W) return false
        return dock.closeFocused()
    }

    private fun editorTarget(): DockTarget {
        model.files.values.firstOrNull { dock.contains(it.id) }
            ?.let { dock.stackIdOf(it.id) }
            ?.let { return DockTarget(it) }
        dock.stackIdOf(ProjectTreeId)?.let { return DockTarget(it, DockPlacement.RIGHT) }
        return DockTarget.Root
    }

    private fun focusedFile(): HollowIdeOpenFile? {
        val focused = dock.focusedItemId ?: return null
        return model.files.values.firstOrNull { it.id == focused }
    }

    private fun goToDefinition(): Boolean {
        val file = focusedEditorFile() ?: return false
        val editorKey = "editor-${file.id}"
        val editor = editorStates[file.path] ?: return false
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
                model.files.values.firstOrNull { it.id == editorFileId && it.textOrNull != null }?.let { return it }
            }
        return focusedFile()?.takeIf { it.textOrNull != null }
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
            pipeline.await()
            val editorKey = "editor-${file.id}"
            val editor = editorStates.getOrPut(file.path) { TextFieldState(file.text, multiline = true) }
            editor.moveCaret(offset)
            surface.runtime.focus(editorKey)
        }
    }

    private fun renderOverlay(target: UiRenderTarget) {
        val window = Minecraft.getInstance().window
        val frameWidth = HollowIdeScale.scaledWidth()
        val frameHeight = HollowIdeScale.scaledHeight()
        val frame = (if (PIPELINE_FRAMES) pipeline.take(frameWidth, frameHeight) else null)
            ?: surface.frame(frameWidth, frameHeight, lastMouseX, lastMouseY, System.nanoTime())
        renderer.render(frame, target)
        val overIde =
            surface.runtime.lastFrame?.hitsVisible(lastMouseX, lastMouseY) == true || surface.runtime.isAnyFocused
        when {
            overIde -> UiCursorManager.apply(window.window, surface.runtime.cursor)
            !TransformGizmoEditor.ownsWorldCursor() -> UiCursorManager.apply(window.window, surface.runtime.cursor)
            else -> Unit
        }
        if (PIPELINE_FRAMES) {
            val mouseX = lastMouseX
            val mouseY = lastMouseY
            pipeline.schedule(frameWidth, frameHeight) {
                surface.frame(frameWidth, frameHeight, mouseX, mouseY, System.nanoTime())
            }
        }
    }

    private fun currentBlitTarget(): UiRenderTarget {
        val viewport = IntArray(4)
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
        val logicalWidth = HollowIdeScale.scaledWidth()
        val logicalHeight = HollowIdeScale.scaledHeight()
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

private const val DefaultDiagnosticsPanelHeight = 160f
private const val MinDiagnosticsPanelHeight = 90f
private const val MaxDiagnosticsPanelHeight = 360f
