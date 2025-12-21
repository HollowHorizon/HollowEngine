package ru.hollowhorizon.hollowengine.common.codeblocks

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.serialization.deserialize
import ru.hollowhorizon.hollowengine.common.utils.serialization.serialize
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class BlockFrame(val tag: CompoundTag = CompoundTag()) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<BlockFrame>
}

@DslMarker
annotation class CodeBlocksDSL

@CodeBlocksDSL
suspend inline fun <reified T : Any> remember(name: String, action: suspend () -> T): T {
    val frame = coroutineContext[BlockFrame.Key] ?: error("Block frame not found")
    frame.tag[name]?.let { return NBTFormat.deserialize(it) }

    val result = action()
    frame.tag.put(name, NBTFormat.serialize(result))
    return result
}

@CodeBlocksDSL
suspend fun forget(name: String): Boolean {
    val frame = coroutineContext[BlockFrame.Key] ?: error("Block frame not found")
    val hasTag = frame.tag.contains(name)
    frame.tag.remove(name)
    return hasTag
}