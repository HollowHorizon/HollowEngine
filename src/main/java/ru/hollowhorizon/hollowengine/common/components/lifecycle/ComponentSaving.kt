@file:JvmName("ComponentSaving")

package ru.hollowhorizon.hollowengine.common.components.lifecycle

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.components.registry.create
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.nbt.loadAsNBT
import ru.hollowhorizon.hollowengine.common.utils.nbt.save
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.io.DataInputStream
import java.io.File

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

fun ComponentDispatcher.save(file: File) {
    val tag = save()
    file.outputStream().use { tag.save(it) }
}

fun ComponentDispatcher.load(file: File) =
    load(file.inputStream().use { DataInputStream(it).loadAsNBT() as CompoundTag })

fun ComponentDispatcher.load(tag: CompoundTag) {
    tag.allKeys.forEach { key ->
        val componentTag = tag.getCompound(key)
        val properties = componentTag.getCompound("properties")
        val factory = ComponentRegistry.getOrNull(key.rl)
        if (factory != null) {
            val component = factory.create(this)

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