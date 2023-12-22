package ru.hollowhorizon.hollowengine.common.scripting.story

import net.minecraft.nbt.CompoundTag
import net.minecraftforge.common.util.INBTSerializable
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.deserializeNoInline
import ru.hollowhorizon.hc.client.utils.nbt.serializeNoInline
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class StoryVariable<T : Any>(var value: () -> T, val clazz: Class<T>, val manager: StoryStateMachine) :
    INBTSerializable<CompoundTag>, ReadWriteProperty<Any?, T> {

    override fun serializeNBT() = CompoundTag().apply {
        put("value", NBTFormat.serializeNoInline(value(), clazz))
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        val res = nbt.get("value") ?: return
        value = { NBTFormat.deserializeNoInline(res, clazz) }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        assert(!manager.isStarted) { "Variable $property is used before starting the story!" }
        return value()
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        assert(!manager.isStarted) { "Variable $property is used before starting the story!" }
        this.value = { value }
    }
}

class GlobalProperty<T : Any>(val value: () -> T, val clazz: Class<T>, val manager: StoryStateMachine) :
    ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val extra = manager.team.extraData["hollowengine_global_properties"] as? CompoundTag
        val variable = extra?.get(property.name)
        return if (variable != null) {
            NBTFormat.deserializeNoInline(variable, clazz)
        } else {
            value()
        }
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        val extra = (manager.team.extraData["hollowengine_global_properties"] as? CompoundTag) ?: CompoundTag().apply {
            manager.team.extraData.put("hollowengine_global_properties", this)
        }
        extra.put(property.name, NBTFormat.serializeNoInline(value, clazz))
        manager.team.save()
    }

}

inline fun <reified T : Any> IContextBuilder.global(noinline any: () -> T) =
    GlobalProperty<T>(any, T::class.java, stateMachine)

inline fun <reified T : Any> IContextBuilder.saveable(noinline any: () -> T): StoryVariable<T> {
    return StoryVariable(any, T::class.java, stateMachine).apply {
        this@saveable.stateMachine.variables += this
    }
}