package ru.hollowhorizon.hollowengine.client.gui.scripting.files.codeblocks

import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.KoolDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.EditorFile
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockRepository
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.*
import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.usecase.PersistRecoveredScriptUseCase
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockFormat
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockSerializer
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import java.io.ByteArrayInputStream

@OptIn(FlowPreview::class)
class CodeBlocksFile(filePath: String, bytes: ByteArray) : EditorFile(filePath) {
    private val scope = CoroutineScope(SupervisorJob() + KoolDispatchers.Frontend)
    private val changeEvents = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val repository = BlockRepository.create("Скрипт") {
        include(StandardModules.AllBasics)
        include(GeneratedComponentBlocksModule)
        include(NPCModule)
        include(EntityModule)
        include(PlayerModule)
        include(WorldModule)
    }
    private val persistRecoveredScript = PersistRecoveredScriptUseCase()
    val format = CodeBlockFormat(repository)
    val editor = BlockEditor(repository) {
        changeEvents.tryEmit(Unit)
    }
    val filter = mutableStateOf("")

    init {
        if (bytes.isNotEmpty()) {
            try {
                val report = format.loadBlocksWithRecovery(ByteArrayInputStream(bytes))
                editor.rootBlocks.addAll(report.blocks)
                editor.rootBlocks.forEach { repository.applyDisplayNames(it, editor) }

                if (report.hasIssues) {
                    val file = filePath.fromReadablePath()
                    val backup = persistRecoveredScript.execute(file, format, report)
                    HollowEngine.LOGGER.warn(
                        "Recovered codeblocks file {} with {} issue(s). Backup: {}",
                        filePath,
                        report.issues.size,
                        backup?.absolutePath ?: "n/a"
                    )
                }
            } catch (e: Exception) {
                HollowEngine.LOGGER.error("File $filePath cannot be loaded!", e)
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

    override fun UiScope.compose() {
        Row(Grow.Std, Grow.Std) {
            modifier.margin(Dimensions.PaddingNormal)

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

    override fun onKeyInput(event: KeyEvent) {
        editor.onKeyInput(event)
    }
}
