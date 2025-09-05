package ru.hollowhorizon.hollowengine.common.components

import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.components.annotations.ComponentMeta
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.components.system.ComponentEvent
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityTrackingEvent
import ru.hollowhorizon.hollowengine.common.events.post
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntity
import ru.hollowhorizon.hollowengine.common.registry.system.keyOf
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForTag
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.common.utils.serialization.Format
import kotlin.reflect.full.findAnnotation


interface ComponentDispatcher {
    val `hollowcore$components`: MutableMap<ResourceLocation, Component<*>>
}

inline fun <reified T: Component<*>> ComponentDispatcher.getComponent(): T {
    val location = T::class.findAnnotation<ComponentMeta>()?.location?.rl ?: error("ComponentMeta annotation not found on ${T::class}")
    return `hollowcore$components`[location] as T
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
    if (this is Entity && !this.level().isClientSide) {
        val changedComponents = HashMap<Int, Map<String, Tag>>()
        `hollowcore$components`.forEach { (location, component) ->
            val props = component.properties.mapNotNull { (name, property) ->
                if (property.sync.shouldSync(property) && property.changed) {
                    property.changed = false
                    name to (property.serialize(NBTFormat) ?: return@mapNotNull null)
                } else null
            }.toMap()

            if (props.isNotEmpty()) {
                val id = ComponentRegistry.getIdByLocation(location)
                    ?: error("Component $location not registered!")
                changedComponents[id] = props
            }
        }

        if (changedComponents.isNotEmpty()) {
            ComponentsSyncPacket(this.id, changedComponents).sendTrackingEntity(this)
        }
    }
}

fun ComponentDispatcher.attachComponent(component: ResourceLocation) {
    val key = keyOf<() -> Component<*>>(component)
    if (ComponentRegistry.contains(key) && !`hollowcore$components`.containsKey(component)) {
        val comp = ComponentRegistry[key]()
        comp.provider = JavaHacks.forceCast(this)
        `hollowcore$components`[component] = comp
        comp.onAttach()
        ComponentEvent.Added(comp).post()
        ComponentsSyncPacket(
            (this as? Entity)?.id ?: return,
            mapOf(ComponentRegistry.getIdByLocation(component)!! to comp.properties.mapNotNull { (name, property) ->
                if (property.sync.shouldSync(property)) {
                    name to (property.serialize(NBTFormat) ?: return@mapNotNull null)
                } else null
            }.toMap())
        ).sendTrackingEntity(this)
    } else {
        error("Component $component not found or already attached!")
    }
}

fun ComponentDispatcher.detachComponent(component: ResourceLocation) {
    val comp = `hollowcore$components`.remove(component) ?: return
    comp.onDetach()
    ComponentEvent.Removed(comp).post()
    if (this is Entity) {
        RemoveComponentPacket(this.id, ComponentRegistry.getIdByLocation(component)!!).sendTrackingEntity(this)
    }
}

fun <T> ComponentDispatcher.editComponent(
    component: ResourceLocation,
    property: String,
    format: Format<T>,
    value: T,
) {
    val comp = `hollowcore$components`[component] ?: error("Component $component not found!")
    val prop = comp.properties[property] ?: error("Property $property not found!")
    prop.deserialize(format, value)
}

@SubscribeEvent
fun onStartTracking(event: EntityTrackingEvent.Start) {
    val dispatcher = event.entity as ComponentDispatcher
    dispatcher.`hollowcore$components`.values.forEach { it.properties.values.forEach { prop -> prop.changed = true } }
}


@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
class ComponentsSyncPacket(
    val entityId: Int,
    val properties: Map<Int, Map<String, @Serializable(ForTag::class) Tag>>,
) : HollowPacket {
    override fun handle(player: Player) {
        val entity = player.level().getEntity(entityId) ?: return
        val dispatcher = entity as? ComponentDispatcher ?: return
        properties.forEach { (componentId, props) ->
            val holder = ComponentRegistry.getHolder(componentId) ?: error("Component with id $componentId not found!")
            val component = dispatcher.`hollowcore$components`[holder.key.location] ?: holder.value().apply {
                provider = JavaHacks.forceCast(entity)
                dispatcher.`hollowcore$components`[holder.key.location] = this
                onAttach()
                ComponentEvent.Added(this).post()
            }
            props.forEach { (name, tag) ->
                val property = component.properties[name] ?: return@forEach
                property.deserialize(NBTFormat, tag)
                property.changed = false
            }
        }
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
class RemoveComponentPacket(val entityId: Int, val componentId: Int) : HollowPacket {
    override fun handle(player: Player) {
        val entity = player.level().getEntity(entityId) ?: return
        val dispatcher = entity as? ComponentDispatcher ?: return
        val componentLocation = ComponentRegistry.getLocationById(componentId) ?: return
        val component = dispatcher.`hollowcore$components`.remove(componentLocation) ?: return
        component.onDetach()
        ComponentEvent.Removed(component).post()
    }
}