package ru.hollowhorizon.hollowengine.client.gui.scripting.files.scripts

import de.fabmax.kool.modules.ui2.Box
import de.fabmax.kool.modules.ui2.Grow
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.util.KoolDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileData
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockRepository
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.NPCModule
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.StandardModules
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockFormat
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockSerializer
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath

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
    }
    val format = CodeBlockFormat(repository)
    val editor = BlockEditor(repository) {
        changeEvents.tryEmit(Unit)
    }

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