package ru.hollowhorizon.hollowengine.common.util

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.deserializeNoInline
import ru.hollowhorizon.hc.client.utils.nbt.serializeNoInline

class Serializer<T : Any> {
    fun serialize(value: T): Tag {
        val clazz = value::class.java as Class<T>
        val clazzName = clazz.name
        return CompoundTag().apply {
            putString("clazz", clazzName)
            put("value", NBTFormat.serializeNoInline(value, clazz))
        }
    }

    operator fun invoke(value: T) = serialize(value)
    operator fun invoke(value: Tag) = deserialize(value)

    fun deserialize(tag: Tag): T {
        if (tag !is CompoundTag) throw IllegalStateException("Tag is not serializable!")
        val clazzName = tag.getString("clazz")
        val clazz = Class.forName(clazzName) as Class<T>
        return NBTFormat.deserializeNoInline(tag.get("value")!!, clazz)
    }
}

inline fun <reified T : Any> T.mcSerializer() = Serializer<T>()