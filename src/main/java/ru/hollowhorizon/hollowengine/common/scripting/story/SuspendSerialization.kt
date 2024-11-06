package ru.hollowhorizon.hollowengine.common.scripting.story

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.JavaHacks
import ru.hollowhorizon.hc.client.utils.currentServer
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.deserializeNoInline
import ru.hollowhorizon.hc.client.utils.nbt.serializeNoInline
import ru.hollowhorizon.hollowengine.compiler.suspendable.AsyncContext
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendContext

fun SuspendContext.serialize(): CompoundTag = CompoundTag().apply {
    putInt("index", index)
    putIntArray("async_ids", asyncControllers.toIntArray())
    put("properties", CompoundTag().apply {
        properties.forEach { (name, value) ->
            when (value) {
                is SuspendContext -> put(name, value.serialize())
                is AsyncContext -> put(name, value.context.serialize())
                is Entity -> putUUID(name, value.uuid)
                else -> {
                    try {
                        put(name, NBTFormat.serializeNoInline(JavaHacks.forceCast(value), value!!::class.java))
                    } catch (e: Exception) {
                        HollowCore.LOGGER.warn("Failed to serialize $value: ", e)
                    }
                }
            }
            if (value != null) putString("$name::class", value::class.java.name)
        }
    })
}

fun SuspendContext.deserialize(tag: CompoundTag): SuspendContext {
    index = tag.getInt("index")
    val props = tag.getCompound("properties")
    props.allKeys.filter { !it.endsWith("::class") }.forEach { name ->
        val type = Class.forName(props.getString("$name::class").ifEmpty { return@forEach })

        val property = props[name]
        if (property == null) {
            HollowCore.LOGGER.warn("Property $name not found in $props, trying to create new value")
            type.constructors.find { it.parameterCount == 0 }?.let {
                val instance = it.newInstance()
                properties[name] = instance
            } ?: type.kotlin.objectInstance?.let { instance ->
                properties[name] = instance
            }
            return@forEach
        }
        properties[name] = when {
            type == SuspendContext::class.java -> SuspendContext().deserialize(property as CompoundTag)
            type == AsyncContext::class.java -> AsyncContext(SuspendContext().deserialize(property as CompoundTag))
            Entity::class.java.isAssignableFrom(type) -> currentServer.overworld().getEntity(props.getUUID(name))
            else -> NBTFormat.deserializeNoInline(property, type)
        }
    }
    asyncControllers.clear()
    asyncControllers.addAll(tag.getIntArray("async_ids").toList())
    return this
}