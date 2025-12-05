package ru.hollowhorizon.hollowengine.common.codeblocks

import de.fabmax.kool.util.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.NpcSayBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc.*
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.StandardModules
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockFormat
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockSerializer
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import java.io.File

class BlockContext(val scope: CoroutineScope) {
    val server = currentServer
    val variables = mutableMapOf<String, Any?>()

    private val _eventBus = MutableSharedFlow<Pair<String, Any?>>()
    val eventBus = _eventBus.asSharedFlow()

    suspend fun emitEvent(name: String, arg: Any? = null) {
        _eventBus.emit(name to arg)
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun runScript(file: File) {
    val repository = BlockRepository.create("Скрипт") {
        include(StandardModules.AllBasics)
        include {
            category("НИПы", Color("7cba00")) {
                block("Создать", ::SpawnNpcBlock)
                block("Идти", ::NpcMoveBlock)
                block("Смотреть", ::NpcLookBlock)
                block("Сказать", ::NpcSayBlock)
                block("Взаимодействовать", ::NpcInteractBlock)
                block("Удалить", ::DespawnNpcBlock)
            }
        }
    }
    val format = CodeBlockFormat(repository)
    val blocks = format.json.decodeFromStream(CodeBlockSerializer(format), file.inputStream())
    runScript(blocks)
}

var oldJob: Job? = null

fun runScript(rootBlocks: List<CodeBlock>) {
    oldJob?.cancel()
    val context = BlockContext(currentServer.coroutineScope)
    oldJob = context.scope.launch {
        rootBlocks.filter { it is StartBlock }.forEach {
            it.execute(context)
        }
    }
}