package ru.hollowhorizon.hollowengine.common.attachments.sync

import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.attachments.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
data class EntityComponentSyncPacket(
    val entityId: Int,
    val version: Long,
    val full: Boolean = false,
    val changed: EntitySnapshot = EntitySnapshot(),
    val removed: List<@Serializable(ForResourceLocation::class) ResourceLocation> = emptyList(),
) : HollowPacket {
    override fun handle(player: Player) {
        ComponentSync.receive(player.level(), this)
    }
}
