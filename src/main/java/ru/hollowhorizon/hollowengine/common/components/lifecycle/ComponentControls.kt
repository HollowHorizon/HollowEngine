package ru.hollowhorizon.hollowengine.common.components.lifecycle

import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf
import ru.hollowhorizon.hollowengine.common.registry.system.ResourceKey
import ru.hollowhorizon.hollowengine.common.utils.serialization.Format

fun ComponentDispatcher.attach(component: ResourceLocation): Boolean {
    if (component in `hollowcore$components`) return false
    `hollowcore$components`[component] = ComponentRegistry[ResourceKey(component)]().apply {
        onAttach()
    }
    ComponentsSyncPacket((this as Entity).id, mapOf(ComponentRegistry.getIdByLocation(component)!! to emptyMap())).sendTrackingEntityAndSelf(this)
    return true
}

fun ComponentDispatcher.detach(component: ResourceLocation) {
    val comp = `hollowcore$components`.remove(component) ?: return
    comp.onDetach()
    RemoveComponentPacket((this as Entity).id, ComponentRegistry.getIdByLocation(component)!!).sendTrackingEntityAndSelf(this)
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

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
class RemoveComponentPacket(val entityId: Int, val componentId: Int) : HollowPacket {
    override fun handle(player: Player) {
        val entity = player.level().getEntity(entityId) ?: return
        val dispatcher = entity as? ComponentDispatcher ?: return
        val componentLocation = ComponentRegistry.getLocationById(componentId) ?: return
        val component = dispatcher.`hollowcore$components`.remove(componentLocation) ?: return
        component.onDetach()
    }
}