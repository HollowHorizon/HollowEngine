package ru.hollowhorizon.hollowengine.common.geary.binding

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.geary.api.GearyRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.LevelSnapshot
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf
import ru.hollowhorizon.hollowengine.common.utils.PlayerPermissions
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForUuid
import java.util.*

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
data class LevelSnapshotPacket(
    val snapshot: LevelSnapshot,
) : HollowPacket {
    override fun handle(player: Player) {
        val level = player.level()
        NodeRuntimeState.init(level)
        NodeRuntimeState.service(level).materialize(snapshot)
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
data class EntitySnapshotPacket(
    val entityId: Int,
    val snapshot: EntitySnapshot,
) : HollowPacket {
    override fun handle(player: Player) {
        val level = player.level()
        val entity = level.getEntity(entityId)
        if (entity != null) {
            GearyRuntimeState.updateEntitySnapshot(entity, snapshot)
        } else {
            Minecraft.getInstance().coroutineScope.launch {
                var entity: Entity?
                do {
                    delay(50)
                    entity = level.getEntity(entityId)
                } while (entity == null)
                GearyRuntimeState.updateEntitySnapshot(entity, snapshot)
            }
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
data class LevelSnapshotRemovePacket(
    val snapshotId: @Serializable(ForUuid::class) UUID,
) : HollowPacket {
    override fun handle(player: Player) {
        val level = player.level()
        NodeRuntimeState.init(level)
        NodeRuntimeState.service(level).remove(snapshotId)
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
data class EntitySnapshotRemovePacket(
    val entityId: Int,
) : HollowPacket {
    override fun handle(player: Player) {
        val entity = player.level().getEntity(entityId) ?: return
        GearyRuntimeState.componentsById(entity).clear()
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
data class EntitySnapshotUpdatePacket(
    val entityId: Int,
    val snapshot: EntitySnapshot,
) : HollowPacket {
    override fun handle(player: Player) {
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) return
        val entity = player.level().getEntity(entityId) ?: return
        GearyRuntimeState.updateEntitySnapshot(entity, snapshot)
        EntitySnapshotPacket(entity.id, snapshot).sendTrackingEntityAndSelf(entity)
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
data class NodeTransformUpdatePacket(
    val snapshotId: @Serializable(ForUuid::class) UUID,
    val nodeId: @Serializable(ForUuid::class) UUID? = null,
    val transform: ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent,
) : HollowPacket {
    override fun handle(player: Player) {
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) return
        val server = player.server ?: return
        for (level in server.allLevels) {
            if (NodeRuntimeState.service(level)
                    .updateTransform(snapshotId, transform, nodeId = nodeId, syncToClients = true)
            ) {
                return
            }
        }
        HollowEngine.LOGGER.warn(
            "NodeTransformUpdatePacket ignored: snapshot {} not found in any server level",
            snapshotId
        )
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
data class NodeSnapshotUpdatePacket(
    val snapshotId: @Serializable(ForUuid::class) UUID? = null,
    val snapshot: LevelSnapshot? = null,
) : HollowPacket {
    constructor(snapshot: LevelSnapshot) : this(snapshot.id, snapshot)

    override fun handle(player: Player) {
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) return
        val id = snapshotId ?: return
        val snapshot = snapshot ?: return
        val server = player.server ?: return
        for (level in server.allLevels) {
            if (NodeRuntimeState.service(level).updateSnapshot(id, snapshot, syncToClients = true)) {
                return
            }
        }
        HollowEngine.LOGGER.warn("NodeSnapshotUpdatePacket ignored: snapshot {} not found in any server level", id)
    }
}
