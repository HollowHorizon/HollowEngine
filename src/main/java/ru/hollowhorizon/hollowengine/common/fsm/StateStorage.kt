package ru.hollowhorizon.hollowengine.common.fsm

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.common.utils.nbt.deserialize
import ru.hollowhorizon.hc.common.utils.nbt.serialize
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.input
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class StateStorage(val tag: CompoundTag): CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<StateStorage>
    override val key: CoroutineContext.Key<*> get() = Key
}

suspend inline fun <reified T> remember(crossinline initializer: () -> T): ReadWriteProperty<Any?, T> {
    val tag = coroutineContext[StateStorage.Key]?.tag ?: error("StateStorage not found!")
    return object: ReadWriteProperty<Any?, T> {
        var value: T? = null

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return value ?: (if(property.name in tag) NBTFormat.deserialize<T>(tag.get(property.name)!!) else initializer())
                .also { value = it }
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            this.value = value
            tag.put(property.name, NBTFormat.serialize(value))
        }

    }
}


suspend fun example(player: Player) {
    var message by remember { "Игрок пока ничего не написал" }

    message = player.input()

    // message будет сохранён
}
