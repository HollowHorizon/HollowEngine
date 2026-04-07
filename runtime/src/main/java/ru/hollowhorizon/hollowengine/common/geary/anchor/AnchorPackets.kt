package ru.hollowhorizon.hollowengine.common.geary.anchor

import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
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
