package ru.hollowhorizon.hollowengine.common.scripting.story

import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.JavaHacks
import ru.hollowhorizon.hc.client.utils.nbt.*
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendContext

fun SuspendContext.serialize(): CompoundTag = CompoundTag().apply {
    putInt("index", index)
    put("properties", CompoundTag().apply {
        properties.forEach { (name, value) ->
            when (value) {
                is SuspendContext -> put(name, value.serialize())
                is Entity -> putUUID(name, value.uuid)
                is Vec3 -> put(name, NBTFormat.serialize(ForVec3, value))
                else -> {
                    try {
                        put(name, NBTFormat.serializeNoInline(JavaHacks.forceCast(value), value!!::class.java))
                    } catch (e: Exception) {
                        HollowCore.LOGGER.warn("Failed to serialize $value: ", e)
                    }
                }
            }
            if (value != null) putString("$name\$type", value::class.java.name)
        }
    })
}

fun SuspendContext.deserialize(tag: CompoundTag): SuspendContext {
    index = tag.getInt("index")
    val props = tag.getCompound("properties")
    props.allKeys.filter { !it.endsWith("\$type") }.forEach {
        val type = Class.forName(props.getString("$it\$type").ifEmpty { return@forEach })

        val property = props[it] ?: return@forEach
        properties[it] = if (type == SuspendContext::class.java) SuspendContext().deserialize(property as CompoundTag)
        else NBTFormat.deserializeNoInline(property, type)
    }
    return this
}

fun main() {
    @Serializable
    class Test(val bp: @Serializable(ForBlockPos::class) BlockPos)
    println(NBTFormat.serialize(ForBlockPos, BlockPos(1, 1, 1)))
}