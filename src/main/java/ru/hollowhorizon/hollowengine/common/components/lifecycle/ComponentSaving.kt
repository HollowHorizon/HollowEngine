package ru.hollowhorizon.hollowengine.common.components.lifecycle

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.registry.system.keyOf
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.rl

const val COMPONENT_TAG = "hollowengine:components"

fun ComponentDispatcher.save(): CompoundTag {
    val tag = CompoundTag()
    `hollowcore$components`.forEach { (location, component) ->
        val componentTag = CompoundTag()
        val properties = CompoundTag().apply {
            component.properties.forEach { (name, property) ->
                if (property.save.shouldSave(property)) {
                    property.serialize(NBTFormat)?.let { nbtValue ->
                        put(name, nbtValue)
                    }
                }
            }
        }
        val extras = CompoundTag().apply(component::saveExtras)
        componentTag.put("properties", properties)
        if (!extras.isEmpty) componentTag.put("extras", extras)
        tag.put(location.toString(), componentTag)
    }
    return tag
}

fun ComponentDispatcher.load(tag: CompoundTag) {
    tag.allKeys.forEach { key ->
        val componentTag = tag.getCompound(key)
        val properties = componentTag.getCompound("properties")
        val factory = ComponentRegistry.getOrNull(keyOf(key.rl))
        if (factory != null) {
            val component = factory().apply {
                owner = JavaHacks.forceCast(this@load)
            }
            `hollowcore$components`[key.rl] = component
            properties.allKeys.forEach { name ->
                component.properties[name]?.deserialize(NBTFormat, properties.get(name)!!)
            }
            component.loadExtras(componentTag.getCompound("extras"))
            component.onAttach()
        } else {
            HollowEngine.LOGGER.warn("Component $key not found!")
        }
    }
}