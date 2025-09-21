package ru.hollowhorizon.hollowengine.common.components.lifecycle

import kotlinx.serialization.Serializable
import net.minecraft.nbt.Tag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityTrackingEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf
import ru.hollowhorizon.hollowengine.common.registry.system.Holder
import ru.hollowhorizon.hollowengine.common.registry.system.ResourceKey
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForTag
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat


fun ComponentDispatcher.onTick() {
    `hollowcore$components`.values.forEach { it.onTick() }

    val entity = this as? Entity ?: return
    if (entity.level().isClientSide) return

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

    if (changedComponents.isNotEmpty()) {
        ComponentsSyncPacket(entity.id, changedComponents).sendTrackingEntityAndSelf(entity)
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
    val dispatcher = event.player as ComponentDispatcher
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
                    newProperty.value = JavaHacks.forceCast(oldProperty.value)
                    newProperty.changed = true
                }
            }
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
class ComponentsSyncPacket(
    val entityId: Int,
    val components: Map<Int, Map<String, @Serializable(ForTag::class) Tag>>,
) : HollowPacket {
    override fun handle(player: Player) {
        val entity = player.level().getEntity(entityId) ?: player.takeIf { it.id == entityId } ?: return
        val dispatcher = entity as? ComponentDispatcher ?: return

        components.forEach { (componentId, props) ->
            val holder = ComponentRegistry.getHolder(componentId) ?: return@forEach

            val component = dispatcher.getOrAttachComponent(holder, entity)

            props.forEach { (name, tag) ->
                component.properties[name]?.apply {
                    deserialize(NBTFormat, tag)
                    changed = false
                }
            }
        }
    }

    private fun ComponentDispatcher.getOrAttachComponent(
        holder: Holder<() -> Component<*>>,
        entity: Entity,
    ): Component<*> {
        return `hollowcore$components`.getOrPut(holder.key.location) {
            holder.value().apply {
                owner = JavaHacks.forceCast(entity)
                onAttach()
            }
        }
    }
}