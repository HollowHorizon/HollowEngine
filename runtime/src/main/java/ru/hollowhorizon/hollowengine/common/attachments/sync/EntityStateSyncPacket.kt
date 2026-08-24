@file:UseSerializers(ForResourceLocation::class, ForCompoundNBT::class)

package ru.hollowhorizon.hollowengine.common.attachments.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.attachments.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForCompoundNBT
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
data class EntityStateSyncPacket(
    val entityId: Int,
    val version: Long,
    val full: Boolean = false,
    val changed: EntitySnapshot = EntitySnapshot(),
    val removed: List<ResourceLocation> = emptyList(),
    val dataChanged: CompoundTag = CompoundTag(),
    val dataRemoved: List<String> = emptyList(),
) : HollowPacket {
    override fun handle(player: Player) {
        EntityStateSync.receive(player.level(), this)
    }
}
