@file:Suppress("UNCHECKED_CAST")

package ru.hollowhorizon.hollowengine.common.components

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.components.lifecycle.RemoveEntityComponentPacket
import ru.hollowhorizon.hollowengine.common.components.lifecycle.RemoveLevelComponentPacket
import ru.hollowhorizon.hollowengine.common.components.lifecycle.SyncEntityComponentsPacket
import ru.hollowhorizon.hollowengine.common.components.lifecycle.SyncLevelComponentsPacket
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentEntry
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.components.registry.create
import ru.hollowhorizon.hollowengine.common.network.sendAllInDimension
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.nbt.loadAsNBT
import ru.hollowhorizon.hollowengine.common.utils.nbt.save
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.io.DataInputStream
import java.io.File

class ComponentContainer(private val provider: ComponentDispatcher) {
    val components: MutableMap<ResourceLocation, Component<*>> =
        Object2ObjectOpenHashMap<ResourceLocation, Component<*>>()

    operator fun <T : Component<*>> get(entry: ComponentEntry<*>): T? {
        val key = ComponentRegistry.getHolder(ComponentRegistry.getId(entry))?.key
            ?: return null
        return components[key] as? T
    }

    operator fun <T : Component<*>> get(capability: ResourceLocation): T? = components[capability] as? T
    fun <T : Component<*>> getOrAttach(component: ResourceLocation): T {
        if (attach(component)) get<T>(component)?.let { return it }
        error("Component '$component' is not attached yet")
    }

    fun attach(location: ResourceLocation, callAttach: Boolean = true): Boolean {
        if (location in components) return true
        val isScript = location.namespace == "hollowengine" && location.path.startsWith("scripts/")
        val factory = ComponentRegistry.getOrNull(location) ?: run {
            HollowCore.LOGGER.warn("Component $location not found in registry")
            return false
        }

        if (!factory.targetType.java.isAssignableFrom(provider.javaClass)) {
            HollowCore.LOGGER.warn("Component '$location' owner is not an instance of ${provider.javaClass}")
            return false
        }

        val instance = factory.create(provider).let {
            if (isScript) ScriptableComponent(provider, location, JavaHacks.forceCast(it))
            else it
        }

        components[location] = instance
        if (callAttach) instance.onAttach()

        if (!provider.isClient) markAllChanged()

        return true
    }

    fun detach() {
        components.keys.forEach { key -> detach(key) }
    }

    fun detach(location: ResourceLocation) {
        val component = components.remove(location) ?: return
        component.onDetach()

        if (provider.isClient) return

        when (provider) {
            is Entity -> RemoveEntityComponentPacket(
                provider.id,
                ComponentRegistry.getIdByLocation(location)!!
            ).sendTrackingEntityAndSelf(provider)

            is Level -> RemoveLevelComponentPacket(
                ComponentRegistry.getIdByLocation(location)!!,
            ).sendAllInDimension(provider)
        }
    }

    fun update() {
        var sync = false

        components.values.forEach { component ->
            component.onTick()

            if (!provider.isClient && component.changedProperties.isNotEmpty()) sync = true
        }

        if (sync) sync()
    }

    fun save(): CompoundTag {
        val allComponents = CompoundTag()
        components.forEach { (location, component) ->
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
            componentTag.put("properties", properties)
            componentTag.put("extras", CompoundTag().apply(component::serialize))
            allComponents.put(location.toString(), componentTag)
        }
        return allComponents
    }

    fun load(tag: CompoundTag) {
        tag.allKeys.forEach { key ->
            val componentTag = tag.getCompound(key)
            val properties = componentTag.getCompound("properties")

            if (attach(key.rl, false)) {
                val component: Component<*> = get(key.rl) ?: error("Component $key does not exist")
                components[key.rl] = component
                properties.allKeys.forEach { name ->
                    component.properties[name]?.deserialize(NBTFormat, properties.get(name)!!)
                }
                component.deserialize(componentTag.getCompound("extras"))
                component.onAttach()
            } else {
                HollowCore.LOGGER.warn("Component $key not found in registry")
            }
        }
    }

    private fun sync() {
        val packet = HashMap<Int, Map<String, Tag>>()

        components.forEach { (key, component) ->
            val props = component.changedProperties.mapNotNull { property ->
                property to (component.properties[property]?.serialize(NBTFormat) ?: return@mapNotNull null)
            }.toMap()

            if (props.isNotEmpty()) {
                packet[ComponentRegistry.getIdByLocation(key) ?: error("Component $key not registered")] = props
                component.changedProperties.clear()
            }
        }

        when (provider) {
            is Entity -> SyncEntityComponentsPacket(provider.id, packet).sendTrackingEntityAndSelf(provider)
            is Level -> SyncLevelComponentsPacket(packet).sendAllInDimension(provider)
        }
    }

    companion object {
        const val COMPONENT_TAG = "hollowengine:components"
    }
}

fun ComponentContainer.save(file: File) {
    file.outputStream().use { save().save(it) }
}

fun ComponentContainer.load(file: File) {
    val tag = if (file.exists()) {
        file.inputStream().use { DataInputStream(it).loadAsNBT() as CompoundTag }
    } else {
        CompoundTag()
    }

    load(tag)
}

fun ComponentContainer.markAllChanged() {
    components.values.forEach { component ->
        component.changedProperties.addAll(component.properties.keys)
    }
}