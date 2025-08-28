package ru.hollowhorizon.hollowengine.common.components

import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.components.annotations.ComponentMeta
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.components.system.ComponentEvent
import ru.hollowhorizon.hollowengine.common.events.post
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntity
import ru.hollowhorizon.hollowengine.common.registry.system.keyOf
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks
import ru.hollowhorizon.hollowengine.common.utils.isLogicalClient
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.reflect.full.findAnnotation


interface ComponentDispatcher {
    val `hollowcore$components`: MutableMap<ResourceLocation, Component<*>>


    fun <T : Component<*>> addComponent(component: T) {
        val meta = component::class.findAnnotation<ComponentMeta>()
            ?: error("Required component ${component::class.simpleName} not registered via ComponentMeta")
        `hollowcore$components`[meta.location.rl] = component
        component.onAttach()
        component.provider = JavaHacks.forceCast(this)
        ComponentEvent.Added(component).post()
    }

    fun removeComponent(location: ResourceLocation): Boolean {
        val component = `hollowcore$components`[location] ?: return false
        val dependents = `hollowcore$components`.values.filter { component::class in it.dependencies }
        if (dependents.isNotEmpty()) {
            throw IllegalStateException("Cannot remove ${component::class.simpleName}, used by ${dependents.joinToString { it::class.simpleName ?: "unknown" }}")
        }
        component.onDetach()
        `hollowcore$components`.remove(location)
        ComponentEvent.Removed(component).post()
        if (!(this as Entity).level().isClientSide) RemoveClientsideComponentPacket(
            entityId = this.id,
            componentId = ComponentRegistry.getIdByLocation(location) ?: return true
        ).sendTrackingEntity(this)
        return true
    }
}

fun ComponentDispatcher.save(): CompoundTag {
    val tag = CompoundTag()
    `hollowcore$components`.forEach { (location, component) ->
        val componentTag = CompoundTag()
        componentTag.putBoolean($$"$enabled", component.enabled)
        component.properties.forEach { (name, property) ->
            if (property.save.shouldSave(property)) {
                componentTag.put(name, property.serialize(NBTFormat) ?: return@forEach)
                property.changed = false
            }
        }
        if (!componentTag.isEmpty) {
            tag.put(location.toString(), componentTag)
        }
    }
    return tag
}

fun ComponentDispatcher.load(tag: CompoundTag) {
    tag.allKeys.forEach { key ->
        val componentTag = tag.getCompound(key)
        ComponentRegistry.getOrNull(keyOf(key.rl))?.let {
            val component = it()
            component.provider = JavaHacks.forceCast(this)
            `hollowcore$components`[key.rl] = component
            val enabled = componentTag.getBoolean($$"$enabled")
            componentTag.remove($$"$enabled")
            component.enabled = enabled
            component.onAttach()
            componentTag.allKeys.forEach { name ->
                val property = component.properties[name] ?: return@forEach
                property.deserialize(NBTFormat, componentTag.get(name)!!)
            }
            ComponentEvent.Added(component).post()
        } ?: run {
            HollowEngine.LOGGER.warn("Component $key not found!")
        }
    }
}

fun ComponentDispatcher.sync() {
    `hollowcore$components`.forEach { (location, component) ->
        if (!component.enabled) return@forEach
        var hasChanges = false

        component.properties.forEach { (name, property) ->
            if (property.sync.shouldSync(property) && !isLogicalClient) {
                hasChanges = true
                SyncPropertyPacket(
                    entityId = (this as? Entity)?.id ?: return@forEach,
                    componentId = ComponentRegistry.getIdByLocation(location) ?: return@forEach,
                    propertyName = name,
                    propertyTag = property.serialize(NBTFormat) ?: return@forEach
                ).sendTrackingEntity(this)
            }
        }

        if (hasChanges) {
            ComponentEvent.Updated(component).post()
        }
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
class SyncPropertyPacket(
    val entityId: Int,
    val componentId: Int,
    val propertyName: String,
    val propertyTag: Tag,
) : HollowPacket {
    override fun handle(player: Player) {
        val entity = player.level().getEntity(entityId) as? ComponentDispatcher ?: return
        val componentKey = ComponentRegistry.run { getHolder(componentId)?.key?.location } ?: run {
            HollowCore.LOGGER.warn("Component with id $componentId not found!")
            return
        }
        val component = entity.`hollowcore$components`[componentKey] ?: ComponentRegistry[keyOf(componentKey)]().apply {
            provider = JavaHacks.forceCast(entity)
            entity.`hollowcore$components`[componentKey] = this
            onAttach()
            ComponentEvent.Added(this).post()
        }
        val property = component.properties[propertyName] ?: run {
            HollowCore.LOGGER.warn("Property $propertyName not found on component $componentKey!")
            return
        }
        property.deserialize(NBTFormat, propertyTag)
        ComponentEvent.Updated(component).post()
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
class RemoveClientsideComponentsPacket(
    val entityId: Int,
) : HollowPacket {
    override fun handle(player: Player) {
        val entity = player.level().getEntity(entityId) as? ComponentDispatcher ?: return
        entity.remove()
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
class RemoveClientsideComponentPacket(
    val entityId: Int,
    val componentId: Int,
) : HollowPacket {
    override fun handle(player: Player) {
        val entity = player.level().getEntity(entityId) as? ComponentDispatcher ?: return
        val componentKey = ComponentRegistry.run { getHolder(componentId)?.key?.location } ?: run {
            HollowCore.LOGGER.warn("Component with id $componentId not found!")
            return
        }
        entity.removeComponent(componentKey)
    }
}

fun ComponentDispatcher.remove() {
    `hollowcore$components`.forEach { (location, component) ->
        component.onDetach()
        ComponentEvent.Removed(component).post()
    }
    `hollowcore$components`.clear()
    if (!(this as Entity).level().isClientSide) RemoveClientsideComponentsPacket(entityId = this.id).sendTrackingEntity(
        this
    )
}
