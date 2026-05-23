package ru.hollowhorizon.hollowengine.common.scripting.katari

import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForCompoundNBT

data class KatariUiEvent(
    val player: ServerPlayer,
    val uiId: String,
    val payload: CompoundTag,
) : Event {
    companion object : EventHandler<KatariUiEvent>()
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
data class KatariUiEventPacket(
    private val uiId: String,
    private val payload: @Serializable(ForCompoundNBT::class) CompoundTag,
) : HollowPacket {
    override fun handle(player: Player) {
        val serverPlayer = player as? ServerPlayer ?: return
        KatariUiEvent.post(KatariUiEvent(serverPlayer, uiId, payload.copy()))
    }
}
