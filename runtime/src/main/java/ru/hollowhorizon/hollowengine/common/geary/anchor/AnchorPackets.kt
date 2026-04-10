package ru.hollowhorizon.hollowengine.common.geary.anchor

import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.util.PlayerPermissions
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForUuid
import java.util.UUID

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
data class AnchoredEntitySnapshotPacket(
    val snapshot: EntitySnapshot,
) : HollowPacket {
    override fun handle(player: Player) {
        val level = player.level()
        MaterializationRuntimeState.init(level)
        MaterializationRuntimeState.service(level).materialize(snapshot)
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
data class AnchoredEntityRemovePacket(
    val stableKey: @Serializable(ForUuid::class) UUID,
) : HollowPacket {
    override fun handle(player: Player) {
        val level = player.level()
        MaterializationRuntimeState.init(level)
        MaterializationRuntimeState.service(level).remove(stableKey)
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
data class AnchoredTransformUpdatePacket(
    val stableKey: @Serializable(ForUuid::class) UUID,
    val transform: TransformComponent,
) : HollowPacket {
    override fun handle(player: Player) {
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) return
        val server = player.server ?: return
        for (level in server.allLevels) {
            if (MaterializationRuntimeState.service(level).updateTransform(stableKey, transform, syncToClients = true)) {
                return
            }
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
data class AnchoredSnapshotUpdatePacket(
    val snapshot: EntitySnapshot,
) : HollowPacket {
    override fun handle(player: Player) {
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) return
        val stableKey = snapshot.stableKeyOrNull() ?: return
        val server = player.server ?: return
        for (level in server.allLevels) {
            if (MaterializationRuntimeState.service(level).updateSnapshot(stableKey, snapshot, syncToClients = true)) {
                return
            }
        }
    }
}
