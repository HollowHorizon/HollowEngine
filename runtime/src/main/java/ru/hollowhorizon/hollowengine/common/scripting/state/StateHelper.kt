package ru.hollowhorizon.hollowengine.common.scripting.state

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.StringTag
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.serialization.serialize

internal suspend fun stateContext(): StateContext {
    return currentCoroutineContext()[StateContext.Key] ?: error("StateContext not found!")
}

internal suspend fun transition(target: String) {
    stateContext().nextState = target
}

suspend fun stateTag(): CompoundTag = stateContext().tag

suspend fun runOnce(name: String, block: suspend () -> Unit) {
    val tag = stateTag()
    val list = tag.getList("once_tasks$", 8)
    val hasEntry = list.map { (it as StringTag).asString }.contains(name)
    if (hasEntry) return

    block()

    list.add(StringTag.valueOf(name))
    tag.put("once_tasks$", list)
}

@OptIn(InternalSerializationApi::class)
suspend inline fun <reified T : Any> remember(name: String, block: suspend () -> T): T {
    val tag = stateTag()

    val serializer = T::class.serializer()

    tag.get(name)?.let {
        return NBTFormat.deserialize(serializer, it)
    }

    val value = block()
    tag.put(name, NBTFormat.serialize(value))
    return value
}

suspend fun forget(name: String): Boolean {
    val tag = stateTag()
    val exists = tag.contains(name)
    tag.remove(name)
    return exists
}