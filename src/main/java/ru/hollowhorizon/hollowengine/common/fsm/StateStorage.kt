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

suspend inline fun <reified T : Any> remember(name: String, initializer: () -> T): ReadWriteProperty<Any?, T> {
    val tag = coroutineContext[StateStorage]?.tag ?: error("StateStorage not found!")
    val variable =
        if (name in tag) NBTFormat.deserialize<T, Tag>(tag.get(name)!!)
        else initializer()

    return RememberValue(name, variable, serializer<T>(), tag)
}

class RememberValue<T : Any>(
    val name: String,
    internal var variable: T,
    private val type: KSerializer<T>,
    private val tag: CompoundTag,
) : ReadWriteProperty<Any?, T> {
    fun save() {
        tag.put(name, NBTFormat.serialize(type, variable))
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = variable

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        variable = value
        save()
    }
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