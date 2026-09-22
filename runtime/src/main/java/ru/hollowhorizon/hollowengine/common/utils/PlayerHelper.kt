package ru.hollowhorizon.hollowengine.common.utils

import kotlinx.serialization.Serializable
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.ui.notification.HollowNotifications
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForTextComponent

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class ToastPacket(val message: @Serializable(ForTextComponent::class) Component) : HollowPacket {
    override fun handle(player: Player) = player.sendToast(message)
}

fun Player.sendToast(message: Component) {
    if (this is ServerPlayer) ToastPacket(message).send(this)
    else HollowNotifications.info(message.string)
}