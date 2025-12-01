@file:JvmName("ComponentControls")

package ru.hollowhorizon.hollowengine.common.components.lifecycle

import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler


@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
class RemoveEntityComponentPacket(val entityId: Int, val componentId: Int) : HollowPacket {
    override fun handle(player: Player) {
        val entity = player.level().getEntity(entityId) ?: return
        val dispatcher = entity as? ComponentDispatcher ?: return
        val componentLocation = ComponentRegistry.getLocationById(componentId) ?: return
        dispatcher.container.detach(componentLocation)
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
class RemoveLevelComponentPacket(val componentId: Int) : HollowPacket {
    override fun handle(player: Player) {
        val level = player.level() ?: return
        val dispatcher = level as? ComponentDispatcher ?: return
        val componentLocation = ComponentRegistry.getLocationById(componentId) ?: return
        dispatcher.container.detach(componentLocation)
    }
}