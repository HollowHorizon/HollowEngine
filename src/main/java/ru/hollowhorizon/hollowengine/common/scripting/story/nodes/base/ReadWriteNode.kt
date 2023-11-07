package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.nbt.CompoundTag
import net.minecraftforge.common.util.INBTSerializable
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.deserializeNoInline
import ru.hollowhorizon.hc.client.utils.nbt.serializeNoInline
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface IReadWriteNode<T: Any>: INBTSerializable<CompoundTag>, ReadWriteProperty<Any?, T> {
    var value: T
    val clazz get() = value::javaClass as Class<T>

    override fun serializeNBT() = CompoundTag().apply {
        put("value", NBTFormat.serializeNoInline(value, clazz))
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        value = NBTFormat.deserializeNoInline(nbt.get("value") ?: return, clazz)
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}