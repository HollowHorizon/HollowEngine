package ru.hollowhorizon.hollowengine.common.codeblocks.execution

import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockFrame
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksDSL
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.ScriptInstance
import java.util.*
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class BlockFrameStackElement(val instance: ScriptInstance) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<BlockFrameStackElement>

    private var index = 0
    internal val frames = Stack<BlockFrame>()

    suspend fun <T> withScopedContext(action: suspend () -> T): T {
        val frame = if (index < frames.size) {
            frames[index] // Берем существующий для восстановления
        } else {
            frames.push(BlockFrame()) // Создаем новый, если это первый проход
        }

        index++ // Двигаем указатель внутрь

        try {
            return withContext(frame) { action() }
        } finally {
            // В теории это не обязательно, поскольку при десериализации мы в любом случае создаём новый контекст
            index--

            // Если произошла отмена корутины, то мы должны её сохранить в оригинальном виде
            // Вообще сохранение должно произойти до `cancel()`, но лучше перестраховаться
            if (coroutineContext.isActive) {
                frames.pop()
            }
        }
    }
}

@CodeBlocksDSL
suspend fun <T> scoped(block: suspend () -> T): T {
    val context = coroutineContext[BlockFrameStackElement.Key] ?: error("Context not found!")

    return context.withScopedContext {
        block()
    }
}