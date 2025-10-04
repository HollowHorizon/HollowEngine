package ru.hollowhorizon.hollowengine.common.components.lifecycle

import kotlinx.serialization.Serializable
import net.minecraft.nbt.Tag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.components.isClient
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentEntry
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityTrackingEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.network.sendAllInDimension
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf
import ru.hollowhorizon.hollowengine.common.registry.system.Holder
import ru.hollowhorizon.hollowengine.common.registry.system.ResourceKey
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForTag
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat


fun ComponentDispatcher.onTick() {
    `hollowcore$components`.values.forEach { it.onTick() }

    if(isClient) return

    val changedComponents = `hollowcore$components`.mapNotNull { (location, component) ->
        val props = component.properties.mapNotNull { (name, property) ->
            if (property.sync.shouldSync(property) && property.changed) {
                property.changed = false
                property.serialize(NBTFormat)?.let { name to it }
            } else null
        }.toMap()

        if (props.isNotEmpty()) {
            val id = ComponentRegistry.getIdByLocation(location) ?: run {
                HollowCore.LOGGER.warn("Component {} not registered!", location)
                return@mapNotNull null
            }
            id to props
        } else null
    }.toMap()

    sync(changedComponents)
}

fun ComponentDispatcher.sync(changedComponents: Map<Int, Map<String, @Serializable(ForTag::class) Tag>>) {
    if (changedComponents.isNotEmpty()) {
        when(this) {
            is Entity ->SyncEntityComponentsPacket(id, changedComponents).sendTrackingEntityAndSelf(this)
            is Level -> SyncLevelComponentsPacket(changedComponents).sendAllInDimension(this)
        }
    }
}

@SubscribeEvent
fun onPlayerClone(event: PlayerEvent.Clone) {
    if (!event.wasDeath) return

    val old = event.oldPlayer as ComponentDispatcher
    val new = event.player as ComponentDispatcher

    new.transferFrom(old)
}

@SubscribeEvent
fun onStartTracking(event: EntityTrackingEvent.Start) {
    val dispatcher = event.entity as ComponentDispatcher
    dispatcher.`hollowcore$components`.values.asSequence()
        .flatMap { it.properties.values }
        .forEach { prop -> prop.changed = true }
}

@SubscribeEvent
fun onChangeDimension(event: PlayerEvent.ChangeDimension) {
    var dispatcher = event.player as ComponentDispatcher
    dispatcher.`hollowcore$components`.values.asSequence()
        .flatMap { it.properties.values }
        .forEach { prop -> prop.changed = true }
    dispatcher = event.to as ComponentDispatcher
    dispatcher.`hollowcore$components`.values.asSequence()
        .flatMap { it.properties.values }
        .forEach { prop -> prop.changed = true }
}

@SubscribeEvent
fun onPlayerLoggedIn(event: PlayerEvent.Join) {
    val dispatcher = event.player.level() as ComponentDispatcher
    dispatcher.`hollowcore$components`.values.asSequence()
        .flatMap { it.properties.values }
        .forEach { prop -> prop.changed = true }
}

fun ComponentDispatcher.transferFrom(old: ComponentDispatcher) {
    old.`hollowcore$components`.forEach { (location, oldComponent) ->
        val newComponent = `hollowcore$components`.getOrPut(location) {
            ComponentRegistry[ResourceKey(location)]()
        }

        oldComponent.properties.asSequence()
            .filter { (_, property) -> property.copyOnDeath }
            .forEach { (name, oldProperty) ->
                newComponent.properties[name]?.let { newProperty ->
                    newProperty.set(JavaHacks.forceCast(oldProperty.get()))
                    newProperty.changed = true
                }
            }
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
class SyncEntityComponentsPacket(
    val entityId: Int,
    val components: Map<Int, Map<String, @Serializable(ForTag::class) Tag>>,
) : HollowPacket {
    override fun handle(player: Player) {
        val entity = player.level().getEntity(entityId) ?: player.takeIf { it.id == entityId } ?: return
        val dispatcher = entity as? ComponentDispatcher ?: return

        components.forEach { (componentId, props) ->
            val holder = ComponentRegistry.getHolder(componentId) ?: return@forEach

            val component = dispatcher.getOrAttachComponent(holder, dispatcher)

            props.forEach { (name, tag) ->
                component.properties[name]?.apply {
                    deserialize(NBTFormat, tag)
                    changed = false
                }
            }
        }
    }
}


@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
class SyncLevelComponentsPacket(
    val components: Map<Int, Map<String, @Serializable(ForTag::class) Tag>>,
): HollowPacket {
    override fun handle(player: Player) {
        val dispatcher = player.level() as? ComponentDispatcher ?: return

        components.forEach { (componentId, props) ->
            val holder = ComponentRegistry.getHolder(componentId) ?: return@forEach

            val component = dispatcher.getOrAttachComponent(holder, dispatcher)

            props.forEach { (name, tag) ->
                component.properties[name]?.apply {
                    deserialize(NBTFormat, tag)
                    changed = false
                }
            }
        }
    }

}

private fun <T: ComponentDispatcher> ComponentDispatcher.getOrAttachComponent(
    holder: Holder<ComponentEntry>,
    owner: T,
): Component<*> {
    return `hollowcore$components`.getOrPut(holder.key.location) {
        holder.value().apply {
            this.owner = JavaHacks.forceCast(owner)
            onAttach()
        }
    }
}