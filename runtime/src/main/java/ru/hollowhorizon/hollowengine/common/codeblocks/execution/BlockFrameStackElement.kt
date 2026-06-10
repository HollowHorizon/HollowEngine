package ru.hollowhorizon.hollowengine.common.codeblocks.execution

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockFrame
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksDSL
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.ScriptInstance
import ru.hollowhorizon.hollowengine.common.coroutines.SerializableCoroutineContextElement
import java.util.*
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class BlockFrameStackElement(
    val instance: ScriptInstance,
) : AbstractCoroutineContextElement(Key), SerializableCoroutineContextElement {
    companion object Key : CoroutineContext.Key<BlockFrameStackElement>

    private var index = 0
    internal val frames = Stack<BlockFrame>()

    suspend fun <T> withScopedContext(action: suspend () -> T): T {
        val frame = if (index < frames.size) {
            frames[index]
        } else {
            frames.push(BlockFrame())
        }

        index++

        try {
            return withContext(frame) { action() }
        } finally {
            index--
            if (currentCoroutineContext().isActive) {
                frames.pop()
            }
        }
    }

    override fun save(tag: CompoundTag) {
        val framesList = ListTag()
        framesList.addAll(frames.map { it.tag })
        tag.put("frames", framesList)
    }

    override fun load(tag: CompoundTag) {
        frames.clear()
        val framesList = tag.getList("frames", 10)
        frames.addAll(framesList.map { BlockFrame(it as CompoundTag) })
    }

    internal fun currentBlockId(): UUID? {
        val frame = frames.lastOrNull() ?: return null
        return if (frame.tag.contains("uuid")) frame.tag.getUUID("uuid") else null
    }
}

@CodeBlocksDSL
suspend fun <T> scoped(block: suspend () -> T): T {
    val context = currentCoroutineContext()[BlockFrameStackElement.Key] ?: error("Context not found!")

    return context.withScopedContext {
        block()
    }
}
