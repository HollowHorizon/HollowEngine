package ru.hollowhorizon.hollowengine.common.scripting.story

import net.minecraft.nbt.CompoundTag
import net.minecraftforge.common.util.INBTSerializable
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.deserializeNoInline
import ru.hollowhorizon.hc.client.utils.nbt.serializeNoInline
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class StoryVariable<T : Any>(var value: () -> T, val clazz: Class<T>) : INBTSerializable<CompoundTag>, ReadWriteProperty<Any?, () -> T> {

    override fun serializeNBT() = CompoundTag().apply {
        put("value", NBTFormat.serializeNoInline(value(), clazz))
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        val res = nbt.get("value") ?: return
        value = { NBTFormat.deserializeNoInline(res, clazz) }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): () -> T {
        return value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: () -> T) {
        this.value = value
    }
}

inline fun <reified T : Any> IContextBuilder.saveable(noinline any: () -> T): StoryVariable<T> {
    return StoryVariable(any, T::class.java).apply {
        this@saveable.stateMachine.variables += this
    }
}