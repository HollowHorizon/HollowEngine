package ru.hollowhorizon.hollowengine.common.components.lifecycle

import kotlinx.serialization.Serializable
import net.minecraft.nbt.Tag
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.components.markAllChanged
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityTrackingEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks.forceCast
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForTag
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat


@SubscribeEvent
fun onPlayerClone(event: PlayerEvent.Clone) {
    if (!event.wasDeath) return

    val old = event.oldPlayer as ComponentDispatcher
    val new = event.player as ComponentDispatcher

    new.transferFrom(old)
    old.container.detach()
}

@SubscribeEvent
fun onPlayerRespawn(event: PlayerEvent.Respawn) {
    val dispatcher = event.player as ComponentDispatcher
    dispatcher.container.markAllChanged()
}

@SubscribeEvent
fun onStartTracking(event: EntityTrackingEvent.Start) {
    val dispatcher = event.entity as ComponentDispatcher
    dispatcher.container.markAllChanged()
}

@SubscribeEvent
fun onChangeDimension(event: PlayerEvent.ChangeDimension) {
    // Синхронизируем данные игрока
    var dispatcher = event.player as ComponentDispatcher
    dispatcher.container.markAllChanged()
    // Синхронизируем данные сервера
    dispatcher = event.to as ComponentDispatcher
    dispatcher.container.markAllChanged()
}

@SubscribeEvent
fun onPlayerLoggedIn(event: PlayerEvent.Join) {
    val dispatcher = event.player.level() as ComponentDispatcher
    dispatcher.container.markAllChanged()
}

fun ComponentDispatcher.transferFrom(old: ComponentDispatcher) {
    old.container.components.forEach { (location, oldComponent) ->
        val newComponent: Component<*> = container.getOrAttach(location)

        oldComponent.properties.asSequence()
            .filter { (_, property) -> property.copyOnDeath }
            .forEach { (name, oldProperty) ->
                newComponent.properties[name]?.let { newProperty ->
                    newProperty.set(forceCast(oldProperty.get()))
                    newComponent.changedProperties += newProperty.name
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

            val component: Component<*> = dispatcher.container.getOrAttach(holder.key)

            props.forEach { (name, tag) ->
                component.properties[name]?.deserialize(NBTFormat, tag)
            }
        }
    }
}


@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
class SyncLevelComponentsPacket(
    val components: Map<Int, Map<String, @Serializable(ForTag::class) Tag>>,
) : HollowPacket {
    override fun handle(player: Player) {
        val dispatcher = player.level() as? ComponentDispatcher ?: return

        components.forEach { (componentId, props) ->
            val holder = ComponentRegistry.getHolder(componentId) ?: return@forEach

            val component: Component<*> = dispatcher.container.getOrAttach(holder.key)

            props.forEach { (name, tag) ->
                component.properties[name]?.deserialize(NBTFormat, tag)
            }
        }
    }
}