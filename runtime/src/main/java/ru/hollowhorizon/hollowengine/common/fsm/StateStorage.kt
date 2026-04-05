package ru.hollowhorizon.hollowengine.common.fsm

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.serialization.deserialize
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class StateStorage(val tag: CompoundTag) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<StateStorage>

    override val key: CoroutineContext.Key<*> get() = Key
}

private fun waiter(checker: CompletableDeferred<Boolean>, condition: () -> Boolean) {
    currentServer.coroutineScope.launch {
        if (condition()) checker.complete(true)
        else {
            delay(50L)
            waiter(checker, condition)
        }
    }
}

suspend fun await(condition: () -> Boolean) {
    val checker = CompletableDeferred<Boolean>()

    waiter(checker, condition)

    checker.await()
}