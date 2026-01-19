package ru.hollowhorizon.hollowengine.common.fleks

import com.github.quillraven.fleks.Snapshot
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.fleks.lookup.MinecraftEntityLookup
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler

@Serializable
sealed interface ComponentSyncPacket : HollowPacket {
    val entityId: Int

    val level get() = Minecraft.getInstance().level ?: error("Client level is not loaded yet!")
}


@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
data class ComponentUpdatePacket(
    override val entityId: Int,
    val snapshot: Snapshot
) : ComponentSyncPacket {
    override fun handle(player: Player) {
        val entity = level.fleks.inject<MinecraftEntityLookup>().getOrCreateById(entityId)
        level.fleks.loadSnapshotAdditive(entity, snapshot)
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
data class ComponentRemovePacket(
    override val entityId: Int,
    val componentTypeId: Int
) : ComponentSyncPacket {
    override fun handle(player: Player) {
        val entity = level.fleks.inject<MinecraftEntityLookup>().getOrCreateById(entityId)
        with(level.fleks) {
            entity.configure {
                entityService.compMasks[it.id].clear(componentTypeId)
                componentService.holderByIndexOrNull(componentTypeId)?.minusAssign(it)
            }
        }
    }
}