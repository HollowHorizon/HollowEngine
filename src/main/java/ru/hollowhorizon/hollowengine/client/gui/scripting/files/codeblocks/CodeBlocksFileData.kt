package ru.hollowhorizon.hollowengine.client.gui.scripting.files.codeblocks

import de.fabmax.kool.input.CursorShape
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dockable
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.KoolDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileTitleBar
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockRepository
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.*
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockFormat
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockSerializer
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.generated.Assets

@OptIn(FlowPreview::class)
class CodeBlocksFileData(filePath: String, bytes: ByteArray) : FileData(filePath.substringAfterLast('/'), filePath) {
    private val scope = CoroutineScope(SupervisorJob() + KoolDispatchers.Frontend)
    private val changeEvents = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val repository = BlockRepository.create("Скрипт") {
        include(StandardModules.AllBasics)
        include(NPCModule)
        include(EntityModule)
        include(PlayerModule)
        include(WorldModule)
    }
    val format = CodeBlockFormat(repository)
    val editor = BlockEditor(repository) {
        changeEvents.tryEmit(Unit)
    }
    var filter = mutableStateOf("")
    private val blocksPreviewWidth = mutableStateOf(Dp(230f))

    init {
        if (bytes.isNotEmpty()) {
            try {
                val jsonString = String(bytes)
                val loadedBlocks = format.json.decodeFromString(CodeBlockSerializer(format), jsonString)

                editor.rootBlocks.addAll(loadedBlocks)

            } catch (e: Exception) {
                HollowEngine.LOGGER.error("File $filePath cannot be loaded!", e)
                val file = filePath.fromReadablePath()
                val backup = file.parentFile.resolve(file.name + ".backup")
                file.copyTo(backup, true)
            }
        }

        changeEvents
            .debounce(5000L)
            .onEach {
                withContext(Dispatchers.IO) {
                    save()
                }
            }
            .launchIn(scope)
    }

    override fun save() {
        val file = filePath.fromReadablePath()
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }

        try {
            val blocksToSave = editor.rootBlocks.toList()

            val jsonString = format.json.encodeToString(CodeBlockSerializer(format), blocksToSave)

            file.writeText(jsonString)
        } catch (e: Exception) {
            HollowEngine.LOGGER.error("File $filePath cannot be saved!", e)
        }
    }

    override fun UiScope.setupContent() {
        Row(Grow.Std, Grow.Std) {
            blocksPanel()
            Splitter()
            Column(Grow.Std, Grow.Std) {
                val overlay = remember { ItemPopupMenu<Dockable>("Title-File-Overlay") }
                overlay()
                FileTitleBar(icon, dockable, isCollapsed, onCloseAction = { dockable ->
                    closeFile(dockable)
                }, onRightClick = { dockable, event ->
                    val menu = SubMenuItem("File-Context-Menu") { createMenu() }
                    overlay.hide()
                    overlay.show(Vec2f(event.screenPosition), menu, dockable)
                })
                if (!isCollapsed.use()) compose()
            }
        }
    }

    private fun UiScope.blocksPanel() {
        Column(blocksPreviewWidth.use(), Grow.Std) {
            modifier.margin(Dimensions.PaddingNormal)
                .padding(Dimensions.PaddingNormal)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingMedium))

            Row(Grow.Std) {
                modifier.padding(Dimensions.PaddingMedium)
                    .margin(Dimensions.PaddingMedium)
                    .background(RoundRectBackground(ColorTheme.UI.BackgroundElements, Dimensions.PaddingHuge))

                Image(Assets.Hollowengine.Textures.Gui.Icons.SEARCH) {
                    modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                        .alignY(AlignmentY.Center).margin(start = Dimensions.PaddingMedium)
                }

                TextField(filter.use()) {
                    modifier.alignY(AlignmentY.Center)
                        .size(Grow.Std, Grow.Std)
                        .colors(lineColor = Color.BLACK.withAlpha(0f), lineColorFocused = Color.BLACK.withAlpha(0f))
                        .hint("hollowengine.message.block_filter".lang)
                        .onEnterPressed { surface.requestFocus(null) }
                        .onChange { filter.set(it) }
                        .margin(start = Dimensions.PaddingMedium)
                }
            }

            LazyColumn(
                containerModifier = { it.backgroundColor(null) },
                scrollPaneModifier = { it.width(FitContent).margin(horizontal = Dimensions.PaddingNormal) },
                vScrollbarModifier = {
                    it.width(Dimensions.PaddingMedium).colors(
                        ColorTheme.UI.BackgroundElements,
                        ColorTheme.UI.BackgroundAccent,
                        Color.WHITE.withAlpha(0f),
                        ColorTheme.UI.BackgroundElements.withAlpha(0.3f),
                    )
                },
                hScrollbarModifier = {
                    it.height(Dimensions.PaddingMedium).colors(
                        ColorTheme.UI.BackgroundElements,
                        ColorTheme.UI.BackgroundAccent,
                        Color.WHITE.withAlpha(0f),
                        ColorTheme.UI.BackgroundElements.withAlpha(0.3f),
                    )
                },
                withHorizontalScrollbar = true
            ) {
                items(repository.rootCategory.items(editor)) {
                    with(editor) {
                        BlocksPanel.Item(it)
                    }
                }
            }
        }
    }

    private fun UiScope.Splitter() {
        val isSplitterHovered = remember(false)
        val dragStartWidth = remember(0f)
        Box(width = Dimensions.PaddingNormal, height = Grow.Std) {
            modifier
                .onEnter { isSplitterHovered.value = true }
                .onExit { isSplitterHovered.value = false }
                .onHover { PointerInput.cursorShape = CursorShape.RESIZE_E }
                .onDragStart {
                    dragStartWidth.value = blocksPreviewWidth.value.px
                }
                .onDrag {
                    val newWidthPx = dragStartWidth.value + it.pointer.dragMovement.x
                    blocksPreviewWidth.set(Dp.fromPx(newWidthPx))
                }
                .backgroundColor(Color.BLACK.withAlpha(0.0001f))
        }
    }

    override fun UiScope.compose() {
        Box(Grow.Std, Grow.Std) {
            with(editor) {
                EditorLayout {}
            }
        }
    }

    override fun close() {
        super.close()
        save()
        scope.cancel()
    }
}