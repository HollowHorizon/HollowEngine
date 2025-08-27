package ru.hollowhorizon.hollowengine.common.fsm

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.common.capabilities.SyncableListImpl
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.nbt.deserialize
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.getLevel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class StateStorage(val tag: CompoundTag) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<StateStorage>

    override val key: CoroutineContext.Key<*> get() = Key
}

suspend inline fun <reified T : Any> remember(name: String, initializer: () -> T): ReadWriteProperty<Any?, T> {
    val tag = coroutineContext[StateStorage]?.tag ?: error("StateStorage not found!")
    val variable =
        if (name in tag) NBTFormat.deserialize<T>(tag.get(name)!!)
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

internal suspend inline fun <reified T : Any> rememberList(
    name: String,
    initializer: () -> MutableList<T>,
): RememberValue<MutableList<T>> {
    val tag = coroutineContext[StateStorage]?.tag ?: error("StateStorage not found!")
    val variable: MutableList<T> =
        if (name in tag) NBTFormat.deserialize(tag.get(name)!!)
        else initializer()

    val delegate = RememberValue(name, variable, ListSerializer(serializer<T>()) as KSerializer<MutableList<T>>, tag)
    val list = SyncableListImpl(variable, T::class.java) {
        delegate.save()
    }
    delegate.variable = list

    return delegate
}

suspend fun <T : LivingEntity> rememberEntity(name: String, initializer: () -> T): T {
    val tag = coroutineContext[StateStorage]?.tag ?: error("StateStorage not found!")
    val server = currentServer

    if (name in tag) {
        val context = tag.getCompound(name)
        val uuid = context.getUUID("uuid")
        val level = server.getLevel(context.getString("level"))
        await { level.getEntity(uuid) != null }
        return level.getEntity(uuid) as T
    } else {
        return initializer().apply {
            tag.put(name, CompoundTag().apply {
                putString("level", level().dimension().location().toString())
                putUUID("uuid", uuid)
            })
        }
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