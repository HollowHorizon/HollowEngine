@file:JvmName("ComponentControls")

package ru.hollowhorizon.hollowengine.common.components.lifecycle

import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.components.components
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentEntry
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.components.registry.create
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.network.sendAllInDimension
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf
import ru.hollowhorizon.hollowengine.common.utils.serialization.Format


fun ComponentDispatcher.attach(component: ResourceLocation): Boolean {
    if (component in `hollowcore$components`) return false
    val container = ComponentRegistry[component]

    if (!container.type.java.isAssignableFrom(this.javaClass)) {
        HollowCore.LOGGER.warn("Component '$component' owner is not an instance of ${this.javaClass}")
        return false
    }

    `hollowcore$components`[component] = container.create(this)
        .apply { onAttach() }
    sync(mapOf(ComponentRegistry.getIdByLocation(component)!! to emptyMap()))
    return true
}

fun ComponentDispatcher.detach(component: ResourceLocation) {
    val comp = `hollowcore$components`.remove(component) ?: return
    comp.onDetach()
    when (this) {
        is Entity -> RemoveEntityComponentPacket(
            id,
            ComponentRegistry.getIdByLocation(component)!!
        ).sendTrackingEntityAndSelf(this)

        is Level -> RemoveLevelComponentPacket(
            ComponentRegistry.getIdByLocation(component)!!
        ).sendAllInDimension(this)
    }
}

fun <T> ComponentDispatcher.edit(
    component: ResourceLocation,
    property: String,
    format: Format<T>,
    value: T,
) {
    val comp = `hollowcore$components`[component] ?: error("Component $component not found!")
    val prop = comp.properties[property] ?: error("Property $property not found!")
    prop.deserialize(format, value)
}

fun <T: Component<*>> Any.get(component: ComponentEntry<*>): T? {
    val id = ComponentRegistry.getId(component).takeIf { it != -1 } ?: return null
    val location = ComponentRegistry.getHolder(id)?.key ?: return null
    return (this as ComponentDispatcher).components[location] as T
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
class RemoveEntityComponentPacket(val entityId: Int, val componentId: Int) : HollowPacket {
    override fun handle(player: Player) {
        val entity = player.level().getEntity(entityId) ?: return
        val dispatcher = entity as? ComponentDispatcher ?: return
        val componentLocation = ComponentRegistry.getLocationById(componentId) ?: return
        val component = dispatcher.`hollowcore$components`.remove(componentLocation) ?: return
        component.onDetach()
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
class RemoveLevelComponentPacket(val componentId: Int) : HollowPacket {
    override fun handle(player: Player) {
        val level = player.level() ?: return
        val dispatcher = level as? ComponentDispatcher ?: return
        val componentLocation = ComponentRegistry.getLocationById(componentId) ?: return
        val component = dispatcher.`hollowcore$components`.remove(componentLocation) ?: return
        component.onDetach()
    }
}