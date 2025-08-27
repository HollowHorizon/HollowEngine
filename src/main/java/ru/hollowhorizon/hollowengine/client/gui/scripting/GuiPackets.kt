package ru.hollowhorizon.hollowengine.client.gui.scripting

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import org.apache.commons.io.FileUtils
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.literal
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForTextComponent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.startScript
import ru.hollowhorizon.hollowengine.common.scripting.stopScript
import java.io.File

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class ToastPacket(val message: @Serializable(ForTextComponent::class) Component) : HollowPacket {
    override fun handle(player: Player) = player.sendToast(message)
}

fun Player.sendToast(message: Component) {
    if (this !is ServerPlayer) Minecraft.getInstance().toasts.addToast(
        SystemToast(
            SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
            "Уведомление".literal,
            message
        )
    )
    else ToastPacket(message).send(this)
}