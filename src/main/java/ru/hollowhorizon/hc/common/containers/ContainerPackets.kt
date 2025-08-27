package ru.hollowhorizon.hc.common.containers

import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.entity.BlockEntity
import ru.hollowhorizon.hc.api.ICapabilityDispatcher
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.network.HollowPacket
import ru.hollowhorizon.hc.common.network.HollowPacketHandler
import ru.hollowhorizon.hc.common.utils.mcText
import ru.hollowhorizon.hc.common.utils.nbt.ForBlockPos

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
class SyncEntityContainerPacket(
    private val entityId: Int,
    val capability: String, private val fromId: Int, private val toId: Int,
    val id: Int,
    private val leftButton: Boolean,
    private val doubleClick: Boolean,
    private val hasShift: Boolean,
) : HollowPacket {
    override fun handle(player: Player) {
        val serverPlayer = player as ServerPlayer

        val from = getContainer(serverPlayer, entityId, capability, fromId)
        val to = getContainer(serverPlayer, entityId, capability, toId)


        ServerContainerManager.clickSlot(player, from, to, id, leftButton, doubleClick, hasShift)

        from.setChanged()
        to.setChanged()
    }

    private fun getContainer(player: ServerPlayer, entityId: Int, capability: String, id: Int): Container {
        if (id == -1) return player.inventory

        val entity = player.serverLevel().getEntity(entityId)
        val cap = (entity as ICapabilityDispatcher).capabilities[capability] ?: error("Capability not found: $capability")

        if (cap.containers.size <= id) {
            player.connection.disconnect("Invalid inventory operation!".mcText)
        }

        return cap.containers[id]
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
class SyncBlockEntityContainerPacket(
    private val pos: @Serializable(ForBlockPos::class) BlockPos,
    val capability: String,
    private val fromId: Int,
    private val toId: Int,
    val id: Int,
    private val leftButton: Boolean,
    private val doubleClick: Boolean,
    private val hasShift: Boolean,
) : HollowPacket {
    override fun handle(player: Player) {
        val serverPlayer = player as ServerPlayer

        val from = getContainer(serverPlayer, pos, capability, fromId)
        val to = getContainer(serverPlayer, pos, capability, toId)


        ServerContainerManager.clickSlot(player, from, to, id, leftButton, doubleClick, hasShift)

        from.setChanged()
        to.setChanged()
    }

    private fun getContainer(player: ServerPlayer, pos: BlockPos, capability: String, id: Int): Container {
        if (id == -1) return player.inventory

        val entity = player.serverLevel().getBlockEntity(pos)
        val cap = (entity as ICapabilityDispatcher).capabilities[capability] ?: error("Capability not found: $capability")

        if (cap.containers.size <= id) {
            player.connection.disconnect("Invalid inventory operation!".mcText)
        }

        return cap.containers[id]
    }
}

fun CapabilityInstance.createSyncPacket(
    fromContainer: Container, toContainer: Container, id: Int,
    leftButton: Boolean, doubleClick: Boolean,
    hasShift: Boolean,
): HollowPacket {
    return when (val p = provider) {
        is Entity -> SyncEntityContainerPacket(
            p.id,
            this.javaClass.name,
            containers.indexOf(fromContainer),
            containers.indexOf(toContainer),
            id,
            leftButton,
            doubleClick,
            hasShift
        )

        is BlockEntity -> SyncBlockEntityContainerPacket(
            p.blockPos, this.javaClass.name,
            containers.indexOf(fromContainer),
            containers.indexOf(toContainer),
            id, leftButton, doubleClick, hasShift,
        )

        else -> throw UnsupportedOperationException("Unsupported provider: ${provider::class.qualifiedName}")
    }
}
