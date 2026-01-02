package ru.hollowhorizon.hollowengine.client.gui.scripting.files.codeblocks

import de.fabmax.kool.modules.ui2.*
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
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
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
    val filter = mutableStateOf("")
    val isMinimized = mutableStateOf(false)

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

    private fun UiScope.blocksPanel() {
        Column(FitContent, Grow.Std) {
            modifier.margin(horizontal = Dimensions.PaddingNormal)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingMedium))

            Row(Grow.Std) {
                modifier.margin(Dimensions.PaddingMedium)

                Row(Grow.Std) {
                    modifier.padding(Dimensions.PaddingMedium)
                        .background(RoundRectBackground(ColorTheme.UI.BackgroundElements, Dimensions.PaddingHuge))

                    Image(Assets.Hollowengine.Textures.Gui.Icons.SEARCH) {
                        modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                            .alignY(AlignmentY.Center).margin(start = Dimensions.PaddingMedium)
                    }

                    TextField(filter.use()) {
                        modifier.alignY(AlignmentY.Center)
                            .size(Grow.Std, Grow.Std)
                            .colors(
                                lineColor = Color.BLACK.withAlpha(0f),
                                lineColorFocused = Color.BLACK.withAlpha(0f)
                            )
                            .hint("hollowengine.message.block_filter".lang)
                            .onEnterPressed { surface.requestFocus(null) }
                            .onChange { filter.set(it) }
                            .margin(start = Dimensions.PaddingMedium)
                    }
                }

                MinimizeButton {}
            }

            LazyColumn(
                containerModifier = { it.backgroundColor(null) },
                scrollPaneModifier = { it.width(Grow.Std).margin(horizontal = Dimensions.PaddingNormal) },
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

    private fun UiScope.MinimizeButton(body: UiScope.() -> Unit) {
        Box {
            modifier.padding(Dimensions.PaddingMedium)
                .margin(start = Dimensions.PaddingNormal)
                .alignY(AlignmentY.Center)

            val isHovered by modifier.hoverable()
            val color by animateColorAsState(if (isHovered) ColorTheme.UI.BackgroundAccent else ColorTheme.UI.BackgroundSecondary)
            modifier.background(RoundRectBackground(color, Dimensions.PaddingNormal))
                .onClick { if (it.pointer.isLeftButtonClicked) isMinimized.set(!isMinimized.use()) }

            Image(if (isMinimized.use()) icons.MAXIMIZE else icons.MINIMIZE) {
                modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                    .align(AlignmentX.Center, AlignmentY.Center)
            }

            body()
        }
    }

    override fun UiScope.compose() {
        Row(Grow.Std, Grow.Std) {
            if (!isMinimized.use()) blocksPanel()
            with(editor) {
                EditorLayout {
                    if (isMinimized.use()) MinimizeButton {
                        modifier.align(AlignmentX.Start, AlignmentY.Top)
                            .margin(Dimensions.PaddingMedium)
                    }
                }
            }
        }
    }

    override fun close() {
        super.close()
        save()
        scope.cancel()
    }
}