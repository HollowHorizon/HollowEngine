package ru.hollowhorizon.hollowengine.common.geary.binding

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.geary.api.Component
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntityNodeSnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.util.PlayerPermissions
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForUuid
import java.util.*

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
data class NodeEntitySnapshotPacket(
    val snapshot: EntitySnapshot,
) : HollowPacket {
    override fun handle(player: Player) {
        val level = player.level()
        NodeRuntimeState.init(level)
        NodeRuntimeState.service(level).materialize(snapshot)
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
data class NodeEntityRemovePacket(
    val stableKey: @Serializable(ForUuid::class) UUID,
) : HollowPacket {
    override fun handle(player: Player) {
        val level = player.level()
        NodeRuntimeState.init(level)
        NodeRuntimeState.service(level).remove(stableKey)
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
data class NodeTransformUpdatePacket(
    val stableKey: @Serializable(ForUuid::class) UUID,
    val nodeId: @Serializable(ForUuid::class) UUID? = null,
    val transform: TransformComponent,
) : HollowPacket {
    override fun handle(player: Player) {
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) return
        val server = player.server ?: return
        var applied = false
        for (level in server.allLevels) {
            if (NodeRuntimeState.service(level).updateTransform(stableKey, transform, nodeId = nodeId, syncToClients = true)) {
                applied = true
                return
            }
        }
        if (!applied) {
            HollowEngine.LOGGER.warn("NodeTransformUpdatePacket ignored: snapshot {} node {} not found in any server level", stableKey, nodeId)
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
data class NodeSnapshotUpdatePacket(
    val snapshot: EntitySnapshot,
) : HollowPacket {
    override fun handle(player: Player) {
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) return
        val stableKey = snapshot.stableKeyOrNull() ?: return
        val server = player.server ?: return
        var applied = false
        for (level in server.allLevels) {
            if (NodeRuntimeState.service(level).updateSnapshot(stableKey, snapshot, syncToClients = true)) {
                applied = true
                return
            }
        }
        if (!applied) {
            HollowEngine.LOGGER.warn("NodeSnapshotUpdatePacket ignored: snapshot {} not found in any server level", stableKey)
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
data class NodeUpdatePacket(
    val stableKey: @Serializable(ForUuid::class) UUID,
    val node: EntityNodeSnapshot,
) : HollowPacket {
    override fun handle(player: Player) {
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) return
        val server = player.server ?: return
        var applied = false
        for (level in server.allLevels) {
            if (NodeRuntimeState.service(level).updateNode(stableKey, node, syncToClients = true)) {
                applied = true
                return
            }
        }
        if (!applied) {
            HollowEngine.LOGGER.warn("NodeUpdatePacket ignored: snapshot {} node {} not found in any server level", stableKey, node.id)
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
data class NodeRemovePacket(
    val stableKey: @Serializable(ForUuid::class) UUID,
    val nodeId: @Serializable(ForUuid::class) UUID,
) : HollowPacket {
    override fun handle(player: Player) {
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) return
        val server = player.server ?: return
        var applied = false
        for (level in server.allLevels) {
            if (NodeRuntimeState.service(level).removeNode(stableKey, nodeId, syncToClients = true)) {
                applied = true
                return
            }
        }
        if (!applied) {
            HollowEngine.LOGGER.warn("NodeRemovePacket ignored: snapshot {} node {} not found in any server level", stableKey, nodeId)
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
data class NodeComponentUpdatePacket(
    val stableKey: @Serializable(ForUuid::class) UUID,
    val nodeId: @Serializable(ForUuid::class) UUID,
    val component: @Polymorphic Component,
) : HollowPacket {
    override fun handle(player: Player) {
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) return
        val server = player.server ?: return
        var applied = false
        for (level in server.allLevels) {
            if (NodeRuntimeState.service(level).updateNodeComponent(stableKey, nodeId, component, syncToClients = true)) {
                applied = true
                return
            }
        }
        if (!applied) {
            HollowEngine.LOGGER.warn("NodeComponentUpdatePacket ignored: snapshot {} node {} not found in any server level", stableKey, nodeId)
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
data class NodeComponentRemovePacket(
    val stableKey: @Serializable(ForUuid::class) UUID,
    val nodeId: @Serializable(ForUuid::class) UUID,
    val componentTypeId: @Serializable(ForResourceLocation::class) ResourceLocation,
) : HollowPacket {
    override fun handle(player: Player) {
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) return
        val server = player.server ?: return
        var applied = false
        for (level in server.allLevels) {
            if (NodeRuntimeState.service(level).removeNodeComponent(stableKey, nodeId, componentTypeId, syncToClients = true)) {
                applied = true
                return
            }
        }
        if (!applied) {
            HollowEngine.LOGGER.warn("NodeComponentRemovePacket ignored: snapshot {} node {} component {} not found in any server level", stableKey, nodeId, componentTypeId)
        }
    }
}
