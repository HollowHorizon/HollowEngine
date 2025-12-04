package ru.hollowhorizon.hollowengine.common.codeblocks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.utils.currentServer

class BlockContext(val scope: CoroutineScope) {
    val variables = mutableMapOf<String, Any?>()

    private val _eventBus = MutableSharedFlow<Pair<String, Any?>>()
    val eventBus = _eventBus.asSharedFlow()

    suspend fun emitEvent(name: String, arg: Any? = null) {
        _eventBus.emit(name to arg)
    }
}

fun runScript(rootBlocks: List<CodeBlock>) {
    val context = BlockContext(currentServer.coroutineScope)

    rootBlocks.filterIsInstance<OnEventBlock>().forEach { it.listen(context) }

    context.scope.launch {
        rootBlocks.filter { it is PrintBlock }.forEach {
            it.execute(context)
        }
    }
}